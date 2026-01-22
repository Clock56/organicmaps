package app.organicmaps.util.telemetry

import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Sends telemetry snapshots over UDP.
 * Stateless by design.
 */
object NavTelemetrySender {

    private const val HOST = "192.168.1.216"   // <-- change if needed
    private const val PORT = 5005

    private val socket by lazy {
        DatagramSocket()
    }

    fun send(snapshot: NavTelemetrySnapshot) {
        val json = JSONObject().apply {
            put("ts_ms", snapshot.timestampMs)
            put("app_alive", snapshot.appAlive)

            put("gps_valid", snapshot.gpsValid)
            put("lat", snapshot.latitude)
            put("lon", snapshot.longitude)
            put("speed_mps", snapshot.speedMps)
            put("heading_deg", snapshot.headingDeg)

            put("navigating", snapshot.navigating)
            put("dist_to_dest_m", snapshot.distanceToDestinationM)
            put("eta_s", snapshot.etaSeconds)
        }

        val data = json.toString().toByteArray()
        val packet = DatagramPacket(
            data,
            data.size,
            InetAddress.getByName(HOST),
            PORT
        )

        socket.send(packet)
    }
}

