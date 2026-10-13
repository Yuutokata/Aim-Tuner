package de.yuuto.aimtune.storage

import de.yuuto.aimtune.core.accel.AccelCurve
import de.yuuto.aimtune.core.accel.ClassicCurve
import de.yuuto.aimtune.core.accel.NaturalCurve
import de.yuuto.aimtune.core.accel.NoAccel
import de.yuuto.aimtune.core.accel.PowerCurve
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RawAccelExportTest {
    @TempDir
    lateinit var dir: Path

    private val curves = listOf(
        NoAccel,
        ClassicCurve(0.0831, exponent = 2.3, inputOffset = 1.5, cap = 1.8),
        ClassicCurve(0.1, cap = 0.6),
        NaturalCurve(0.12, 1.4, inputOffset = 2.0),
        NaturalCurve(0.1, 0.7),
        PowerCurve(0.0421, 0.35, outputOffset = 1.0, cap = 1.6),
        PowerCurve(0.05, 0.4),
    )

    private fun root(curve: AccelCurve, factor: Double = 1.0): JsonObject =
        Json.parseToJsonElement(RawAccelExport.settingsJson(curve, 800.0, factor)).jsonObject

    private fun profile(root: JsonObject) = (root.getValue("profiles") as JsonArray).first().jsonObject

    /** The properties `wrapper.cpp` marks as required (ItemRequired = Always) on `Profile`, and what `AccelArgs` needs to be valid. */
    private val requiredProfileKeys = listOf(
        "name", "Stretches domain for horizontal vs vertical inputs", "Stretches accel range for horizontal vs vertical inputs",
        "Whole or horizontal accel parameters", "Vertical accel parameters", "Input speed calculation parameters", "Output DPI",
        "Y/X output DPI ratio (vertical sens multiplier)", "L/R output DPI ratio (left sens multiplier)", "U/D output DPI ratio (up sens multiplier)",
        "Degrees of rotation", "Degrees of angle snapping", "Input Speed Cap",
    )
    private val accelKeys = listOf(
        "mode", "Gain / Velocity", "inputOffset", "outputOffset", "acceleration", "decayRate", "gamma", "motivity", "exponentClassic", "scale",
        "exponentPower", "limit", "syncSpeed", "smooth", "Cap / Jump", "Cap mode", "data",
    )

    @Test
    fun `the file has every property raw accel requires and the top level layout of its driver config`() {
        val root = root(ClassicCurve(0.05))
        assertEquals(setOf("version", "defaultDeviceConfig", "profiles", "devices"), root.keys)
        assertEquals("1.7.1", root.getValue("version").jsonPrimitive.content)
        val device = root.getValue("defaultDeviceConfig").jsonObject
        assertEquals(800.0, device.getValue("DPI (normalizes input speed unit: counts/ms -> in/s)").jsonPrimitive.double)
        assertTrue(device.containsKey("disable") && device.containsKey("Polling rate Hz (keep at 0 for automatic adjustment)"))
        val profile = profile(root)
        assertTrue(profile.keys.containsAll(requiredProfileKeys), "missing ${requiredProfileKeys - profile.keys}")
        assertEquals(requiredProfileKeys.toSet(), profile.keys, "no unknown properties")
        for (name in listOf("Whole or horizontal accel parameters", "Vertical accel parameters")) {
            assertEquals(accelKeys.toSet(), profile.getValue(name).jsonObject.keys, name)
        }
    }

    @Test
    fun `every value passes raw accel's own validation, also the parameters of unused modes`() {
        for (curve in curves) {
            val args = profile(root(curve)).getValue("Whole or horizontal accel parameters").jsonObject
            fun n(name: String) = args.getValue(name).jsonPrimitive.double
            val cap = args.getValue("Cap / Jump").jsonObject
            val capX = cap.getValue("x").jsonPrimitive.double
            val capY = cap.getValue("y").jsonPrimitive.double
            val mode = args.getValue("mode").jsonPrimitive.content
            // The checks of common/rawaccel-validate.hpp, applied to every accel_args.
            assertTrue(n("inputOffset") >= 0 && n("outputOffset") >= 0, "$curve offsets")
            assertTrue(capX >= 0 && capY >= 0, "$curve caps")
            assertTrue(!(mode == "jump" && (capX == 0.0 || capY == 0.0)), "$curve jump caps")
            assertTrue(n("acceleration") > 0 && n("scale") > 0 && n("gamma") > 0 && n("decayRate") > 0, "$curve positive parameters")
            assertTrue(n("motivity") > 1 && n("exponentClassic") > 1 && n("exponentPower") > 0 && n("limit") > 0 && n("syncSpeed") > 0, "$curve ranges")
            assertTrue(n("smooth") in 0.0..1.0, "$curve smooth")
            assertTrue(mode != "power" || capY == 0.0 || capY >= n("outputOffset"), "$curve cap below offset")
            assertEquals("output", args.getValue("Cap mode").jsonPrimitive.content)
            assertEquals(false, args.getValue("Gain / Velocity").jsonPrimitive.content.toBoolean())
            assertEquals(0, (args.getValue("data") as JsonArray).size)
        }
    }

    @Test
    fun `the curve and the factor come back out of the file`() {
        for (curve in curves) {
            val (back, factor) = RawAccelExport.parse(RawAccelExport.settingsJson(curve, 800.0, 0.94))
            assertEquals(curve, back)
            assertEquals(0.94, factor, 1e-12)
        }
    }

    @Test
    fun `the factor goes into the output dpi so the in-game sensitivity is left alone`() {
        assertEquals(800.0, profile(root(NoAccel)).getValue("Output DPI").jsonPrimitive.double, 1e-9)
        assertEquals(752.0, profile(root(NoAccel, 0.94)).getValue("Output DPI").jsonPrimitive.double, 1e-9)
    }

    @Test
    fun `the file is written as settings json in a new folder`() {
        val file = RawAccelExport.write(dir.resolve("exports").resolve("rawaccel"), NaturalCurve(0.1, 1.5), 1600.0, 1.0)
        assertEquals("settings.json", file.fileName.toString())
        assertTrue(Files.readString(file).contains("\"natural\""))
        assertTrue(Files.readString(file).endsWith("}\n"))
    }
}
