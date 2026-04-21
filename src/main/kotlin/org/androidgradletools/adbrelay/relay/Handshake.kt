package org.androidgradletools.adbrelay.relay

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal data class HandshakeMessage(
    val v: Int,
    val role: String,
    val token: String,
)

internal val handshakeJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

internal fun parseHandshake(text: String): HandshakeMessage? =
    try {
        handshakeJson.decodeFromString(HandshakeMessage.serializer(), text)
    } catch (_: Exception) {
        null
    }

internal fun HandshakeMessage.isValid(): Boolean =
    v == 1 &&
        token.isNotEmpty() &&
        (role == "dev" || role == "device")
