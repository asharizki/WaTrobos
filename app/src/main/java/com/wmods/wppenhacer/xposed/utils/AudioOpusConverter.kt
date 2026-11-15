package com.wmods.wppenhacer.xposed.utils

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.AudioFormat
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer
import kotlin.math.min

object AudioOpusConverter {

    data class StatusAudio(val file: File, val durationMs: Long, val waveform: ByteArray)

    /** A separate bounded path so importing status audio does not alter chat voice notes. */
    fun convertForStatus(input: File, output: File, startMs: Long = 0, endMs: Long = 30_000): StatusAudio? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoder = 0L
        var success = false
        try {
            require(startMs >= 0 && endMs > startMs && endMs - startMs <= 30_000)
            extractor.setDataSource(input.absolutePath)
            val track = selectAudioTrack(extractor)
            if (track < 0) throw IOException("No audio track")
            extractor.selectTrack(track)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val format = extractor.getTrackFormat(track)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(format, null, null, 0)
            decoder.start()
            encoder = nativeInitOpusEncoder(output.absolutePath, OPUS_SAMPLE_RATE, 1)
            if (encoder == 0L) throw IOException("Encoder unavailable")
            val info = MediaCodec.BufferInfo()
            val maxSamples = OPUS_SAMPLE_RATE * (endMs - startMs) / 1000
            var samples = 0L
            var inputDone = false
            var outputDone = false
            val peaks = ArrayList<Int>()
            var peak = 0
            var peakSamples = 0
            val started = SystemClock.elapsedRealtime()
            while (!outputDone && samples < maxSamples) {
                if (Thread.currentThread().isInterrupted || SystemClock.elapsedRealtime() - started > 75_000) {
                    throw IOException("Audio preparation timed out")
                }
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val decodedFormat = decoder.outputFormat
                    rate = decodedFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = decodedFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (decodedFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                        decodedFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                } else if (index >= 0) {
                    try {
                        val buffer = decoder.getOutputBuffer(index)
                        if (buffer != null && info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                            val bytesPerFrame = channels * if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                            val frames = info.size / bytesPerFrame
                            val first = kotlin.math.ceil((startMs * 1000 - info.presentationTimeUs) * rate / 1_000_000.0).toInt().coerceIn(0, frames)
                            val last = kotlin.math.ceil((endMs * 1000 - info.presentationTimeUs) * rate / 1_000_000.0).toInt().coerceIn(0, frames)
                            if (info.presentationTimeUs >= endMs * 1000) outputDone = true
                            buffer.position(info.offset + first * bytesPerFrame)
                            buffer.limit(info.offset + maxOf(first, last) * bytesPerFrame)
                            val pcm = resampleStatusPcm(buffer.slice(), rate, channels, encoding)
                            val length = min(pcm.size.toLong(), (maxSamples - samples) * 2).toInt()
                            if (length > 0) {
                                nativeEncodeOpus(encoder, pcm, length)
                                for (i in 0 until length step 2) {
                                    val sample = ((pcm[i].toInt() and 255) or (pcm[i + 1].toInt() shl 8)).toShort().toInt()
                                    peak = maxOf(peak, kotlin.math.abs(sample) * 100 / 32768)
                                    if (++peakSamples == 960) { peaks.add(peak); peak = 0; peakSamples = 0 }
                                }
                                samples += length / 2
                            }
                        }
                        outputDone = outputDone || info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    } finally {
                        decoder.releaseOutputBuffer(index, false)
                    }
                }
            }
            if (samples == 0L) throw IOException("Empty audio range")
            if (peakSamples > 0) peaks.add(peak)
            val waveform = ByteArray(64) { bin ->
                val first = bin * peaks.size / 64
                val end = maxOf(first + 1, (bin + 1) * peaks.size / 64).coerceAtMost(peaks.size)
                (peaks.subList(first.coerceAtMost(peaks.lastIndex), end).maxOrNull() ?: 0).coerceIn(0, 100).toByte()
            }
            success = true
            return StatusAudio(output, samples * 1000 / OPUS_SAMPLE_RATE, waveform)
        } catch (error: Throwable) {
            Log.e(TAG, "Status conversion failed: ${error.javaClass.simpleName}")
            return null
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { extractor.release() }
            if (encoder != 0L) runCatching { nativeCloseOpusEncoder(encoder) }
            if (!success) output.delete()
        }
    }

    private fun resampleStatusPcm(buffer: ByteBuffer, rate: Int, channels: Int, encoding: Int): ByteArray {
        require(rate > 0 && channels in 1..8)
        require(encoding == AudioFormat.ENCODING_PCM_16BIT || encoding == AudioFormat.ENCODING_PCM_FLOAT)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val floatPcm = encoding == AudioFormat.ENCODING_PCM_FLOAT
        val frames = buffer.remaining() / ((if (floatPcm) 4 else 2) * channels)
        if (frames == 0) return ByteArray(0)
        fun mono(frame: Int): Float {
            var total = 0f
            for (channel in 0 until channels) {
                val index = frame * channels + channel
                total += if (floatPcm) buffer.getFloat(index * 4).coerceIn(-1f, 1f) * 32767 else buffer.getShort(index * 2).toFloat()
            }
            return total / channels
        }
        val count = (frames.toLong() * OPUS_SAMPLE_RATE / rate).toInt()
        return ByteArray(count * 2).also { out ->
            for (i in 0 until count) {
                val position = i.toDouble() * rate / OPUS_SAMPLE_RATE
                val first = position.toInt().coerceAtMost(frames - 1)
                val fraction = (position - first).toFloat()
                val value = (mono(first) * (1 - fraction) + mono(min(first + 1, frames - 1)) * fraction).toInt().coerceIn(-32768, 32767)
                out[i * 2] = value.toByte()
                out[i * 2 + 1] = (value shr 8).toByte()
            }
        }
    }

    private const val TAG = "AudioOpusConverter"
    private const val OPUS_SAMPLE_RATE = 48_000
    private const val OPUS_CHANNEL_COUNT = 1
    private const val TIMEOUT_US = 10_000L

    init {
        try {
            System.loadLibrary("audio_opus_converter")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load audio_opus_converter", e)
        }
    }

    @JvmStatic
    private external fun nativeInitOpusEncoder(
        outputPath: String,
        sampleRate: Int,
        channels: Int
    ): Long

    @JvmStatic
    private external fun nativeEncodeOpus(handle: Long, pcmData: ByteArray, lengthBytes: Int)

    @JvmStatic
    private external fun nativeCloseOpusEncoder(handle: Long)

    @JvmStatic
    fun convert(filePath: String): File? {
        val outFile =
            File(File(filePath).parentFile, "voice_note_${System.currentTimeMillis()}.opus")

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var encoderHandle: Long = 0

        try {
            extractor.setDataSource(filePath)
            val audioTrack = selectAudioTrack(extractor)
            if (audioTrack < 0) throw IOException("No audio track found in URI")

            extractor.selectTrack(audioTrack)
            val inputFormat = extractor.getTrackFormat(audioTrack)
            val inputMime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IOException("No MIME type found")

            decoder = MediaCodec.createDecoderByType(inputMime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            encoderHandle =
                nativeInitOpusEncoder(outFile.absolutePath, OPUS_SAMPLE_RATE, OPUS_CHANNEL_COUNT)
            if (encoderHandle == 0L) {
                throw IOException("Failed to initialize native opus encoder")
            }

            transcode(extractor, decoder, encoderHandle)
            return outFile

        } catch (e: Throwable) {
            YukiLog.log(e)
            Log.e(TAG, "Conversion failed", e)
            if (outFile.exists()) outFile.delete()
            return null
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
            try {
                if (encoderHandle != 0L) nativeCloseOpusEncoder(encoderHandle)
            } catch (_: Exception) {
            }
        }
    }

    private fun transcode(extractor: MediaExtractor, decoder: MediaCodec, encoderHandle: Long) {
        val decoderInfo = MediaCodec.BufferInfo()
        var extractorDone = false
        var decoderDone = false
        var inputSampleRate = OPUS_SAMPLE_RATE
        var inputChannels = 1

        while (!decoderDone) {
            if (!extractorDone) {
                val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIdx >= 0) {
                    val inBuf = decoder.getInputBuffer(inIdx)
                    val sampleSize = inBuf?.let { extractor.readSampleData(it, 0) } ?: -1
                    if (sampleSize < 0) {
                        decoder.queueInputBuffer(
                            inIdx,
                            0,
                            0,
                            0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        extractorDone = true
                    } else {
                        val pts = extractor.sampleTime
                        decoder.queueInputBuffer(inIdx, 0, sampleSize, pts, 0)
                        extractor.advance()
                    }
                }
            }

            val outIdx = decoder.dequeueOutputBuffer(decoderInfo, TIMEOUT_US)
            if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val fmt = decoder.outputFormat
                inputSampleRate =
                    if (fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) else OPUS_SAMPLE_RATE
                inputChannels =
                    if (fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            } else if (outIdx >= 0) {
                val pcmBuf = decoder.getOutputBuffer(outIdx)
                val eos = (decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0

                if (decoderInfo.size > 0 && pcmBuf != null) {
                    pcmBuf.position(decoderInfo.offset)
                    pcmBuf.limit(decoderInfo.offset + decoderInfo.size)

                    val resampled = resampleAndDownmix(pcmBuf, inputSampleRate, inputChannels)
                    if (resampled.isNotEmpty()) {
                        nativeEncodeOpus(encoderHandle, resampled, resampled.size)
                    }
                }

                decoder.releaseOutputBuffer(outIdx, false)
                if (eos) decoderDone = true
            }
        }
    }

    private fun resampleAndDownmix(
        pcmBuf: ByteBuffer,
        inSampleRate: Int,
        inChannels: Int
    ): ByteArray {
        pcmBuf.order(ByteOrder.LITTLE_ENDIAN)
        val shortBuf = pcmBuf.asShortBuffer()
        val numInputFrames = shortBuf.remaining() / inChannels

        val outSampleRate = OPUS_SAMPLE_RATE
        if (numInputFrames <= 0) return ByteArray(0)

        val numOutputFrames = (numInputFrames.toLong() * outSampleRate / inSampleRate).toInt()
        val outBytes = ByteArray(numOutputFrames * 2)

        for (i in 0 until numOutputFrames) {
            val inPos = i.toFloat() * inSampleRate / outSampleRate
            val inIdx1 = inPos.toInt()
            val inIdx2 = min(inIdx1 + 1, numInputFrames - 1)
            val frac = inPos - inIdx1

            val s1 = getMonoSample(shortBuf, inIdx1, inChannels)
            val s2 = getMonoSample(shortBuf, inIdx2, inChannels)

            var interpolated = s1 + frac * (s2 - s1)

            if (interpolated > 32767.0f) interpolated = 32767.0f
            if (interpolated < -32768.0f) interpolated = -32768.0f

            val outSample = interpolated.toInt().toShort()
            outBytes[i * 2] = (outSample.toInt() and 0xFF).toByte()
            outBytes[i * 2 + 1] = ((outSample.toInt() shr 8) and 0xFF).toByte()
        }
        return outBytes
    }

    private fun getMonoSample(buf: ShortBuffer, frameIndex: Int, channels: Int): Float {
        return if (channels == 1) {
            buf.get(frameIndex).toFloat()
        } else {
            val left = buf.get(frameIndex * channels).toFloat()
            val right = buf.get(frameIndex * channels + 1).toFloat()
            (left + right) / 2.0f
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME)
            if (mime != null && mime.startsWith("audio/")) return i
        }
        return -1
    }
}
