package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.RetrofitClient
import com.example.data.db.CryptoDatabase
import com.example.data.model.BitcoinTicker
import com.example.data.model.ChartInterval
import com.example.data.model.PriceAlert
import com.example.data.model.PricePoint
import com.example.data.repository.CryptoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TickerUiState {
    object Loading : TickerUiState
    data class Success(
        val ticker: BitcoinTicker,
        val isPriceRise: Boolean? = null // null: sin cambios, true: subió, false: bajó en la última actualización
    ) : TickerUiState
    data class Error(val error: String) : TickerUiState
}

sealed interface ChartUiState {
    object Loading : ChartUiState
    data class Success(val points: List<PricePoint>, val interval: ChartInterval) : ChartUiState
    data class Error(val error: String) : ChartUiState
}

class CryptoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: CryptoRepository
    private var livePriceJob: Job? = null

    // Estados de UI expuestos
    private val _tickerUiState = MutableStateFlow<TickerUiState>(TickerUiState.Loading)
    val tickerUiState: StateFlow<TickerUiState> = _tickerUiState.asStateFlow()

    private val _chartUiState = MutableStateFlow<ChartUiState>(ChartUiState.Loading)
    val chartUiState: StateFlow<ChartUiState> = _chartUiState.asStateFlow()

    private val _selectedInterval = MutableStateFlow(ChartInterval.TWENTY_FOUR_HOURS)
    val selectedInterval: StateFlow<ChartInterval> = _selectedInterval.asStateFlow()

    val alertsState: StateFlow<List<PriceAlert>>

    // Eventos de estado de operaciones (para mensajes como "Alerta agregada", etc.)
    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent: SharedFlow<String> = _uiEvent.asSharedFlow()

    // Control de simulación (para pruebas rápidas de alertas)
    private val _simulatedPrice = MutableStateFlow<Double?>(null)
    val simulatedPrice: StateFlow<Double?> = _simulatedPrice.asStateFlow()

    private var previousPrice: Double? = null

    init {
        val database = CryptoDatabase.getDatabase(application)
        repository = CryptoRepository(
            binanceApi = RetrofitClient.binanceApi,
            alertDao = database.alertDao,
            context = application
        )

        // Escuchar alertas desde Room
        alertsState = repository.allAlerts
            .catch { e -> Log.e("CryptoViewModel", "Error reading alerts", e) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Iniciar polling en tiempo real y carga inicial
        startRealtimeTracking()
        loadChartData(ChartInterval.TWENTY_FOUR_HOURS)
    }

    /**
     * Inicia el ciclo de actualización en tiempo real de Binance (cada 4 segundos)
     */
    fun startRealtimeTracking() {
        livePriceJob?.cancel()
        livePriceJob = viewModelScope.launch {
            while (true) {
                fetchLatestBitcoinPrice()
                delay(4000) // Actualiza cada 4 segundos para un efecto de tiempo real fluido
            }
        }
    }

    /**
     * Detiene el rastreador en vivo si se desea pausar
     */
    fun stopRealtimeTracking() {
        livePriceJob?.cancel()
        livePriceJob = null
    }

    /**
     * Método interno para consultar el precio actual
     */
    private suspend fun fetchLatestBitcoinPrice() {
        try {
            val ticker = repository.getBitcoinTicker()
            val currentPrice = _simulatedPrice.value ?: ticker.lastPriceDouble

            // Determinar si el precio de BTC subió o bajó con respecto a la iteración anterior
            val isRise = when {
                previousPrice == null -> null
                currentPrice > previousPrice!! -> true
                currentPrice < previousPrice!! -> false
                else -> null
            }

            _tickerUiState.value = TickerUiState.Success(
                ticker = if (_simulatedPrice.value != null) {
                    // Si estamos simulando, sobreescribimos lastPrice
                    ticker.copy(lastPrice = _simulatedPrice.value.toString())
                } else ticker,
                isPriceRise = isRise
            )

            previousPrice = currentPrice

            // Comprobar alertas activas con el precio actual
            repository.checkAndTriggerAlerts(currentPrice)

        } catch (e: Exception) {
            Log.e("CryptoViewModel", "Error fetching price tick", e)
            if (_tickerUiState.value is TickerUiState.Loading) {
                _tickerUiState.value = TickerUiState.Error("Error al conectar con Binance API. Verifica tu conexión.")
            }
        }
    }

    /**
     * Fuerza recarga manual del precio y la gráfica
     */
    fun refreshAll() {
        viewModelScope.launch {
            _tickerUiState.value = TickerUiState.Loading
            fetchLatestBitcoinPrice()
            loadChartData(_selectedInterval.value)
            _uiEvent.emit("Datos actualizados de forma manual")
        }
    }

    /**
     * Carga el historial del gráfico según el intervalo seleccionado
     */
    fun loadChartData(interval: ChartInterval) {
        _selectedInterval.value = interval
        viewModelScope.launch {
            _chartUiState.value = ChartUiState.Loading
            try {
                val dataPoints = repository.getBitcoinHistory(interval)
                if (dataPoints.isNotEmpty()) {
                    _chartUiState.value = ChartUiState.Success(dataPoints, interval)
                } else {
                    _chartUiState.value = ChartUiState.Error("No se pudieron reconstruir los puntos del gráfico.")
                }
            } catch (e: Exception) {
                Log.e("CryptoViewModel", "Error loading chart", e)
                _chartUiState.value = ChartUiState.Error("Fallo de red al descargar histórico.")
            }
        }
    }

    /**
     * Agrega una alerta personalizada
     */
    fun createAlert(targetPrice: Double, isAbove: Boolean, label: String = "") {
        viewModelScope.launch {
            val alert = PriceAlert(
                targetPrice = targetPrice,
                isAbove = isAbove,
                label = label
            )
            repository.insertAlert(alert)
            _uiEvent.emit("Alerta para $targetPrice USD creada con éxito")
        }
    }

    /**
     * Elimina una alerta por id o entidad
     */
    fun deleteAlert(alert: PriceAlert) {
        viewModelScope.launch {
            repository.deleteAlert(alert)
            _uiEvent.emit("Alerta eliminada")
        }
    }

    /**
     * Restablece una alerta disparada para volver a monitorearla
     */
    fun resetAlert(id: Int) {
        viewModelScope.launch {
            repository.resetAlert(id)
            _uiEvent.emit("Alerta activa nuevamente")
        }
    }

    /**
     * Simula un precio para pruebas inmediatas de alertas en local
     */
    fun simulatePrice(price: Double?) {
        viewModelScope.launch {
            _simulatedPrice.value = price
            if (price != null) {
                _uiEvent.emit("Precio simulado en $price USD")
                // Evaluar inmediatamente
                fetchLatestBitcoinPrice()
            } else {
                _uiEvent.emit("Volviendo a precio real de Bitcoin")
                previousPrice = null
                fetchLatestBitcoinPrice()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRealtimeTracking()
    }
}
