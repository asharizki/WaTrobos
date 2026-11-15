package com.wmods.wppenhacer.scheduler

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.highcapable.kavaref.KavaRef.Companion.resolve
import com.highcapable.yukihookapi.extension.kavaref.hook
import com.highcapable.yukihookapi.hook.param.PackageParam
import java.util.concurrent.atomic.AtomicBoolean

/** Uses the same libxposed runtime as the rest of WaTrobos. */
object SchedulerHooks {
    private val installed = AtomicBoolean()

    @JvmStatic
    context(param: PackageParam)
    fun start(application: Application, loader: ClassLoader) {
        if (!installed.compareAndSet(false, true)) return
        try {
            val receiverType = loader.loadClass("com.whatsapp.alarmservice.AlarmBroadcastReceiver")
            val owner = receiverType.getMethod("onReceive", Context::class.java, Intent::class.java).declaringClass
            owner.resolve().firstMethod {
                name = "onReceive"
                parameters(Context::class, Intent::class)
            }.hook {
                before {
                    val intent = args[1] as? Intent
                    if (intent?.action == SchedulerRuntime.TICK && receiverType.isInstance(instance)) {
                        result = null
                        SchedulerRuntime.onTick(instance as BroadcastReceiver)
                    }
                }
            }
            SchedulerRuntime.start(application, loader)
        } catch (error: Throwable) {
            Log.e("WaTrobosScheduler", "Scheduler hook unavailable", error)
        }
    }
}
