package com.medbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.medbox.ui.AppRoot
import com.medbox.ui.MedBoxTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 拒绝也能用，只是收不到提醒 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        Reminders.ensureChannel(this)
        Reminders.refresh(this)
        askNotificationPermission()
        setContent {
            MedBoxTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户可能刚从系统设置里打开"允许精确闹钟"，回来后重排一次
        runCatching { Reminders.refresh(this) }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
