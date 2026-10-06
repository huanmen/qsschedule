package cn.qztc.timetable.feature.timetable

import android.content.Context
import android.content.Intent
import android.net.Uri

private const val AMAP_PACKAGE = "com.autonavi.minimap"

fun openCampusExternalNavigation(
    context: Context,
    origin: CampusCoordinate?,
    destination: CampusPoint,
): Boolean {
    val amapIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.Builder()
            .scheme("amapuri")
            .authority("route")
            .appendPath("plan")
            .appendQueryParameter("sourceApplication", "泉师课程表")
            .apply {
                origin?.let {
                    appendQueryParameter("slat", it.latitude.toString())
                    appendQueryParameter("slon", it.longitude.toString())
                    appendQueryParameter("sname", "当前位置")
                }
            }
            .appendQueryParameter("dlat", destination.coordinate.latitude.toString())
            .appendQueryParameter("dlon", destination.coordinate.longitude.toString())
            .appendQueryParameter("dname", destination.name)
            .appendQueryParameter("dev", "1")
            .appendQueryParameter("t", "2")
            .build(),
    ).setPackage(AMAP_PACKAGE)
    if (amapIntent.resolveActivity(context.packageManager) != null) {
        context.startActivity(amapIntent)
        return true
    }

    val webUri = Uri.Builder()
        .scheme("https")
        .authority("uri.amap.com")
        .path("navigation")
        .apply {
            origin?.let {
                appendQueryParameter(
                    "from",
                    "${it.longitude},${it.latitude},当前位置",
                )
            }
        }
        .appendQueryParameter(
            "to",
            "${destination.coordinate.longitude},${destination.coordinate.latitude},${destination.name}",
        )
        .appendQueryParameter("mode", "walk")
        .appendQueryParameter("coordinate", "wgs84")
        .appendQueryParameter("callnative", "1")
        .build()
    val webIntent = Intent(Intent.ACTION_VIEW, webUri)
    if (webIntent.resolveActivity(context.packageManager) == null) return false
    context.startActivity(webIntent)
    return true
}
