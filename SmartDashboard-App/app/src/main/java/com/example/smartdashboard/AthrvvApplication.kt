package com.example.smartdashboard

import android.app.Application

class AthrvvApplication : Application() {

    lateinit var repository: DashboardRepository
        private set

    lateinit var callStateMonitor: CallStateMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        repository = DashboardRepository.getInstance(this)
        callStateMonitor = CallStateMonitor(this)

        // Start call monitor (will safely no-op until READ_PHONE_STATE is granted)
        callStateMonitor.start()

        // Initial background ping to Flask server
        repository.checkConnection()
    }

    companion object {
        lateinit var instance: AthrvvApplication
            private set
    }
}

