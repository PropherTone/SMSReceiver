package com.sms.smsreceiver

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.sms.smsreceiver.databinding.ActivityServiceBinding
import com.sms.smsreceiver.databinding.InputViewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

const val TAG = "TAG"
const val PHONE_NUMBER = "PhoneNumber"

var phoneNumber = ""

class ServiceActivity : AppCompatActivity(), CoroutineScope by MainScope() {

    private val binding by lazy { ActivityServiceBinding.inflate(layoutInflater) }
    private var binder: SMSReceiveService.SMSBinder? = null
    private val con by lazy {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                binding.apply {
                    if (service is SMSReceiveService.SMSBinder) {
                        binder = service
                        service.observeSMS().observe(this@ServiceActivity) {
                            phoneNumber.text = it.phone
                            smsBody.text = it.body
                            uploadStatus.text = it.uploadResultCode.toString()
                        }
                        statues.apply {
                            text = "运行中"
                            setTextColor(resources.getColor(R.color.green, null))
                        }
                    } else {
                        statues.apply {
                            text = "服务断线"
                            setTextColor(resources.getColor(R.color.red, null))
                        }
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
                binding.statues.apply {
                    text = "服务断线"
                    setTextColor(resources.getColor(R.color.red, null))
                }
            }

        }
    }

    @SuppressLint("BatteryLife")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = applicationContext.packageName
        val manager = getSystemService(POWER_SERVICE) as PowerManager
        if (!manager.isIgnoringBatteryOptimizations(packageName)) startActivity(Intent().apply {
            action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            data = Uri.parse("package:$packageName")
        })
        setContentView(binding.root)
        if (checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
            && checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        ) {
            startSMSService()
        } else {
            xiaomiSMSPermission()
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_PHONE_STATE,
                ),
                0x10
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        try {
            if (requestCode == 0x10
                && permissions.contains(Manifest.permission.READ_SMS)
                && permissions.contains(Manifest.permission.RECEIVE_SMS)
                && permissions.contains(Manifest.permission.READ_PHONE_STATE)
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && grantResults[1] == PackageManager.PERMISSION_GRANTED
                && grantResults[2] == PackageManager.PERMISSION_GRANTED
            ) {
                startSMSService()
            } else finish()
        } catch (e: IndexOutOfBoundsException) {
            finish()
        }
    }

    private fun xiaomiSMSPermission() {
        if (Build.MANUFACTURER.equals("Xiaomi", true)) {
            try {
                val uri = Uri.parse("content://sms/inbox")
                val cr = contentResolver
                val projection = arrayOf("_id")
                cr.query(uri, projection, null, null, "date desc")?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("ApplySharedPref", "MissingPermission", "HardwareIds")
    private fun startSMSService() {
        launch {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            Log.d(TAG, "startSMSService:${telephonyManager.networkOperator}")
            val sharedPreferences = getSharedPreferences("SMS", MODE_PRIVATE)
            val re = sharedPreferences.getString(PHONE_NUMBER, null)?.let {
                if (it.isNotEmpty()) {
                    phoneNumber = it
                    true
                } else null
            } ?: telephonyManager.line1Number?.let {
                sharedPreferences.edit()
                    .putString(
                        PHONE_NUMBER,
                        it.replace("+86", "").also { number -> phoneNumber = number })
                    .commit()
                true
            } ?: sendSMS()
            if (re) {
                startService(Intent(this@ServiceActivity, SMSReceiveService::class.java))
                bindService(
                    Intent(this@ServiceActivity, SMSReceiveService::class.java),
                    con,
                    BIND_AUTO_CREATE
                )
                binding.phoneNum.text = phoneNumber
                binding.changePhoneNumber.setOnClickListener {
                    showChangePhoneNumber()
                }
            } else AlertDialog.Builder(this@ServiceActivity).setMessage("无法获取手机号")
                .setNegativeButton("退出") { dialog, _ ->
                    dialog.dismiss()
                    finish()
                }.create().show()
        }
    }

    private suspend fun sendSMS(): Boolean {
//        val smsManager = getSystemService(SmsManager::class.java) as SmsManager
//        when (operator) {
//            "46000", "46002" -> smsManager.sendTextMessage("10086",)
//            "46001" -> smsManager.sendTextMessage("10010",)
//            "46003" -> smsManager.sendTextMessage("10001",)
//            else -> Unit
//        }
        return getPhoneNumber()
    }

    @SuppressLint("ApplySharedPref")
    private suspend fun getPhoneNumber(): Boolean = suspendCancellableCoroutine {
        val inputBinding = InputViewBinding.inflate(layoutInflater)
        AlertDialog.Builder(this).setView(inputBinding.root)
            .setPositiveButton("确认") { dialog, _ ->
                val phone = inputBinding.phoneNumber.text.toString()
                if (phone.isEmpty()) {
                    Toast.makeText(this@ServiceActivity, "输入手机号", Toast.LENGTH_SHORT)
                        .show()
                    it.resumeWith(Result.success(false))
                }
                val sharedPreferences = getSharedPreferences("SMS", MODE_PRIVATE)
                sharedPreferences.edit()
                    .putString(
                        PHONE_NUMBER,
                        phone.replace("+86", "").also { number -> phoneNumber = number }
                    ).commit()
                dialog?.dismiss()
                it.resumeWith(Result.success(true))
            }.setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                it.resumeWith(Result.success(false))
            }.create().show()
    }

    @SuppressLint("ApplySharedPref")
    private fun showChangePhoneNumber() {
        val inputBinding = InputViewBinding.inflate(layoutInflater)
        AlertDialog.Builder(this).setView(inputBinding.root)
            .setPositiveButton("确认") { dialog, _ ->
                val phone = inputBinding.phoneNumber.text.toString()
                if (phone.isEmpty()) {
                    Toast.makeText(this@ServiceActivity, "输入手机号", Toast.LENGTH_SHORT)
                        .show()
                }
                val sharedPreferences = getSharedPreferences("SMS", MODE_PRIVATE)
                sharedPreferences.edit()
                    .putString(
                        PHONE_NUMBER,
                        phone.replace("+86", "").also { number -> phoneNumber = number }
                    ).commit()
                binding.phoneNum.text = phoneNumber
                binder?.phoneNumberChanged()
                dialog?.dismiss()
            }.setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }.create().show()
    }

}