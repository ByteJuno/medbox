package com.medbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.medbox.Medicine
import com.medbox.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedEditorDialog(
    initial: Medicine?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dosage by remember { mutableStateOf(initial?.dosage ?: "") }
    val times = remember { mutableStateListOf<String>().apply { addAll(initial?.times ?: emptyList()) } }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加药品" else "编辑药品") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("药品名称") },
                    placeholder = { Text("如：二甲双胍") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = dosage,
                    onValueChange = { dosage = it },
                    label = { Text("每次剂量") },
                    placeholder = { Text("如：1 片 / 0.5g") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "每天提醒时间",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                if (times.isEmpty()) {
                    Text(
                        "还没有添加时间，点下面按钮添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                times.forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeChip(t)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { times.remove(t) }) {
                            Text("移除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                TextButton(onClick = { showPicker = true }) { Text("＋ 添加时间") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && times.isNotEmpty(),
                onClick = {
                    if (initial == null) {
                        Store.addMed(name, dosage, times.toList())
                    } else {
                        Store.updateMed(initial.copy(name = name, dosage = dosage, times = times.toList()))
                    }
                    onChanged()
                    onDismiss()
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )

    if (showPicker) {
        val state = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "选择提醒时间",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    TimePicker(state = state)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showPicker = false }) { Text("取消") }
                        TextButton(onClick = {
                            val t = String.format("%02d:%02d", state.hour, state.minute)
                            if (!times.contains(t)) {
                                times.add(t)
                                times.sort()
                            }
                            showPicker = false
                        }) { Text("确定") }
                    }
                }
            }
        }
    }
}
