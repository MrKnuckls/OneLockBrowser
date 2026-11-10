# OneLock Browser — keep WebView JS bridges (b4) and backend model classes.
-keepclassmembers class com.knuckls.onelockbrowser.** {
    @android.webkit.JavascriptInterface <methods>;
}
