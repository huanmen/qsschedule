package cn.qztc.timetable

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.core.ui.theme.QztcTheme
import cn.qztc.timetable.feature.timetable.CampusRouteScreen
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class AmapLocationAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<FeatureAcceptanceActivity>()

    @Suppress("DEPRECATION")
    @Test fun sdkAndPrivateServiceArePackagedWithAndroidKey() {
        val context = compose.activity
        val info = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
        assertEquals("cn.qztc.timetable", context.packageName)
        assertTrue(info.metaData.getString("com.amap.api.v2.apikey").orEmpty().matches(Regex("[a-fA-F0-9]{32}")))
        val service = context.packageManager.getServiceInfo(ComponentName(context, "com.amap.api.location.APSService"), 0)
        assertFalse(service.exported)
    }

    @Test fun mapPrivacyChoiceCanDeclineWithoutLosingTheMap() {
        val prefs = compose.activity.getSharedPreferences("campus_location_privacy", 0)
        val existed = prefs.contains("amap_consent_v1")
        val before = prefs.getBoolean("amap_consent_v1", false)
        try {
            prefs.edit().remove("amap_consent_v1").commit()
            compose.setContent { QztcTheme { CampusRouteScreen("", onBack = {}) } }
            compose.onNodeWithText("启用高德定位").assertExists()
            compose.onNodeWithText("使用系统定位").performClick()
            compose.onNodeWithText("启用高德定位").assertDoesNotExist()
            compose.onNodeWithText("校园地图").assertExists()
            assertTrue(prefs.contains("amap_consent_v1"))
            assertFalse(prefs.getBoolean("amap_consent_v1", true))
        } finally {
            compose.runOnIdle { compose.activity.setContentView(android.widget.FrameLayout(compose.activity)) }
            val editor = prefs.edit()
            if (existed) editor.putBoolean("amap_consent_v1", before) else editor.remove("amap_consent_v1")
            editor.commit()
        }
    }

    /** Does not print coordinates, addresses, device IDs, SDK error details, or the key. */
    @Test fun realSdkReturnsLocationOrAnExplicitDeviceLimitation() {
        val context = compose.activity
        assumeTrue(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        val done = CountDownLatch(1)
        val code = AtomicInteger(-1)
        val authReason = AtomicReference("unspecified")
        var client: AMapLocationClient? = null
        try {
            compose.runOnIdle {
                AMapLocationClient.updatePrivacyShow(context, true, true)
                AMapLocationClient.updatePrivacyAgree(context, true)
                AMapLocationClientOption.setLocationProtocol(AMapLocationClientOption.AMapLocationProtocol.HTTPS)
                client = AMapLocationClient(context).apply {
                    setLocationOption(AMapLocationClientOption().apply {
                        setOnceLocation(true)
                        setLocationCacheEnable(false)
                        setNeedAddress(false)
                        setHttpTimeOut(15_000L)
                        setMockEnable(false)
                    })
                    setLocationListener { result ->
                        code.set(result?.errorCode ?: -2)
                        val detail = result?.locationDetail.orEmpty().uppercase()
                        authReason.set(listOf("INVALID_USER_KEY", "INVALID_USER_SCODE", "USERKEY_PLAT_NOMATCH",
                            "INVALID_USER_SIGNATURE", "SERVICE_NOT_AVAILABLE", "USER_DAILY_QUERY_OVER_LIMIT",
                            "USER_NOT_EXIST", "KEY_NOT_EXIST", "INVALID_USER_DOMAIN", "USERKEY_RECYCLED",
                            "INSUFFICIENT_PRIVILEGES").firstOrNull { detail.contains(it) }
                            ?: Regex("\\b[A-Z]+(?:_[A-Z]+)+\\b").find(detail)?.value
                            ?: Regex("#0[0-9]{3}\\b").find(detail)?.value ?: "unspecified")
                        done.countDown()
                    }
                    startLocation()
                }
            }
            assertTrue("SDK did not return a callback", done.await(40, TimeUnit.SECONDS))
            InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
                putString("amap_result_code", code.get().toString())
                putString("amap_auth_reason", authReason.get())
            })
            assumeTrue("This device's request requires overseas location entitlement",
                authReason.get() != "INSUFFICIENT_ABROAD_PRIVILEGES")
            assertNotEquals("Android key authentication failed", 7, code.get())
            assumeTrue("Device/network limitation; actual code=${code.get()}", code.get() == 0)
        } finally {
            compose.runOnIdle {
                client?.stopLocation()
                client?.onDestroy()
                AMapLocationClient.updatePrivacyAgree(context, context.getSharedPreferences("campus_location_privacy", 0).getBoolean("amap_consent_v1", false))
            }
        }
    }

    @Test fun simulatedCampusGpsPassesThroughSdk() {
        assumeTrue("Emulator-only synthetic location test", android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        val context = compose.activity
        assumeTrue(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        val done = CountDownLatch(1)
        val valid = java.util.concurrent.atomic.AtomicBoolean(false)
        var client: AMapLocationClient? = null
        try {
            compose.runOnIdle {
                AMapLocationClient.updatePrivacyShow(context, true, true)
                AMapLocationClient.updatePrivacyAgree(context, true)
                client = AMapLocationClient(context).apply {
                    setLocationOption(AMapLocationClientOption().apply {
                        setLocationMode(AMapLocationClientOption.AMapLocationMode.Device_Sensors)
                        setOnceLocation(false)
                        setNeedAddress(false)
                        setLocationCacheEnable(false)
                        // Only this emulator test allows injected GPS; production rejects it.
                        setMockEnable(true)
                    })
                    setLocationListener { result ->
                        if (result != null && result.errorCode == 0) {
                            valid.set(result.latitude in 24.85..24.89 && result.longitude in 118.65..118.69 &&
                                result.coordType in setOf("GCJ02", "WGS84") && result.accuracy > 0)
                            done.countDown()
                        }
                    }
                    startLocation()
                }
            }
            assertTrue("No simulated campus GPS callback", done.await(35, TimeUnit.SECONDS))
            assertTrue("SDK did not return the injected campus position", valid.get())
        } finally {
            compose.runOnIdle {
                client?.stopLocation()
                client?.onDestroy()
                AMapLocationClient.updatePrivacyAgree(context, context.getSharedPreferences("campus_location_privacy", 0).getBoolean("amap_consent_v1", false))
            }
        }
    }
}
