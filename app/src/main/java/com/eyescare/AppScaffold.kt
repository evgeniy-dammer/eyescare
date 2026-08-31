package com.eyescare

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

/** Состояние главного/настроечного UI. Наполняется из [SettingsRepository]. */
data class MainUiState(
    val monitoringEnabled: Boolean = false,
    val childMode: Boolean = false,
    val threshold: Int = 30,
    val languageLabel: String = "",
    val languageTag: String? = null, // null = как в системе
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val ignoringBatteryOptimizations: Boolean = false, // true = приложению не мешает оптимизация батареи
    val breakRemindersEnabled: Boolean = true,
    val darkRoomWarningEnabled: Boolean = true,
    val postureWarningEnabled: Boolean = false,
    val weeklyStats: WeeklyStats = WeeklyStats(),
)

private const val ROUTE_LANGUAGE = "language"
private const val ROUTE_THEME = "theme"
private const val ROUTE_BACKGROUND = "background"

private sealed class Destination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    // iOS-манера: неактивный таб — контурная иконка, активный — заполненная.
    data object Monitoring : Destination("monitoring", R.string.nav_monitoring, Icons.Filled.Home, Icons.Outlined.Home)
    data object Calibration : Destination("calibration", R.string.nav_calibration, Icons.Filled.Person, Icons.Outlined.Person)
    data object Settings : Destination("settings", R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)
}

private val topLevelDestinations = listOf(
    Destination.Monitoring,
    Destination.Calibration,
    Destination.Settings,
)

private const val TAB_ANIM_MS = 250
private val NAV_BAR_HEIGHT = 80.dp
private val BAR_MARGIN = 24.dp
private val BAR_CORNER = 28.dp

// Порядок для направления анимации: детальные экраны «правее» Настроек (push вправо / pop влево).
private val NAV_ORDER = listOf(
    Destination.Monitoring.route,
    Destination.Calibration.route,
    Destination.Settings.route,
    ROUTE_LANGUAGE,
    ROUTE_THEME,
    ROUTE_BACKGROUND,
)

/** Направление слайда по порядку экранов: вправо к «дальнему», влево — к «ближнему». */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSlideDirection(): AnimatedContentTransitionScope.SlideDirection {
    val from = NAV_ORDER.indexOf(initialState.destination.route)
    val to = NAV_ORDER.indexOf(targetState.destination.route)
    return if (to >= from) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

/**
 * Корневой каркас: edge-to-edge. [NavHost] занимает весь экран и помечен `hazeSource`, а нижняя
 * панель табов ([NavigationBar]) наложена поверх и через `hazeEffect` **размывает контент под собой**
 * (реально на Android 12+, ниже — полупрозрачная заливка). Экраны получают нижний отступ
 * [contentBottomPadding], чтобы контент не оставался под баром.
 */
@Composable
fun AppScaffold(
    mainState: MainUiState,
    thresholdOptions: List<Int>,
    calibrationController: CalibrationController,
    onMonitoringToggle: (Boolean) -> Unit,
    onChildModeToggle: (Boolean) -> Unit,
    onThresholdSelect: (Int) -> Unit,
    onSelectLanguage: (String?) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onOpenAutostartSettings: () -> Unit,
    onBreakRemindersToggle: (Boolean) -> Unit,
    onDarkRoomWarningToggle: (Boolean) -> Unit,
    onPostureWarningToggle: (Boolean) -> Unit,
    onSnooze: (minutes: Int) -> Unit,
    onCancelSnooze: () -> Unit,
    ensureConsent: (onGranted: () -> Unit, onDenied: () -> Unit) -> Unit,
) {
    val navController = rememberNavController()
    val barHazeState = remember { HazeState() }
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // Плавающий бар: высота + верхний/нижний отступы + системный inset — чтобы контент его не заходил.
    val contentBottomPadding = navBottomInset + NAV_BAR_HEIGHT + BAR_MARGIN * 2

    GlassBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Destination.Monitoring.route,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .hazeSource(barHazeState),
                enterTransition = {
                    slideIntoContainer(tabSlideDirection(), tween(TAB_ANIM_MS)) + fadeIn(tween(TAB_ANIM_MS))
                },
                exitTransition = {
                    slideOutOfContainer(tabSlideDirection(), tween(TAB_ANIM_MS)) + fadeOut(tween(TAB_ANIM_MS))
                },
            ) {
                composable(Destination.Monitoring.route) {
                    MonitoringScreen(
                        enabled = mainState.monitoringEnabled,
                        onToggle = onMonitoringToggle,
                        weeklyStats = mainState.weeklyStats,
                        snoozeOptionsMinutes = ForegroundMonitoringService.SNOOZE_OPTIONS_MINUTES,
                        onSnooze = onSnooze,
                        onCancelSnooze = onCancelSnooze,
                        contentBottomPadding = contentBottomPadding,
                    )
                }
                composable(Destination.Calibration.route) {
                    CalibrationScreen(
                        controller = calibrationController,
                        ensureConsent = ensureConsent,
                        contentBottomPadding = contentBottomPadding,
                        onExit = {
                            navController.navigate(Destination.Monitoring.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable(Destination.Settings.route) {
                    SettingsScreen(
                        childMode = mainState.childMode,
                        threshold = mainState.threshold,
                        thresholdOptions = thresholdOptions,
                        languageLabel = mainState.languageLabel,
                        themeMode = mainState.themeMode,
                        ignoringBatteryOptimizations = mainState.ignoringBatteryOptimizations,
                        breakRemindersEnabled = mainState.breakRemindersEnabled,
                        darkRoomWarningEnabled = mainState.darkRoomWarningEnabled,
                        postureWarningEnabled = mainState.postureWarningEnabled,
                        contentBottomPadding = contentBottomPadding,
                        onChildModeToggle = onChildModeToggle,
                        onThresholdSelect = onThresholdSelect,
                        onBreakRemindersToggle = onBreakRemindersToggle,
                        onDarkRoomWarningToggle = onDarkRoomWarningToggle,
                        onPostureWarningToggle = onPostureWarningToggle,
                        onLanguageClick = { navController.navigate(ROUTE_LANGUAGE) },
                        onThemeClick = { navController.navigate(ROUTE_THEME) },
                        onBackgroundClick = { navController.navigate(ROUTE_BACKGROUND) },
                    )
                }
                composable(ROUTE_LANGUAGE) {
                    LanguageDetailScreen(
                        currentTag = mainState.languageTag,
                        contentBottomPadding = contentBottomPadding,
                        onSelect = { onSelectLanguage(it); navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_THEME) {
                    ThemeDetailScreen(
                        current = mainState.themeMode,
                        contentBottomPadding = contentBottomPadding,
                        onSelect = { onSelectTheme(it); navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(ROUTE_BACKGROUND) {
                    BackgroundDetailScreen(
                        ignoringBatteryOptimizations = mainState.ignoringBatteryOptimizations,
                        contentBottomPadding = contentBottomPadding,
                        onBatteryClick = onRequestBatteryExemption,
                        onAutostartClick = onOpenAutostartSettings,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // Плавающая «таблетка» табов: приподнята от низа, с боковыми отступами и скруглением.
            val barShape = RoundedCornerShape(BAR_CORNER)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = BAR_MARGIN, vertical = BAR_MARGIN),
            ) {
                NavigationBar(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(barShape)
                        .hazeEffect(
                            state = barHazeState,
                            style = HazeStyle(
                                backgroundColor = MaterialTheme.colorScheme.surface,
                                tints = listOf(HazeTint(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f))),
                                blurRadius = 28.dp,
                            ),
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), barShape),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0),
                ) {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    topLevelDestinations.forEach { destination ->
                        val selected = currentRoute == destination.route ||
                            (destination == Destination.Settings && currentRoute in listOf(ROUTE_LANGUAGE, ROUTE_THEME, ROUTE_BACKGROUND))
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                // Метки табов не должны разъезжаться при очень крупном системном
                                // шрифте (как на iOS ограничен Dynamic Type для таб-бара): шрифт
                                // масштабируется, но не безгранично — иначе слово рвётся и обрезается
                                // о фиксированную высоту бара. Контент экранов масштабируется полностью.
                                val density = LocalDensity.current
                                val clamped = remember(density) {
                                    Density(density.density, density.fontScale.coerceAtMost(1.3f))
                                }
                                CompositionLocalProvider(LocalDensity provides clamped) {
                                    Text(
                                        text = stringResource(destination.labelRes),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            },
                            // iOS: без «капсулы», активный таб просто окрашен акцентом.
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Color.Transparent,
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        }
    }
}
