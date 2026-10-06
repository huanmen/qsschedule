package cn.qztc.timetable.update

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException

class AppUpdateClientTest {
    @Test fun unavailableOrInvalidPrimaryFallsBackToGitee() {
        val fallback = AppUpdateClient.Manifest(13, "1.0.0", "https://gitee.com/test/app.apk", "a".repeat(64), 100)
        val calls = mutableListOf<String>()
        val found = AppUpdateClient.resolveManifest { url ->
            calls += url
            if (url == AppUpdateClient.PRIMARY_MANIFEST_URL) fallback.copy(sha256 = "bad") else fallback
        }
        assertEquals(fallback, found)
        assertEquals(listOf(AppUpdateClient.PRIMARY_MANIFEST_URL, AppUpdateClient.MANIFEST_URL), calls)
    }
    @Test fun downloadFallbackMustBeExactlySameRelease() {
        val original = AppUpdateClient.Manifest(13, "1.0.0", "https://huanmen.com.cn/qsschedule/downloads/app.apk", "a".repeat(64), 100)
        assertTrue(AppUpdateClient.sameRelease(original, original.copy(apkUrl = "https://gitee.com/test/app.apk")))
        assertFalse(AppUpdateClient.sameRelease(original, original.copy(latestVersionCode = 14)))
        assertFalse(AppUpdateClient.sameRelease(original, original.copy(sizeBytes = 99)))
        assertFalse(AppUpdateClient.sameRelease(original, original.copy(sha256 = "b".repeat(64))))
    }
    @Test fun progressReportsCopiedBytesInOrderAndReachesTotal() {
        val bytes = ByteArray(150_001)
        val progress = mutableListOf<Long>()
        AppUpdateClient.copyLimited(ByteArrayInputStream(bytes), ByteArrayOutputStream(), bytes.size.toLong(), onBytesCopied = { progress += it })
        assertEquals(bytes.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a < b })
    }

    @Test fun cancellationStopsBeforeWritingAnotherChunk() {
        val control = AppUpdateClient.DownloadControl()
        val output = ByteArrayOutputStream()
        val result = runCatching {
            AppUpdateClient.copyLimited(ByteArrayInputStream(ByteArray(200_000)), output, 200_000,
                onBytesCopied = { control.cancel() }, checkCancellation = control::ensureRunning)
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(64 * 1024, output.size())
    }

    @Test fun cancellationDisconnectsTheActiveNetworkRequest() {
        var disconnected = false
        val connection = object : HttpURLConnection(URL("https://gitee.com/test.apk")) {
            override fun connect() = Unit
            override fun usingProxy() = false
            override fun disconnect() { disconnected = true }
        }
        val control = AppUpdateClient.DownloadControl()
        control.attach(connection)
        control.cancel()
        assertTrue(disconnected)
        assertTrue(runCatching { control.ensureRunning() }.exceptionOrNull() is CancellationException)
    }

    @Test fun clearingRemovesCompleteAndPartialPackagesButPreservesOtherFiles() {
        val directory = Files.createTempDirectory("update-cleanup-test").toFile()
        try {
            File(directory, "qztc-timetable-6.apk").writeText("synthetic")
            File(directory, ".qztc-timetable-6.apk.part").writeText("partial")
            val unrelated = File(directory, "keep.txt").apply { writeText("keep") }
            assertEquals(2, AppUpdateClient.clearDownloadedFiles(directory))
            assertEquals("keep", unrelated.readText())
            assertEquals(0, AppUpdateClient.clearDownloadedFiles(directory))
        } finally { directory.deleteRecursively() }
    }

    @Test fun giteeReleaseAndFileDeliveryHostsAreAccepted() {
        listOf(
            "https://gitee.com/huandyu/qsschedule/releases/download/v0.3.2/app.apk",
            "https://foruda.gitee.com/attach_file/example/app.apk",
            "https://giteeusercontent.com/app.apk",
            "https://assets.giteeusercontent.com/app.apk",
            "https://huanmen.com.cn/qsschedule/downloads/qsschedule-1.0.0-release.apk",
        ).forEach { AppUpdateClient.validateDownloadUrl(it) }
    }

    @Test fun unrelatedAndLookalikeDownloadHostsRemainRejected() {
        listOf(
            "https://example.com/app.apk",
            "https://foruda.gitee.com.example.com/app.apk",
            "https://evilgiteeusercontent.com/app.apk",
            "https://other.gitee.com/app.apk",
            "https://huanmen.com.cn.example.com/qsschedule/app.apk",
            "https://huanmen.com.cn/other/app.apk",
            "https://huanmen.com.cn:8443/qsschedule/app.apk",
        ).forEach { value ->
            assertTrue(value, runCatching { AppUpdateClient.validateDownloadUrl(value) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun nonHttpsCredentialsAndFragmentsRemainRejected() {
        listOf(
            "http://foruda.gitee.com/app.apk",
            "https://user:password@foruda.gitee.com/app.apk",
            "https://foruda.gitee.com/app.apk#fragment",
        ).forEach { value ->
            assertTrue(value, runCatching { AppUpdateClient.validateDownloadUrl(value) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun chunkedDownloadCanReachButNeverExceedDeclaredLimit() {
        val bytes = ByteArray(130_001) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        AppUpdateClient.copyLimited(ByteArrayInputStream(bytes), output, bytes.size.toLong())
        assertArrayEquals(bytes, output.toByteArray())
        val rejected = runCatching { AppUpdateClient.copyLimited(ByteArrayInputStream(bytes), ByteArrayOutputStream(), 130_000) }
        assertTrue(rejected.exceptionOrNull() is IllegalArgumentException)
    }
}
