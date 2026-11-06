package fr.labonnealerte.core.data

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import fr.labonnealerte.core.data.session.KeystoreTokenCipher
import fr.labonnealerte.core.data.session.SessionStore
import fr.labonnealerte.core.data.session.TokenCipher
import fr.labonnealerte.core.network.SessionTokenProvider

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    fun clock(): Clock = Clock.System

    @Provides
    fun tokenCipher(): TokenCipher = KeystoreTokenCipher()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {
    @Binds
    abstract fun tokenProvider(s: SessionStore): SessionTokenProvider

    @Multibinds
    abstract fun sessionHooks(): Set<SessionHook>
}
