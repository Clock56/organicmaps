package app.organicmaps.util.telemetry

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

object TelemetryUdpSender {

    @Volatile var enabled: Boolean = false
    @Volatile var host: String = "192.168.1.133"
    @Volatile var port: Int = 5005

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
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }
}
