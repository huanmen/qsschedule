# 插件接入与兼容性

维护者的网页管理、资料编辑、发布/下架及服务器部署说明见 [插件后台管理](PLUGIN-ADMIN.md)。后台管理已经接入宿主的功能及规则数据，不替代原生插件接入步骤。

插件市场目前支持两种交付方式。原生插件随宿主 APK 编译发布，适合需要页面、系统权限或本地存储的功能；第三方来源只提供受限广告规则数据，不能安装 APK、加载 DEX、执行脚本或获取宿主的教务数据。

赛事与考证使用 `academic-records` 数据插件：渲染器随宿主编译，主要核对资料通过独立 JSON 包安装，APK 不包含 `audited-record-data.js`。市场支持下载、更新、删除及从文件安装；首次安装后置顶到个人页。未安装时，日历的相关入口打开插件市场，每日待办隐藏相关开关，后台赛事更新不运行。卸载保留用户关注和已加入日期，重新安装可恢复使用。

本地执行 `python docs/build-academic-plugin.py` 从已保存资料生成 `server/qsschedule/academic-records.json` 和 `server/qsschedule/downloads/qsschedule-academic-records-1.json`。发布时需一起更新服务端 `app.py`、元数据和 JSON 文件。下载校验元数据中的 SHA-256；校验失败保留原版。文件安装通过 Android 文档选择器读取并验证包类型、版本和结构。包仅为数据，不能增加原生功能或执行远程脚本。

## 原生插件

实现 `PluginController`，声明稳定的 `PluginDescriptor.id`、名称、摘要、内置版本及依赖，再将控制器注册到 `PluginRegistry.plugins`。实现 `installedVersion`、`install` 和 `uninstall`，让市场统一呈现下载、更新和删除。插件自己的设置页通过导航接入；页面容器使用 `core/ui/plugin/PluginSurface.kt`，不要引用广告辅助包中的 UI 函数。

需要保存密钥的插件使用 `SecretStore` 和独立 Keystore 别名。删除插件时只删除自己的偏好设置和密钥，不清理课表或其他插件数据。无障碍插件须声明 `ACCESSIBILITY` 依赖；如果与其他插件共用服务，执行窗口操作前通过 `AccessibilitySession` 占用会话并在结束、失败和取消时释放。当前校园 VPN 助手占用期间，广告扫描暂停。

原生插件需要随宿主重新构建和签名。远程目录不能向用户设备注入新的原生功能。

## 宿主接口 v1

原生插件使用 `PluginHostServices(context, descriptor.id)`。每次调用核对插件已安装、描述符已声明该能力、用户已授权；缺少任一条件会抛出 `SecurityException`。安装时可选择授权，安装后在插件市场“接口权限”调整。撤销权限立即影响后续调用，删除插件清除授权和 SDK 设置。

| 能力 | 接口 | 用途 |
| --- | --- | --- |
| `READ_TIMETABLE` | `semesters()`、`timetable(semesterId)`、`periodScheme(semesterId)` | 学期、课程、节次及调停课 |
| `READ_CALENDAR` | `activities(semesterId)`、`agenda(date)` | 已加入活动、日历赛事与考证的当日汇总 |
| 上述两项 | `agenda(date, includeCourses = true)` | 加入当天有效课程，排除停课 |
| `WRITE_ACTIVITIES` | `saveActivity(activity)`、`deleteActivity(id)` | 管理本插件创建的活动 |
| `NOTIFICATIONS` | `notify(title, detail, mode)` | 使用独立通知频道发送通知 |
| `OPEN_LINKS` | `openLink(httpsUrl)` | 打开公开 HTTPS 网页 |
| `PUBLIC_DATA` | `publicSchedulesJson()` | 读取随 APK 发布的公开赛事和考试参考数据 |
| 无额外能力，须已安装 | `readSetting(key)`、`writeSetting(key, value)` | 隔离的插件设置 |

`apiVersion` 为 1。读写接口为挂起函数，适合在页面协程中使用；通知和网页接口直接调用。`agenda` 每次读取当前已加入的数据，不把公开目录中的全部记录当作用户待办。公开数据接口目前返回打包快照，前端联网更新后的缓存没有通过 SDK 暴露。

例如活动助手在描述符声明：

```kotlin
PluginDescriptor(
    id = "org.example.agenda", name = "活动助手", summary = "当日安排", version = 1,
    capabilities = setOf(PluginCapability.READ_CALENDAR, PluginCapability.WRITE_ACTIVITIES),
)
// 将相应 PluginController 注册进 PluginRegistry.plugins，用户安装并授权后：
val host = PluginHostServices(context, "org.example.agenda")
val today = host.agenda(LocalDate.now())
host.saveActivity(ScheduleActivity(
    id = "plugin:org.example.agenda:meeting-1", semesterId = importedSemesterId,
    name = "小组讨论", kind = ActivityKind.DAY, startDate = LocalDate.now(),
))
```

活动 ID 必须是 `plugin:<插件 ID>:<本地 ID>`，不能覆盖宿主或其他插件的安排。必须使用已导入学期，每学期最多 200 个本插件活动，日期区间最多 366 天。删除插件后，用户已加入课表的活动保留，仍能从宿主编辑和删除；重新安装同 ID 插件后可在授权下继续管理。教务账号、密码、密钥和数据库文件不通过 SDK 提供。

这些权限用于宿主接口和用户授权，并非隔离随 APK 编译的可信原生代码。远程广告规则包不能调用此 SDK。新增原生插件仍须代码审核、重新构建和签名。

## 第三方规则目录

用户可在插件市场顶栏添加 HTTPS 目录 URL。目录为 UTF-8 JSON，最大 64 KiB，不接受重定向；当前只支持 `adskip-rule-pack`。例如：

```json
{
  "schemaVersion": 1,
  "plugins": [
    {
      "id": "org.example.ads",
      "name": "示例广告规则",
      "summary": "示例规则数据包",
      "version": 1,
      "kind": "adskip-rule-pack",
      "rulesUrl": "https://example.org/rules.json5"
    }
  ]
}
```

`id` 必须稳定且在所有已添加来源中唯一；更新时增加 `version`。规则文件最大 4 MiB，可采用宿主 v2 JSON 规则包或受支持的 GKD JSON5 / 李跳跳 JSON 格式。市场安装要求整包都能被当前适配器解释；若有不兼容项，安装会停止，开发者可在广告辅助中单独导入原文件查看问题。新增或变更的规则默认关闭，用户自行启用。删除目录前需先删除从中安装的规则插件。网络失败时保留上次成功获取的目录和已安装规则。

需要较新宿主时可填写 `minAppVersion`（Android 内部版本号）和 `minAdapterVersion`；不填均为 1。高于当前宿主能力的目录条目会被拒绝，不会静默降级执行。

目录 URL 与规则 URL 均需为 HTTPS 直链。目录仅声明元数据，实际规则仍走 `AdSkipImport` 和 `AdSkipRules.validate`；规则来源应明确自己的维护者和许可证。目录接入不代表宿主认可其规则安全性或跳过效果。

## 兼容性边界

GKD 的订阅、规则组和快照审查启发了这里的目录及诊断设计；其完整高级选择器、触发条件和脚本能力没有移植。GKD 官方仓库采用 GPL-3.0，不能直接复制引擎源码到本工程后忽略许可要求。李跳跳第三方规则包也只导入当前适配器能安全解释的字段。规则数量与实际成功率并不等价；各设备应分别验证广告目标、关闭动作以及点击后的页面状态。

参考：[GKD 源码](https://github.com/gkd-kit/gkd)、[GKD 订阅格式](https://github.com/gkd-kit/docs/blob/main/docs/guide/subscription.md)、[李跳跳第三方规则格式示例](https://github.com/kernelai/LiTiaotiao)。
