package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa los detalles actuales de Bitcoin obtenidos de Binance Ticker 24h.
 */
data class BitcoinTicker(
    val symbol: String,
    val priceChange: String,
    val priceChangePercent: String,
    val lastPrice: String,
    val highPrice: String,
    val lowPrice: String,
    val volume: String,
    val openPrice: String
) {
    val lastPriceDouble: Double get() = lastPrice.toDoubleOrNull() ?: 0.0
    val priceChangePercentDouble: Double get() = priceChangePercent.toDoubleOrNull() ?: 0.0
    val priceChangeDouble: Double get() = priceChange.toDoubleOrNull() ?: 0.0
    val highPriceDouble: Double get() = highPrice.toDoubleOrNull() ?: 0.0
    val lowPriceDouble: Double get() = lowPrice.toDoubleOrNull() ?: 0.0
    val volumeDouble: Double get() = volume.toDoubleOrNull() ?: 0.0
    val openPriceDouble: Double get() = openPrice.toDoubleOrNull() ?: 0.0
}

/**
 * Entidad de Room para representar una alerta de precio personalizada.
 */
@Entity(tableName = "price_alerts")
data class PriceAlert(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val targetPrice: Double,
    val isAbove: Boolean, // true si alerta cuando el precio sube por encima de, false si baja por debajo de
    val isTriggered: Boolean = false,
    val triggeredAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val label: String = ""
)

/**
 * Punto de datos individual para el gráfico interactivo.
 */
data class PricePoint(
    val timestamp: Long,
    val price: Double
)

/**
 * Intervalos de tiempo disponibles para el gráfico.
 */
enum class ChartInterval(
    val displayText: String,
    val binanceInterval: String,
    val limit: Int,
    val description: String
) {
    ONE_HOUR("1H", "1m", 60, "Última hora (1m interval)"),
    TWENTY_FOUR_HOURS("24H", "15m", 96, "Últimas 24 horas (15m interval)"),
    ONE_WEEK("1S", "2h", 84, "Última semana (2h interval)"),
    ONE_MONTH("1M", "12h", 60, "Último mes (12h interval)")
}
