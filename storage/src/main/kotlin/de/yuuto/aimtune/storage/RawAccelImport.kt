package de.yuuto.aimtune.storage

import de.yuuto.aimtune.core.accel.RawAccelArgs
import de.yuuto.aimtune.core.accel.RawAccelCapMode
import de.yuuto.aimtune.core.accel.RawAccelMode
import de.yuuto.aimtune.core.accel.RawAccelProfile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.abs

/** The file or the chosen profile is not a Raw Accel 1.7.1 `settings.json` (as opposed to a valid one AimTune cannot reproduce, see [RawAccelImportResult.Refused]). */
class RawAccelImportException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** The property names of Raw Accel's `settings.json` (Json.NET names from `wrapper/wrapper.cpp:64-244`), shared by import and patch export. */
internal object RawAccelKeys {
    const val VERSION = "version"
    const val DEVICE_CONFIG = "defaultDeviceConfig"
    const val DEVICE_DPI = "DPI (normalizes input speed unit: counts/ms -> in/s)"
    const val POLLING_RATE = "Polling rate Hz (keep at 0 for automatic adjustment)"
    const val POLL_TIME_LOCK = "Use constant time interval based on polling rate"
    const val PROFILES = "profiles"
    const val DEVICES = "devices"
    const val NAME = "name"
    const val DOMAIN = "Stretches domain for horizontal vs vertical inputs"
    const val RANGE = "Stretches accel range for horizontal vs vertical inputs"
    const val WHOLE_ARGS = "Whole or horizontal accel parameters"
    const val VERTICAL_ARGS = "Vertical accel parameters"
    const val SPEED_ARGS = "Input speed calculation parameters"
    const val WHOLE_FLAG = "Whole/combined accel (set false for 'by component' mode)"
    const val LP_NORM = "lpNorm"
    const val INPUT_HALFLIFE = "Time in ms after which an input is weighted at half its original value."
    const val SCALE_HALFLIFE = "Time in ms after which scale is weighted at half its original value."
    const val OUTPUT_HALFLIFE = "Time in ms after which an output is weighted at half its original value."
    const val OUTPUT_DPI = "Output DPI"
    const val YX_RATIO = "Y/X output DPI ratio (vertical sens multiplier)"
    const val LR_RATIO = "L/R output DPI ratio (left sens multiplier)"
    const val UD_RATIO = "U/D output DPI ratio (up sens multiplier)"
    const val ROTATION = "Degrees of rotation"
    const val SNAP = "Degrees of angle snapping"
    const val SPEED_CAP = "Input Speed Cap"

    const val MODE = "mode"
    const val GAIN = "Gain / Velocity"
    const val INPUT_OFFSET = "inputOffset"
    const val ACCELERATION = "acceleration"
    const val DECAY_RATE = "decayRate"
    const val EXPONENT_CLASSIC = "exponentClassic"
    const val LIMIT = "limit"
    const val CAP = "Cap / Jump"
    const val CAP_MODE = "Cap mode"
    const val DATA = "data"
}

/** A parsed `settings.json`: the original tree (kept untouched for [RawAccelExport.patch]) and what is needed to list its profiles. */
class RawAccelFile internal constructor(val root: JsonObject) {
    val version: String = (root[RawAccelKeys.VERSION] as? JsonPrimitive)?.content ?: ""

    /** The `defaultDeviceConfig` DPI, an int in Raw Accel (`wrapper.cpp:271`). 0 means the driver does not normalize. */
    val deviceDpi: Int = deviceConfig(root).let { RawAccelImport.number(it, RawAccelKeys.DEVICE_DPI, "defaultDeviceConfig").toInt() }

    val profileNames: List<String> = profiles(root).map { it.string(RawAccelKeys.NAME) }

    internal fun profileObject(name: String): JsonObject =
        profiles(root).firstOrNull { it.string(RawAccelKeys.NAME) == name }
            ?: throw RawAccelImportException("no profile named '$name' (the file has: ${profileNames.joinToString { "'$it'" }})")

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw RawAccelImportException("a profile has no '$key' text")

    companion object {
        internal fun deviceConfig(root: JsonObject): JsonObject =
            root[RawAccelKeys.DEVICE_CONFIG] as? JsonObject ?: throw RawAccelImportException("'defaultDeviceConfig' is missing")

        internal fun profiles(root: JsonObject): List<JsonObject> {
            val array = root[RawAccelKeys.PROFILES] as? JsonArray ?: throw RawAccelImportException("'profiles' is missing or not a list")
            return array.map { it as? JsonObject ?: throw RawAccelImportException("a profile is not an object") }
        }
    }
}

/** What importing one profile gave: the profile the trainer can reproduce, or the reasons it cannot. [warnings] are things to tell the user in both cases. */
sealed interface RawAccelImportResult {
    val warnings: List<String>

    data class Imported(val profile: RawAccelProfile, override val warnings: List<String>) : RawAccelImportResult

    data class Refused(val reasons: List<String>, override val warnings: List<String>) : RawAccelImportResult
}

/**
 * Reads a Raw Accel 1.7.1 `settings.json` without loss: [parse] keeps the whole JSON tree, [profile] turns one profile into the
 * typed [RawAccelProfile] the trainer simulates. A profile is only imported when the trainer reproduces it exactly: the speed is
 * the magnitude of the whole input (`rawaccel.hpp:381`) and the factor is the accel function times output DPI / device DPI, so
 * everything that changes either (by-component mode, smoothing, speed cap, stretches, rotation, snapping, direction and Y/X
 * ratios, other Lp norms, other modes) is a refusal, not a silent approximation. The DPI, polling and device options that only
 * change how well the trainer matches the real driver are warnings.
 */
object RawAccelImport {
    /** @throws RawAccelImportException if [text] is not JSON or lacks what Raw Accel itself requires at the top level. */
    fun parse(text: String): RawAccelFile {
        val root = try {
            Json.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw RawAccelImportException("the file is not valid JSON: ${e.message?.lineSequence()?.firstOrNull()}", e)
        }
        if (root !is JsonObject) throw RawAccelImportException("the file is not a Raw Accel settings object")
        return RawAccelFile(root)
    }

    /**
     * @param expectedDpi the DPI of AimTune's settings; a different DPI in the file is a warning because the speed axis of the
     *   curve belongs to the file's DPI.
     * @throws RawAccelImportException if the profile does not exist or a property Raw Accel requires is missing or of the wrong type.
     */
    fun profile(file: RawAccelFile, name: String, expectedDpi: Double? = null): RawAccelImportResult {
        val profile = file.profileObject(name)
        val warnings = warnings(file, expectedDpi)
        val reasons = refusals(profile, name)
        if (reasons.isNotEmpty()) return RawAccelImportResult.Refused(reasons, warnings)
        val args = try {
            accelArgs(profile.obj(RawAccelKeys.WHOLE_ARGS, name), name)
        } catch (e: IllegalArgumentException) {
            if (e is RawAccelImportException) throw e
            throw RawAccelImportException("profile '$name' has values Raw Accel itself would refuse: ${e.message}", e)
        }
        val outputDpi = number(profile, RawAccelKeys.OUTPUT_DPI, name)
        val typed = try {
            RawAccelProfile(name, args, outputDpi, file.deviceDpi.toDouble())
        } catch (e: IllegalArgumentException) {
            throw RawAccelImportException("profile '$name': ${e.message}", e)
        }
        return RawAccelImportResult.Imported(typed, warnings)
    }

    /** The typed accel parameters of one `accel_args` block; used by [profile] and to check a patched profile. */
    internal fun accelArgs(block: JsonObject, profileName: String): RawAccelArgs {
        val mode = RawAccelMode.entries.firstOrNull { it.json == text(block, RawAccelKeys.MODE, profileName) }
            ?: throw RawAccelImportException("profile '$profileName': mode '${text(block, RawAccelKeys.MODE, profileName)}' is not supported")
        val capMode = RawAccelCapMode.entries.firstOrNull { it.json == text(block, RawAccelKeys.CAP_MODE, profileName) }
            ?: throw RawAccelImportException("profile '$profileName': unknown cap mode '${text(block, RawAccelKeys.CAP_MODE, profileName)}'")
        val cap = block.obj(RawAccelKeys.CAP, profileName)
        val data = (block[RawAccelKeys.DATA] as? JsonArray ?: throw RawAccelImportException("profile '$profileName': 'data' is missing"))
            .map { element ->
                (element as? JsonPrimitive)?.content?.toFloatOrNull() ?: throw RawAccelImportException("profile '$profileName': 'data' holds a non-number")
            }
        return RawAccelArgs(
            mode = mode,
            gain = (block[RawAccelKeys.GAIN] as? JsonPrimitive)?.booleanOrNull
                ?: throw RawAccelImportException("profile '$profileName': '${RawAccelKeys.GAIN}' is missing or not true/false"),
            acceleration = number(block, RawAccelKeys.ACCELERATION, profileName),
            exponentClassic = number(block, RawAccelKeys.EXPONENT_CLASSIC, profileName),
            inputOffset = number(block, RawAccelKeys.INPUT_OFFSET, profileName),
            decayRate = number(block, RawAccelKeys.DECAY_RATE, profileName),
            limit = number(block, RawAccelKeys.LIMIT, profileName),
            capX = number(cap, "x", profileName),
            capY = number(cap, "y", profileName),
            capMode = capMode,
            lut = data,
        )
    }

    private fun warnings(file: RawAccelFile, expectedDpi: Double?): List<String> = buildList {
        if (file.version != "1.7.1") add("the file is version '${file.version}', AimTune reproduces Raw Accel 1.7.1")
        val dpi = file.deviceDpi
        if (dpi == 0) add("the device DPI is 0: Raw Accel does not normalize the speed then, which AimTune treats as 1000 DPI")
        if (expectedDpi != null && dpi != 0 && abs(dpi - expectedDpi) > 0.5) {
            add("the file's device DPI is $dpi but AimTune is set to ${expectedDpi.toBigDecimal().stripTrailingZeros().toPlainString()}: the speed axis of the curve belongs to $dpi")
        }
        val device = RawAccelFile.deviceConfig(file.root)
        if ((device[RawAccelKeys.POLLING_RATE] as? JsonPrimitive)?.doubleOrNull.let { it != null && it > 0 }) {
            add("a polling rate is set: Raw Accel then uses it as the minimum time between reports, AimTune uses the measured time")
        }
        if ((device[RawAccelKeys.POLL_TIME_LOCK] as? JsonPrimitive)?.booleanOrNull == true) {
            add("'constant time interval based on polling rate' is on: AimTune uses the measured time between reports")
        }
        if (device.containsKey("minimumTime") || device.containsKey("maximumTime")) {
            add("custom time limits are set: AimTune uses Raw Accel's default limits (0.0625 to 100 ms)")
        }
        if ((device["disable"] as? JsonPrimitive)?.booleanOrNull == true) add("the default device config is disabled")
        val devices = (file.root[RawAccelKeys.DEVICES] as? JsonArray)?.size ?: 0
        if (devices > 0) add("the file lists $devices device(s) with their own settings; AimTune only reads the default device config")
    }

    /** Everything in [profile] that makes the trainer's simulation differ from the driver; empty when it matches. */
    private fun refusals(profile: JsonObject, name: String): List<String> = buildList {
        val speed = profile.obj(RawAccelKeys.SPEED_ARGS, name)
        if ((speed[RawAccelKeys.WHOLE_FLAG] as? JsonPrimitive)?.booleanOrNull != true) {
            add("by-component mode (Whole/combined accel is off): the trainer only simulates the speed of the whole movement")
        }
        if (number(speed, RawAccelKeys.LP_NORM, name) != 2.0) add("the Lp norm is not 2 (other distance than the euclidean speed)")
        listOf(RawAccelKeys.INPUT_HALFLIFE to "input", RawAccelKeys.SCALE_HALFLIFE to "scale", RawAccelKeys.OUTPUT_HALFLIFE to "output").forEach { (key, what) ->
            if (number(speed, key, name) != 0.0) add("$what smoothing half-life is not 0 (smoothing needs the driver's state)")
        }
        if (number(profile, RawAccelKeys.SPEED_CAP, name) != 0.0) add("Input Speed Cap is not 0")
        for (key in listOf(RawAccelKeys.DOMAIN, RawAccelKeys.RANGE)) {
            val stretch = profile.obj(key, name)
            if (number(stretch, "x", name) != 1.0 || number(stretch, "y", name) != 1.0) add("'$key' is not 1 / 1")
        }
        if (number(profile, RawAccelKeys.ROTATION, name) != 0.0) add("Degrees of rotation is not 0")
        if (number(profile, RawAccelKeys.SNAP, name) != 0.0) add("Degrees of angle snapping is not 0")
        for ((key, label) in listOf(RawAccelKeys.LR_RATIO to "L/R", RawAccelKeys.UD_RATIO to "U/D", RawAccelKeys.YX_RATIO to "Y/X")) {
            if (number(profile, key, name) != 1.0) add("the $label output DPI ratio is not 1")
        }
        val whole = profile.obj(RawAccelKeys.WHOLE_ARGS, name)
        val vertical = profile.obj(RawAccelKeys.VERTICAL_ARGS, name)
        if (!sameJson(whole, vertical)) add("the vertical accel parameters differ from the horizontal ones")
        when (val mode = text(whole, RawAccelKeys.MODE, name)) {
            RawAccelMode.CLASSIC.json, RawAccelMode.NATURAL.json, RawAccelMode.LUT.json, RawAccelMode.NOACCEL.json -> Unit
            else -> add("acceleration mode '$mode' is not supported (classic, natural, lut and noaccel are)")
        }
    }

    /** JSON equality that reads numbers by value, so 2 and 2.0 are the same. */
    internal fun sameJson(a: JsonElement?, b: JsonElement?): Boolean = when {
        a is JsonObject && b is JsonObject -> a.keys == b.keys && a.all { (key, value) -> sameJson(value, b[key]) }
        a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { sameJson(a[it], b[it]) }
        a is JsonPrimitive && b is JsonPrimitive && !a.isString && !b.isString -> {
            val x = a.doubleOrNull
            val y = b.doubleOrNull
            if (x != null && y != null) x == y else a == b
        }
        else -> a == b
    }

    internal fun number(obj: JsonObject, key: String, context: String): Double =
        (obj[key] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
            ?: throw RawAccelImportException("'$context': '$key' is missing or not a number")

    private fun text(obj: JsonObject, key: String, profileName: String): String =
        (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw RawAccelImportException("profile '$profileName': '$key' is missing or not text")

    private fun JsonObject.obj(key: String, profileName: String): JsonObject =
        this[key] as? JsonObject ?: throw RawAccelImportException("profile '$profileName': '$key' is missing")
}
