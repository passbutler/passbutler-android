# Uncomment this to preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable

## Tinylog
-keepnames interface org.tinylog.**
-keepnames class * implements org.tinylog.**
-keepclassmembers class * implements org.tinylog.** { <init>(...); }