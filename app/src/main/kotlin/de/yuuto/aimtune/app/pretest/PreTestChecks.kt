package de.yuuto.aimtune.app.pretest

import de.yuuto.aimtune.input.windows.PointerAcceleration

enum class CheckLevel { OK, ATTENTION, UNKNOWN }

/** One line of the pre-test notice. The checks only inform; starting the test is never blocked. */
data class CheckRow(val id: String, val title: String, val detail: String, val state: String, val level: CheckLevel)

object PreTestChecks {
    const val POINTER = "pointer"
    const val RAW_ACCEL = "rawaccel"
    const val INPUT = "input"

    /**
     * The notice rows. Windows pointer acceleration is read from the system. Raw Accel cannot be
     * detected (its driver keeps no queryable settings), so the user confirms it by hand. The input
     * row says whether the precise raw source is in use.
     */
    fun rows(acceleration: PointerAcceleration?, rawAccelConfirmed: Boolean, sourceName: String, sourceIsRaw: Boolean): List<CheckRow> = listOf(
        pointerRow(acceleration),
        rawAccelRow(rawAccelConfirmed),
        inputRow(sourceName, sourceIsRaw),
    )

    private fun pointerRow(acceleration: PointerAcceleration?): CheckRow {
        val title = "Windows pointer acceleration"
        return when {
            acceleration == null ->
                CheckRow(POINTER, title, "Could not be read on this system. Check Control Panel → Mouse → Pointer Options.", "UNKNOWN", CheckLevel.UNKNOWN)
            acceleration.enabled ->
                CheckRow(
                    POINTER, title,
                    "On. Turn off \"Enhance pointer precision\" in Control Panel → Mouse → Pointer Options so the test matches the game.",
                    "ON", CheckLevel.ATTENTION,
                )
            else -> CheckRow(POINTER, title, "Off (\"Enhance pointer precision\" in Control Panel → Mouse → Pointer Options).", "OFF", CheckLevel.OK)
        }
    }

    private fun rawAccelRow(confirmed: Boolean): CheckRow = if (confirmed) {
        CheckRow(RAW_ACCEL, "Raw Accel", "You confirmed that it is off or set to a linear curve.", "OK", CheckLevel.OK)
    } else {
        CheckRow(
            RAW_ACCEL, "Raw Accel",
            "Cannot be detected automatically. If you use Raw Accel, set it to no acceleration or a linear curve while testing.",
            "CHECK", CheckLevel.ATTENTION,
        )
    }

    private fun inputRow(sourceName: String, isRaw: Boolean): CheckRow = if (isRaw) {
        CheckRow(INPUT, "Mouse input in AimTune", "Raw input is used, no smoothing.", "RAW", CheckLevel.OK)
    } else {
        CheckRow(INPUT, "Mouse input in AimTune", "$sourceName: movement comes from the window system with coarser timing.", "FALLBACK", CheckLevel.ATTENTION)
    }
}
