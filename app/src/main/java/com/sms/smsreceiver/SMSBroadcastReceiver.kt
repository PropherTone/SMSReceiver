package com.sms.smsreceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class SMSBroadcastReceiver(private val messenger: MutableSharedFlow<SMS>) : BroadcastReceiver(),
    CoroutineScope by CoroutineScope(Dispatchers.Default) {
    override fun onReceive(context: Context?, intent: Intent?) {
        val bundle = intent?.extras //通过getExtras()方法获取短信内容
        val format = intent?.getStringExtra("format")
        if (bundle != null) {
            //根据pdus关键字获取短信字节数组，数组内的每个元素都是一条短信
            (bundle.get("pdus") as Array<*>? ?: return).map {
                SmsMessage.createFromPdu(it as ByteArray, format)
            }.forEach {
                if (it == null) return@forEach
                launch {
                    messenger.emit(
                        SMS(
                            phoneNumber,
                            "${it.messageBody}\n${it.emailBody}\n${it.displayMessageBody}\n${System.currentTimeMillis()}"
                        )
                    )
                }
            }
        }
    }
}