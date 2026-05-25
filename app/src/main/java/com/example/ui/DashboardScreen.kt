package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import com.example.data.model.BitcoinTicker
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.ChartInterval
import com.example.data.model.PriceAlert
import com.example.ui.components.InteractiveCryptoChart
import kotlinx.coroutines.flow.collectLatest
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: CryptoViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tickerState by viewModel.tickerUiState.collectAsState()
    val chartState by viewModel.chartUiState.collectAsState()
    val selectedInterval by viewModel.selectedInterval.collectAsState()
    val alerts by viewModel.alertsState.collectAsState()
    val simulatedPrice by viewModel.simulatedPrice.collectAsState()

    // Control de permisos de notificaciones para Android 13+
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Suscripción a alertas habilitada", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Alerta: No recibirás notificaciones del sistema sin permisos.", Toast.LENGTH_LONG).show()
        }
    }

    // Inicializar canal y solicitar permisos al cargar si es necesario
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Escuchar eventos / Toasts del ViewModel
    LaunchedEffect(viewModel.uiEvent) {
        viewModel.uiEvent.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Formulario de creación de alertas local
    var targetPriceInput by remember { mutableStateOf("") }
    var isAboveSelection by remember { mutableStateOf(true) }
    var alertLabelInput by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    // Control del simulador de precio activo
    var showSimulator by remember { mutableStateOf(false) }
    var simulatorInput by remember { mutableStateOf("") }

    // Formateador de precios
    val decimalFormat = remember { DecimalFormat("$#,##0.00").apply { roundingMode = RoundingMode.HALF_UP } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1113))
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // --- 1. Top Bar / App Header ---
        HeaderBar(
            onRefresh = { viewModel.refreshAll() },
            isSimulating = simulatedPrice != null
        )

        // Alerta de permisos omitidos para notificaciones
        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionWarningBanner(onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            })
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- 2. Live Price Hero Card & Stats ---
            item {
                when (val state = tickerState) {
                    is TickerUiState.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Sintonizando mercados en vivo...", color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                    is TickerUiState.Success -> {
                        PriceHeroCard(
                            ticker = state.ticker,
                            isPriceRise = state.isPriceRise,
                            simulatedPrice = simulatedPrice,
                            formatter = decimalFormat
                        )
                    }
                    is TickerUiState.Error -> {
                        ErrorCard(message = state.error, onAction = { viewModel.refreshAll() })
                    }
                }
            }

            // --- 3. Interactive Chart Visualizer ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1F)),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F4759)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Historial Cambios (BTC/USDT)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE2E2E6)
                                )
                                Text(
                                    text = "Gráfico interactivo de Binance",
                                    fontSize = 12.sp,
                                    color = Color(0xFF8E9199)
                                )
                            }
                            
                            // Selector de intervalo
                            IntervalTabs(
                                selectedInterval = selectedInterval,
                                onIntervalSelected = { viewModel.loadChartData(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Render de gráfica según estado
                        when (val chart = chartState) {
                            is ChartUiState.Loading -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Dibujando velas históricas...", color = Color.White.copy(alpha = 0.5f))
                                }
                            }
                            is ChartUiState.Success -> {
                                InteractiveCryptoChart(
                                    points = chart.points,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp),
                                    lineColor = if (chart.points.first().price <= chart.points.last().price) Color(0xFFB4F1BE) else Color(0xFFFF897A)
                                )
                            }
                            is ChartUiState.Error -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(chart.error, color = Color.White.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Button(
                                            onClick = { viewModel.loadChartData(selectedInterval) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF7931A))
                                        ) {
                                            Text("Reintentar", color = Color.Black)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 4. Mock Developer Price Simulator Section ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1F)),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F4759))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSimulator = !showSimulator },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Science,
                                    contentDescription = "Simulador",
                                    tint = Color(0xFFA8C7FA),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Simulador de Precios (Pruebas Rápidas)",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFE2E2E6)
                                    )
                                    Text(
                                        text = if (simulatedPrice != null) "Simulando precio: $${decimalFormat.format(simulatedPrice)}" else "Cambia el precio para activar tus alertas instantáneamente",
                                        fontSize = 11.sp,
                                        color = if (simulatedPrice != null) Color(0xFFB4F1BE) else Color(0xFF8E9199)
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (showSimulator) Icons.Outlined.Close else Icons.Default.PlayArrow,
                                contentDescription = "Expandir Simulador",
                                tint = Color(0xFF8E9199),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        AnimatedVisibility(visible = showSimulator) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                Text(
                                    text = "Fuerza un precio para simular subidas o caídas bruscas de Bitcoin y verificar las notificaciones del sistema de inmediato sin esperar al mercado real.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF8E9199)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = simulatorInput,
                                        onValueChange = { simulatorInput = it },
                                        label = { Text("Precio a Simular (USD)") },
                                        placeholder = { Text("Ejem: 85000") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFFA8C7FA),
                                            unfocusedBorderColor = Color(0xFF3F4759),
                                            focusedLabelColor = Color(0xFFA8C7FA),
                                            unfocusedLabelColor = Color(0xFF8E9199),
                                            focusedTextColor = Color(0xFFE2E2E6),
                                            unfocusedTextColor = Color(0xFFE2E2E6)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("simulator_price_input")
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Button(
                                            onClick = {
                                                val price = simulatorInput.toDoubleOrNull()
                                                if (price != null) {
                                                    viewModel.simulatePrice(price)
                                                } else {
                                                    Toast.makeText(context, "Ingresa un número decimal válido", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFFD0E4FF),
                                                contentColor = Color(0xFF003355)
                                            ),
                                            modifier = Modifier.testTag("submit_simulation_button")
                                        ) {
                                            Text("Fijar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                        if (simulatedPrice != null) {
                                            Button(
                                                onClick = {
                                                    viewModel.simulatePrice(null)
                                                    simulatorInput = ""
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF3F4759).copy(alpha = 0.4f),
                                                    contentColor = Color(0xFFE2E2E6)
                                                )
                                            ) {
                                                Text("Reset", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 5. Custom Alerts Config Form ---
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1F)),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F4759)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Alertas",
                                tint = Color(0xFFA8C7FA),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Configurar Nueva Alerta",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE2E2E6)
                            )
                        }
                        
                        Text(
                            text = "Te enviaremos una alerta en tiempo real cuando Bitcoin cruce el umbral.",
                            fontSize = 12.sp,
                            color = Color(0xFF8E9199),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Fila de controles de dirección (Subida / Caída)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isAboveSelection) Color(0xFF1E2A20) else Color(0xFF1B1B1F))
                                    .border(
                                        width = 1.dp,
                                        color = if (isAboveSelection) Color(0xFF2D4532) else Color(0xFF3F4759).copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { isAboveSelection = true }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = "Es Mayor que",
                                        tint = if (isAboveSelection) Color(0xFFB4F1BE) else Color(0xFF8E9199),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Suba por encima de",
                                        color = if (isAboveSelection) Color(0xFFE2E2E6) else Color(0xFF8E9199),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (!isAboveSelection) Color(0xFF370001) else Color(0xFF1B1B1F))
                                    .border(
                                        width = 1.dp,
                                        color = if (!isAboveSelection) Color(0xFF8C1D18) else Color(0xFF3F4759).copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { isAboveSelection = false }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDownward,
                                        contentDescription = "Es Menor que",
                                        tint = if (!isAboveSelection) Color(0xFFFF897A) else Color(0xFF8E9199),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Caiga por debajo de",
                                        color = if (!isAboveSelection) Color(0xFFE2E2E6) else Color(0xFF8E9199),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Campos Inputs
                        OutlinedTextField(
                            value = targetPriceInput,
                            onValueChange = {
                                targetPriceInput = it
                                inputError = null
                            },
                            label = { Text("Precio Objetivo (USD)") },
                            placeholder = { Text("Ej. 68500.00") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = inputError != null,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFA8C7FA),
                                unfocusedBorderColor = Color(0xFF3F4759),
                                focusedLabelColor = Color(0xFFA8C7FA),
                                unfocusedLabelColor = Color(0xFF8E9199),
                                focusedTextColor = Color(0xFFE2E2E6),
                                unfocusedTextColor = Color(0xFFE2E2E6),
                                errorBorderColor = Color(0xFFFF897A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("alert_price_input")
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = alertLabelInput,
                            onValueChange = { alertLabelInput = it },
                            label = { Text("Etiqueta Personalizada (Opcional)") },
                            placeholder = { Text("Ej. Alerta de Venta, Oportunidad") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFA8C7FA),
                                unfocusedBorderColor = Color(0xFF3F4759),
                                focusedLabelColor = Color(0xFFA8C7FA),
                                unfocusedLabelColor = Color(0xFF8E9199),
                                focusedTextColor = Color(0xFFE2E2E6),
                                unfocusedTextColor = Color(0xFFE2E2E6)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("alert_label_input")
                        )

                        inputError?.let {
                            Text(
                                text = it,
                                color = Color(0xFFFF897A),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val target = targetPriceInput.toDoubleOrNull()
                                if (target == null || target <= 0.0) {
                                    inputError = "Por favor, ingresa un valor de precio válido mayor a 0."
                                } else {
                                    viewModel.createAlert(
                                        targetPrice = target,
                                        isAbove = isAboveSelection,
                                        label = alertLabelInput
                                    )
                                    // Limpiar inputs
                                    targetPriceInput = ""
                                    alertLabelInput = ""
                                    inputError = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD0E4FF),
                                contentColor = Color(0xFF003355)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("create_alert_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = "Add Icon", tint = Color(0xFF003355))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Guardar Alerta", color = Color(0xFF003355), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // --- 6. Active / Triggered Alerts List ---
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mis Alertas Guardadas",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E2E6)
                    )
                    Text(
                        text = "${alerts.size} totales",
                        fontSize = 12.sp,
                        color = Color(0xFF8E9199)
                    )
                }
            }

            if (alerts.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.HourglassEmpty,
                            contentDescription = "Sin alertas",
                            tint = Color(0xFF8E9199).copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Aún no tienes alertas configuradas.",
                            color = Color(0xFF8E9199),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Define un precio arriba de para avisarte de inmediato.",
                            color = Color(0xFF8E9199).copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                items(alerts, key = { it.id }) { alert ->
                    AlertItemRow(
                        alert = alert,
                        onDelete = { viewModel.deleteAlert(alert) },
                        onReset = { viewModel.resetAlert(alert.id) },
                        formatter = decimalFormat
                    )
                }
            }

            // Espaciador final al fondo de la lista
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun HeaderBar(
    onRefresh: () -> Unit,
    isSimulating: Boolean
) {
    var isRotating by remember { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotating) 360f else 0f,
        animationSpec = tween(durationMillis = 600),
        label = "rotation"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F1113))
            .padding(vertical = 12.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color(0xFFE2E2E6),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = "Bitcoin Tracker",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE2E2E6)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFB4F1BE))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSimulating) "Precios Simulados" else "Tink en tiempo real activo",
                        fontSize = 10.sp,
                        color = Color(0xFF8E9199)
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(
                onClick = {
                    isRotating = !isRotating
                    onRefresh()
                },
                modifier = Modifier.testTag("force_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Actualizar",
                    tint = Color(0xFFE2E2E6),
                    modifier = Modifier.rotate(rotationAngle)
                )
            }
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = "Notificaciones",
                tint = Color(0xFFE2E2E6),
                modifier = Modifier.size(24.dp)
            )
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Cuenta",
                tint = Color(0xFFE2E2E6),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun PermissionWarningBanner(
    onRequestPermission: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF3D00).copy(alpha = 0.15f)),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF3D00).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = "Advertencia de permisos",
                tint = Color(0xFFFF3D00)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Permisos Desactivados",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Para recibir alertas push de precio alto/bajo necesitas habilitar notificaciones.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Habilitar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PriceHeroCard(
    ticker: BitcoinTicker,
    isPriceRise: Boolean?,
    simulatedPrice: Double?,
    formatter: DecimalFormat
) {
    // Animación de parpadeo suave al subir/bajar de precio
    val targetBgColor = when (isPriceRise) {
        true -> Color(0xFF1E2A20) // Dark green
        false -> Color(0xFF370001) // Dark red
        null -> Color(0xFF1B1B1F) // Dark surface
    }

    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = tween(durationMillis = 550),
        label = "price_flicker"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = animatedBgColor),
        shape = RoundedCornerShape(24.dp), // More modern round shape matching tailwind rounded-2xl
        border = if (simulatedPrice != null) {
            androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFA8C7FA))
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F4759)) // Sleek border matching theme
        }
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (ticker.priceChangePercentDouble >= 0) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                        contentDescription = "Tendencia",
                        tint = if (ticker.priceChangePercentDouble >= 0) Color(0xFFB4F1BE) else Color(0xFFFF897A),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "PRECIO ACTUAL",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = Color(0xFFC4C6CF)
                    )
                }

                if (simulatedPrice != null) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFFA8C7FA).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "MODO PRUEBA",
                            color = Color(0xFFA8C7FA),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Precio actual destacado en azul suave de la paleta
            val currentPriceDouble = ticker.lastPriceDouble
            val priceStr = formatter.format(currentPriceDouble)

            Text(
                text = priceStr,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD0E4FF),
                modifier = Modifier.testTag("live_bitcoin_price_text")
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Porcentaje card con bordes coloreados y fondos semitransparentes elegantes
            val pctChange = ticker.priceChangePercentDouble
            val pctStr = if (pctChange >= 0) "${String.format(Locale.getDefault(), "%.2f", pctChange)}% (24h)" else "${String.format(Locale.getDefault(), "%.2f", pctChange)}% (24h)"
            
            val isPositive = pctChange >= 0
            val bgPillColor = if (isPositive) Color(0xFF1E2A20) else Color(0xFF370001)
            val borderPillColor = if (isPositive) Color(0xFF2D4532) else Color(0xFF8C1D18)
            val textPillColor = if (isPositive) Color(0xFFB4F1BE) else Color(0xFFFF897A)

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(bgPillColor)
                    .border(1.dp, borderPillColor, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = textPillColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = pctStr,
                        color = textPillColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color(0xFF3F4759).copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Subgráficos del ticker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TickerMinichip(
                    title = "Máximo 24H",
                    value = formatter.format(ticker.highPriceDouble),
                    indicatorColor = Color(0xFFB4F1BE)
                )
                TickerMinichip(
                    title = "Mínimo 24H",
                    value = formatter.format(ticker.lowPriceDouble),
                    indicatorColor = Color(0xFFFF897A)
                )
                TickerMinichip(
                    title = "Volumen (BTC)",
                    value = String.format(Locale.getDefault(), "%,.1f", ticker.volumeDouble),
                    indicatorColor = Color(0xFFA8C7FA)
                )
            }
        }
    }
}

@Composable
fun TickerMinichip(
    title: String,
    value: String,
    indicatorColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, fontSize = 10.sp, color = Color(0xFF8E9199))
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE2E2E6)
            )
        }
    }
}

@Composable
fun ErrorCard(
    message: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF3D00).copy(alpha = 0.1f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Error icon",
                tint = Color(0xFFFF3D00),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = Color.White,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D00)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Reintentar Conexión", color = Color.White)
            }
        }
    }
}

@Composable
fun IntervalTabs(
    selectedInterval: ChartInterval,
    onIntervalSelected: (ChartInterval) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF3F4759).copy(alpha = 0.15f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ChartInterval.values().forEach { interval ->
            val isSelected = selectedInterval == interval
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Color(0xFF3F4759).copy(alpha = 0.4f) else Color.Transparent)
                    .clickable { onIntervalSelected(interval) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = interval.displayText,
                    color = if (isSelected) Color(0xFFA8C7FA) else Color(0xFF8E9199),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun AlertItemRow(
    alert: PriceAlert,
    onDelete: () -> Unit,
    onReset: () -> Unit,
    formatter: DecimalFormat
) {
    val directionSymbol = if (alert.isAbove) "≥" else "≤"
    val directionColor = if (alert.isAbove) Color(0xFFB4F1BE) else Color(0xFFFF897A)
    val directionText = if (alert.isAbove) "Aviso al subirse por encima de" else "Aviso al caer debajo de"

    val simpleDateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alert_item_${alert.id}"),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B1B1F)
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (alert.isTriggered) {
            androidx.compose.foundation.BorderStroke(1.5.dp, directionColor)
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3F4759))
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(directionColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (alert.label.isNotBlank()) alert.label else "Alerta de Precio",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFFE2E2E6)
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "$directionText ${formatter.format(alert.targetPrice)}",
                    fontSize = 12.sp,
                    color = Color(0xFF8E9199)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Creada: ${simpleDateFormat.format(Date(alert.createdAt))}",
                    fontSize = 10.sp,
                    color = Color(0xFF8E9199).copy(alpha = 0.6f)
                )

                if (alert.isTriggered && alert.triggeredAt != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(directionColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, directionColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Disparada",
                            tint = directionColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "DISPARADA el ${simpleDateFormat.format(Date(alert.triggeredAt))}",
                            color = directionColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (alert.isTriggered) {
                    IconButton(
                        onClick = onReset,
                        modifier = Modifier.testTag("reset_alert_btn_${alert.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Reactivar Alerta",
                            tint = Color(0xFFA8C7FA),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_alert_btn_${alert.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eliminar Alerta",
                        tint = Color(0xFFFF897A).copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
