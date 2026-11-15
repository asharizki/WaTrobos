package com.wmods.wppenhacer.xposed.features.media

import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import com.wmods.wppenhacer.BuildConfig
import com.wmods.wppenhacer.xposed.core.Feature
import com.wmods.wppenhacer.xposed.features.others.MenuHome

class VideoStatusCutter(loader: ClassLoader, preferences: SharedPreferences) : Feature(loader, preferences) {
    override fun getPluginName() = "Pemotong Video Status"
    override fun doHook() {
        if (!xprefs.getBoolean("video_status_cutter", true)) return
        MenuHome.addMenuItem { menu, activity ->
            menu.add("Potong video status").setOnMenuItemClickListener {
                runCatching {
                    activity.startActivity(Intent().setComponent(ComponentName(BuildConfig.APPLICATION_ID,
                        "com.wmods.wppenhacer.video.VideoStatusActivity")))
                }
                true
            }
        }
    }
}
