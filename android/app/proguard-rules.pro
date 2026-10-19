# Adivery mediation adapters reference some networks that are optional in this
# build. MBridge is not bundled; suppress only its absent adapter references so
# release R8 can optimize the app without treating them as required classes.
-dontwarn com.mbridge.msdk.**
