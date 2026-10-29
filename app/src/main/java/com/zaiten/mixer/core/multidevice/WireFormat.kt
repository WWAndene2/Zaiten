package com.zaiten.mixer.core.multidevice

import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * Messages entre la source et les enceintes, sur TCP : [type: octet][taille: int][contenu].
 * Le son voyage en PCM 16 bits stéréo 48 kHz (~190 ko/s par enceinte, sans compression : aucune
 * latence d'encodage, et le Wi-Fi local suffit).
 */
sealed interface Message {
    /** Enceinte → source : présentation. */
    data class Hello(val name: String, val role: SpeakerRole) : Message
    data class Ping(val t0: Long) : Message
    data class Pong(val t0: Long, val t1: Long) : Message
    /** Source → enceintes : rôles présents, pour le repli des canaux absents. */
    data class Roster(val roles: Set<SpeakerRole>) : Message
    /** Source → enceintes : [samples] à faire sortir à [playAtNs] (horloge de la source). */
    class Audio(val playAtNs: Long, val samples: ShortArray, val count: Int) : Message

    companion object {
        private const val HELLO = 1
        private const val PING = 2
        private const val PONG = 3
        private const val ROSTER = 4
        private const val AUDIO = 5
        /** Garde-fou contre un flux corrompu : un message audio fait ~4 ko. */
        private const val MAX_PAYLOAD = 1 shl 20

        fun write(out: DataOutputStream, message: Message) {
            when (message) {
                is Hello -> frame(out, HELLO) { writeUTF(message.name); writeByte(message.role.ordinal) }
                is Ping -> frame(out, PING) { writeLong(message.t0) }
                is Pong -> frame(out, PONG) { writeLong(message.t0); writeLong(message.t1) }
                is Roster -> frame(out, ROSTER) { writeInt(message.roles.fold(0) { acc, r -> acc or (1 shl r.ordinal) }) }
                is Audio -> frame(out, AUDIO) {
                    writeLong(message.playAtNs)
                    writeInt(message.count)
                    for (i in 0 until message.count) writeShort(message.samples[i].toInt())
                }
            }
            out.flush()
        }

        fun read(input: DataInputStream): Message {
            val type = input.readUnsignedByte()
            val size = input.readInt()
            require(size in 0..MAX_PAYLOAD) { "Message trop grand : $size" }
            val body = ByteArray(size).also(input::readFully)
            val d = DataInputStream(body.inputStream())
            return when (type) {
                HELLO -> Hello(d.readUTF(), SpeakerRole.entries[d.readUnsignedByte()])
                PING -> Ping(d.readLong())
                PONG -> Pong(d.readLong(), d.readLong())
                ROSTER -> d.readInt().let { bits -> Roster(SpeakerRole.entries.filter { bits and (1 shl it.ordinal) != 0 }.toSet()) }
                AUDIO -> {
                    val at = d.readLong()
                    val count = d.readInt()
                    Audio(at, ShortArray(count) { d.readShort() }, count)
                }
                else -> throw IllegalArgumentException("Type inconnu : $type")
            }
        }

        private inline fun frame(out: DataOutputStream, type: Int, body: DataOutputStream.() -> Unit) {
            val bytes = java.io.ByteArrayOutputStream()
            DataOutputStream(bytes).apply(body).flush()
            out.writeByte(type)
            out.writeInt(bytes.size())
            bytes.writeTo(out)
        }
    }
}
