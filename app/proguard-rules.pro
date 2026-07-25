# MockLocation ProGuard rules
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn org.osmdroid.**
