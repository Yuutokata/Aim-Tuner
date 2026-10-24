package de.yuuto.aimtune.storage

import de.yuuto.aimtune.core.accel.NoAccel
import de.yuuto.aimtune.core.accel.RawAccelArgs
import de.yuuto.aimtune.core.accel.RawAccelCapMode
import de.yuuto.aimtune.core.accel.RawAccelMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RawAccelPatchTest {
    @TempDir
    lateinit var dir: Path

    private val userText = checkNotNull(javaClass.getResource("/rawaccel/user-classic-gain.json")) { "fixture missing" }.readText()
    private val original = Json.parseToJsonElement(userText).jsonObject
    private val userArgs = RawAccelArgs(RawAccelMode.CLASSIC, gain = true, inputOffset = 10.0, capX = 62.5, capY = 1.6, capMode = RawAccelCapMode.IN_OUT)

    private fun profiles(root: JsonObject) = (root.getValue("profiles") as JsonArray).map { it.jsonObject }

    private fun reimport(root: JsonObject, name: String): RawAccelImportResult.Imported =
        assertIs(RawAccelImport.profile(RawAccelImport.parse(Json.encodeToString(JsonElement.serializer(), root)), name))

    @Test
    fun `a patch without changes is the original plus a copy under the new name`() {
        val patched = RawAccelExport.patch(original, "default", userArgs, null, "AimTune test")
        assertEquals(original.keys.toList(), patched.keys.toList(), "top-level key order")
        for (key in original.keys - "profiles") assertEquals(original.getValue(key), patched.getValue(key), key)

        val (first, copy) = profiles(patched).also { assertEquals(2, it.size) }
        assertEquals(profiles(original).single(), first)
        assertEquals(first.keys.toList(), copy.keys.toList())
        for (key in first.keys - "name") assertEquals(first.getValue(key), copy.getValue(key), key)
        assertEquals("AimTune test", copy.getValue("name").jsonPrimitive.content)
        // unchanged numbers keep their original text
        assertEquals("2.0", copy.getValue("Whole or horizontal accel parameters").jsonObject.getValue("exponentClassic").jsonPrimitive.content)
    }

    @Test
    fun `changed fields land in both parameter blocks and the profile parses back to the new arguments`() {
        val changed = RawAccelArgs(RawAccelMode.NATURAL, gain = false, decayRate = 0.25, limit = 1.8, inputOffset = 6.0, capMode = RawAccelCapMode.INPUT)
        val patched = RawAccelExport.patch(original, "default", changed, newOutputDpi = 560.0, newName = "AimTune natural")
        val copy = profiles(patched).last()
        assertEquals(copy.getValue("Whole or horizontal accel parameters"), copy.getValue("Vertical accel parameters"))
        assertEquals(560.0, copy.getValue("Output DPI").jsonPrimitive.double)

        val back = reimport(patched, "AimTune natural")
        assertEquals(changed, back.profile.args)
        assertEquals(560.0, back.profile.outputDpi)
        assertEquals(800.0, back.profile.deviceDpi)
        // the profile it was copied from is still there and unchanged
        assertEquals(userArgs, reimport(patched, "default").profile.args)
        assertEquals(600.0, reimport(patched, "default").profile.outputDpi)
    }

    @Test
    fun `parameters the typed arguments do not hold and the comment keys stay as they were`() {
        val patched = RawAccelExport.patch(original, "default", userArgs.copy(inputOffset = 12.0), null, "copy")
        val copy = profiles(patched).last().getValue("Whole or horizontal accel parameters").jsonObject
        val old = profiles(original).single().getValue("Whole or horizontal accel parameters").jsonObject
        assertEquals(old.keys.toList(), copy.keys.toList())
        for (key in old.keys - "inputOffset") assertEquals(old.getValue(key), copy.getValue(key), key)
        assertEquals(12.0, copy.getValue("inputOffset").jsonPrimitive.double)
        assertTrue("### Accel modes ###" in patched.keys && "### Cap modes ###" in patched.keys)
    }

    @Test
    fun `a lut table is written as floats and comes back`() {
        val lut = RawAccelArgs(RawAccelMode.LUT, gain = false, lut = listOf(2f, 0.9f, 5f, 1.1f, 20f, 1.5f))
        val patched = RawAccelExport.patch(original, "default", lut, null, "lut copy")
        assertEquals(lut.lut, reimport(patched, "lut copy").profile.args.lut)
    }

    @Test
    fun `a bad new name or an unknown profile is refused`() {
        assertFailsWith<IllegalArgumentException> { RawAccelExport.patch(original, "default", userArgs, null, "default") }
        assertFailsWith<IllegalArgumentException> { RawAccelExport.patch(original, "default", userArgs, null, " ") }
        assertFailsWith<IllegalArgumentException> { RawAccelExport.patch(original, "default", userArgs, -1.0, "x") }
        assertFailsWith<RawAccelImportException> { RawAccelExport.patch(original, "nope", userArgs, null, "x") }
    }

    @Test
    fun `the patched file is written under a name that does not collide`() {
        val patched = RawAccelExport.patch(original, "default", userArgs, null, "AimTune v1")
        val folder = dir.resolve("exports")
        val first = RawAccelExport.write(folder, patched, "AimTune v1")
        val second = RawAccelExport.write(folder, patched, "AimTune v1")
        assertEquals("settings-AimTune_v1.json", first.fileName.toString())
        assertEquals("settings-AimTune_v1 (2).json", second.fileName.toString())
        assertNotEquals(first, second)
        val text = Files.readString(first)
        assertTrue(text.endsWith("}\n") && "\"### Accel modes ###\"" in text)
        assertEquals(patched, Json.parseToJsonElement(text))
    }

    @Test
    fun `the device dpi is an integer in the settings file`() {
        val root = Json.parseToJsonElement(RawAccelExport.settingsJson(NoAccel, 800.4, 0.5)).jsonObject
        val device = root.getValue("defaultDeviceConfig").jsonObject.getValue("DPI (normalizes input speed unit: counts/ms -> in/s)").jsonPrimitive
        assertEquals("800", device.content)
        assertEquals(400.0, profiles(root).single().getValue("Output DPI").jsonPrimitive.double, 1e-9)
    }
}
