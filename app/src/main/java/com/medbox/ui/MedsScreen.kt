package com.medbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.medbox.Medicine
import com.medbox.Store

@Composable
fun MedsScreen(onEdit: (Medicine?) -> Unit) {
    val meds = Store.meds
    var pendingDelete by remember { mutableStateOf<Medicine?>(null) }

    if (meds.isEmpty()) {
        EmptyState(
            title = "药品列表是空的",
            subtitle = "添加药品后，系统会按你设置的时间提醒你吃药。",
            action = "添加药品",
            onAction = { onEdit(null) }
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(meds, key = { it.id }) { med ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    med.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (med.dosage.isNotBlank()) {
                                    Text(
                                        "每次 ${med.dosage}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            SmallTextButton("编辑", { onEdit(med) })
                            SmallTextButton(
                                "删除",
                                { pendingDelete = med },
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "每天 ${med.times.size} 次",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.padding(horizontal = 6.dp))
                            med.times.forEach { t ->
                                TimeChip(t)
                                Spacer(Modifier.padding(horizontal = 3.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    val target = pendingDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${target.name}」？") },
            text = { Text("该药品的服药记录也会一起删除，删除后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    Store.deleteMed(target.id)
                    pendingDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}
