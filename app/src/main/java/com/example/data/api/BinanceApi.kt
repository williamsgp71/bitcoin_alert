package com.example.data.api

import com.example.data.model.BitcoinTicker
import retrofit2.http.GET
import retrofit2.http.Query

interface BinanceApi {
    @GET("api/v3/ticker/24hr")
    suspend fun getTicker(
        @Query("symbol") symbol: String = "BTCUSDT"
    ): BitcoinTicker

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String = "BTCUSDT",
        @Query("interval") interval: String,
        @Query("limit") limit: Int
    ): List<List<Any>>
}
