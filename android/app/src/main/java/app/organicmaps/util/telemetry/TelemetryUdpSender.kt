package app.organicmaps.util.telemetry

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

/**
 * STEP 4A – UDP sender (Kotlin-only).
 *
 * - No JNI
 * - Uses a single background thread to avoid network-on-main-thread
 * - Not wired to the ticker yet (that’s Step 4B)
 */
object TelemetryUdpSender {

    // TODO (you will set these in Step 4B once we know your PC listener address)
    @Volatile var enabled: Boolean = false
    @Volatile var host: String = "192.168.1.100"
    @Volatile var port: Int = 5055

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "TelemetryUdpSender").apply { isDaemon = true }
    }

    fun send(json: String) {
        if (!enabled) return

        val bytes = json.toByteArray(Charsets.UTF_8)

        executor.execute {
            try {
                DatagramSocket().use { socket ->
                    val addr = InetAddress.getByName(host)
                    val packet = DatagramPacket(bytes, bytes.size, addr, port)
                    socket.send(packet)
                }
            } catch (_: Throwable) {
                // Intentionally swallow errors for now to avoid destabilising the app.
                // Later, we can add optional logging.
            }
        }
    }
}
