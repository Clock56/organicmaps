package app.organicmaps.util.telemetry
import app.organicmaps.util.telemetry.NavTelemetrySender
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


/**
 * Central telemetry coordinator.
 *
 * - Tracks app foreground/background
 * - Periodically emits idle telemetry
 * - Later will merge routing + alerts
 */
object NavTelemetryCollector {

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var task: ScheduledFuture<*>? = null

    @Volatile
    private var appActive = false

    // ------------------------------------------------------------
    // LIFECYCLE (called from MwmActivity)
    // ------------------------------------------------------------

    @JvmStatic
    fun onAppResumed() {
        appActive = true
        startIdleTelemetry()
    }

    @JvmStatic
    fun onAppPaused() {
        appActive = false
        stopIdleTelemetry()
    }


// ------------------------------------------------------------
// ROUTING TELEMETRY (called from NavigationService)
// ------------------------------------------------------------

@JvmStatic
fun onRoutingUpdated(
    distanceToDestinationM: Double,
    etaSeconds: Long
) {
    if (!appActive)
        return

    val snapshot = NavTelemetrySnapshot(
        timestampMs = System.currentTimeMillis(),
        appAlive = true,

        gpsValid = true,
        latitude = null,
        longitude = null,
        speedMps = null,
        headingDeg = null,

        navigating = true,
        distanceToDestinationM = distanceToDestinationM,
        etaSeconds = etaSeconds
    )

    NavTelemetrySender.send(snapshot)
}


    // ------------------------------------------------------------
    // IDLE TELEMETRY LOOP
    // ------------------------------------------------------------

    private fun startIdleTelemetry() {
        if (task != null && !task!!.isCancelled)
            return

        task = scheduler.scheduleAtFixedRate(
            {
                if (!appActive) return@scheduleAtFixedRate

                val snapshot = collectIdleSnapshot()
                NavTelemetrySender.send(snapshot)
            },
            0,
            1,
            TimeUnit.SECONDS
        )
    }

    private fun stopIdleTelemetry() {
        task?.cancel(false)
        task = null
    }

    // ------------------------------------------------------------
    // SNAPSHOT CREATION (idle only for now)
    // ------------------------------------------------------------

    fun collectIdleSnapshot(): NavTelemetrySnapshot {
        return NavTelemetrySnapshot(
            timestampMs = System.currentTimeMillis(),
            appAlive = appActive,

            gpsValid = false,
            latitude = null,
            longitude = null,
            speedMps = null,
            headingDeg = null,

            navigating = false,
            distanceToDestinationM = null,
            etaSeconds = null
        )
    }
}



