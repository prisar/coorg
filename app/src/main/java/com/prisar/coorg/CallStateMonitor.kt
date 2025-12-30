package com.prisar.coorg

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi

interface CallStateCallback {
    fun onCallStarted(phoneNumber: String?)
    fun onCallEnded()
}

class CallStateMonitor(
    private val context: Context,
    private val callback: CallStateCallback
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private var phoneStateListener: PhoneStateListener? = null
    private var telephonyCallback: TelephonyCallback? = null
    private var isInCall = false

    fun register() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerTelephonyCallback()
        } else {
            registerPhoneStateListener()
        }
    }

    fun unregister() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            unregisterTelephonyCallback()
        } else {
            unregisterPhoneStateListener()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallback() {
        val callStateListener = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallStateChange(state)
            }
        }
        telephonyCallback = callStateListener
        telephonyManager.registerTelephonyCallback(context.mainExecutor, callStateListener)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun unregisterTelephonyCallback() {
        telephonyCallback?.let {
            telephonyManager.unregisterTelephonyCallback(it)
        }
        telephonyCallback = null
    }

    @Suppress("DEPRECATION")
    private fun registerPhoneStateListener() {
        phoneStateListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                handleCallStateChange(state, phoneNumber)
            }
        }
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    @Suppress("DEPRECATION")
    private fun unregisterPhoneStateListener() {
        phoneStateListener?.let {
            telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
        }
        phoneStateListener = null
    }

    private fun handleCallStateChange(state: Int, phoneNumber: String? = null) {
        when (state) {
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isInCall) {
                    isInCall = false
                    callback.onCallEnded()
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                if (!isInCall) {
                    isInCall = true
                    callback.onCallStarted(phoneNumber)
                }
            }
            TelephonyManager.CALL_STATE_RINGING -> {
            }
        }
    }
}
