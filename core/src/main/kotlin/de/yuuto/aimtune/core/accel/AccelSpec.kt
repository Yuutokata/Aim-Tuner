package de.yuuto.aimtune.core.accel

/**
 * A one-line text form of a curve, used to store it with a round: `none`, or the mode name followed
 * by `name=value` parameters separated by semicolons, for example `classic;acceleration=0.08;exponent=2.0;inputOffset=0.0;cap=0.0`.
 * Doubles are written with Kotlin's shortest round-trip form, so decoding gives an equal curve.
 */
object AccelSpec {
    fun encode(curve: AccelCurve): String = when (curve) {
        NoAccel -> NONE
        is ClassicCurve -> "classic;acceleration=${curve.acceleration};exponent=${curve.exponent};inputOffset=${curve.inputOffset};cap=${curve.cap}"
        is NaturalCurve -> "natural;decayRate=${curve.decayRate};limit=${curve.limit};inputOffset=${curve.inputOffset}"
        is PowerCurve -> "power;scale=${curve.scale};exponent=${curve.exponent};outputOffset=${curve.outputOffset};cap=${curve.cap}"
        is LookupCurve -> "$LUT;points=" + curve.speeds.indices.joinToString(",") { "${curve.speeds[it]}:${curve.multipliers[it]}" }
    }

    /** @throws IllegalArgumentException for unknown modes, missing or invalid parameters. */
    fun decode(text: String): AccelCurve {
        val parts = text.trim().split(';')
        if (parts.first() == LUT) return decodeLookup(text, parts)
        val values = parts.drop(1).associate { part ->
            val (name, value) = part.split('=', limit = 2).also { require(it.size == 2) { "bad parameter '$part' in '$text'" } }
            name to requireNotNull(value.toDoubleOrNull()) { "'$value' is not a number in '$text'" }
        }
        fun get(name: String) = requireNotNull(values[name]) { "missing '$name' in '$text'" }
        return when (parts.first()) {
            NONE -> NoAccel
            "classic" -> ClassicCurve(get("acceleration"), get("exponent"), get("inputOffset"), get("cap"))
            "natural" -> NaturalCurve(get("decayRate"), get("limit"), get("inputOffset"))
            "power" -> PowerCurve(get("scale"), get("exponent"), get("outputOffset"), get("cap"))
            else -> throw IllegalArgumentException("unknown acceleration mode in '$text'")
        }
    }

    /** `lut;points=speed:multiplier,speed:multiplier,...` */
    private fun decodeLookup(text: String, parts: List<String>): LookupCurve {
        val points = parts.getOrNull(1)?.removePrefix("points=")?.takeIf { parts.size == 2 && parts[1].startsWith("points=") }
            ?: throw IllegalArgumentException("missing 'points' in '$text'")
        val pairs = points.split(',').map { point ->
            val (speed, multiplier) = point.split(':').also { require(it.size == 2) { "bad point '$point' in '$text'" } }
            requireNotNull(speed.toDoubleOrNull()) { "'$speed' is not a number in '$text'" } to
                requireNotNull(multiplier.toDoubleOrNull()) { "'$multiplier' is not a number in '$text'" }
        }
        return LookupCurve(pairs.map { it.first }, pairs.map { it.second })
    }

    private const val NONE = "none"
    private const val LUT = "lut"
}
