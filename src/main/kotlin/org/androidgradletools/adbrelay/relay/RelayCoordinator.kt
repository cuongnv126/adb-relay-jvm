package org.androidgradletools.adbrelay.relay

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred

private sealed class RegisterOutcome {
    data class Matched(
        val aToB: List<ByteArray>,
        val bToA: List<ByteArray>,
        val a: RelayCoordinator.Pending,
        val b: RelayCoordinator.Pending,
    ) : RegisterOutcome()

    object Waiting : RegisterOutcome()
}

/**
 * Pairs [dev] and [device] WebSockets by shared session [token], mirroring
 * [android-ide-extension/tools/remote-adb-relay/relay.cjs].
 */
internal class RelayCoordinator {
    private val lock = Any()
    private val pendingDev = HashMap<String, Pending>()
    private val pendingDevice = HashMap<String, Pending>()

    /**
     * @return true if this [pending] was registered (still waiting); false if already matched.
     */
    suspend fun registerOrMatch(role: String, token: String, pending: Pending): Boolean {
        val outcome = synchronized(lock) {
            if (role == "device") {
                val other = pendingDev.remove(token)
                if (other != null) {
                    val aToB = other.drainBufferSnapshot()
                    val bToA = pending.drainBufferSnapshot()
                    return@synchronized RegisterOutcome.Matched(aToB, bToA, other, pending)
                }
                pendingDevice[token] = pending
                return@synchronized RegisterOutcome.Waiting
            }
            val other = pendingDevice.remove(token)
            if (other != null) {
                val aToB = pending.drainBufferSnapshot()
                val bToA = other.drainBufferSnapshot()
                return@synchronized RegisterOutcome.Matched(aToB, bToA, pending, other)
            }
            pendingDev[token] = pending
            RegisterOutcome.Waiting
        }

        return when (outcome) {
            is RegisterOutcome.Matched -> {
                for (chunk in outcome.aToB) {
                    outcome.b.session.send(Frame.Binary(fin = true, data = chunk))
                }
                for (chunk in outcome.bToA) {
                    outcome.a.session.send(Frame.Binary(fin = true, data = chunk))
                }
                if (!outcome.a.peerFuture.isCompleted) {
                    outcome.a.peerFuture.complete(outcome.b.session)
                }
                if (!outcome.b.peerFuture.isCompleted) {
                    outcome.b.peerFuture.complete(outcome.a.session)
                }
                false
            }
            RegisterOutcome.Waiting -> true
        }
    }

    fun removePendingWebSocket(token: String, role: String, session: DefaultWebSocketServerSession) {
        synchronized(lock) {
            val map = if (role == "device") pendingDevice else pendingDev
            val current = map[token] ?: return@synchronized
            if (current.session === session) {
                map.remove(token)
            }
        }
    }

    internal class Pending(
        val session: DefaultWebSocketServerSession,
        val peerFuture: CompletableDeferred<DefaultWebSocketServerSession> = CompletableDeferred(),
        val buffer: MutableList<ByteArray> = mutableListOf(),
    ) {
        private var bufferedBytes = 0

        companion object {
            private const val MAX_BUFFER_BYTES = 32 * 1024 * 1024
        }

        /** Returns false if the buffer limit is exceeded; caller should close the connection. */
        fun addChunk(chunk: ByteArray): Boolean = synchronized(buffer) {
            if (bufferedBytes + chunk.size > MAX_BUFFER_BYTES) return false
            buffer.add(chunk)
            bufferedBytes += chunk.size
            true
        }

        fun drainBufferSnapshot(): List<ByteArray> = synchronized(buffer) {
            if (buffer.isEmpty()) return emptyList()
            val copy = ArrayList(buffer)
            buffer.clear()
            bufferedBytes = 0
            copy
        }

        suspend fun drainBufferTo(peer: DefaultWebSocketServerSession) {
            while (true) {
                val batch = drainBufferSnapshot()
                if (batch.isEmpty()) return
                for (chunk in batch) {
                    peer.send(Frame.Binary(fin = true, data = chunk))
                }
            }
        }
    }
}
