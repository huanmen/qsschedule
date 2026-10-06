package cn.qztc.timetable.feature.timetable

internal enum class CampusCoordinateSystem { WGS84, GCJ02 }

internal fun CampusCoordinate.forCampusMap(system: CampusCoordinateSystem): CampusCoordinate =
    if (system == CampusCoordinateSystem.WGS84) CampusAmapProjection.wgs84ToGcj02(this) else this

internal fun CampusCoordinate.forExternalNavigation(system: CampusCoordinateSystem): CampusCoordinate =
    if (system == CampusCoordinateSystem.GCJ02) CampusAmapProjection.gcj02ToWgs84(this) else this
