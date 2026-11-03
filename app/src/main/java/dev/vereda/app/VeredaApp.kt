package dev.vereda.app

import android.app.Application
import android.content.Context

class VeredaApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocales.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
