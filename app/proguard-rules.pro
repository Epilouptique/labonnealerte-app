# Regles R8 de l'app. Les bibliotheques (Retrofit, OkHttp, kotlinx.serialization,
# Room, Hilt, WorkManager) fournissent leurs propres regles consommateur.

# Credential Manager (flavor play, APP-22).
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** { *; }

# Modeles serialises : garder les serialiseurs generes.
-keepclassmembers @kotlinx.serialization.Serializable class fr.labonnealerte.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Retrofit : interfaces d'API
-keep,allowobfuscation,allowshrinking interface fr.labonnealerte.core.network.** { *; }

# Pas de numeros de ligne/fichiers sources inutiles dans l'APK (reproductibilite, taille)
-renamesourcefileattribute SourceFile

# Journaux de debogage retires du binaire release (aucune trace, meme anodine).
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
}
