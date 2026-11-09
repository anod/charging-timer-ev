# Preserve the Play-scanned API and Kotlin default-argument bridge from R8 optimization.
-keep class androidx.activity.EdgeToEdge {
    public static void enable(...);
    public static void enable$default(...);
}
