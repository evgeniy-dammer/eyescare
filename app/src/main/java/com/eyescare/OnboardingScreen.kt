package com.eyescare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Экран первого запуска: коротко объясняет, что делает приложение, что камера работает локально,
 * и как калибровать. Показывается один раз (флаг `isOnboardingDone` в [SettingsRepository]).
 */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    GlassBackground {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            // Пропорциональный отступ кнопки от низа экрана (~5% высоты).
            val bottomMargin = maxHeight * 0.05f

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                // Прокручиваемый контент занимает всё пространство над кнопкой.
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 24.dp),
                    horizontalAlignment = Alignment.Start,
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.onboarding_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(32.dp))

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            OnboardingPoint(
                                icon = Icons.Outlined.Visibility,
                                title = stringResource(R.string.onboarding_point1_title),
                                desc = stringResource(R.string.onboarding_point1_desc),
                            )
                            OnboardingPoint(
                                icon = Icons.Outlined.Lock,
                                title = stringResource(R.string.onboarding_point2_title),
                                desc = stringResource(R.string.onboarding_point2_desc),
                            )
                            OnboardingPoint(
                                icon = Icons.Outlined.Face,
                                title = stringResource(R.string.onboarding_point3_title),
                                desc = stringResource(R.string.onboarding_point3_desc),
                            )
                            OnboardingPoint(
                                icon = Icons.Outlined.Timer,
                                title = stringResource(R.string.onboarding_point4_title),
                                desc = stringResource(R.string.onboarding_point4_desc),
                            )
                            OnboardingPoint(
                                icon = Icons.Outlined.ChildCare,
                                title = stringResource(R.string.onboarding_point5_title),
                                desc = stringResource(R.string.onboarding_point5_desc),
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }

                // Кнопка прикреплена к низу с пропорциональным отступом.
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .padding(bottom = bottomMargin)
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_start),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPoint(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
