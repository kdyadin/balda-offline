package com.kdyadin.balda

import android.app.Application
import com.kdyadin.balda.data.AppContainer

class BaldaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.warmUp()
    }
}
