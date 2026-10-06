# 校园地图资源

`campus-map.json` 是本地矢量资源，用于在 APP 内绘制校园底图。道路、步行路径和校园边界来自 OpenStreetMap；建筑平面轮廓来自 Overture Maps 的开放建筑数据。App 沿这些道路与步行路径规划校内步行，不请求在线地图瓦片。

`places.json` 是与网页协作地图同步的校内地点目录。改标记请在本机网页 `http://127.0.0.1:19120/` 完成，再说一声后导入；不要在 App 里改建筑位置。

来源范围：`118.660,24.862,118.678,24.878`

重新生成道路底图：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/Convert-OsmCampusMap.ps1 `
  -InputPath C:\path\to\map.osm `
  -OutputPath android/feature/timetable/src/main/assets/campus/campus-map.json
```

补入建筑轮廓：

```powershell
uvx --from overturemaps overturemaps download `
  --bbox=118.660,24.862,118.678,24.878 `
  --type=building -f geojson -o C:\temp\qnu-overture-buildings.geojson

node scripts/Merge-OvertureCampusBuildings.mjs `
  android/feature/timetable/src/main/assets/campus/campus-map.json `
  C:\temp\qnu-overture-buildings.geojson `
  android/feature/timetable/src/main/assets/campus/campus-map.json `
  web/campus-map/data/places.json
```

完成后将该文件同步到 `web/campus-map/data/campus-map.json`。数据包保留 OpenStreetMap 与 Overture Maps Foundation 的署名。

同步地点目录（只导入已发布地点，不要直接 Copy-Item 整份网页数据）：

```powershell
node scripts/Import-CampusPlaces.mjs
```
