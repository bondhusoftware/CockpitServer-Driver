package com.bondhu.cockpitserver.server

import android.content.Context
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Server Driver (v1): Retrofit singleton।
 * Base URL runtime-এ বদলালে rebuild করে।
 */
object ApiClient {
    @Volatile private var api: ApiInterface? = null
    @Volatile private var currentBase: String = ""

    @Synchronized
    fun get(context: Context): ApiInterface {
        val base = ServerConfig.getBaseUrl(context)
        if (api == null || base != currentBase) {
            currentBase = base
            val http = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()
            api = Retrofit.Builder()
                .baseUrl(if (base.endsWith("/")) base else "$base/")
                .client(http)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiInterface::class.java)
        }
        return api!!
    }

    @Synchronized
    fun reset() {
        api = null
        currentBase = ""
    }
}
