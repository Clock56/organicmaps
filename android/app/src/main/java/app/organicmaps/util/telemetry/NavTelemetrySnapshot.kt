package app.organicmaps.util.telemetry

/**
 * Immutable snapshot of navigation / app state
 * suitable for UDP export.
 */
data class NavTelemetrySnapshot(
    val timestampMs: Long,
    val appAlive: Boolean,

    // Position / motion
    val gpsValid: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val speedMps: Double?,
    val headingDeg: Double?,

    // Navigation
    val navigating: Boolean,
    val distanceToDestinationM: Double?,
    val etaSeconds: Long?
)

