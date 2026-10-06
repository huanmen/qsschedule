# APP 内置离线地图

来源：同机已验证的轻量发布包，高德-离线地图-轻量版-20260913-214458。原地图运行文件、WebP 瓦片、旋转插件、SVG 操场和许可随包保存。

APP 入口为 app.html；独立编辑器入口 index.html 保留。app.js/app.css 负责手机导航界面，data/map-editor.js 仅增加原生定位起点、目的地接口和首次安装的数据初始化。高德密钥不进入此目录，地图资源、搜索和寻路均在设备本地运行。

首次初始化使用 2026-09-13 19:33:35 导出的“泉师地图-地点编辑 (2).json”，记录为 data/initial-edits.js：212 个地点修改、3 条路线、4 个圈选。仅在浏览器编辑存储为空时载入；用户已有修改不会被覆盖。重新生成命令：node scripts/Seed-OfflineMap.mjs <最新编辑文件>。

Android 通过 WebViewAssetLoader 将资产映射到 appassets.androidplatform.net 的专用目录。文件访问和网络加载关闭，页面只允许专用目录内的资源。定位由 Android 获取、从 WGS-84 转到 GCJ-02；导入/导出通过系统文档选择器；外部导航是用户主动点击后交给系统处理。

APP 独立于桌面浏览器的 localStorage。后续桌面更新请导出新编辑文件并在 APP 的“地图编辑 / 导入编辑”中导入。尚未导出的桌面修改不随安装包自动更新。

第三方许可：vendor/leaflet-rotate/LICENSE（GPL-3.0）、vendor/package/LICENSE.md（MIT）；底图高德归属保留在页面。地图资料仅按本项目现有个人使用范围打包，正式分发需要核对对应地图素材的使用条件。
