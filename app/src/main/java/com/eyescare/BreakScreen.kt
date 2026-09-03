package com.eyescare

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Экран перерыва 20-20-20.
 *
 * Зачем экран, если было уведомление: уведомление «пора сделать перерыв» — это текст, который
 * смахивают, не сделав перерыв. Здесь перерыв становится отсчётом, за которым видно, сколько
 * осталось, — и досидеть до конца заметно легче, чем выдержать двадцать секунд «на глаз».
 *
 * Экран намеренно почти пустой и тёмный: на него не нужно смотреть — смысл шага в том, чтобы
 * смотреть ВДАЛЬ. Крупная цифра читается боковым зрением, анимация служит ориентиром, а не
 * приманкой для взгляда.
 */
@Composable
fun BreakScreen(onFinish: () -> Unit) {
    // «Назад» должна закрывать перерыв, а не всё приложение.
    BackHandler(onBack = onFinish)

    // Во время перерыва на экран не смотрят и его не трогают, а перерыв длится дольше, чем многие
    // держат подсветку. Без этого экран успевал бы погаснуть до конца отсчёта — и заодно поставил
    // бы мониторинг на паузу по ACTION_SCREEN_OFF.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    val startMs = remember { SystemClock.elapsedRealtime() }

    // Тик чаще секунды — чтобы анимация шла плавно, а не рывками раз в секунду. После конца
    // перерыва цикл ОСТАНАВЛИВАЕТСЯ: иначе экран, оставленный открытым, продолжал бы просыпаться
    // каждые 50 мс и держать подсветку включённой сколь угодно долго.
    val elapsed by produceState(initialValue = 0L, startMs) {
        while (true) {
            val now = SystemClock.elapsedRealtime() - startMs
            value = now
            if (now >= BreakExercise.TOTAL_MS) break
            delay(50)
        }
    }
    val progress = BreakExercise.progressAt(elapsed)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val titleRes = when (progress.step) {
                BreakStep.LOOK_FAR -> R.string.break_step_look_far
                BreakStep.BLINK -> R.string.break_step_blink
                BreakStep.DONE -> R.string.break_step_done
            }
            val hintRes = when (progress.step) {
                BreakStep.LOOK_FAR -> R.string.break_step_look_far_hint
                BreakStep.BLINK -> R.string.break_step_blink_hint
                BreakStep.DONE -> R.string.break_step_done_hint
            }

            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            BreakVisual(progress)

            if (progress.step != BreakStep.DONE) {
                Text(
                    text = BreakExercise.displaySeconds(progress.remainingInStepMs).toString(),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            // До конца — «Пропустить», после — «Закрыть»: одна и та же кнопка, разный смысл.
            val actionRes = if (progress.step == BreakStep.DONE) {
                R.string.break_action_close
            } else {
                R.string.break_action_skip
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .clickable(onClick = onFinish)
                    .padding(horizontal = 32.dp, vertical = 12.dp),
            ) {
                Text(
                    text = stringResource(actionRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Activity из контекста View: у Compose это может быть не сама Activity, а обёртка
 * ([ContextWrapper]/`ContextThemeWrapper`). Прямой каст в таком случае молча дал бы `null`, и флаг
 * «не гасить экран» тихо не применился бы — то есть фича сломалась бы без единого признака.
 */
private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * Ориентир для шага, без деталей, которые тянули бы взгляд обратно на экран.
 *
 * «Вдаль» — круг, который на протяжении шага уменьшается, будто удаляется. «Моргните» — круг,
 * плавно смыкающийся и раскрывающийся примерно раз в секунду, задавая темп морганию.
 */
@Composable
private fun BreakVisual(progress: BreakExercise.Progress) {
    val accent = MaterialTheme.colorScheme.primary

    val targetScale = when (progress.step) {
        // Уходит вдаль: от полного размера к маленькой точке.
        BreakStep.LOOK_FAR -> 1f - 0.75f * progress.stepFraction
        BreakStep.BLINK -> 1f
        BreakStep.DONE -> 1f
    }
    val scale by animateFloatAsState(targetValue = targetScale, animationSpec = tween(200), label = "scale")

    // Веко для шага моргания: треугольная волна 0→1→0 с периодом секунда.
    val lidOpen = if (progress.step == BreakStep.BLINK) {
        val phase = (progress.stepFraction * BreakExercise.BLINK_MS / 1000f) % 1f
        1f - 2f * abs(phase - 0.5f)
    } else {
        1f
    }

    Box(modifier = Modifier.height(160.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(160.dp)) {
            val maxRadius = size.minDimension / 2f
            when (progress.step) {
                BreakStep.LOOK_FAR -> {
                    // Внешнее кольцо — исходный размер, чтобы удаление было с чем сравнить.
                    drawCircle(color = accent.copy(alpha = 0.15f), radius = maxRadius)
                    drawCircle(color = accent, radius = maxRadius * scale.coerceAtLeast(0.05f))
                }
                BreakStep.BLINK -> {
                    // Сплющиваем круг по вертикали — получается смыкающееся веко.
                    val open = lidOpen.coerceIn(0.08f, 1f)
                    scale(scaleX = 1f, scaleY = open) {
                        drawCircle(color = accent, radius = maxRadius * 0.8f)
                    }
                }
                BreakStep.DONE -> {
                    drawCircle(color = accent.copy(alpha = 0.15f), radius = maxRadius)
                    drawCircle(color = accent, radius = maxRadius * 0.35f)
                }
            }
        }
    }
}
