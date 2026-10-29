package com.zaiten.mixer.core.multidevice

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Système multi-appareils (§2.7) : ce téléphone est soit la source, soit une enceinte, soit hors groupe. */
object MultiDeviceRepository {
    enum class Mode { OFF, SOURCE, SPEAKER }

    data class FoundSource(val name: String, val service: NsdServiceInfo)

    private val _mode = MutableStateFlow(Mode.OFF)
    val mode: StateFlow<Mode> = _mode

    private val _speakers = MutableStateFlow<List<SourceServer.ConnectedSpeaker>>(emptyList())
    val speakers: StateFlow<List<SourceServer.ConnectedSpeaker>> = _speakers

    private val _sources = MutableStateFlow<List<FoundSource>>(emptyList())
    val sources: StateFlow<List<FoundSource>> = _sources

    private val _connectedTo = MutableStateFlow<String?>(null)
    val connectedTo: StateFlow<String?> = _connectedTo

    private val _speakerVolume = MutableStateFlow(100)
    val speakerVolume: StateFlow<Int> = _speakerVolume

    private val _speakerOffsetMs = MutableStateFlow(0)
    val speakerOffsetMs: StateFlow<Int> = _speakerOffsetMs

    private var server: SourceServer? = null
    private var client: SpeakerClient? = null
    private var discovery: NsdManager.DiscoveryListener? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    fun startSource(context: Context) {
        stopAll(context)
        server = SourceServer(context.applicationContext) { _speakers.value = it }.also { it.start() }
        _mode.value = Mode.SOURCE
    }

    /** Cherche les sources du Wi-Fi local pour s'y connecter en enceinte. */
    fun startDiscovery(context: Context) {
        stopAll(context)
        _mode.value = Mode.SPEAKER
        _sources.value = emptyList()
        // Certains téléphones filtrent le multicast en veille : la découverte (mDNS) en a besoin.
        multicastLock = context.getSystemService(WifiManager::class.java).createMulticastLock("zaiten-discovery").apply { acquire() }
        val nsd = context.getSystemService(NsdManager::class.java)
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                _sources.update { list -> list.filterNot { it.name == info.serviceName } + FoundSource(info.serviceName, info) }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                _sources.update { list -> list.filterNot { it.name == info.serviceName } }
            }
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, error: Int) {}
            override fun onStopDiscoveryFailed(type: String, error: Int) {}
        }
        discovery = listener
        nsd.discoverServices(SourceServer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    @Suppress("DEPRECATION") // resolveService : la nouvelle API (registerServiceInfoCallback) exige Android 14.
    fun connect(context: Context, source: FoundSource, role: SpeakerRole) {
        client?.disconnect()
        context.getSystemService(NsdManager::class.java).resolveService(source.service, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                client = SpeakerClient(
                    role = role,
                    volume = { _speakerVolume.value / 100f },
                    offsetMs = { _speakerOffsetMs.value },
                    onDisconnected = { _connectedTo.value = null },
                ).also { it.connect(host, info.port) }
                _connectedTo.value = source.name
            }
        })
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _connectedTo.value = null
    }

    fun setSpeakerVolume(percent: Int) { _speakerVolume.value = percent.coerceIn(0, 200) }

    fun setSpeakerOffset(ms: Int) { _speakerOffsetMs.value = (ms / 10 * 10).coerceIn(-500, 500) }

    fun clockUncertaintyMs(): Double? = client?.clockUncertaintyMs

    fun stopAll(context: Context) {
        server?.stop()
        server = null
        disconnect()
        discovery?.let { runCatching { context.getSystemService(NsdManager::class.java).stopServiceDiscovery(it) } }
        discovery = null
        multicastLock?.let { if (it.isHeld) it.release() }
        multicastLock = null
        _speakers.value = emptyList()
        _sources.value = emptyList()
        _mode.value = Mode.OFF
    }
}
