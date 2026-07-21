package com.itcabs.di

import com.itcabs.core.network.AuthApi
import com.itcabs.core.network.DispatchApi
import com.itcabs.core.network.NetworkFactory
import android.content.Context
import com.itcabs.BuildConfig
import com.itcabs.PersistentTokenStore
import com.itcabs.data.AuthRepositoryImpl
import com.itcabs.data.DispatchRepositoryImpl
import com.itcabs.data.TokenStore
import com.itcabs.domain.repository.AuthRepository
import com.itcabs.domain.repository.DispatchRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The composition root. Wires the pure-JVM data layer (:data + :core:network) into the app.
 * This module is where Hilt validates the whole object graph at compile time.
 *
 * Base URL comes from BuildConfig.BASE_URL — debug → local dev backend, release → hosted
 * (set in app/build.gradle.kts; see docs/DEPLOY.md).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun tokenStore(@ApplicationContext context: Context): TokenStore = PersistentTokenStore(context)

    @Provides
    @Singleton
    fun authApi(tokenStore: TokenStore): AuthApi =
        NetworkFactory.authApi(BuildConfig.BASE_URL, tokenStore, debug = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun dispatchApi(tokenStore: TokenStore): DispatchApi =
        NetworkFactory.dispatchApi(BuildConfig.BASE_URL, tokenStore, debug = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun authRepository(api: AuthApi, tokenStore: TokenStore): AuthRepository =
        AuthRepositoryImpl(api, tokenStore)

    @Provides
    @Singleton
    fun dispatchRepository(api: DispatchApi): DispatchRepository =
        DispatchRepositoryImpl(api)
}
