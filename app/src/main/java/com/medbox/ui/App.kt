package com.medbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.medbox.Medicine
import com.medbox.Reminders
import com.medbox.Store
import androidx.compose.ui.platform.LocalContext

private val TAB_TITLES = listOf("今日服药", "我的药品", "服药记录")
private val TAB_GLYPHS = listOf("今", "药", "录")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot() {
    var tab by remember { mutableIntStateOf(0) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<Medicine?>(null) }
    val context = LocalContext.current

    val openEditor: (Medicine?) -> Unit = { med ->
        editorTarget = med
        editorOpen = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(TAB_TITLES[tab], fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                TAB_TITLES.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (tab == index) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = TAB_GLYPHS[index],
                                    fontSize = 13.sp,
                                    color = if (tab == index) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        label = { Text(title, fontSize = 11.sp) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != 2) {
                ExtendedFloatingActionButton(
                    onClick = { openEditor(null) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    text = { Text("添加药品") },
                    icon = {}
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (tab) {
                0 -> TodayScreen(onAddFirst = { openEditor(null) })
                1 -> MedsScreen(onEdit = { openEditor(it) })
                else -> HistoryScreen(onGoMeds = { tab = 1 })
            }
        }
    }

    if (editorOpen) {
        MedEditorDialog(
            initial = editorTarget,
            onDismiss = { editorOpen = false },
            onChanged = { Reminders.refresh(context) }
        )
    }
}

/** 一体化的进度条，避免依赖不同版本 API */
@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, height: Int = 8) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp / 2f))
            .background(MaterialTheme.colorScheme.primaryContainer)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, action: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text(action) }
    }
}

@Composable
fun SectionCard(content: PaddingValues = PaddingValues(16.dp), body: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(Modifier.padding(content)) { body() }
    }
}

@Composable
fun TimeChip(text: String, primary: Boolean = true) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (primary) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            fontSize = 12.sp,
            color = if (primary) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text(text, fontSize = 13.sp)
    }
}

@Composable
fun SmallTextButton(text: String, onClick: () -> Unit, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
    ) {
        Text(text, fontSize = 13.sp, color = color)
    }
}

@Composable
fun HorizontalGap(w: Int = 4) { Spacer(Modifier.width(w.dp)) }

@Composable
fun VerticalGap(h: Int = 8) { Spacer(Modifier.height(h.dp)) }

fun List<Medicine>.allTimes(): List<String> = flatMap { it.times }.distinct().sorted()
