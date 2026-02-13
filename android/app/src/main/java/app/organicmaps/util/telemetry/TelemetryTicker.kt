package app.organicmaps.util.telemetry

import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * TelemetryTicker (schema v1)
 *
 * - Main thread tick
 * - Location from MwmActivity
 * - UDP sent from background thread
 * - No new JNI calls from here
 */
object TelemetryTicker {
    private const val INTERVAL_MS: Long = 2500L
    private var roundaboutExit: Int? = null
    private var lastTurnGraphicUid: String? = null

private var speedCameraDistanceM: Int? = null
private var speedCameraSpeedMps: Double? = null
private var speedCameraActive: Boolean = false
private var speedCameraExceeded: Boolean = false

    private var navActive: Boolean = false
    private var currentRoad: String? = null
    private var nextRoad: String? = null
    private var speedLimitMps: Double? = null

    private var distanceToTurnM: Int? = null
    private var timeToTurnS: Int? = null
    private var turnType: String? = null

    private var distanceToDestinationM: Int? = null
    private var timeToDestinationS: Int? = null
    private var etaEpochS: Long? = null

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

            // ---- LOOK-AHEAD (routing-only, safe / no native geometry) ----
            // Relative angle that gradually blends toward the upcoming turn direction as we approach it.
			
val (roadAheadRelDeg, roadAheadConf) =
    computeRoadAheadRelativeDeg(turnType, distanceToTurnM, navActive)

// ----- TURN GRAPHIC UID -----
val currentTurnGraphicUid =
    if (navActive && turnType != null) {
        if (roundaboutExit != null && roundaboutExit!! > 0)
            "${turnType}_exit_$roundaboutExit"
        else
            turnType
    } else {
        null
    }

val json = """
{
...

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
    "speed_limit_mps": ${speedLimitMps ?: "null"}
  },

  "navigation": {
    "distance_to_turn_m": ${distanceToTurnM ?: "null"},
    "time_to_turn_s": ${timeToTurnS ?: "null"},
    "turn_type": ${turnType?.let { "\"$it\"" } ?: "null"},
    "roundabout_exit": ${roundaboutExit ?: "null"},
    "current_road": ${currentRoad?.let { "\"$it\"" } ?: "null"},
    "next_road": ${nextRoad?.let { "\"$it\"" } ?: "null"},
    "distance_to_destination_m": ${distanceToDestinationM ?: "null"},
    "time_to_destination_s": ${timeToDestinationS ?: "null"},
    "eta_epoch_s": ${etaEpochS ?: "null"}
  },

  "turn_graphic": {
  "present": ${if (currentTurnGraphicUid != null) "true" else "false"},
  "uid": ${currentTurnGraphicUid?.let { "\"$it\"" } ?: "null"},
  "format": ${if (currentTurnGraphicUid != null) "\"png\"" else "null"},
  "width": ${if (currentTurnGraphicUid != null) 128 else "null"},
  "height": ${if (currentTurnGraphicUid != null) 128 else "null"}
},

"alerts": {
  "speed_camera": {
    "active": $speedCameraActive,
    "distance_m": ${speedCameraDistanceM ?: "null"},
    "time_s": ${
        if (speedCameraDistanceM != null && speed > 0)
            (speedCameraDistanceM!! / speed).roundToInt()
        else
            "null"
    },
    "speed_limit_mps": ${speedCameraSpeedMps ?: "null"},
    "speed_exceeded": $speedCameraExceeded
  }
},


  "idle": {
    "road_ahead_bearing_deg": ${roadAheadRelDeg ?: "null"},
    "road_ahead_confidence": $roadAheadConf
  },

  "system": {
    "battery_low": false
  }
}
""".trimIndent()

// ----- TURN GRAPHIC UID GATE (Phase 1 Step 2) -----



if (currentTurnGraphicUid != null &&
    currentTurnGraphicUid != lastTurnGraphicUid) {

    lastTurnGraphicUid = currentTurnGraphicUid

    Toast.makeText(
        appContext,
        "TURN UID: $currentTurnGraphicUid",
        Toast.LENGTH_SHORT
    ).show()
}


            // Send UDP
            TelemetryUdpSender.send(json)

            // Keep toast for verification
            //Toast.makeText(
            //    appContext,
             //   "SPD: %.1f  BRG: %d".format(speed, bearing),
             //   Toast.LENGTH_SHORT
            //).show()

            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start(context: Context) {
        if (running) return
        running = true
        appContext = context.applicationContext

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
            // Clear all routing state when navigation stops
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
    roundExit: Int?,
    currentRoadName: String?,
    nextRoadName: String?,
    speedLimit: Double?,
    distanceDestM: Int?,
    timeDestS: Int?,
    etaS: Long?
) {
    // Always update turn data
    distanceToTurnM = distanceM
    timeToTurnS = timeS
    turnType = type
    roundaboutExit = roundExit

    currentRoad = currentRoadName
    nextRoad = nextRoadName

    if (speedLimit != null)
        speedLimitMps = speedLimit

    if (distanceDestM != null)
        distanceToDestinationM = distanceDestM

    if (timeDestS != null)
        timeToDestinationS = timeDestS

    if (etaS != null)
        etaEpochS = etaS
}

fun setSpeedCameraData(
    distanceMeters: Double,
    speedKmph: Double,
    active: Boolean,
    exceeded: Boolean
) {
    speedCameraDistanceM =
        if (distanceMeters >= 0)
            distanceMeters.roundToInt()
        else
            null

    speedCameraSpeedMps =
        if (speedKmph > 0)
            speedKmph / 3.6
        else
            null

     // Active if camera exists ahead
    speedCameraActive = distanceMeters >= 0
    speedCameraExceeded = exceeded
}

fun updateLocation(location: Location) {
    lastLocation = location
}


    /**
     * Safe “road-ahead” approximation for routing mode:
     * - Uses the next turn instruction (turnType) as the target angle.
     * - Blends from 0.0 (far) to 1.0 (near) based on distanceToTurnM.
     *
     * Output is RELATIVE degrees (signed), suitable for a pointer.
     */
    private fun computeRoadAheadRelativeDeg(
        turnType: String?,
        distanceToTurnM: Int?,
        navActive: Boolean
    ): Pair<Int?, Double> {
        if (!navActive) return Pair(null, 0.0)
        if (turnType == null) return Pair(null, 0.0)
        if (distanceToTurnM == null) return Pair(null, 0.0)

        val targetDeg = turnTypeToRelativeAngleDeg(turnType) ?: return Pair(null, 0.0)

        // Blend window (tunable, safe defaults)
        val startBlendM = 2000   // start blending 2km out (very gentle)
        val fullBlendM = 50      // fully committed by 50m to the turn

        val d = distanceToTurnM

        if (d > startBlendM) return Pair(null, 0.0)

        val t = when {
            d <= fullBlendM -> 1.0
            else -> (startBlendM - d).toDouble() / (startBlendM - fullBlendM).toDouble()
        }

        val conf = t.coerceIn(0.0, 1.0)
        val rel = (targetDeg * conf).roundToInt()

        return Pair(rel, conf)
    }

    /**
     * Maps OM CarDirection enum names (as strings) to a signed relative angle in degrees.
     * Right = positive, Left = negative.
     */
    private fun turnTypeToRelativeAngleDeg(turnType: String): Int? {
        return when (turnType) {
            "NoTurn" -> null
            "GoStraight" -> 0

            "TurnSlightRight" -> 30
            "TurnRight" -> 90
            "TurnSharpRight" -> 150

            "TurnSlightLeft" -> -30
            "TurnLeft" -> -90
            "TurnSharpLeft" -> -150

            "UTurnRight" -> 180
            "UTurnLeft" -> -180

            // Roundabout handling will be improved once we wire roundabout_exit.
            "EnterRoundAbout" -> 0
            "StayOnRoundAbout" -> 0
            "LeaveRoundAbout" -> 0

            "ExitHighwayToRight" -> 60
            "ExitHighwayToLeft" -> -60

            "StartAtEndOfStreet" -> 0
            "ReachedYourDestination" -> 0

            else -> null
        }
    }
}
