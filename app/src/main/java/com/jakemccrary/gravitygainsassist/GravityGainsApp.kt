package com.jakemccrary.gravitygainsassist

import android.app.Application
import androidx.work.Configuration

class GravityGainsApp : Application(), Configuration.Provider {
    val appContainer: AppContainer by lazy(LazyThreadSafetyMode.NONE) {
        AppContainer.create(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(appContainer.workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        appContainer
    }
}
