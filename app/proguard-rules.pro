# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# -dontobfuscate
#-renamesourcefileattribute SourceFile
#-keepattributes SourceFile,LineNumberTable

-dontwarn com.arialyy.aria.**
-keep class com.arialyy.aria.**{*;}
-keep class **$$DownloadListenerProxy{ *; }
-keep class **$$UploadListenerProxy{ *; }
-keep class **$$DownloadGroupListenerProxy{ *; }
-keep class **$$DGSubListenerProxy{ *; }
-keep class com.hzy.lib7z.**{*;}
-keepclasseswithmembernames class * {
    @Download.* <methods>;
    @Upload.* <methods>;
    @DownloadGroup.* <methods>;
}

# LocalePlugin 混淆规则
-keep class com.mallotec.reb.localeplugin.** { *; }
-dontwarn com.mallotec.reb.localeplugin.**

-keep class com.termux.zerocore.ftp.new_ftp.** { *; }
-dontwarn com.termux.zerocore.ftp.new_ftp.**

-keep class org.apache.mina.** { *; }
-dontwarn org.apache.mina.**

-keep class org.apache.ftpserver.** { *; }
-dontwarn org.apache.ftpserver.**

# 保留 CmdEntryPoint 及其依赖
-keep class com.termux.x11.CmdEntryPoint { *; }
-keep class com.termux.x11.Loader { *; }

# 保留所有native方法
-keepclasseswithmembernames class * {
    native <methods>;
}

# 保留自定义异常类
-keep public class * extends java.lang.Exception

# 保留ActivityThread相关反射类
-keep class android.app.ActivityThread { *; }
-keepclassmembers class android.app.ActivityThread {
    public static *** currentActivityThread();
    public *** getSystemContext();
}

# 忽略无害警告 (减少构建噪音)
-dontwarn com.alipay.sdk.**
-dontwarn com.alipay.api.**
-dontwarn java.beans.**
-dontwarn com.google.zxing.**
-dontwarn com.lzy.okgo.**
-dontwarn cn.bingoogolapple.photopicker.**
-dontwarn com.draggable.library.extension.**

# 保护签名/注解信息（对 Gson/反射/注解框架关键）
-keepattributes Signature
-keepattributes *Annotation*

# 保护数据模型
-keep class com.termux.zerocore.bean.** { *; }

# 忽略 R8 缺失类警告 (由第三方库依赖引起)
-dontwarn com.trilead.ssh2.**
-dontwarn dalvik.annotation.optimization.**
-dontwarn kotlinx.parcelize.**
-dontwarn kotlinx.serialization.**

# 保持这些类不被混淆，因为它们可能被反射使用或 R8 误判
-keep class com.trilead.ssh2.** { *; }
-keep class dalvik.annotation.optimization.** { *; }
-keep class kotlinx.parcelize.** { *; }
-keep class kotlinx.serialization.** { *; }

# Conscrypt rules
-dontwarn org.conscrypt.**
-keep class org.conscrypt.** { *; }
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.xnet.provider.jsse.**
