package com.sms.smsreceiver

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class Status(val phone: String, @SerializedName("expire_time") val expireTime: Int)
