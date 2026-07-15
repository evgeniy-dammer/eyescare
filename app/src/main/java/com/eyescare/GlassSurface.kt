package com.eyescare

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/**
 * Общий [HazeState] для backdrop-размытия. `null`, если [GlassCard] используется вне
 * [GlassBackground] или там, где источник размытия не подходит (например, поверх камеры) —
 * тогда карточка рендерится полупрозрачной без блюра.
 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

/**
 * Фон в стиле «liquid glass»: сплошная база темы + мягкие цветные «пятна». Всё это помечено как
 * `hazeSource` — источник для реального backdrop-размытия в [GlassCard]. На Android 12+ haze даёт
 * настоящий блюр, ниже — автоматически откатывается на полупрозрачную заливку (tint).
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hazeState = remember { HazeState() }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val base = MaterialTheme.colorScheme.background

    Box(modifier = modifier.fillMaxSize()) {
        // Источник размытия: база + цветные пятна
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(base)
                .hazeSource(hazeState),
        ) {
            val r = size.minDimension * 0.7f
            drawRect(base)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primary.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width * 0.18f, size.height * 0.12f),
                    radius = r,
                ),
                radius = r,
                center = Offset(size.width * 0.18f, size.height * 0.12f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tertiary.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.85f),
                    radius = r,
                ),
                radius = r,
                center = Offset(size.width * 0.9f, size.height * 0.85f),
            )
        }
        CompositionLocalProvider(LocalHazeState provides hazeState) {
            content()
        }
    }
}

/**
 * «Стеклянная» карточка: реальный backdrop-блюр фона (haze) на Android 12+ либо полупрозрачная
 * заливка ниже / когда источник недоступен, плюс тонкий светлый край-блик и верхняя подсветка.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val hazeState = LocalHazeState.current
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline

    val glassModifier = if (hazeState != null) {
        Modifier
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    backgroundColor = surface,
                    tints = listOf(HazeTint(surface.copy(alpha = 0.72f))),
                    blurRadius = 28.dp,
                ),
            )
    } else {
        Modifier
            .clip(shape)
            .background(surface.copy(alpha = 0.86f))
    }

    Box(
        modifier = modifier
            .then(glassModifier)
            .border(
                width = 1.dp,
                color = outline.copy(alpha = 0.35f),
                shape = shape,
            ),
    ) {
        // Box (в отличие от Surface) не задаёт LocalContentColor — задаём явно,
        // иначе текст по умолчанию чёрный и в тёмной теме не виден.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            Box(
                modifier = Modifier.background(
                    Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.06f), Color.Transparent),
                    ),
                ),
            ) {
                content()
            }
        }
    }
}
