package com.eyecare

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/** Состояния процесса калибровки; управляют цветом контура и подсказкой. */
enum class CalibrationState { SEARCHING, ADJUSTING, LOCKING }

/** Состояние экрана калибровки для Compose. */
data class CalibrationUiState(
    val instructionRes: Int = R.string.calib_place_face,
    val state: CalibrationState = CalibrationState.SEARCHING,
)

/**
 * Экран калибровки как вкладка. Перед использованием камеры проверяет согласие (ТЗ п. 6.4)
 * и разрешение на камеру; камера привязана к жизненному циклу вкладки через [CalibrationController]
 * (старт при входе, [CalibrationController.stop] в [DisposableEffect] при уходе).
 */
@Composable
fun CalibrationScreen(
    controller: CalibrationController,
    ensureConsent: (onGranted: () -> Unit, onDenied: () -> Unit) -> Unit,
    onExit: () -> Unit,
    contentBottomPadding: Dp = 0.dp,
) {
    val context = LocalContext.current
    var consentGranted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ensureConsent({ consentGranted = true }, { onExit() })
    }

    if (!consentGranted) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black))
        return
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    // Останавливаем камеру при уходе с вкладки.
    DisposableEffect(Unit) {
        onDispose { controller.stop() }
    }

    // Стартуем камеру, как только доступны и разрешение, и PreviewView.
    LaunchedEffect(hasPermission, previewView) {
        val pv = previewView
        if (hasPermission && pv != null) controller.start(pv, onExit)
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!hasPermission) {
            Text(
                text = stringResource(R.string.calib_permission_required),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        } else {
            CalibrationContent(
                state = controller.uiState,
                ipdInput = controller.ipdInput,
                ipdError = controller.ipdError,
                contentBottomPadding = contentBottomPadding,
                onPreviewCreated = { previewView = it },
                onIpdChange = controller::onIpdChange,
                onSaveIpd = controller::saveManualIpd,
            )
        }
    }
}

@Composable
private fun CalibrationContent(
    state: CalibrationUiState,
    ipdInput: String,
    ipdError: String?,
    contentBottomPadding: Dp,
    onPreviewCreated: (PreviewView) -> Unit,
    onIpdChange: (String) -> Unit,
    onSaveIpd: () -> Unit,
) {
    // Цвет контура/тинта по состоянию
    val strokeColor = when (state.state) {
        CalibrationState.SEARCHING -> Color(0xFFF44336) // красный — лицо не найдено
        CalibrationState.ADJUSTING -> Color(0xFFFFEB3B) // жёлтый — найдено, ждём стабилизации
        CalibrationState.LOCKING -> Color(0xFF4CAF50) // зелёный — идёт калибровка
    }
    val fillColor = when (state.state) {
        CalibrationState.SEARCHING -> Color.Transparent
        CalibrationState.ADJUSTING -> Color(0x33FFEB3B)
        CalibrationState.LOCKING -> Color(0x334CAF50)
    }
    val strokeDp = if (state.state == CalibrationState.LOCKING) 3.dp else 2.dp

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).also(onPreviewCreated) },
            modifier = Modifier.fillMaxSize(),
        )

        // Затемняющая маска вокруг овала (как в iOS-сканерах: фокус на лице) + контур/тинт.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val ovalW = 240.dp.toPx()
            val ovalH = 320.dp.toPx()
            val left = (size.width - ovalW) / 2f
            val top = (size.height - ovalH) / 2f
            val ovalTopLeft = Offset(left, top)
            val ovalSize = Size(ovalW, ovalH)

            // Скрим везде, кроме овала (even-odd — без blend-режимов, корректно поверх камеры).
            val mask = Path().apply {
                addRect(Rect(0f, 0f, size.width, size.height))
                addOval(Rect(left, top, left + ovalW, top + ovalH))
                fillType = PathFillType.EvenOdd
            }
            drawPath(mask, Color.Black.copy(alpha = 0.5f))

            if (fillColor != Color.Transparent) {
                drawOval(color = fillColor, topLeft = ovalTopLeft, size = ovalSize)
            }
            drawOval(color = strokeColor, topLeft = ovalTopLeft, size = ovalSize, style = Stroke(width = strokeDp.toPx()))
        }

        // Сверху: заголовок + подсказка по левому краю (как на остальных экранах).
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                // 24dp = 16dp (отступ Column) + 8dp (внутренний отступ ScreenTitle) — как на др. экранах.
                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.nav_calibration),
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(state.instructionRes),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Start,
            )
        }

        // Снизу: стеклянная панель ручного ввода IPD (над таб-баром).
        // Поверх камеры (SurfaceView) backdrop-размытие невозможно — форсируем translucent-стекло.
        CompositionLocalProvider(LocalHazeState provides null) {
            GlassCard(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = contentBottomPadding),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.calib_manual_ipd_label),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.size(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IosTextField(
                            value = ipdInput,
                            onValueChange = onIpdChange,
                            modifier = Modifier.weight(1f),
                            isError = ipdError != null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = onSaveIpd) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                    if (ipdError != null) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = ipdError,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}
