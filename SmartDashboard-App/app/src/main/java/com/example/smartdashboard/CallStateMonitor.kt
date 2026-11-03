@file:Suppress("DEPRECATION")

package com.example.smartdashboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat

class CallStateMonitor(private val context: Context) {

    private val repository = DashboardRepository.getInstance(context)
    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private var isRegistered = false
    private var modernCallback: Any? = null
    @Suppress("DEPRECATION")
    private var legacyListener: PhoneStateListener? = null

    fun start() {
        if (isRegistered) return
        val tm = telephonyManager ?: return

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            repository.refreshPermissionStatuses(context)
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        handleCallState(state)
                    }
                }
                modernCallback = callback
                tm.registerTelephonyCallback(context.mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        handleCallState(state)
                    }
                }
                legacyListener = listener
                @Suppress("DEPRECATION")
                tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            }
            isRegistered = true
        } catch (_: SecurityException) {
            isRegistered = false
        } catch (_: Exception) {
            isRegistered = false
        }
    }

    fun stop() {
        if (!isRegistered) return
        val tm = telephonyManager ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (modernCallback as? TelephonyCallback)?.let {
                    tm.unregisterTelephonyCallback(it)
                }
                modernCallback = null
            } else {
                legacyListener?.let {
                    @Suppress("DEPRECATION")
                    tm.listen(it, PhoneStateListener.LISTEN_NONE)
                }
                legacyListener = null
            }
        } catch (_: Exception) {}
        isRegistered = false
    }

    private fun handleCallState(state: Int) {
        val (status, caller) = when (state) {
            TelephonyManager.CALL_STATE_RINGING -> Pair("ringing", "Phone call")
            TelephonyManager.CALL_STATE_OFFHOOK -> Pair("active", "Phone call")
            TelephonyManager.CALL_STATE_IDLE -> Pair("ended", "Phone call")
            else -> Pair("ended", "Phone call")
        }

        repository.postCallEvent(status = status, caller = caller)
    }
}
