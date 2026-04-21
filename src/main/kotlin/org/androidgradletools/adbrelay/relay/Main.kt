package org.androidgradletools.adbrelay.relay

fun main(args: Array<String>) {
    val port = args.getOrNull(0)?.toIntOrNull()
        ?: System.getenv("RELAY_PORT")?.toIntOrNull()
        ?: AdbRelayServer.DEFAULT_PORT
    val host = System.getenv("RELAY_HOST") ?: AdbRelayServer.DEFAULT_HOST
    System.err.println(
        "[remote-adb-relay] listening on $host:$port (WebSocket path /). " +
            "Use ws://THIS_MACHINE_LAN_IP:$port from phones on Wi‑Fi. TLS: terminate wss:// upstream.",
    )
    AdbRelayServer(host = host, port = port).start(wait = true)
}
