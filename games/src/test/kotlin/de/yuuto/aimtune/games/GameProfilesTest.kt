package de.yuuto.aimtune.games

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameProfilesTest {
    @TempDir
    lateinit var dir: Path

    private val valid = """
        {"id":"mygame","name":"My Game","yaw":0.05,
         "fov":{"degrees":100.0,"type":"horizontal","aspect":{"width":16,"height":9},"scaling":"hor_plus"},
         "sensitivity":{"decimals":2}}
    """.trimIndent()

    private val bundled = GameProfiles.load()

    @Test
    fun `bundled profiles load`() {
        assertEquals(listOf("valorant", "cs2"), bundled.profiles.map { it.id })
        assertTrue(bundled.problems.isEmpty())
    }

    @Test
    fun `valorant example 800 dpi sens 0_249 is about 65_6 cm per 360 and converts back`() {
        val valorant = assertNotNull(bundled.find("valorant"))
        val cm = valorant.cm360(dpi = 800.0, sensitivity = 0.249)
        assertEquals(65.6, cm, 0.05)
        assertEquals(0.249, valorant.roundSensitivity(valorant.sensitivityFor(800.0, cm)))
    }

    @Test
    fun `cs2 example from the design mockup is 43_3 cm per 360`() {
        val cs2 = assertNotNull(bundled.find("cs2"))
        assertEquals(43.3, cs2.cm360(dpi = 800.0, sensitivity = 1.2), 0.05)
        assertEquals(1.24, cs2.roundSensitivity(cs2.sensitivityFor(800.0, 41.9)), 0.005)
    }

    @Test
    fun `vertical fov follows hor plus scaling`() {
        assertEquals(70.53, assertNotNull(bundled.find("valorant")).verticalFovDegrees, 0.05)
        assertEquals(73.74, assertNotNull(bundled.find("cs2")).verticalFovDegrees, 0.05)
    }

    @Test
    fun `sensitivity is rounded and formatted with the profile decimals`() {
        val valorant = assertNotNull(bundled.find("valorant"))
        assertEquals(0.249, valorant.roundSensitivity(0.2494999))
        assertEquals("0.400", valorant.formatSensitivity(0.4))
        assertEquals("1.235", valorant.formatSensitivity(1.2345))
    }

    @Test
    fun `custom profile uses the given yaw and a horizontal fov at 16 9`() {
        val custom = GameProfile.custom(yawDegreesPerCount = 0.01, horizontalFovDegrees = 100.0)
        assertEquals(GameProfile.CUSTOM_ID, custom.id)
        assertEquals(800.0 * 1.0 * 0.01, 800.0 * custom.sensitivityFor(800.0, custom.cm360(800.0, 1.0)) * 0.01, 1e-9)
        assertEquals(67.67, custom.verticalFovDegrees, 0.05)
    }

    @Test
    fun `user profiles are added and can replace bundled ones`() {
        Files.writeString(dir.resolve("mygame.json"), valid)
        Files.writeString(dir.resolve("valorant.json"), valid.replace("mygame", "valorant").replace("0.05", "0.06"))
        val loaded = GameProfiles.load(dir)
        assertEquals(listOf("valorant", "cs2", "mygame"), loaded.profiles.map { it.id })
        assertEquals(0.06, assertNotNull(loaded.find("valorant")).yawDegreesPerCount)
        assertNull(loaded.find("nope"))
    }

    @Test
    fun `broken user files are reported and skipped`() {
        Files.writeString(dir.resolve("broken.json"), "{ nope")
        Files.writeString(dir.resolve("badyaw.json"), valid.replace("0.05", "-1"))
        Files.writeString(dir.resolve("badscaling.json"), valid.replace("hor_plus", "vert_minus"))
        Files.writeString(dir.resolve("readme.txt"), "ignored")
        val loaded = GameProfiles.load(dir)
        assertEquals(listOf("valorant", "cs2"), loaded.profiles.map { it.id })
        assertEquals(3, loaded.problems.size)
        assertTrue(loaded.problems.any { it.startsWith("badscaling.json") && "vert_minus" in it })
    }

    @Test
    fun `invalid profiles are rejected with a clear error`() {
        assertFailsWith<GameProfileException> { parseGameProfile(valid.replace("\"id\":\"mygame\"", "\"id\":\"Bad Id\"")) }
        assertFailsWith<GameProfileException> { parseGameProfile(valid.replace("horizontal", "diagonal")) }
        assertFailsWith<GameProfileException> { parseGameProfile(valid.replace("\"decimals\":2", "\"decimals\":20")) }
        assertFailsWith<GameProfileException> { parseGameProfile(valid.replace("\"width\":16", "\"width\":0")) }
        assertFailsWith<GameProfileException> { parseGameProfile("{}") }
    }

    @Test
    fun `trainer geometry is read from the profile and falls back to the defaults`() {
        val valorant = assertNotNull(bundled.find("valorant"))
        assertEquals(0.4, valorant.geometry.clickTargetDiameterDegrees.min)
        assertEquals(1.9, valorant.geometry.clickTargetDiameterDegrees.max)
        assertEquals(false, valorant.geometry.verified)
        val plain = parseGameProfile(valid)
        assertEquals(de.yuuto.aimtune.core.scenario.TrainerGeometry.DEFAULT.copy(flash = plain.geometry.flash), plain.geometry)
        val custom = parseGameProfile(valid.replace("\"sensitivity\"", "\"trainer\":{\"clickTargetDiameterDegrees\":{\"min\":1.0,\"max\":2.0},\"verified\":true},\"sensitivity\""))
        assertEquals(1.0, custom.geometry.clickTargetDiameterDegrees.min)
        assertEquals(true, custom.geometry.verified)
        assertEquals(de.yuuto.aimtune.core.scenario.TrainerGeometry.DEFAULT.flickAngles, custom.geometry.flickAngles)
        assertFailsWith<GameProfileException> {
            parseGameProfile(valid.replace("\"sensitivity\"", "\"trainer\":{\"clickTargetDiameterDegrees\":{\"min\":3.0,\"max\":2.0}},\"sensitivity\""))
        }
    }

    @Test
    fun `the vertical distribution comes from the profile and can be switched back to the old uniform field`() {
        val valorant = assertNotNull(bundled.find("valorant"))
        assertEquals(1.5, valorant.geometry.elevation.spreadDegrees)
        assertEquals(0.12, valorant.geometry.elevation.offLevelShare)
        assertEquals(false, valorant.geometry.elevation.uniform)
        assertEquals(1.6, valorant.geometry.eyeHeightMeters)
        assertEquals(false, parseGameProfile(valid).geometry.elevation.uniform)
        val uniform = parseGameProfile(valid.replace("\"sensitivity\"", "\"trainer\":{\"elevation\":{\"uniform\":true},\"headHeightMeters\":1.7},\"sensitivity\""))
        assertEquals(true, uniform.geometry.elevation.uniform)
        assertEquals(1.7, uniform.geometry.headHeightMeters)
        assertFailsWith<GameProfileException> {
            parseGameProfile(valid.replace("\"sensitivity\"", "\"trainer\":{\"elevation\":{\"offLevelShare\":2.0}},\"sensitivity\""))
        }
    }

    @Test
    fun `the flash geometry follows the profile's field of view and can be overridden`() {
        val valorant = assertNotNull(bundled.find("valorant"))
        assertEquals(51.5, valorant.geometry.flash.halfFovDegrees, 0.1)
        assertEquals(61.5, valorant.geometry.flash.safeAngleDegrees, 0.1)
        assertEquals(0.5, valorant.geometry.flash.popDelaySeconds.min)
        assertEquals(false, valorant.geometry.flash.verified)
        assertEquals(50.0, parseGameProfile(valid).geometry.flash.halfFovDegrees, 0.1)
        val custom = parseGameProfile(valid.replace("\"sensitivity\"", "\"trainer\":{\"flash\":{\"blindSeconds\":2.0,\"popDelaySeconds\":{\"min\":0.3,\"max\":0.6},\"verified\":true}},\"sensitivity\""))
        assertEquals(2.0, custom.geometry.flash.blindSeconds)
        assertEquals(0.6, custom.geometry.flash.popDelaySeconds.max)
        assertEquals(true, custom.geometry.flash.verified)
        assertEquals(50.0, custom.geometry.flash.halfFovDegrees, 0.1)
        assertEquals(45.0, de.yuuto.aimtune.games.GameProfile.custom(0.07, 90.0).geometry.flash.halfFovDegrees, 0.1)
    }
}
