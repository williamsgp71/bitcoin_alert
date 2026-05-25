package com.example.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.api.BinanceApi
import com.example.data.db.AlertDao
import com.example.data.model.BitcoinTicker
import com.example.data.model.ChartInterval
import com.example.data.model.PriceAlert
import com.example.data.model.PricePoint
import kotlinx.coroutines.flow.Flow
import java.math.RoundingMode
import java.text.DecimalFormat

class CryptoRepository(
    private val binanceApi: BinanceApi,
    private val alertDao: AlertDao,
    private val context: Context
) {
    val allAlerts: Flow<List<PriceAlert>> = alertDao.getAllAlertsFlow()

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val CHANNEL_ID = "bitcoin_alerts_channel"
        private const val CHANNEL_NAME = "Bitcoin Alerts"
        private const val TAG = "CryptoRepository"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones para alertas de precio de Bitcoin"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Obtiene los valores actuales de Bitcoin.
     */
    suspend fun getBitcoinTicker(): BitcoinTicker {
        return binanceApi.getTicker("BTCUSDT")
    }

    /**
     * Obtiene datos históricos de Bitcoin y los parsea a PricePoint defensively.
     */
    suspend fun getBitcoinHistory(interval: ChartInterval): List<PricePoint> {
        return try {
            val rawKlines = binanceApi.getKlines(
                symbol = "BTCUSDT",
                interval = interval.binanceInterval,
                limit = interval.limit
            )
            rawKlines.mapNotNull { kline ->
                if (kline.size >= 5) {
                    val timestampNum = kline[0] as? Number
                    val closePriceStr = kline[4] as? String
                    
                    val timestamp = timestampNum?.toLong() ?: return@mapNotNull null
                    val price = closePriceStr?.toDoubleOrNull() ?: return@mapNotNull null
                    
                    PricePoint(timestamp = timestamp, price = price)
                } else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching bitcoin history", e)
            emptyList()
        }
    }

    /**
     * Inserta una nueva alerta de precio en la base de datos.
     */
    suspend fun insertAlert(alert: PriceAlert): Long {
        return alertDao.insertAlert(alert)
    }

    /**
     * Elimina una alerta de precio.
     */
    suspend fun deleteAlert(alert: PriceAlert) {
        alertDao.deleteAlert(alert)
    }

    /**
     * Elimina una alerta por ID.
     */
    suspend fun deleteAlertById(id: Int) {
        alertDao.deleteAlertById(id)
    }

    /**
     * Restablece una alerta disparada para que se vuelva a activar.
     */
    suspend fun resetAlert(id: Int) {
        alertDao.resetAlert(id)
    }

    /**
     * Compara el precio actual con las alertas activas, las dispara y lanza notificaciones locales si corresponde.
     */
    suspend fun checkAndTriggerAlerts(currentPrice: Double) {
        try {
            val activeAlerts = alertDao.getActiveAlerts()
            for (alert in activeAlerts) {
                val shouldTrigger = if (alert.isAbove) {
                    currentPrice >= alert.targetPrice
                } else {
                    currentPrice <= alert.targetPrice
                }

                if (shouldTrigger) {
                    // Actualizar alerta en BD
                    val triggeredAlert = alert.copy(
                        isTriggered = true,
                        triggeredAt = System.currentTimeMillis()
                    )
                    alertDao.updateAlert(triggeredAlert)

                    // Lanzar notificación del sistema
                    sendSystemNotification(triggeredAlert, currentPrice)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking or triggering alerts", e)
        }
    }

    private fun sendSystemNotification(alert: PriceAlert, actualPrice: Double) {
        val df = DecimalFormat("$#,##0.00").apply { roundingMode = RoundingMode.HALF_UP }
        val priceFormatted = df.format(actualPrice)
        val targetFormatted = df.format(alert.targetPrice)
        val title = "🚨 ¡Alerta de Bitcoin!"
        
        val directionString = if (alert.isAbove) "superado" else "caído por debajo de"
        val customLabel = if (alert.label.isNotBlank()) " [${alert.label}]" else ""
        
        val content = "Bitcoin ha $directionString su precio objetivo de $targetFormatted. Precio actual: $priceFormatted.$customLabel"

        // Usamos un ID único por alerta para no acumular las mismas notificaciones
        val notificationId = alert.id

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning) // Usamos un icono por defecto del sistema
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing POST_NOTIFICATIONS permission to send warning", e)
        }
    }
}
