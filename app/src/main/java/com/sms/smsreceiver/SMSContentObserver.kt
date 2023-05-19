package com.sms.smsreceiver

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.util.Log
import kotlinx.coroutines.channels.Channel


class SMSContentObserver(
    private val context: Context,
    private val messenger: Channel<SMS>,
    handler: Handler?
) : ContentObserver(handler) {

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "onChange: $uri")
        runCatching { getValidateCode() }
    }

    private fun getValidateCode() {
        context.contentResolver?.query(
            Uri.parse("content://sms/inbox"),
            arrayOf("address", "body"),
            "read=?",
            arrayOf("0"),
            "date desc"
        )?.apply {
            Log.d(TAG, "getValidateCode: ")
            if (!moveToFirst()) return
            val addressIndex = getColumnIndex("address")
            val bodyIndex = getColumnIndex("body")
            Log.d(TAG, "getValidateCode: first $addressIndex , $bodyIndex")
            if (addressIndex >= 0 && bodyIndex >= 0){
                messenger.trySend(SMS("10086", getString(bodyIndex)))
            }
        }?.close()
    }
}