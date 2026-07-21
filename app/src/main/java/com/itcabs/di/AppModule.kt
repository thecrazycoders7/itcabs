package com.itcabs.di

import com.itcabs.core.network.AuthApi
import com.itcabs.core.network.DispatchApi
import com.itcabs.core.network.NetworkFactory
import com.itcabs.data.AuthRepositoryImpl
import com.itcabs.data.DispatchRepositoryImpl
import com.itcabs.data.InMemoryTokenStore
import com.itcabs.data.TokenStore
import com.itcabs.domain.repository.AuthRepository
import com.itcabs.domain.repository.DispatchRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The composition root. Wires the pure-JVM data layer (:data + :core:network) into the app.
 * This module is where Hilt validates the whole object graph at compile time.
 *
 * ponytail: base URL is the emulator→host loopback for the dev backend. Move to BuildConfig
 * / a config seam when there are real environments; TokenStore is in-memory until :core:datastore.
 */
private const val DEV_BASE_URL = "http://10.0.2.2:8081/"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun tokenStore(): TokenStore = InMemoryTokenStore()

    @Provides
    @Singleton
    fun authApi(tokenStore: TokenStore): AuthApi =
        NetworkFactory.authApi(DEV_BASE_URL, tokenStore, debug = true)

    @Provides
    @Singleton
    fun dispatchApi(tokenStore: TokenStore): DispatchApi =
        NetworkFactory.dispatchApi(DEV_BASE_URL, tokenStore, debug = true)

    @Provides
    @Singleton
    fun authRepository(api: AuthApi, tokenStore: TokenStore): AuthRepository =
        AuthRepositoryImpl(api, tokenStore)

    @Provides
    @Singleton
    fun dispatchRepository(api: DispatchApi): DispatchRepository =
        DispatchRepositoryImpl(api)
}
