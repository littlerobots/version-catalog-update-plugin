# Keep your public API so that it's callable from scripts
-keep class nl.littlerobots.vcu.** { *; }

# Repackage other classes
-repackageclasses nl.littlerobots.vcu.relocated
-dontobfuscate

# Allows more aggressive repackaging
-allowaccessmodification

# We need to keep type arguments for Gradle to be able to instantiate abstract models like `Property`
-keepattributes Signature,Exceptions,*Annotation*,InnerClasses,PermittedSubclasses,EnclosingMethod,Deprecated,SourceFile,LineNumberTable

# Keep kotlin metadata so that the Kotlin compiler knows about top level functions
-keep class kotlin.Metadata { *; }
# Keep Unit as it's in the signature of public methods:
-keep class kotlin.Unit { *; }