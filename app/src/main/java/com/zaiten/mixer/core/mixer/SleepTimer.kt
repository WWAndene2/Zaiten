package com.zaiten.mixer.core.mixer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Minuterie de sommeil (§4.3) : baisse progressive du volume maître pendant la dernière minute,
 * puis arrêt de la capture. Le volume maître d'avant est rétabli pour la prochaine écoute.
 */
object SleepTimer {
    private val _remainingMs = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remainingMs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    /** Volume maître d'avant le fondu ; non nul pendant que la minuterie tourne. */
    private var initialMaster: Int? = null

    fun start(minutes: Int, onFinished: () -> Unit) {
        cancel()
        val total = minutes * 60_000L
        val initialMaster = MixerRepository.masterPercent.value.also { this.initialMaster = it }
        job = scope.launch {
            var left = total
            while (left > 0) {
                _remainingMs.value = left
                if (left <= FADE_MS) MixerRepository.setMaster((initialMaster * left / FADE_MS).toInt())
                delay(TICK_MS)
                left -= TICK_MS
            }
            onFinished()
            MixerRepository.setMaster(initialMaster)
            this@SleepTimer.initialMaster = null
            _remainingMs.value = null
        }
    }

    /** Annulation, même en plein fondu : le volume maître revient à sa valeur d'avant. */
    fun cancel() {
        job?.cancel()
        job = null
        initialMaster?.let(MixerRepository::setMaster)
        initialMaster = null
        _remainingMs.value = null
    }

    private const val FADE_MS = 60_000L
    private const val TICK_MS = 1_000L
}
