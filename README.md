# 药盒 · MedBox

安卓上的极简**吃药记录 + 到点提醒** App。纯本地存储，不联网、不上传、无账号，数据只留在你自己手机上。

给自己（或家里老人）用的小工具：把每天要吃的药加进去，到点手机响，吃完了在通知栏点一下「已服用」就完成打卡。

## 下载安装

到 [**Releases**](https://github.com/ByteJuno/medbox/releases/latest) 下载 `medbox-v0.1.0.apk`，传到手机点击安装（需允许「安装未知来源应用」）。

要求：Android 8.0（API 26）及以上。已在 Pixel 8 / Android 16 上跑通全流程。

## 截图

| 空状态 | 到点提醒 |
|---|---|
| ![今日](screenshots/1-today-empty.png) | ![提醒](screenshots/2-reminder.png) |

## 功能

- **今日服药** —— 按时间点列出今天要吃的药，一键「已服 / 跳过 / 撤销」，顶部显示 `2 / 3 次已完成` 和进度条
- **我的药品** —— 增删改，一种药可设多个每日时间点（如 08:00 / 13:00 / 20:00）
- **到点提醒** —— AlarmManager 精确闹钟 + 高优先级通知，**通知栏直接点「已服用」就算打卡**
- **开机自恢复** —— 手机重启、应用更新后自动重排闹钟
- **服药记录** —— 近 7 天 / 近 30 天完成率，按天展开看每一条打卡明细

## 装完必做两件事

否则提醒可能不响（App 内「今日服药」页顶部会显示黄色提示条）：

1. 允许**通知**（Android 13+ 首次启动会弹窗）
2. 系统设置 → 应用 → 药盒 → **闹钟和提醒 → 允许**（Android 12+ 默认可能关闭）

国产 ROM（小米 / 华为 / OPPO / vivo）还需在系统里给本应用开启：**自启动**、**后台运行**、**忽略电池优化**。否则系统会杀掉闹钟——这是所有提醒类 App 的通病，不是本应用特有。

## 自己编译

环境：JDK 17+、Android SDK platform 36、Gradle 8.13（AGP 8.13.0 / Kotlin 2.2.21 / Compose BOM 2025.12.01）。

```bash
# 在项目根目录建 local.properties，写入你的 SDK 路径
# Windows 示例：sdk.dir=D\:\\Android\\Sdk

./gradlew :app:assembleDebug        # Linux / macOS
gradlew.bat :app:assembleDebug      # Windows
```

或直接用 Android Studio 打开本目录。

> 若项目路径含中文，AGP 会拒绝构建，已在 `gradle.properties` 里加 `android.overridePathCheck=true` 绕过。

## 代码结构

```
app/src/main/java/com/medbox/
├── MainActivity.kt          入口：初始化、通知权限申请
├── Store.kt                 数据层：药品 + 记录，JSON 存 filesDir/medbox.json
├── Reminders.kt             闹钟排程、通知构建、ReminderReceiver / BootReceiver
└── ui/
    ├── Theme.kt             Material3 配色（青绿 + 浅灰，支持深色模式）
    ├── App.kt               底部三标签导航、公共组件
    ├── TodayScreen.kt       今日打卡 + 权限提示
    ├── MedsScreen.kt        药品列表、删除确认
    ├── HistoryScreen.kt     完成率统计、按天明细
    └── MedEditorDialog.kt   添加/编辑药品 + 时间选择器
```

技术要点：

- **Kotlin + Jetpack Compose + Material3**，`minSdk 26` / `targetSdk 36`，零第三方依赖（除 AndroidX）
- **数据**用 `org.json` 手写序列化，一个文件搞定，不引入 Room / kotlinx-serialization
- **提醒**用 `AlarmManager.setExactAndAllowWhileIdle`（无精确闹钟权限时降级为 `setAndAllowWhileIdle`），响过后自动排明天；`BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` 后由 `BootReceiver` 补排
- 只申请 4 个权限：`POST_NOTIFICATIONS`、`SCHEDULE_EXACT_ALARM`、`RECEIVE_BOOT_COMPLETED`、`VIBRATE`

## 已知限制（v0.1，自用够）

- 单机数据，换手机需手动迁移（`/data/data/com.medbox/files/medbox.json`，暂无可视化导出）
- 「应服次数」按药品加入日期推算，改服药时间不会回溯修正历史统计
- 没有库存管理、没有家人共享、没有 CSV 导出

## Roadmap

- [ ] 库存与补药提醒
- [ ] 导出 CSV（复诊给医生看）
- [ ] JSON 备份 / 恢复
- [ ] 多设备同步

## License

[MIT](LICENSE)。仅用于记录与提醒，不提供任何医疗建议；用药请遵医嘱。
