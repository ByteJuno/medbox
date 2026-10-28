package com.medbox.ui

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medbox.Medicine
import com.medbox.Reminders
import com.medbox.Store
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class TodayRow(val med: Medicine, val time: String, val status: String?)

@Composable
fun TodayScreen(onAddFirst: () -> Unit) {
    val meds = Store.meds
    val today = Store.today()

    if (meds.isEmpty()) {
        EmptyState(
            title = "还没有添加药品",
            subtitle = "先把每天要吃的药加进来，之后到点就会提醒你。",
            action = "添加第一种药",
            onAction = onAddFirst
        )
        return
    }

    // 读取 Store.records（Compose 会跟踪状态，打卡后自动刷新）
    val rows = meds
        .flatMap { med -> med.times.map { time -> TodayRow(med, time, Store.statusOf(med.id, today, time)) } }
        .sortedWith(compareBy({ it.time }, { it.med.name }))

    val total = rows.size
    val taken = rows.count { it.status == Store.TAKEN }
    val skipped = rows.count { it.status == Store.SKIPPED }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { PermissionHint() }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        LocalDate.now().format(
                            DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$taken",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            " / $total 次已完成",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Spacer(Modifier.weight(1f))
                        if (skipped > 0) {
                            Text(
                                "跳过 $skipped",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    ProgressBar(
                        fraction = if (total == 0) 0f else taken.toFloat() / total,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        items(rows, key = { "${it.med.id}-${it.time}" }) { row ->
            DoseCard(row)
        }
    }
}

@Composable
private fun DoseCard(row: TodayRow) {
    val today = Store.today()
    val status = row.status
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                Store.TAKEN -> MaterialTheme.colorScheme.primaryContainer
                Store.SKIPPED -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.padding(end = 10.dp)) {
                Text(
                    row.time,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    row.med.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (row.med.dosage.isNotBlank()) {
                    Text(
                        row.med.dosage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when (status) {
                Store.TAKEN -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "已服用",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        SmallTextButton("撤销", {
                            Store.clear(row.med.id, today, row.time)
                        })
                    }
                }
                Store.SKIPPED -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "已跳过",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SmallTextButton("撤销", {
                            Store.clear(row.med.id, today, row.time)
                        })
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallButton("已服") {
                            Store.mark(row.med.id, today, row.time, Store.TAKEN)
                        }
                        SmallTextButton("跳过", {
                            Store.mark(row.med.id, today, row.time, Store.SKIPPED)
                        })
                    }
                }
            }
        }
    }
}

/** 通知权限 / 精确闹钟没开时给个提示，避免"以为设置了其实不响" */
@Composable
private fun PermissionHint() {
    val context = LocalContext.current
    val nm = context.getSystemService(NotificationManager::class.java)
    val notificationsOn = nm?.areNotificationsEnabled() ?: true
    val exactOk = Reminders.canScheduleExact(context)
    if (notificationsOn && exactOk) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "提醒可能不会准时响",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (!notificationsOn) "请允许本应用发送通知。"
                else "请在系统设置中允许「闹钟和提醒」（精确闹钟）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                if (!notificationsOn) {
                    TextButton(onClick = {
                        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        runCatching { context.startActivity(i) }
                    }) { Text("去开启通知") }
                }
                if (!exactOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    TextButton(onClick = {
                        val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:${context.packageName}"))
                        runCatching { context.startActivity(i) }
                    }) { Text("去开启精确闹钟") }
                }
            }
        }
    }
}
