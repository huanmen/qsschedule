# 泉师课程表

面向泉州师范学院学生的 Android 课程表，支持 Android 8.0 及以上。包含今日与周课表、教务导入、学期切换、校历、活动、课程提醒、桌面小部件和校园地图。

## 版本

- `main` / `v1.0.0-source`：1.0.0（versionCode 13）的恢复源码基线。
- 原始安装包以 2026 年 10 月 5 日发布的 APK 为准，源码恢复过程与核验边界见 `docs/SOURCE-PROVENANCE.md`。
- `feat/1.0.0-feature-migration`：在上述基线迁入插件、公告、AI 评价草稿及成绩查询，保持原版课表页面与数据库结构。迁移范围及验收见 `docs/MIGRATION-20261006.md`，不混入原版标签。

## 成绩查询（迁移分支）

在课程导入中保存教务账号后，打开“我的 → 学习情况”即可查询。每次获取使用独立、临时的 WebView 配置，从保存的账号密码重新认证，不借用课程导入的登录会话；正常过程只显示原生界面。自动获取全部有成绩的学期，默认展开上一个已有成绩的学期，其余折叠。

需要校园网或可访问教务的网络，以及支持多配置的 Android System WebView。验证码或额外交互认证不自动绕过；失败时保留本地加密缓存。快速评教入口暂时隐藏，成绩和学分入口保留。

## 构建

安装 JDK 17 和 Android SDK 34，在 `android/` 目录执行：

```shell
./gradlew :app:testReleaseUnitTest :feature:timetable:testReleaseUnitTest :app:assembleRelease
```

Windows 使用 `gradlew.bat`。通过 `ANDROID_HOME` 或本地 `local.properties` 配置 SDK；不要提交本机路径。地图定位 SDK 的 Key 使用 `AMAP_LOCATION_KEY` 环境变量或本地 Gradle 属性配置，仓库不提供 Key。

此基线的 Release 构建沿用当时本地验收签名配置。新机器会生成不同签名，不能用自行构建的 APK 覆盖官方签名版本；发布签名文件不在仓库中。

## 隐私和第三方内容

公开仓库只含构建输入、合成测试样例及说明，不含账号、密码、Cookie、个人课表、成绩、设备数据库或截图。具体见 `PRIVACY.md`。

第三方组件、地图数据和资源遵循各自的许可及署名要求，相关许可保留在资源目录中。公开可见不代表本项目所有内容都已获同一开源许可证授权。
