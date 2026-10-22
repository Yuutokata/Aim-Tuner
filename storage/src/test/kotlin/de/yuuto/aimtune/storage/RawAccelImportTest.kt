package de.yuuto.aimtune.storage

import de.yuuto.aimtune.core.accel.LookupCurve
import de.yuuto.aimtune.core.accel.RawAccelArgs
import de.yuuto.aimtune.core.accel.RawAccelCapMode
import de.yuuto.aimtune.core.accel.RawAccelMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RawAccelImportTest {
    private val userText = checkNotNull(javaClass.getResource("/rawaccel/user-classic-gain.json")) { "fixture missing" }.readText()
    private val userRoot = Json.parseToJsonElement(userText).jsonObject

    /** [userRoot] with the value at [path] inside its (only) profile replaced. */
    private fun edited(vararg path: String, value: JsonElement): RawAccelFile {
        return RawAccelImport.parse(Json.encodeToString(JsonElement.serializer(), set(userRoot, listOf("profiles", "0") + path, value)))
    }

    /** Changes [key] in the horizontal and the vertical block, so the difference between them is not a refusal of its own. */
    private fun editedBoth(key: String, value: JsonElement): RawAccelFile {
        val once = set(userRoot, listOf("profiles", "0", "Whole or horizontal accel parameters", key), value)
        return RawAccelImport.parse(Json.encodeToString(JsonElement.serializer(), set(once, listOf("profiles", "0", "Vertical accel parameters", key), value)))
    }

    private fun set(node: JsonElement, path: List<String>, value: JsonElement): JsonElement {
        if (path.isEmpty()) return value
        val key = path.first()
        return when (node) {
            is JsonArray -> JsonArray(node.mapIndexed { i, e -> if (i == key.toInt()) set(e, path.drop(1), value) else e })
            else -> JsonObject(LinkedHashMap(node.jsonObject).also { it[key] = set(node.jsonObject.getValue(key), path.drop(1), value) })
        }
    }

    private fun refusals(file: RawAccelFile) = assertIs<RawAccelImportResult.Refused>(RawAccelImport.profile(file, "default")).reasons

    @Test
    fun `the user's file imports as the classic gain profile with its output dpi and device dpi`() {
        val file = RawAccelImport.parse(userText)
        assertEquals(listOf("default"), file.profileNames)
        assertEquals(800, file.deviceDpi)
        val result = assertIs<RawAccelImportResult.Imported>(RawAccelImport.profile(file, "default", expectedDpi = 800.0))
        assertTrue(result.warnings.isEmpty(), "${result.warnings}")
        val profile = result.profile
        assertEquals(
            RawAccelArgs(RawAccelMode.CLASSIC, gain = true, inputOffset = 10.0, capX = 62.5, capY = 1.6, capMode = RawAccelCapMode.IN_OUT),
            profile.args,
        )
        assertEquals(600.0, profile.outputDpi)
        assertEquals(800.0, profile.deviceDpi)
        assertEquals(1.3825 * 0.75, profile.totalMultiplier(100.0), 1e-4)
    }

    @Test
    fun `a different dpi in settings is a warning and the polling options are warnings`() {
        val file = RawAccelImport.parse(userText)
        val warnings = RawAccelImport.profile(file, "default", expectedDpi = 1600.0).warnings
        assertEquals(1, warnings.size)
        assertTrue("800" in warnings.single() && "1600" in warnings.single(), warnings.single())

        val device = userRoot.getValue("defaultDeviceConfig").jsonObject
        val withPolling = JsonObject(
            LinkedHashMap(userRoot).also {
                it["defaultDeviceConfig"] = JsonObject(
                    LinkedHashMap(device).also { d ->
                        d["Polling rate Hz (keep at 0 for automatic adjustment)"] = JsonPrimitive(1000)
                        d["Use constant time interval based on polling rate"] = JsonPrimitive(true)
                    },
                )
                it["devices"] = JsonArray(listOf(JsonObject(mapOf("name" to JsonPrimitive("Mouse")))))
            },
        )
        val polled = RawAccelImport.profile(RawAccelImport.parse(Json.encodeToString(JsonElement.serializer(), withPolling)), "default")
        assertIs<RawAccelImportResult.Imported>(polled)
        assertEquals(3, polled.warnings.size, "${polled.warnings}")
    }

    @Test
    fun `everything the trainer cannot reproduce is a refusal with its reason`() {
        val whole = "Input speed calculation parameters"
        val cases = listOf(
            listOf(whole, "Whole/combined accel (set false for 'by component' mode)") to (JsonPrimitive(false) to "by-component"),
            listOf(whole, "lpNorm") to (JsonPrimitive(3.0) to "Lp norm"),
            listOf(whole, "Time in ms after which an input is weighted at half its original value.") to (JsonPrimitive(2.0) to "input smoothing"),
            listOf(whole, "Time in ms after which scale is weighted at half its original value.") to (JsonPrimitive(2.0) to "scale smoothing"),
            listOf(whole, "Time in ms after which an output is weighted at half its original value.") to (JsonPrimitive(2.0) to "output smoothing"),
            listOf("Input Speed Cap") to (JsonPrimitive(120.0) to "Input Speed Cap"),
            listOf("Stretches domain for horizontal vs vertical inputs", "x") to (JsonPrimitive(1.5) to "Stretches domain"),
            listOf("Stretches accel range for horizontal vs vertical inputs", "y") to (JsonPrimitive(0.5) to "Stretches accel range"),
            listOf("Degrees of rotation") to (JsonPrimitive(5.0) to "rotation"),
            listOf("Degrees of angle snapping") to (JsonPrimitive(3.0) to "snapping"),
            listOf("Y/X output DPI ratio (vertical sens multiplier)") to (JsonPrimitive(1.2) to "Y/X"),
            listOf("L/R output DPI ratio (left sens multiplier)") to (JsonPrimitive(0.9) to "L/R"),
            listOf("U/D output DPI ratio (up sens multiplier)") to (JsonPrimitive(0.9) to "U/D"),
            listOf("Vertical accel parameters", "acceleration") to (JsonPrimitive(0.01) to "vertical"),
        )
        for ((path, change) in cases) {
            val (value, expected) = change
            val reasons = refusals(edited(*path.toTypedArray(), value = value))
            assertEquals(1, reasons.size, "$path: $reasons")
            assertTrue(expected in reasons.single(), "$path: ${reasons.single()}")
        }
        for (mode in listOf("jump", "synchronous", "power")) {
            val file = edited("Whole or horizontal accel parameters", "mode", value = JsonPrimitive(mode))
            assertTrue(refusals(file).any { "'$mode'" in it })
        }
    }

    @Test
    fun `several reasons are all reported`() {
        val file = edited("Degrees of rotation", value = JsonPrimitive(5.0)).let {
            RawAccelImport.parse(
                Json.encodeToString(
                    JsonElement.serializer(),
                    set(it.root, listOf("profiles", "0", "Input Speed Cap"), JsonPrimitive(100.0)),
                ),
            )
        }
        assertEquals(2, refusals(file).size)
    }

    @Test
    fun `malformed files and profiles fail with a clear message`() {
        assertFailsWith<RawAccelImportException> { RawAccelImport.parse("{ not json") }.also { assertTrue("not valid JSON" in it.message!!) }
        assertFailsWith<RawAccelImportException> { RawAccelImport.parse("[1, 2]") }
        assertFailsWith<RawAccelImportException> { RawAccelImport.parse("""{"version": "1.7.1"}""") }
        val file = RawAccelImport.parse(userText)
        assertFailsWith<RawAccelImportException> { RawAccelImport.profile(file, "missing") }.also { assertTrue("'default'" in it.message!!) }
        assertFailsWith<RawAccelImportException> { RawAccelImport.profile(edited("Output DPI", value = JsonPrimitive("fast")), "default") }
        assertFailsWith<RawAccelImportException> {
            RawAccelImport.profile(editedBoth("Cap mode", JsonPrimitive("sideways")), "default")
        }
        // values Raw Accel's own validation rejects
        val both = editedBoth("acceleration", JsonPrimitive(0.0))
        assertFailsWith<RawAccelImportException> { RawAccelImport.profile(both, "default") }.also { assertTrue("acceleration" in it.message!!) }
    }

    @Test
    fun `a lut profile imports its table as floats`() {
        val text = RawAccelExport.settingsJson(LookupCurve(listOf(2.0, 4.5, 9.0), listOf(0.9, 1.1, 1.3)), 800.0, 0.9)
        val result = assertIs<RawAccelImportResult.Imported>(RawAccelImport.profile(RawAccelImport.parse(text), RawAccelExport.PROFILE_NAME, 800.0))
        assertEquals(RawAccelMode.LUT, result.profile.args.mode)
        assertEquals(listOf(2f, 0.9f, 4.5f, 1.1f, 9f, 1.3f), result.profile.args.lut)
        assertEquals(0.9f.toDouble() * 0.9, result.profile.totalMultiplier(2.0), 1e-9) // table y at 2 in/s times 720 / 800
    }
}
