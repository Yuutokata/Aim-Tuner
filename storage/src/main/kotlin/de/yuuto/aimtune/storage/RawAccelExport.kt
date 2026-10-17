package de.yuuto.aimtune.storage

import de.yuuto.aimtune.core.accel.AccelCurve
import de.yuuto.aimtune.core.accel.ClassicCurve
import de.yuuto.aimtune.core.accel.LookupCurve
import de.yuuto.aimtune.core.accel.NaturalCurve
import de.yuuto.aimtune.core.accel.NoAccel
import de.yuuto.aimtune.core.accel.PowerCurve
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes a Raw Accel `settings.json` for a fitted curve. AimTune never touches the driver: the file
 * is only produced for the user to copy into Raw Accel's folder (or import in its GUI).
 *
 * The layout follows Raw Accel 1.7.1: the property names are the ones Json.NET derives in
 * `wrapper/wrapper.cpp` (`DriverConfig`, `Profile`, `AccelArgs`, `DeviceConfig`), every field the
 * wrapper marks as required is written, and every parameter, also of modes that are not used, holds
 * a value that passes `common/rawaccel-validate.hpp`. The curve is exported in the Velocity form
 * (`Gain / Velocity` false), which is the form AimTune simulates. The sensitivity factor goes into
 * the profile's output DPI (device DPI times the factor), so the in-game sensitivity stays as it is.
 * The file could not be loaded into Raw Accel from here; see docs/ALGORITHMS.md ("Acceleration").
 */
object RawAccelExport {
    const val RAW_ACCEL_VERSION = "1.7.1"
    const val PROFILE_NAME = "AimTune"

    private val pretty = Json { prettyPrint = true }

    /**
     * @param dpi the mouse DPI, which Raw Accel needs to turn counts per ms into inches per second.
     * @param sensitivityFactor the fit's factor; the total gain is this times the curve.
     */
    fun settingsJson(curve: AccelCurve, dpi: Double, sensitivityFactor: Double = 1.0): String {
        require(dpi > 0.0) { "dpi must be positive" }
        require(sensitivityFactor > 0.0 && sensitivityFactor.isFinite()) { "sensitivity factor must be positive" }
        val root = buildJsonObject {
            put("version", RAW_ACCEL_VERSION)
            putJsonObject("defaultDeviceConfig") {
                put("disable", false)
                put("DPI (normalizes input speed unit: counts/ms -> in/s)", dpi.toInt())
                put("Polling rate Hz (keep at 0 for automatic adjustment)", 0)
            }
            putJsonArray("profiles") { add(profile(curve, dpi, sensitivityFactor)) }
            putJsonArray("devices") {}
        }
        return pretty.encodeToString(JsonElement.serializer(), root) + "\n"
    }

    /** Writes the file into [directory] (created if needed) as `settings.json` and returns its path. */
    fun write(directory: Path, curve: AccelCurve, dpi: Double, sensitivityFactor: Double): Path {
        Files.createDirectories(directory)
        val file = directory.resolve("settings.json")
        Files.writeString(file, settingsJson(curve, dpi, sensitivityFactor))
        return file
    }

    /** Reads the curve and the sensitivity factor back from a file written by [settingsJson]. */
    fun parse(text: String): Pair<AccelCurve, Double> {
        val root = Json.parseToJsonElement(text).jsonObject
        val config = root.getValue("defaultDeviceConfig").jsonObject
        val dpi = config.getValue("DPI (normalizes input speed unit: counts/ms -> in/s)").jsonPrimitive.double()
        val profile = root.getValue("profiles").let { it as JsonArray }.first().jsonObject
        val factor = profile.getValue("Output DPI").jsonPrimitive.double() / dpi
        val args = profile.getValue("Whole or horizontal accel parameters").jsonObject
        fun number(name: String) = args.getValue(name).jsonPrimitive.double()
        val cap = args.getValue("Cap / Jump").jsonObject.getValue("y").jsonPrimitive.double()
        val curve = when (val mode = args.getValue("mode").jsonPrimitive.content) {
            "noaccel" -> NoAccel
            "classic" -> ClassicCurve(number("acceleration"), number("exponentClassic"), number("inputOffset"), cap)
            "natural" -> NaturalCurve(number("decayRate"), number("limit"), number("inputOffset"))
            "power" -> PowerCurve(number("scale"), number("exponentPower"), number("outputOffset"), cap)
            "lut" -> {
                // With "Gain / Velocity" true the y values would be output speeds, not multipliers.
                require(args.getValue("Gain / Velocity").jsonPrimitive.content == "false") { "only the Velocity form of a lut is supported" }
                val data = (args.getValue("data") as JsonArray).map { it.jsonPrimitive.double() }
                require(data.size % 2 == 0) { "lut data must hold speed and multiplier pairs" }
                LookupCurve(data.filterIndexed { i, _ -> i % 2 == 0 }, data.filterIndexed { i, _ -> i % 2 == 1 })
            }
            else -> throw IllegalArgumentException("unsupported mode '$mode'")
        }
        return curve to factor
    }

    private fun JsonPrimitive.double(): Double = requireNotNull(doubleOrNull) { "not a number: $content" }

    private fun profile(curve: AccelCurve, dpi: Double, factor: Double): JsonObject = buildJsonObject {
        put("name", PROFILE_NAME)
        putJsonObject("Stretches domain for horizontal vs vertical inputs") { put("x", 1.0); put("y", 1.0) }
        putJsonObject("Stretches accel range for horizontal vs vertical inputs") { put("x", 1.0); put("y", 1.0) }
        put("Whole or horizontal accel parameters", accelArgs(curve))
        put("Vertical accel parameters", accelArgs(curve))
        putJsonObject("Input speed calculation parameters") {
            put("Whole/combined accel (set false for 'by component' mode)", true)
            put("lpNorm", 2.0)
            put("Time in ms after which an input is weighted at half its original value.", 0.0)
            put("Time in ms after which scale is weighted at half its original value.", 0.0)
            put("Time in ms after which an output is weighted at half its original value.", 0.0)
        }
        // Raw Accel multiplies the counts by output DPI / 1000 * 1000 / device DPI after the curve, so
        // an output DPI equal to the device DPI leaves the sensitivity alone and the factor scales it.
        put("Output DPI", dpi * factor)
        put("Y/X output DPI ratio (vertical sens multiplier)", 1.0)
        put("L/R output DPI ratio (left sens multiplier)", 1.0)
        put("U/D output DPI ratio (up sens multiplier)", 1.0)
        put("Degrees of rotation", 0.0)
        put("Degrees of angle snapping", 0.0)
        put("Input Speed Cap", 0.0)
    }

    /** All parameters of Raw Accel's `accel_args`, defaults from `rawaccel-base.hpp` unless the curve sets them. */
    private fun accelArgs(curve: AccelCurve): JsonObject {
        var mode = "noaccel"
        var inputOffset = 0.0
        var outputOffset = 0.0
        var acceleration = 0.005
        var decayRate = 0.1
        var exponentClassic = 2.0
        var scale = 1.0
        var exponentPower = 0.05
        var limit = 1.5
        var capY = 1.5
        when (curve) {
            NoAccel -> Unit
            is ClassicCurve -> {
                mode = "classic"
                acceleration = curve.acceleration
                exponentClassic = curve.exponent
                inputOffset = curve.inputOffset
                capY = curve.cap
            }
            is NaturalCurve -> {
                mode = "natural"
                decayRate = curve.decayRate
                limit = curve.limit
                inputOffset = curve.inputOffset
            }
            is PowerCurve -> {
                mode = "power"
                scale = curve.scale
                exponentPower = curve.exponent
                outputOffset = curve.outputOffset
                capY = curve.cap
            }
            is LookupCurve -> mode = "lut"
        }
        // lut reads only "data": x1, y1, x2, y2, ... (speed in in/s, multiplier, since "Gain / Velocity" is false);
        // wrapper.cpp takes the array's length as the table size (common/accel-lookup.hpp, v1.7.1).
        val table = (curve as? LookupCurve)?.let { lut -> lut.speeds.indices.flatMap { listOf(lut.speeds[it], lut.multipliers[it]) } } ?: emptyList()
        return buildJsonObject {
            put("mode", mode)
            // true would be the Gain form; the curves here are sensitivity multipliers per speed.
            put("Gain / Velocity", false)
            put("inputOffset", inputOffset)
            put("outputOffset", outputOffset)
            put("acceleration", acceleration)
            put("decayRate", decayRate)
            put("gamma", 1.0)
            put("motivity", 1.5)
            put("exponentClassic", exponentClassic)
            put("scale", scale)
            put("exponentPower", exponentPower)
            put("limit", limit)
            put("syncSpeed", 5.0)
            put("smooth", 0.5)
            putJsonObject("Cap / Jump") { put("x", 0.0); put("y", capY) }
            put("Cap mode", "output")
            putJsonArray("data") { table.forEach { add(JsonPrimitive(it.toFloat())) } }
        }
    }
}
