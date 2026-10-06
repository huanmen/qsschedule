package cn.qztc.timetable

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Test

/** Real-device diagnostic independent of Compose idleness; never exports location or SDK detail. */
class AmapPhoneDiagnosticTest {
    @Test fun freshLocationRequest() {
        assumeFalse("Real-device diagnostic is not meaningful on an emulator", Build.FINGERPRINT.contains("generic") || Build.HARDWARE.contains("ranchu"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION))
        val done = CountDownLatch(1)
        val code = AtomicInteger(-1)
        val reason = AtomicReference("unspecified")
        val valid = java.util.concurrent.atomic.AtomicBoolean(false)
        var client: AMapLocationClient? = null
        try {
            instrumentation.runOnMainSync {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
                        reason.set(listOf("INSUFFICIENT_ABROAD_PRIVILEGES", "INVALID_USER_SCODE", "INVALID_USER_KEY",
                            "USERKEY_PLAT_NOMATCH", "INVALID_USER_SIGNATURE", "SERVICE_NOT_AVAILABLE",
                            "USER_DAILY_QUERY_OVER_LIMIT", "INSUFFICIENT_PRIVILEGES").firstOrNull { it in detail } ?: "unspecified")
                        valid.set(result != null && result.errorCode == 0 && !result.isMock() && result.latitude.isFinite() &&
                            result.longitude.isFinite() && result.latitude in -90.0..90.0 && result.longitude in -180.0..180.0 &&
                            result.accuracy.isFinite() && result.accuracy > 0 && result.coordType in setOf("GCJ02", "WGS84"))
                        done.countDown()
                    }
                    startLocation()
                }
            }
            assertTrue("No location callback within 40 seconds", done.await(40, TimeUnit.SECONDS))
            instrumentation.sendStatus(0, Bundle().apply {
                putString("amap_result_code", code.get().toString())
                putString("amap_auth_reason", reason.get())
                putString("valid_location_received", valid.get().toString())
            })
            assertEquals("Location failed: ${reason.get()}", 0, code.get())
            assertTrue("Invalid location fields", valid.get())
        } finally {
            instrumentation.runOnMainSync {
                client?.stopLocation()
                client?.onDestroy()
                AMapLocationClient.updatePrivacyAgree(context, context.getSharedPreferences("campus_location_privacy", 0).getBoolean("amap_consent_v1", false))
            }
        }
    }
}
