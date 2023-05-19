package com.sms.smsreceiver

import androidx.annotation.Keep

@Keep
data class Status(val phone: String, val status: Int)
