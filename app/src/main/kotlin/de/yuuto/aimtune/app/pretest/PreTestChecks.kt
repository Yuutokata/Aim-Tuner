package de.yuuto.aimtune.app.pretest

import de.yuuto.aimtune.input.windows.PointerAcceleration

enum class CheckLevel { OK, ATTENTION, UNKNOWN }

/** One line of the pre-test notice. The checks only inform; starting the test is never blocked. */
data class CheckRow(
    val id: String,
    /** Keys of the i18n bundles; [detailArg] fills the `{0}` of the detail text. */
    val title: String,
    val detail: String,
    val state: String,
    val level: CheckLevel,
    val detailArg: String? = null,
)

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
        val title = "pretest.pointer.title"
        return when {
            acceleration == null ->
                CheckRow(POINTER, title, "pretest.pointer.unknown", "pretest.state.unknown", CheckLevel.UNKNOWN)
            acceleration.enabled ->
                CheckRow(POINTER, title, "pretest.pointer.on", "pretest.state.on", CheckLevel.ATTENTION)
            else -> CheckRow(POINTER, title, "pretest.pointer.off", "pretest.state.off", CheckLevel.OK)
        }
    }

    private fun rawAccelRow(confirmed: Boolean): CheckRow = if (confirmed) {
        CheckRow(RAW_ACCEL, "pretest.rawaccel.title", "pretest.rawaccel.confirmed", "pretest.state.ok", CheckLevel.OK)
    } else {
        CheckRow(RAW_ACCEL, "pretest.rawaccel.title", "pretest.rawaccel.unknown", "pretest.state.check", CheckLevel.ATTENTION)
    }

    private fun inputRow(sourceName: String, isRaw: Boolean): CheckRow = if (isRaw) {
        CheckRow(INPUT, "pretest.input.title", "pretest.input.raw", "pretest.state.raw", CheckLevel.OK)
    } else {
        CheckRow(INPUT, "pretest.input.title", "pretest.input.fallback", "pretest.state.fallback", CheckLevel.ATTENTION, detailArg = sourceName)
    }
}
