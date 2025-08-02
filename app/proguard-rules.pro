# Proguard rules for TalkToFigma Desktop
# We aren't obfuscating, but this file is needed to enable dSYM generation.

-dontobfuscate
-keepattributes Signature,InnerClasses,EnclosingMethod
-keep class kr.co.metadata.mcp.** { *; }
-keep interface kr.co.metadata.mcp.** { *; } 