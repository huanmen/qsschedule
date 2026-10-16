package cn.qztc.timetable.feature.settings

import android.content.Context

/** Shared with the embedded calendar so imported nodes use the same persisted palette choice. */
object CalendarCardColors {
    fun preferences(context:Context)=context.getSharedPreferences("calendar-card-colors",Context.MODE_PRIVATE)
    fun read(context:Context):Map<String,String> = preferences(context).all.mapNotNull {(key,value) ->
        (value as? String)?.takeIf {it.matches(Regex("#[0-9a-fA-F]{6}"))}?.let {key to it}
    }.toMap()
    fun set(context:Context,key:String,color:String?) {
        require(key.length<=300)
        require(color==null||color.matches(Regex("#[0-9a-fA-F]{6}")))
        preferences(context).edit().apply {if(color==null)remove(key)else putString(key,color)}.apply()
    }
}
