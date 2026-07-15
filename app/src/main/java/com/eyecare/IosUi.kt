package com.eyecare

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Крупный заголовок экрана в стиле iOS (large title): жирный, слева, с воздухом сверху. */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(top = 8.dp, bottom = 8.dp),
    )
}

/**
 * Кнопка «Назад» в стиле iOS: тонкий шеврон «‹» (рисуется в Canvas, не Material-иконка) вплотную
 * к названию предыдущего экрана, акцентным цветом.
 */
@Composable
fun IosBackButton(label: String, onClick: () -> Unit) {
    val tint = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            // role=Button, чтобы TalkBack озвучил элемент как кнопку («‹ Настройки, кнопка»);
            // шеврон рисуется в Canvas и семантики не несёт (декоративен).
            .clickable(onClick = onClick, role = Role.Button)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(modifier = Modifier.size(width = 9.dp, height = 17.dp)) {
            val stroke = 2.2.dp.toPx()
            val midY = size.height / 2f
            drawLine(tint, Offset(size.width, 0f), Offset(0f, midY), stroke, cap = StrokeCap.Round)
            drawLine(tint, Offset(0f, midY), Offset(size.width, size.height), stroke, cap = StrokeCap.Round)
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = tint,
        )
    }
}

/**
 * Переключатель в стиле iOS `UISwitch`: пилюля 51×31, крупный белый бегунок, зелёный трек во
 * включённом состоянии, серый — в выключенном; без рамки.
 */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackWidth = 51.dp
    val trackHeight = 31.dp
    val thumbSize = 27.dp
    val edge = 2.dp
    val onColor = Color(0xFF34C759) // системный зелёный iOS
    val offColor = MaterialTheme.colorScheme.surfaceVariant
    val trackColor by animateColorAsState(if (checked) onColor else offColor, label = "track")
    val thumbStart by animateDpAsState(if (checked) trackWidth - thumbSize - edge else edge, label = "thumb")

    Box(
        modifier = modifier
            // Область нажатия расширяется до 48dp (визуальный размер 51×31 неизменен), чтобы
            // соответствовать минимальному размеру касания для доступности.
            .minimumInteractiveComponentSize()
            .size(width = trackWidth, height = trackHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor)
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = thumbStart)
                .size(thumbSize)
                .shadow(2.dp, CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * Сегментированный контрол в стиле iOS `UISegmentedControl`: серый трек-пилюля, у выбранного
 * сегмента — светлая нейтральная «капсула» (не акцентная заливка), подпись обычным цветом.
 */
@Composable
fun IosSegmentedControl(
    options: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .then(
                        if (isSelected) {
                            Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(7.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable(role = Role.RadioButton) {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onSelect(option)
                    }
                    // Озвучиваем выбранный сегмент для TalkBack («30, выбрано»).
                    .semantics { this.selected = isSelected }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * Текстовое поле в стиле iOS: скруглённый прямоугольник с лёгкой заливкой, без плавающего лейбла
 * и подчёркивания; плейсхолдер внутри; красная рамка при ошибке.
 */
@Composable
fun IosTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (isError) MaterialTheme.colorScheme.error else Color.Transparent
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = keyboardOptions,
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, borderColor, shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}

/**
 * Модальный алерт в стиле iOS `UIAlertController`: узкая скруглённая карточка по центру,
 * заголовок и текст по центру, две кнопки внизу через разделители (подтверждение — жирным).
 */
@Composable
fun IosAlertDialog(
    title: String,
    message: String,
    confirmText: String,
    cancelText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(270.dp),
        ) {
            Column {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                HorizontalDivider()
                Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(cancelText)
                    }
                    VerticalDivider()
                    TextButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Text(confirmText, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
