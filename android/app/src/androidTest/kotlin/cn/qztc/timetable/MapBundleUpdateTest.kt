package cn.qztc.timetable

import androidx.test.platform.app.InstrumentationRegistry
import cn.qztc.timetable.feature.timetable.OfflinePlaceRepository
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MapBundleUpdateTest {
    @Test fun bundledUpdatePreservesPhoneEditAndAddsNewRoadsAndGate() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.filesDir,"offline-campus-edits.json")
        val before=file.takeIf{it.exists()}?.readBytes()
        val prefs=context.getSharedPreferences("offline-map-updates",0)
        val marker="20260918-user-export-4"
        val wasMarked=prefs.getBoolean(marker,false)
        try {
            val old=JSONObject(context.assets.open("offline-campus/data/previous-initial-edits.json").bufferedReader().use{it.readText()})
            val local=old.getJSONArray("places").getJSONObject(0)
            val id=local.getString("id")
            local.put("name","Synthetic phone edit")
            file.writeText(old.toString());prefs.edit().remove(marker).commit()
            val merged=JSONObject(OfflinePlaceRepository(context).readEdits())
            val places=merged.getJSONArray("places")
            assertTrue((0 until places.length()).any{places.getJSONObject(it).getString("name")=="后山铁门"})
            assertEquals("Synthetic phone edit",(0 until places.length()).map{places.getJSONObject(it)}.first{it.getString("id")==id}.getString("name"))
            val roads=merged.getJSONArray("roads")
            assertTrue((0 until roads.length()).any{roads.getJSONObject(it).getString("id")=="road-mu52p9n7-yfyo"})
            assertTrue(prefs.getBoolean(marker,false))
        } finally {
            if(before!=null)file.writeBytes(before)else file.delete()
            prefs.edit().putBoolean(marker,wasMarked).commit()
        }
    }
}
