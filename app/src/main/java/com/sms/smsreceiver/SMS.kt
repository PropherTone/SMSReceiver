package com.sms.smsreceiver

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName

@Keep
data class SMS(
    @SerializedName("phone") val phone: String,
    @SerializedName("sms_content") val body: String
)
