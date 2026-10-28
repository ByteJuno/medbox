package com.medbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medbox.DoseRecord
import com.medbox.Store
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FMT = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)

@Composable
fun HistoryScreen(onGoMeds: () -> Unit = {}) {
    val meds = Store.meds
    var expandedDate by remember { mutableStateOf<String?>(null) }

    if (meds.isEmpty()) {
        EmptyState(
            title = "还没有记录",
            subtitle = "添加药品并开始打卡后，这里会显示每天的服药情况。",
            action = "去添加药品",
            onAction = onGoMeds
        )
        return
    }

    val today = LocalDate.now()
    val days = (0 until 30).map { today.minusDays(it.toLong()) }.map { it.toString() }

    val last7 = days.take(7)
    val expected7 = last7.sumOf { Store.expectedOn(it) }
    val taken7 = last7.sumOf { Store.takenOn(it) }
    val rate7 = if (expected7 == 0) 0 else (taken7 * 100 / expected7)

    val last30 = days
    val expected30 = last30.sumOf { Store.expectedOn(it) }
    val taken30 = last30.sumOf { Store.takenOn(it) }
    val rate30 = if (expected30 == 0) 0 else (taken30 * 100 / expected30)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "服药完成率",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatBlock("近 7 天", "$rate7%", "$taken7 / $expected7", Modifier.weight(1f))
                        StatBlock("近 30 天", "$rate30%", "$taken30 / $expected30", Modifier.weight(1f))
                    }
                }
            }
        }

        items(days, key = { it }) { date ->
            val expected = Store.expectedOn(date)
            val taken = Store.takenOn(date)
            val recs = Store.recordsOn(date)
            val skipped = recs.count { it.status == Store.SKIPPED }
            val isOpen = expandedDate == date

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedDate = if (isOpen) null else date },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            LocalDate.parse(date).format(DAY_FMT),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        when {
                            expected == 0 -> Text(
                                "无安排",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            taken >= expected -> Text(
                                "全部完成",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            else -> Text(
                                "$taken / $expected" + if (skipped > 0) "（跳过 $skipped）" else "",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (expected > 0) {
                        Spacer(Modifier.height(8.dp))
                        ProgressBar(
                            fraction = taken.toFloat() / expected,
                            modifier = Modifier.fillMaxWidth(),
                            height = 6
                        )
                    }
                    if (isOpen && recs.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        recs.forEach { DetailRow(it) }
                    }
                    if (isOpen && recs.isEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "这天没有任何打卡记录",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DetailRow(rec: DoseRecord) {
    val taken = rec.status == Store.TAKEN
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TimeChip(rec.time, primary = taken)
        Spacer(Modifier.padding(horizontal = 4.dp))
        Text(
            Store.medName(rec.medId),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            if (taken) "已服" else "跳过",
            fontSize = 12.sp,
            color = if (taken) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
