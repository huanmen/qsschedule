package cn.qztc.timetable.qnu

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The supported hand-off point exposed by the installed EasyConnect client.
 * EasyConnect owns the VPN tunnel and its authentication; this app never
 * attempts to reproduce or inject its private protocol.
 */
object EasyConnectBridge {
    const val PACKAGE_NAME = "com.sangfor.vpn.client.phone"
    private const val CONNECTION_URI = "sangfor://easyconnect"

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(PACKAGE_NAME, 0)
        true
    }.getOrDefault(false)

    fun connectionIntent(context: Context): Intent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(CONNECTION_URI),
    ).apply {
        setPackage(PACKAGE_NAME)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun open(context: Context): Boolean = runCatching {
        if (!isInstalled(context)) return false
        context.startActivity(connectionIntent(context))
        true
    }.getOrDefault(false)
}
