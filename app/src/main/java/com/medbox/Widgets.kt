package com.medbox

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val WIDGET_DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)

data class WidgetItem(val medId: Long, val time: String, val name: String, val dosage: String)

/**
 * 桌面小组件。
 *
 * 只用 RemoteViews 支持的控件（LinearLayout / TextView / ProgressBar），
 * 不用 ListView 这类 Collection 控件 —— 小米 MIUI/HyperOS 与 OPPO ColorOS 桌面
 * 对 Collection 小组件支持都不稳，固定行数最保险。
 */
object Widgets {
    const val ACTION_TAKEN = "com.medbox.WIDGET_TAKEN"

    private const val ROW_HEIGHT_DP = 24
    private const val FIXED_HEIGHT_DP = 56

    private class Palette(
        val cardBg: Int,
        val title: Int,
        val sub: Int,
        val primary: Int,
        val btnBg: Int,
        val btnText: Int
    )

    private fun palette(context: Context): Palette {
        val dark = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        return if (dark) {
            Palette(
                cardBg = R.drawable.widget_bg_dark,
                title = 0xFFE1E3E2.toInt(),
                sub = 0xFFBFC9C7.toInt(),
                primary = 0xFF6FD9CC.toInt(),
                btnBg = R.drawable.widget_btn_dark,
                btnText = 0xFF6FD9CC.toInt()
            )
        } else {
            Palette(
                cardBg = R.drawable.widget_bg_light,
                title = 0xFF191C1B.toInt(),
                sub = 0xFF47514F.toInt(),
                primary = 0xFF0F766E.toInt(),
                btnBg = R.drawable.widget_btn_light,
                btnText = 0xFF0F766E.toInt()
            )
        }
    }

    fun todayItems(): List<WidgetItem> =
        Store.meds
            .flatMap { m -> m.times.map { WidgetItem(m.id, it, m.name, m.dosage) } }
            .sortedBy { it.time }

    // ---------------- 今日打卡小组件 ----------------

    fun updateToday(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        Store.init(context)
        val p = palette(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_today)

        val today = Store.today()
        val all = todayItems()
        val pending = all.filter { Store.statusOf(it.medId, today, it.time) == null }
        val done = all.size - pending.size
        val rows = rowsFor(mgr, widgetId)

        rv.setInt(R.id.widget_root, "setBackgroundResource", p.cardBg)
        rv.setTextViewText(R.id.widget_title, LocalDate.now().format(WIDGET_DATE_FMT))
        rv.setTextColor(R.id.widget_title, p.title)
        rv.setTextViewText(R.id.widget_count, if (all.isEmpty()) "—" else "$done/${all.size}")
        rv.setTextColor(R.id.widget_count, p.primary)
        rv.setProgressBar(R.id.widget_progress, maxOf(all.size, 1), done, false)

        val rowIds = intArrayOf(R.id.row1, R.id.row2, R.id.row3, R.id.row4)
        val timeIds = intArrayOf(R.id.row1_time, R.id.row2_time, R.id.row3_time, R.id.row4_time)
        val nameIds = intArrayOf(R.id.row1_name, R.id.row2_name, R.id.row3_name, R.id.row4_name)
        val btnIds = intArrayOf(R.id.row1_btn, R.id.row2_btn, R.id.row3_btn, R.id.row4_btn)

        for (i in rowIds.indices) {
            if (i < rows && i < pending.size) {
                val item = pending[i]
                rv.setViewVisibility(rowIds[i], View.VISIBLE)
                rv.setTextViewText(timeIds[i], item.time)
                rv.setTextColor(timeIds[i], p.primary)
                rv.setTextViewText(nameIds[i], item.name)
                rv.setTextColor(nameIds[i], p.title)
                rv.setTextViewText(btnIds[i], "打卡")
                rv.setTextColor(btnIds[i], p.btnText)
                rv.setInt(btnIds[i], "setBackgroundResource", p.btnBg)
                rv.setOnClickPendingIntent(btnIds[i], takenPending(context, widgetId, item, i))
            } else {
                rv.setViewVisibility(rowIds[i], View.GONE)
            }
        }

        val more = pending.size - rows
        if (more > 0) {
            rv.setViewVisibility(R.id.widget_more, View.VISIBLE)
            rv.setTextViewText(R.id.widget_more, "还有 $more 项待服，点开查看")
            rv.setTextColor(R.id.widget_more, p.sub)
        } else {
            rv.setViewVisibility(R.id.widget_more, View.GONE)
        }

        when {
            all.isEmpty() -> {
                rv.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                rv.setTextViewText(R.id.widget_empty, "还没有添加药品，点这里去添加")
                rv.setTextColor(R.id.widget_empty, p.sub)
            }
            pending.isEmpty() -> {
                rv.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                rv.setTextViewText(R.id.widget_empty, "今天的药都吃完了 ✓")
                rv.setTextColor(R.id.widget_empty, p.primary)
            }
            else -> rv.setViewVisibility(R.id.widget_empty, View.GONE)
        }

        // 顶部日期 + 右侧计数 + 空状态提示负责"打开 App"，不与每行的打卡按钮抢点击
        val open = openPending(context, widgetId)
        rv.setOnClickPendingIntent(R.id.widget_title, open)
        rv.setOnClickPendingIntent(R.id.widget_count, open)
        rv.setOnClickPendingIntent(R.id.widget_empty, open)
        mgr.updateAppWidget(widgetId, rv)
    }

    /** 根据系统给出的小组件最大高度决定能放几行（用户拉伸时自适应） */
    private fun rowsFor(mgr: AppWidgetManager, widgetId: Int): Int {
        val options = mgr.getAppWidgetOptions(widgetId)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
        return ((height - FIXED_HEIGHT_DP) / ROW_HEIGHT_DP).coerceIn(1, 4)
    }

    // ---------------- 进度概要小组件 ----------------

    fun updateSummary(context: Context, mgr: AppWidgetManager, widgetId: Int) {
        Store.init(context)
        val p = palette(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_summary)

        val today = Store.today()
        val all = todayItems()
        val pending = all.filter { Store.statusOf(it.medId, today, it.time) == null }
        val done = all.size - pending.size
        val next = nextUpcoming(pending)

        rv.setInt(R.id.summary_root, "setBackgroundResource", p.cardBg)
        rv.setTextColor(R.id.summary_title, p.title)
        rv.setTextColor(R.id.summary_big, p.primary)
        rv.setTextColor(R.id.summary_next, p.sub)
        rv.setTextColor(R.id.summary_med, p.title)

        rv.setTextViewText(R.id.summary_title, LocalDate.now().format(WIDGET_DATE_FMT))
        rv.setTextViewText(R.id.summary_big, if (all.isEmpty()) "—" else "$done/${all.size}")
        rv.setProgressBar(R.id.summary_progress, maxOf(all.size, 1), done, false)

        when {
            all.isEmpty() -> {
                rv.setTextViewText(R.id.summary_next, "还没有添加药品")
                rv.setTextViewText(R.id.summary_med, "点这里开始")
            }
            pending.isEmpty() -> {
                rv.setTextViewText(R.id.summary_next, "今天已完成")
                rv.setTextViewText(R.id.summary_med, "全部 $done 次都吃完了 ✓")
            }
            else -> {
                rv.setTextViewText(R.id.summary_next, "下一次 ${next?.time ?: "--:--"}")
                rv.setTextViewText(
                    R.id.summary_med,
                    listOfNotNull(next?.name, next?.dosage?.takeIf { it.isNotBlank() }).joinToString(" · ")
                )
            }
        }

        rv.setOnClickPendingIntent(R.id.summary_root, openPending(context, widgetId))
        mgr.updateAppWidget(widgetId, rv)
    }

    private fun nextUpcoming(pending: List<WidgetItem>): WidgetItem? {
        if (pending.isEmpty()) return null
        val now = ZonedDateTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        return pending.firstOrNull { item ->
            val parts = item.time.split(":")
            val minutes = (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
            minutes >= nowMinutes
        } ?: pending.first()
    }

    // ---------------- 刷新与 PendingIntent ----------------

    fun refreshAll(context: Context) {
        runCatching {
            Store.init(context)
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val todayIds = mgr.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
            todayIds?.forEach { updateToday(context, mgr, it) }
            val summaryIds = mgr.getAppWidgetIds(ComponentName(context, SummaryWidgetProvider::class.java))
            summaryIds?.forEach { updateSummary(context, mgr, it) }
        }
    }

    private fun takenPending(context: Context, widgetId: Int, item: WidgetItem, index: Int): PendingIntent {
        val intent = Intent(context, TodayWidgetProvider::class.java).apply {
            action = ACTION_TAKEN
            // data 不同才能保证每个按钮的 PendingIntent 互不覆盖
            data = Uri.parse("medbox://taken/$widgetId/$index")
            putExtra("medId", item.medId)
            putExtra("time", item.time)
        }
        return PendingIntent.getBroadcast(
            context,
            widgetId * 100 + index,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun openPending(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("medbox://open/$requestCode")
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/** 4x2 今日打卡小组件 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { Widgets.updateToday(context, appWidgetManager, it) }
    }

    /** 用户拉伸/缩小后按新尺寸重排行数 */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        Widgets.updateToday(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Widgets.ACTION_TAKEN) {
            Store.init(context)
            val medId = intent.getLongExtra("medId", -1L)
            val time = intent.getStringExtra("time")
            if (medId > 0 && time != null) {
                Store.mark(medId, Store.today(), time, Store.TAKEN)
                // 顺手把这次的时间点对应通知消掉
                val med = Store.meds.firstOrNull { it.id == medId }
                val index = med?.times?.indexOf(time) ?: -1
                if (index >= 0) Reminders.cancel(context, Reminders.codeOf(medId, index))
            }
            Widgets.refreshAll(context)
            return
        }
        super.onReceive(context, intent)
    }
}

/** 2x2 进度概要小组件 */
class SummaryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { Widgets.updateSummary(context, appWidgetManager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        Widgets.updateSummary(context, appWidgetManager, appWidgetId)
    }
}
