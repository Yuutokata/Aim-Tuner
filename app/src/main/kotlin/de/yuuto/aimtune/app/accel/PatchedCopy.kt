package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.core.accel.RawAccelProfile
import de.yuuto.aimtune.storage.PassiveChanges
import de.yuuto.aimtune.storage.RawAccelExport
import de.yuuto.aimtune.storage.RawAccelPassivePatch
import kotlinx.serialization.json.JsonObject

/** One changed field of the patched copy; [passive] ones come from the stored rounds and were not blind-tested. */
data class PatchedChange(val field: String, val old: String, val new: String, val passive: Boolean)

/**
 * The patched copy of the player's `settings.json`: the profile [imported] is copied under [exportName] with the curve of
 * [refined] (the blind-tested refine result, if there is one; [RawAccelExport.patch], curve-shape whitelist) and then the
 * [passive] recommendations the player ticked ([RawAccelPassivePatch], rotation and the two ratios only). The original profile and
 * everything else in the file stay as they were.
 */
class PatchedCopy(
    val source: RefineSource,
    val imported: RawAccelProfile,
    val refined: RawAccelProfile?,
    passive: PassiveChanges,
) {
    /** The ticked recommendations that differ from what the profile already has; a value that is already set is not rewritten. */
    val passive = PassiveChanges(
        passive.rotationDegrees?.takeIf { it != imported.rotationDegrees },
        passive.yxRatio?.takeIf { it != imported.yxRatio },
        passive.lrRatio?.takeIf { it != imported.lrRatio },
    )

    val exportName: String get() = "${imported.name} (AimTune)"

    /** Whether the copy differs from the original profile at all. */
    val hasChanges: Boolean get() = changes().isNotEmpty()

    /** The blind-tested changes first, then the passive ones. */
    fun changes(): List<PatchedChange> = buildList {
        refined?.let { addAll(RefineDiff.changes(imported, it).map { c -> PatchedChange(c.field, c.old, c.new, passive = false) }) }
        passive.rotationDegrees?.let { add(PatchedChange("rotation", RefineDiff.number(imported.rotationDegrees), RefineDiff.number(it), passive = true)) }
        passive.yxRatio?.let { add(PatchedChange("Y/X ratio", RefineDiff.number(imported.yxRatio), RefineDiff.number(it), passive = true)) }
        passive.lrRatio?.let { add(PatchedChange("L/R ratio", RefineDiff.number(imported.lrRatio), RefineDiff.number(it), passive = true)) }
    }

    /** The whole file with the copy added. @throws IllegalArgumentException if a patch step refuses its input. */
    fun root(): JsonObject {
        val withCurve = RawAccelExport.patch(source.file.root, imported.name, imported, refined ?: imported, exportName)
        return RawAccelPassivePatch.apply(withCurve, exportName, passive)
    }
}
