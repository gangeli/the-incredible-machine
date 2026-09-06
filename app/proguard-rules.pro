# The activity is referenced from the manifest; everything else is reachable from it.
-keep class tim.android.MainActivity { <init>(); }
-dontwarn kotlin.**
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkNotNull(...);
    static void checkNotNullParameter(...);
    static void checkNotNullExpressionValue(...);
    static void checkExpressionValueIsNotNull(...);
    static void checkParameterIsNotNull(...);
    static void checkFieldIsNotNull(...);
    static void checkReturnedValueIsNotNull(...);
    static void throwUninitializedPropertyAccessException(...);
}
