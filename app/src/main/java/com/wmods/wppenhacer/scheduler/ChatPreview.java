package com.wmods.wppenhacer.scheduler;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;

/** Read-only, bounded previews from WhatsApp's existing local data. No downloads. */
final class ChatPreview {
    static String message(String text, int type, boolean outgoing) {
        String value = text == null ? "" : text.replaceAll("[\\p{Cntrl}]", " ").trim();
        String media;
        switch (type) {
            case 1: media = "Foto"; break;
            case 2: media = "Pesan suara / audio"; break;
            case 3: media = "Video"; break;
            case 4: media = "Kontak"; break;
            case 5: media = "Lokasi"; break;
            case 9: media = "Dokumen"; break;
            case 13: media = "GIF"; break;
            case 15: media = "Pesan dihapus"; break;
            case 20: media = "Stiker"; break;
            default: media = type == 0 ? "Pesan" : "Pesan nonteks";
        }
        if (value.isEmpty()) value = media;
        else if (type != 0) value = media + " \u00b7 " + value;
        if (outgoing) value = "Anda: " + value;
        return value.length() > 180 ? value.substring(0, 180) : value;
    }

    static String avatar(Context context, String phoneJid, String chatJid, boolean self) {
        File directory = new File(context.getFilesDir(), "Avatars");
        File[] candidates = self
                ? new File[]{new File(directory, "me.j"), new File(context.getFilesDir(), "me.jpg"), new File(directory, phoneJid + ".j")}
                : new File[]{new File(directory, phoneJid + ".j"), new File(directory, chatJid + ".j")};
        for (File file : candidates) {
            if (!file.isFile() || file.length() > 8 * 1024 * 1024) continue;
            Bitmap decoded = null, thumb = null;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), options);
                if (options.outWidth <= 0 || options.outHeight <= 0) continue;
                options.inSampleSize = 1;
                while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 128) options.inSampleSize *= 2;
                options.inJustDecodeBounds = false;
                decoded = BitmapFactory.decodeFile(file.getPath(), options);
                if (decoded == null) continue;
                // Avatars are square in WhatsApp; crop before shrinking if necessary.
                int side = Math.min(decoded.getWidth(), decoded.getHeight());
                Bitmap crop = Bitmap.createBitmap(decoded, (decoded.getWidth()-side)/2, (decoded.getHeight()-side)/2, side, side);
                thumb = Bitmap.createScaledBitmap(crop, 64, 64, true);
                if (crop != decoded && crop != thumb) crop.recycle();
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                for (int quality : new int[]{65, 45, 25}) {
                    buffer.reset();
                    thumb.compress(Bitmap.CompressFormat.JPEG, quality, buffer);
                    if (buffer.size() <= 1920) return Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP);
                }
            } catch (RuntimeException ignored) {
                // Missing/unreadable photos use the initials placeholder in the picker.
            } finally {
                if (thumb != null && thumb != decoded) thumb.recycle();
                if (decoded != null) decoded.recycle();
            }
        }
        return "";
    }
}
