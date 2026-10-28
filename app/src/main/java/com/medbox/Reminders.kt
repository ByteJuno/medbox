package com.medbox

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import java.time.ZonedDateTime

/**
 * 提醒中枢：用 AlarmManager 的精确闹钟在每天固定时间弹通知。
 * 应用被杀掉、手机重启后依然有效（闹钟在系统层）。
 */
object Reminders {
    const val CHANNEL_ID = "medbox_reminder"
    const val ACTION_ALARM = "com.medbox.ACTION_ALARM"
    const val ACTION_TAKEN = "com.medbox.ACTION_TAKEN"
    const val ACTION_MIDNIGHT = "com.medbox.ACTION_MIDNIGHT"

    private const val PREFS = "medbox_alarms"

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "吃药提醒",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "到点提醒你服药"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        nm.createNotificationChannel(channel)
    }

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.canScheduleExactAlarms() else true
    }

    /** 把所有药品的所有时间点重排一遍（药品增删改后调用） */
    fun refresh(context: Context) {
        Store.init(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        // 先取消旧闹钟
        prefs.getStringSet("codes", emptySet())?.forEach { code ->
            runCatching { am.cancel(alarmPending(context, code.toInt())) }
        }

        val codes = HashSet<String>()
        for (med in Store.meds) {
            med.times.forEachIndexed { index, time ->
                val code = codeOf(med.id, index)
                val parts = time.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: return@forEachIndexed
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: return@forEachIndexed
                schedule(
                    context = context,
                    am = am,
                    code = code,
                    medId = med.id,
                    index = index,
                    name = med.name,
                    dosage = med.dosage,
                    time = time,
                    triggerAt = nextTrigger(hour, minute)
                )
                codes.add(code.toString())
            }
        }
        prefs.edit().putStringSet("codes", codes).apply()

        // 小组件跟着刷新，并安排跨天（0 点）刷新，保证日期/进度不会停在昨天
        Widgets.refreshAll(context)
        scheduleMidnightTick(context)
    }

    /** 每天 0 点过一点刷新一次小组件（跨天后"今天"要重算），并自己续排下一次 */
    fun scheduleMidnightTick(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply { action = ACTION_MIDNIGHT }
        val pi = PendingIntent.getBroadcast(
            context, 987654, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val now = ZonedDateTime.now()
        var next = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(5)
        if (!next.isAfter(now)) next = next.plusDays(1)
        runCatching {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pi)
        }
    }

    /** 单个闹钟（响过之后排明天的） */
    fun scheduleOne(context: Context, code: Int, medId: Long, index: Int, time: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val med = Store.meds.firstOrNull { it.id == medId } ?: return
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return
        schedule(context, am, code, medId, index, med.name, med.dosage, time, nextTrigger(hour, minute))
    }

    private fun schedule(
        context: Context,
        am: AlarmManager,
        code: Int,
        medId: Long,
        index: Int,
        name: String,
        dosage: String,
        time: String,
        triggerAt: Long
    ) {
        val pi = alarmPending(context, code)
        // 再写入一次 extras，保证 receiver 拿到最新的药品信息
        val intent = alarmIntent(context, code).apply {
            putExtra("medId", medId)
            putExtra("index", index)
            putExtra("name", name)
            putExtra("dosage", dosage)
            putExtra("time", time)
        }
        val op = PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ) ?: pi
        runCatching {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, op)
            }
        }
    }

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val now = ZonedDateTime.now()
        var t = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    fun codeOf(medId: Long, index: Int): Int = (medId * 100L + index).toInt()

    private fun alarmIntent(context: Context, code: Int): Intent =
        Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra("code", code)
        }

    private fun alarmPending(context: Context, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code, alarmIntent(context, code),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun openAppPending(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    fun show(context: Context, code: Int, medId: Long, name: String, dosage: String, time: String) {
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val takenIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_TAKEN
            putExtra("code", code)
            putExtra("medId", medId)
            putExtra("time", time)
        }
        val takenPending = PendingIntent.getBroadcast(
            context, code + 500000, takenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (dosage.isBlank()) time else "$time · $dosage"
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("该吃药了：$name")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText("$text\n吃完记得点下面的「已服用」。"))
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openAppPending(context))
            .addAction(
                Notification.Action.Builder(null, "已服用", takenPending).build()
            )
            .build()
        runCatching { nm.notify(code, notification) }
    }

    fun cancel(context: Context, code: Int) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(code) }
    }
}

/** 闹钟到点 / 用户点「已服用」 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val code = intent.getIntExtra("code", 0)
        when (intent.action) {
            Reminders.ACTION_MIDNIGHT -> {
                Widgets.refreshAll(context)
                Reminders.scheduleMidnightTick(context)
            }
            Reminders.ACTION_TAKEN -> {
                val medId = intent.getLongExtra("medId", -1L)
                val time = intent.getStringExtra("time")
                if (medId > 0 && time != null) {
                    Store.mark(medId, Store.today(), time, Store.TAKEN)
                }
                Reminders.cancel(context, code)
            }
            else -> {
                val medId = intent.getLongExtra("medId", -1L)
                val index = intent.getIntExtra("index", 0)
                val name = intent.getStringExtra("name") ?: "药品"
                val dosage = intent.getStringExtra("dosage") ?: ""
                val time = intent.getStringExtra("time") ?: ""
                Reminders.show(context, code, medId, name, dosage, time)
                // 排明天的同一个闹钟
                if (medId > 0 && time.isNotEmpty()) {
                    Reminders.scheduleOne(context, code, medId, index, time)
                }
            }
        }
    }
}

/** 重启或应用更新后，系统会清掉闹钟，这里补排 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Store.init(context)
            Reminders.ensureChannel(context)
            Reminders.refresh(context)
        }
    }
}
