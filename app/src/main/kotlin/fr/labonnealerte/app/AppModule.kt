package fr.labonnealerte.app

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.labonnealerte.core.network.ClientInfo
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * Build debug uniquement : si le fichier `files/use_fake_api` existe, l'app parle au serveur
     * factice de la machine hote (tools/fake-api) via « adb reverse tcp:8787 tcp:8787 » au lieu de la production.
     *   adb shell run-as fr.labonnealerte.app.debug touch files/use_fake_api
     */
    @Provides
    @Singleton
    fun clientInfo(@ApplicationContext ctx: Context): ClientInfo {
        val fake = BuildConfig.DEBUG && BuildConfig.FAKE_API_BASE_URL.isNotEmpty() && File(ctx.filesDir, "use_fake_api").exists()
        val url = if (fake) BuildConfig.FAKE_API_BASE_URL else BuildConfig.API_BASE_URL
        timber.log.Timber.i("Serveur : %s", if (fake) "factice" else "production")
        return object : ClientInfo {
            override val baseUrl: String = url
            override val clientHeader: String = "android/${BuildConfig.VERSION_NAME}"
        }
    }
}
