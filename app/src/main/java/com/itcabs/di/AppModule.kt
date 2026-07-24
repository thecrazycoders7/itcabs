package com.itcabs.di

import com.itcabs.core.network.AuthApi
import com.itcabs.core.network.ChatApi
import com.itcabs.core.network.DispatchApi
import com.itcabs.core.network.DriverApi
import com.itcabs.core.network.NetworkFactory
import com.itcabs.core.network.PushApi
import com.itcabs.core.network.RealtimeClient
import com.itcabs.core.network.SupabaseAuthApi
import com.itcabs.PushTokenManager
import android.content.Context
import com.itcabs.BuildConfig
import com.itcabs.PersistentDeviceId
import com.itcabs.PersistentTokenStore
import com.itcabs.core.database.LegDao
import com.itcabs.core.database.UserDao
import com.itcabs.data.AuthRepositoryImpl
import com.itcabs.data.ChatRepositoryImpl
import com.itcabs.data.DeviceIdProvider
import com.itcabs.data.DispatchRepositoryImpl
import com.itcabs.data.DriverRepositoryImpl
import com.itcabs.data.TokenStore
import com.itcabs.domain.repository.AuthRepository
import com.itcabs.domain.repository.ChatRepository
import com.itcabs.domain.repository.DispatchRepository
import com.itcabs.domain.repository.DriverRepository
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
        NetworkFactory.authApi(BuildConfig.BASE_URL, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, tokenStore, debug = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun dispatchApi(tokenStore: TokenStore): DispatchApi =
        NetworkFactory.dispatchApi(BuildConfig.BASE_URL, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, tokenStore, debug = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun driverApi(tokenStore: TokenStore): DriverApi =
        NetworkFactory.driverApi(BuildConfig.BASE_URL, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, tokenStore, debug = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun deviceIdProvider(@ApplicationContext context: Context): DeviceIdProvider =
        PersistentDeviceId(context)

    @Provides
    @Singleton
    fun supabaseAuthApi(): SupabaseAuthApi =
        NetworkFactory.supabaseAuthApi(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)

    @Provides
    @Singleton
    fun authRepository(api: AuthApi, supabase: SupabaseAuthApi, tokenStore: TokenStore, userDao: UserDao): AuthRepository =
        AuthRepositoryImpl(api, supabase, tokenStore, userDao)

    @Provides
    @Singleton
    fun driverRepository(api: DriverApi): DriverRepository =
        DriverRepositoryImpl(api)

    @Provides
    @Singleton
    fun realtimeClient(tokenStore: TokenStore): RealtimeClient =
        NetworkFactory.realtimeClient(BuildConfig.BASE_URL, tokenStore)

    @Provides
    @Singleton
    fun pushApi(tokenStore: TokenStore): PushApi =
        NetworkFactory.pushApi(BuildConfig.BASE_URL, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, tokenStore, debug = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun pushTokenManager(api: PushApi): PushTokenManager = PushTokenManager(api)

    @Provides
    @Singleton
    fun chatApi(tokenStore: TokenStore): ChatApi =
        NetworkFactory.chatApi(BuildConfig.BASE_URL, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, tokenStore, debug = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun chatRepository(api: ChatApi): ChatRepository = ChatRepositoryImpl(api)

    @Provides
    @Singleton
    fun dispatchRepository(api: DispatchApi, dao: LegDao, realtime: RealtimeClient): DispatchRepository =
        DispatchRepositoryImpl(api, dao, realtime)

    @Provides
    @Singleton
    fun companyJobApi(tokenStore: TokenStore): com.itcabs.core.network.CompanyJobApi =
        NetworkFactory.companyJobApi(BuildConfig.BASE_URL, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY, tokenStore, debug = BuildConfig.DEBUG)

    @Provides
    @Singleton
    fun companyJobRepository(api: com.itcabs.core.network.CompanyJobApi): com.itcabs.domain.repository.CompanyJobRepository =
        com.itcabs.data.CompanyJobRepositoryImpl(api)
}
