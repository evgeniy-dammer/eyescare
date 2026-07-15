package com.eyecare

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Снимок «живого» состояния мониторинга для UI. */
data class MonitoringStatus(
    val running: Boolean = false,
    val distanceCm: Float? = null, // null — лицо не найдено / нет данных
    val tooClose: Boolean = false,
)

/**
 * Общий источник состояния мониторинга между [ForegroundMonitoringService] (пишет) и UI
 * (читает). Процесс-синглтон — согласуется с подходом `SettingsRepository.getInstance`.
 */
object MonitoringStateHolder {
    private val _state = MutableStateFlow(MonitoringStatus())
    val state: StateFlow<MonitoringStatus> = _state.asStateFlow()

    fun setRunning(running: Boolean) = _state.update { it.copy(running = running) }
    fun setDistance(distanceCm: Float?) = _state.update { it.copy(distanceCm = distanceCm) }
    fun setTooClose(tooClose: Boolean) = _state.update { it.copy(tooClose = tooClose) }

    /** Сбрасывает состояние при остановке сервиса. */
    fun reset() {
        _state.value = MonitoringStatus()
    }
}
