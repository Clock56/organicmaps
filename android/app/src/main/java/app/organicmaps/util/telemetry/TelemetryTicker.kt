package app.organicmaps.util.telemetry


import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * STEP 4B – Telemetry + UDP (Kotlin only)
 *
 * - Main thread tick
 * - Location from MwmActivity
 * - UDP sent from background thread
 * - No JNI
 */
object TelemetryTicker {
private var distanceToDestinationM: Int? = null
private var timeToDestinationS: Int? = null
private var etaEpochS: Long? = null

    private const val INTERVAL_MS: Long = 2500L
    private var navActive: Boolean = false
    private var distanceToTurnM: Int? = null
    private var timeToTurnS: Int? = null
    private var turnType: String? = null

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var appContext: Context? = null

    private var lastLocation: Location? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return

            val loc = lastLocation

            val speed = loc?.speed ?: 0f
            val bearing = if (loc?.hasBearing() == true) loc.bearing.roundToInt() else 0
            val timestampMs = System.currentTimeMillis()
            val json = """
{
  "meta": {
    "version": 1,
    "timestamp_ms": $timestampMs,
    "app_alive": true,
    "nav_active": $navActive
  },

  "vehicle": {
  "speed_mps": ${if (loc != null) "%.2f".format(speed) else "null"},
  "heading_deg": ${if (loc != null) bearing else "null"},
  "latitude_deg": ${loc?.latitude ?: "null"},
  "longitude_deg": ${loc?.longitude ?: "null"},
  "speed_limit_mps": null
},


  "navigation": {
  "distance_to_turn_m": ${distanceToTurnM ?: "null"},
  "time_to_turn_s": ${timeToTurnS ?: "null"},
  "turn_type": ${turnType?.let { "\"$it\"" } ?: "null"},
  "roundabout_exit": null,
  "current_road": null,
  "next_road": null,
  "distance_to_destination_m": ${distanceToDestinationM ?: "null"},
  "time_to_destination_s": ${timeToDestinationS ?: "null"},
  "eta_epoch_s": ${etaEpochS ?: "null"}
},


  "turn_graphic": {
    "present": false,
    "uid": null,
    "format": null,
    "width": null,
    "height": null
  },

  "alerts": {
    "speed_camera": {
      "active": false,
      "distance_m": null,
      "time_s": null,
      "speed_limit_mps": null
    }
  },

  "idle": {
    "road_ahead_bearing_deg": null,
    "road_ahead_confidence": 0.0
  },

  "system": {
    "battery_low": false
  }
}
""".trimIndent()

            // Send UDP
            TelemetryUdpSender.send(json)

            // Keep toast for verification
            Toast.makeText(
                appContext,
                "SPD: %.1f  BRG: %d".format(speed, bearing),
                Toast.LENGTH_SHORT
            ).show()

            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start(context: Context) {
        if (running) return
        running = true
        appContext = context.applicationContext

        // Enable UDP once app starts
        TelemetryUdpSender.host = "192.168.1.133"
        TelemetryUdpSender.port = 5005
        TelemetryUdpSender.enabled = true

        handler.post(tickRunnable)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tickRunnable)
    }

fun setNavActive(active: Boolean) {
    navActive = active

    if (!active) {
        distanceToTurnM = null
        timeToTurnS = null
        turnType = null

        distanceToDestinationM = null
        timeToDestinationS = null
        etaEpochS = null
    }
}

fun setRoutingData(
    distanceM: Int?,
    timeS: Int?,
    type: String?,
    distanceDestM: Int?,
    timeDestS: Int?,
    etaS: Long?
) {
    // Always update turn data
    distanceToTurnM = distanceM
    timeToTurnS = timeS
    turnType = type

    // Only update destination data if non-null
    if (distanceDestM != null)
        distanceToDestinationM = distanceDestM

    if (timeDestS != null)
        timeToDestinationS = timeDestS

    if (etaS != null)
        etaEpochS = etaS
}

fun updateLocation(location: Location) {
    lastLocation = location
}
}
