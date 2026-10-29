# Paho MQTT uses reflection for its logger and resource bundles.
-keep class org.eclipse.paho.client.mqttv3.** { *; }
-dontwarn org.eclipse.paho.client.mqttv3.**

# Methods called from JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
