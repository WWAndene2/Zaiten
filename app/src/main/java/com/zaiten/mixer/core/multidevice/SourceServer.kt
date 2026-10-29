package com.zaiten.mixer.core.multidevice

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.zaiten.mixer.core.engine.NativeMixerEngine
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * Côté source du groupe d'enceintes (§2.7) : annonce le service sur le Wi-Fi local, accepte les
 * enceintes, et leur diffuse le mix final horodaté à l'instant où il doit sortir.
 */
class SourceServer(private val context: Context, private val onSpeakersChanged: (List<ConnectedSpeaker>) -> Unit) {
    data class ConnectedSpeaker(val id: Int, val name: String, val role: SpeakerRole)

    private class Client(val id: Int, val socket: Socket) {
        val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
        @Volatile var speaker: ConnectedSpeaker? = null
        fun send(m: Message) = synchronized(out) { Message.write(out, m) }
    }

    @Volatile private var running = false
    private var server: ServerSocket? = null
    private val clients = CopyOnWriteArrayList<Client>()
    private var nextId = 1
    private var registration: NsdManager.RegistrationListener? = null

    fun start() {
        if (running) return
        running = true
        val socket = ServerSocket(0)
        server = socket
        register(socket.localPort)
        NativeMixerEngine.setOutputDelayMs(PLAYOUT_DELAY_MS)
        thread(name = "zaiten-accept") { acceptLoop(socket) }
        thread(name = "zaiten-broadcast") { broadcastLoop() }
    }

    fun stop() {
        running = false
        registration?.let { runCatching { context.getSystemService(NsdManager::class.java).unregisterService(it) } }
        registration = null
        runCatching { server?.close() }
        clients.forEach { runCatching { it.socket.close() } }
        clients.clear()
        NativeMixerEngine.setOutputDelayMs(0)
        publish()
    }

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            val s = try { socket.accept() } catch (e: IOException) { break }
            s.tcpNoDelay = true
            val client = Client(nextId++, s)
            clients += client
            thread(name = "zaiten-speaker-${client.id}") { readLoop(client) }
        }
    }

    private fun readLoop(client: Client) {
        try {
            val input = DataInputStream(BufferedInputStream(client.socket.getInputStream()))
            while (running) {
                when (val m = Message.read(input)) {
                    is Message.Hello -> {
                        client.speaker = ConnectedSpeaker(client.id, m.name, m.role)
                        publish()
                    }
                    is Message.Ping -> client.send(Message.Pong(m.t0, System.nanoTime()))
                    else -> {}
                }
            }
        } catch (e: Exception) {
            // Déconnexion : son canal est replié sur les autres via le nouveau Roster.
        } finally {
            clients -= client
            runCatching { client.socket.close() }
            publish()
        }
    }

    private fun publish() {
        val speakers = clients.mapNotNull { it.speaker }
        onSpeakersChanged(speakers)
        val roster = Message.Roster(speakers.map { it.role }.toSet())
        clients.forEach { c -> runCatching { c.send(roster) } }
    }

    private fun broadcastLoop() {
        var frameIndex = NativeMixerEngine.setTapEnabled(true)
        val buffer = ShortArray(BLOCK_SAMPLES)
        try {
            while (running) {
                val count = NativeMixerEngine.readTap(buffer)
                if (count == 0) {
                    Thread.sleep(IDLE_SLEEP_MS)
                    continue
                }
                val clock = NativeMixerEngine.tapClock()
                val playAt = clock.timeNs + (frameIndex - clock.frames) * NS_PER_SECOND / SAMPLE_RATE
                frameIndex += count / CHANNELS
                val message = Message.Audio(playAt, buffer, count)
                clients.forEach { c -> if (c.speaker != null) runCatching { c.send(message) } }
            }
        } finally {
            NativeMixerEngine.setTapEnabled(false)
        }
    }

    private fun register(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "Zaiten ${Build.MODEL}"
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {}
            override fun onRegistrationFailed(info: NsdServiceInfo, error: Int) {}
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, error: Int) {}
        }
        registration = listener
        context.getSystemService(NsdManager::class.java).registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    companion object {
        const val SERVICE_TYPE = "_zaiten._tcp."
        /**
         * Délai entre la sortie du mix et sa lecture partout : marge pour le Wi-Fi et les tampons des
         * enceintes. Le son du téléphone source est retardé d'autant pour rester aligné.
         */
        const val PLAYOUT_DELAY_MS = 150
        const val SAMPLE_RATE = 48_000
        const val CHANNELS = 2
        /** 10 ms de stéréo par message. */
        const val BLOCK_SAMPLES = SAMPLE_RATE / 100 * CHANNELS
        private const val IDLE_SLEEP_MS = 2L
        private const val NS_PER_SECOND = 1_000_000_000L
    }
}
