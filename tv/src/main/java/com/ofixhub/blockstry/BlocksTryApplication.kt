package com.ofixhub.blockstry

import android.app.Application
import com.ofixhub.blockstry.shared.SettingsManager

class BlocksTryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
    }
}
