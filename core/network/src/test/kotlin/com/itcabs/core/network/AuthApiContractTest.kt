package com.itcabs.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import kotlin.test.Test

class AuthApiContractTest {

    /**
     * validateEagerly makes Retrofit inspect every method's annotations at create() time,
     * so a malformed @POST/@Body/@Path/etc. or an unconvertible body type fails here instead
     * of at the first real call. No server needed.
     */
    @Test
    fun all_endpoints_have_valid_retrofit_annotations() {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://localhost/")
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .validateEagerly(true)
            .build()
        retrofit.create(AuthApi::class.java)
        retrofit.create(DispatchApi::class.java)
        retrofit.create(DriverApi::class.java)
    }
}
