# R8 is on for release (Design §1). Keep this file short; add a rule only with a reason.

# Design §14: no user value may reach logcat in release. R8 removes these calls.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
