package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PricePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun InteractiveCryptoChart(
    points: List<PricePoint>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFFA8C7FA),
    gradientColors: List<Color> = listOf(Color(0xFFA8C7FA).copy(alpha = 0.4f), Color.Transparent)
) {
    if (points.size < 2) {
        Box(
            modifier = modifier
                .background(Color(0xFF1B1B1F), RoundedCornerShape(16.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sin datos suficientes para graficar.",
                color = Color(0xFF8E9199),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    val prices = points.map { it.price }
    val maxPrice = prices.maxOrNull() ?: 1.0
    val minPrice = prices.minOrNull() ?: 0.0
    val priceDiff = if (maxPrice - minPrice == 0.0) 1.0 else maxPrice - minPrice
    
    // Margen superior e inferior para evitar que toque los extremos
    val buffer = priceDiff * 0.08
    val adjustedMax = maxPrice + buffer
    val adjustedMin = if (minPrice - buffer > 0.0) minPrice - buffer else 0.0
    val adjustedDiff = adjustedMax - adjustedMin

    // Animación de entrada inicial del gráfico
    var isLoaded by remember { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (isLoaded) 1f else 0f,
        animationSpec = tween(durationMillis = 1000),
        label = "chart_entrance"
    )

    LaunchedEffect(points) {
        isLoaded = true
    }

    // Estado del scrub / toque interactivo
    var activeIndex by remember(points) { mutableStateOf<Int?>(null) }
    val textMeasurer = rememberTextMeasurer()

    val dateFormat = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val priceFormat = remember { "%.2f" }

    BoxWithConstraints(
        modifier = modifier
            .padding(12.dp)
    ) {
        val width = constraints.maxWidth.toFloat()
        // Dejamos un espacio en la base para etiquetas de tiempo o padding
        val extraPaddingBottom = with(LocalDensity.current) { 24.dp.toPx() }
        val height = constraints.maxHeight.toFloat() - extraPaddingBottom

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val index = calculateNearestPointIndex(offset.x, width, points.size)
                                activeIndex = index
                            },
                            onDrag = { change, _ ->
                                val index = calculateNearestPointIndex(change.position.x, width, points.size)
                                activeIndex = index
                            },
                            onDragEnd = {
                                activeIndex = null
                            },
                            onDragCancel = {
                                activeIndex = null
                            }
                        )
                    }
            ) {
                if (width <= 0f || height <= 0f) return@Canvas

                val stepX = width / (points.size - 1)

                // 1. Dibujar líneas de cuadrícula horizontales (Guía)
                val gridLines = 3
                val gridColor = Color.White.copy(alpha = 0.06f)
                val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                
                for (i in 0..gridLines) {
                    val y = (height / gridLines) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    
                    // Precios de referencia
                    val referencePrice = adjustedMax - (adjustedDiff / gridLines) * i
                    val refText = String.format(Locale.getDefault(), "$%.0f", referencePrice)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = refText,
                        topLeft = Offset(8f, y.coerceIn(0f, height - 25f) + 2f),
                        style = labelStyle
                    )
                }

                // 2. Construir Paths para Línea y Gradient Area
                val linePath = Path()
                val areaPath = Path()

                points.forEachIndexed { index, point ->
                    val x = index * stepX
                    // Aplicar la animación progresiva a la coordenada Y
                    val normalY = height - ((point.price - adjustedMin) / adjustedDiff * height).toFloat()
                    // Si el progreso de la animación es menor que 1, colapsamos el gráfico a la base
                    val animatedY = height - (height - normalY) * animationProgress

                    if (index == 0) {
                        linePath.moveTo(x, animatedY)
                        areaPath.moveTo(x, height)
                        areaPath.lineTo(x, animatedY)
                    } else {
                        // Usar conexión cúbica suave entre puntos o líneas rectas estándar
                        // Las líneas rectas son más estables y rápidas en rendimiento de Compose Canvas
                        linePath.lineTo(x, animatedY)
                        areaPath.lineTo(x, animatedY)
                    }
                }

                // Cerrar el AreaPath al fondo
                if (points.isNotEmpty()) {
                    val lastX = (points.size - 1) * stepX
                    areaPath.lineTo(lastX, height)
                    areaPath.lineTo(0f, height)
                    areaPath.close()

                    // Dibujar fondo degradado
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(gradientColors, startY = 0f, endY = height)
                    )
                }

                // Dibujar línea principal de variaciones
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // 3. Dibujar marcador de toque interactivo (Si se está presionando la pantalla)
                activeIndex?.let { idx ->
                    if (idx in points.indices) {
                        val selectedPoint = points[idx]
                        val x = idx * stepX
                        val y = height - ((selectedPoint.price - adjustedMin) / adjustedDiff * height).toFloat()

                        // Línea vertical vertical de seguimiento
                        drawLine(
                            color = lineColor.copy(alpha = 0.6f),
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 1.5.dp.toPx()
                        )

                        // Punto exterior brillante
                        drawCircle(
                            color = lineColor.copy(alpha = 0.3f),
                            radius = 12.dp.toPx(),
                            center = Offset(x, y)
                        )

                        // Punto central sólido
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = Offset(x, y)
                        )
                        
                        // Punto interior del color de la línea
                        drawCircle(
                            color = lineColor,
                            radius = 3.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }

            // 4. Mostrar Tooltip flotante interactivo (dentro del Box del gráfico)
            activeIndex?.let { idx ->
                if (idx in points.indices) {
                    val point = points[idx]
                    val dateStr = dateFormat.format(Date(point.timestamp))
                    val priceStr = String.format(Locale.getDefault(), priceFormat, point.price)

                    val isRightSide = idx > points.size / 2
                    val alignment = if (isRightSide) Alignment.TopStart else Alignment.TopEnd

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = alignment
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF3F4759).copy(alpha = 0.95f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                            modifier = Modifier.width(160.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = dateStr,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .height(8.dp)
                                            .background(lineColor, RoundedCornerShape(4.dp))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$$priceStr",
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // 5. Mostrar tutorial rápido si no se está tocando
            if (activeIndex == null && points.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Arrastra el dedo sobre el gráfico para ver detalles",
                        color = Color.White.copy(alpha = 0.25f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

/**
 * Calcula de manera eficiente el índice del punto más cercano al toque X.
 */
private fun calculateNearestPointIndex(touchX: Float, width: Float, listSize: Int): Int {
    if (width <= 0f || listSize <= 1) return 0
    val stepX = width / (listSize - 1)
    return (touchX / stepX).coerceIn(0f, (listSize - 1).toFloat()).plus(0.5f).toInt()
}
