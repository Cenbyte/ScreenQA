# liquidglass 2.0.11 references these Android 16 (API 36) classes only in its
# version-guarded optional effects. compileSdk 35 does not expose them. Preserve
# the existing lower-API fallbacks; do not suppress missing classes globally.
-dontwarn android.graphics.RuntimeColorFilter
-dontwarn android.graphics.RuntimeXfermode
