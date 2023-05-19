package com.sms.smsreceiver

import androidx.annotation.Keep

@Keep
data class SMSStatus(val phone: String, val body: String, val uploadResultCode: Int)
