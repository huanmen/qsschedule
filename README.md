# 泉师课程表

面向泉州师范学院学生的 Android 课程表，支持 Android 8.0 及以上。包含今日与周课表、教务导入、学期切换、校历、活动、课程提醒、桌面小部件和校园地图。

## 版本

- `main` / `v1.0.0-source`：1.0.0（versionCode 13）的恢复源码基线。
- 原始安装包以 2026 年 10 月 5 日发布的 APK 为准，源码恢复过程与核验边界见 `docs/SOURCE-PROVENANCE.md`。
- 后续插件、公告、AI 评价草稿及成绩查询改动在独立迁移分支维护，不混入本基线标签。

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
