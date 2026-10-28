package com.medbox

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** 一种药：名称、每次剂量、每天固定的服药时间点、加入日期 */
data class Medicine(
    val id: Long,
    val name: String,
    val dosage: String,
    val times: List<String>,   // 形如 "08:00"，已排序、去重
    val since: String          // 加入日期 yyyy-MM-dd
)

/** 一次服药记录 */
data class DoseRecord(
    val medId: Long,
    val date: String,          // yyyy-MM-dd
    val time: String,          // HH:mm
    val status: String,        // Store.TAKEN / Store.SKIPPED
    val at: Long               // 记录时间戳
)

/**
 * 极简本地存储：一个 JSON 文件，全部数据留在手机上，不联网、不上传。
 */
object Store {
    const val TAKEN = "taken"
    const val SKIPPED = "skipped"

    var meds by mutableStateOf<List<Medicine>>(emptyList())
        private set
    var records by mutableStateOf<List<DoseRecord>>(emptyList())
        private set

    private var file: File? = null
    private var nextId = 1L
    private var loaded = false

    fun init(context: Context) {
        if (loaded) return
        file = File(context.applicationContext.filesDir, "medbox.json")
        load()
        loaded = true
    }

    fun today(): String = LocalDate.now().toString()

    fun medName(id: Long): String = meds.firstOrNull { it.id == id }?.name ?: "（已删除的药品）"
    fun medDosage(id: Long): String = meds.firstOrNull { it.id == id }?.dosage ?: ""

    fun addMed(name: String, dosage: String, times: List<String>, since: String = today()): Medicine {
        val med = Medicine(nextId++, name.trim(), dosage.trim(), normalizeTimes(times), since)
        meds = meds + med
        save()
        return med
    }

    fun updateMed(med: Medicine) {
        meds = meds.map {
            if (it.id == med.id) med.copy(times = normalizeTimes(med.times)) else it
        }
        save()
    }

    fun deleteMed(id: Long) {
        meds = meds.filterNot { it.id == id }
        records = records.filterNot { it.medId == id }
        save()
    }

    fun statusOf(medId: Long, date: String, time: String): String? =
        records.firstOrNull { it.medId == medId && it.date == date && it.time == time }?.status

    fun mark(medId: Long, date: String, time: String, status: String) {
        val rec = DoseRecord(medId, date, time, status, System.currentTimeMillis())
        records = records.filterNot { it.medId == medId && it.date == date && it.time == time } + rec
        save()
    }

    fun clear(medId: Long, date: String, time: String) {
        records = records.filterNot { it.medId == medId && it.date == date && it.time == time }
        save()
    }

    fun recordsOn(date: String): List<DoseRecord> =
        records.filter { it.date == date }.sortedBy { it.time }

    /** 某天应该吃几次（只算当天及之前已加入的药） */
    fun expectedOn(date: String): Int =
        meds.filter { it.since <= date }.sumOf { it.times.size }

    /** 某天实际完成次数（已服 + 已跳过都算"处理过"） */
    fun handledOn(date: String): Int = recordsOn(date).size

    /** 某天已服次数 */
    fun takenOn(date: String): Int = recordsOn(date).count { it.status == TAKEN }

    private fun normalizeTimes(times: List<String>): List<String> =
        times.map { it.trim() }
            .filter { Regex("^\\d{1,2}:\\d{2}$").matches(it) }
            .distinct()
            .sorted()

    // ---------- 持久化 ----------

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        try {
            val root = JSONObject(f.readText(Charsets.UTF_8))
            nextId = root.optLong("nextId", 1L)

            val medArr = root.optJSONArray("meds") ?: JSONArray()
            val medList = ArrayList<Medicine>(medArr.length())
            for (i in 0 until medArr.length()) {
                val o = medArr.getJSONObject(i)
                val timesArr = o.optJSONArray("times") ?: JSONArray()
                val times = ArrayList<String>(timesArr.length())
                for (j in 0 until timesArr.length()) times.add(timesArr.getString(j))
                medList.add(
                    Medicine(
                        id = o.getLong("id"),
                        name = o.optString("name"),
                        dosage = o.optString("dosage"),
                        times = times,
                        since = o.optString("since", today())
                    )
                )
            }
            meds = medList.sortedBy { it.id }

            val recArr = root.optJSONArray("records") ?: JSONArray()
            val recList = ArrayList<DoseRecord>(recArr.length())
            for (i in 0 until recArr.length()) {
                val o = recArr.getJSONObject(i)
                recList.add(
                    DoseRecord(
                        medId = o.getLong("medId"),
                        date = o.getString("date"),
                        time = o.getString("time"),
                        status = o.optString("status", TAKEN),
                        at = o.optLong("at", 0L)
                    )
                )
            }
            records = recList
        } catch (e: Exception) {
            // 文件损坏时不让应用崩溃：保留空数据继续用
            meds = emptyList()
            records = emptyList()
        }
    }

    private fun save() {
        val f = file ?: return
        try {
            val root = JSONObject()
            root.put("nextId", nextId)

            val medArr = JSONArray()
            for (m in meds) {
                val o = JSONObject()
                o.put("id", m.id)
                o.put("name", m.name)
                o.put("dosage", m.dosage)
                o.put("since", m.since)
                val t = JSONArray()
                m.times.forEach { t.put(it) }
                o.put("times", t)
                medArr.put(o)
            }
            root.put("meds", medArr)

            val recArr = JSONArray()
            for (r in records) {
                val o = JSONObject()
                o.put("medId", r.medId)
                o.put("date", r.date)
                o.put("time", r.time)
                o.put("status", r.status)
                o.put("at", r.at)
                recArr.put(o)
            }
            root.put("records", recArr)

            f.writeText(root.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            // 写盘失败不阻塞界面
        }
    }
}
