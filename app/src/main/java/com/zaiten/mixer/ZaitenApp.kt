package com.zaiten.mixer

import android.app.Application
import com.zaiten.mixer.core.storage.SettingsStore

class ZaitenApp : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
    }
}
