package cn.qztc.timetable

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.Assert.assertTrue

class MainStartupProbeTest {
    @Test fun mainThreadRespondsAfterColdLaunch() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(3000)
        val response=CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { response.countDown() }
        val responsive=response.await(10,TimeUnit.SECONDS)
        // Method names only: never log UI text, credentials, or database contents.
        val stack=if(responsive) "responsive" else Looper.getMainLooper().thread.stackTrace.take(22).joinToString("\n")
        assertTrue("Main-thread startup probe: $stack",responsive)
    }
}
