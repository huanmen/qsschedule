# Required by the location SDK's reflective service and internal models.
-keep class com.amap.api.location.** { *; }
-keep class com.amap.api.fence.** { *; }
-keep class com.loc.** { *; }
-keep class com.autonavi.aps.amapapi.model.** { *; }
# Present only in AMap's combined SDK. The standalone location artifact keeps
# the branch disabled but still contains the bytecode reference.
-dontwarn com.amap.ams.gnss.GnssSoftLocator
