package de.yuuto.aimtune.app.accel

import de.yuuto.aimtune.core.accel.RawAccelMode
import de.yuuto.aimtune.core.accel.RawAccelProfile
import java.util.Locale

/** One field of the Raw Accel profile that the refined profile changes, with the text of the old and the new value. */
data class FieldChange(val field: String, val old: String, val new: String)

object RefineDiff {
    /** The fields that differ between [old] and [new], under the names Raw Accel's file uses. */
    fun changes(old: RawAccelProfile, new: RawAccelProfile): List<FieldChange> = buildList {
        fun add(field: String, a: Double, b: Double) {
            if (a != b) add(FieldChange(field, number(a), number(b)))
        }
        add("Output DPI", old.outputDpi, new.outputDpi)
        add("inputOffset", old.args.inputOffset, new.args.inputOffset)
        add("acceleration", old.args.acceleration, new.args.acceleration)
        add("decayRate", old.args.decayRate, new.args.decayRate)
        add("limit", old.args.limit, new.args.limit)
        add("Cap / Jump x", old.args.capX, new.args.capX)
        add("Cap / Jump y", old.args.capY, new.args.capY)
    }

    /** A short multi-line description of the profile's curve for the import screen. */
    fun describe(profile: RawAccelProfile): String {
        val a = profile.args
        val lines = mutableListOf("${a.mode.json} (${if (a.gain) "Gain" else "Velocity"})")
        when (a.mode) {
            RawAccelMode.CLASSIC -> {
                lines += "inputOffset ${number(a.inputOffset)}  acceleration ${number(a.acceleration)}  exponent ${number(a.exponentClassic)}"
                lines += "cap ${number(a.capX)} / ${number(a.capY)} (${a.capMode.json})"
            }
            RawAccelMode.NATURAL -> lines += "inputOffset ${number(a.inputOffset)}  decayRate ${number(a.decayRate)}  limit ${number(a.limit)}"
            RawAccelMode.LUT -> lines += "${a.lut.size / 2} points"
            RawAccelMode.NOACCEL -> Unit
        }
        lines += "Output DPI ${number(profile.outputDpi)}  device DPI ${number(profile.deviceDpi)}"
        return lines.joinToString("\n")
    }

    fun number(value: Double): String = "%.5f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.')
}
