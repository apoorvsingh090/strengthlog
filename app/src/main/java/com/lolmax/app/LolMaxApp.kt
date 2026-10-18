package com.lolmax.app

import android.app.Application
import com.lolmax.app.di.AppContainer

class LolMaxApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
