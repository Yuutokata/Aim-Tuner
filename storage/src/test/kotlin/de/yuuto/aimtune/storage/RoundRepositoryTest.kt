package de.yuuto.aimtune.storage

import de.yuuto.aimtune.core.input.MouseEvent
import de.yuuto.aimtune.core.math.Vec3
import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.scenario.Target
import de.yuuto.aimtune.core.scenario.TargetEvent
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundRepositoryTest {
    @TempDir
    lateinit var dir: Path

    private val ms = 1_000_000L

    private fun recording(origin: Long = 7_000_000_000L, cm360: Double = 40.0) = RoundRecording(
        scenarioId = "flick", seed = 42, dpi = 800.0, cm360 = cm360, startNanos = origin, endNanos = origin + 45_000 * ms,
        mouseEvents = listOf(MouseEvent(origin + 10 * ms, 120, -4), MouseEvent(origin + 11 * ms, 60, 0), MouseEvent(origin + 400 * ms, -2, 3)),
        targetEvents = listOf(
            TargetEvent.Spawned(origin, Target(0, Vec3(5.0, 1.0, -9.0), 0.25)),
            TargetEvent.Miss(origin + 100 * ms),
            TargetEvent.Hit(origin + 300 * ms, 0),
        ),
    )

    @Test
    fun `a fresh database is migrated to the current schema`() {
        Database.open(dir.resolve("a.db")).use { assertEquals(Database.CURRENT_VERSION, it.schemaVersion) }
    }

    @Test
    fun `reopening keeps the data and does not migrate twice`() {
        val file = dir.resolve("a.db")
        val id = Database.open(file).use { RoundRepository(it).createSession(1, "valorant", 800.0, SessionKind.SENSITIVITY) }
        Database.open(file).use {
            assertEquals(Database.CURRENT_VERSION, it.schemaVersion)
            assertEquals(listOf(id), RoundRepository(it).sessions().map { s -> s.id })
        }
    }

    @Test
    fun `a database from a newer version is refused`() {
        val file = dir.resolve("future.db")
        Database.open(file).use { }
        java.sql.DriverManager.getConnection("jdbc:sqlite:$file").use { c ->
            c.createStatement().use { it.execute("PRAGMA user_version = ${Database.CURRENT_VERSION + 5}") }
        }
        assertFailsWith<IllegalStateException> { Database.open(file) }
    }

    @Test
    fun `a saved round loads back identically`() {
        Database.open(dir.resolve("a.db")).use { db ->
            val repo = RoundRepository(db)
            val session = repo.createSession(1_000, "valorant", 800.0, SessionKind.SENSITIVITY)
            val original = recording()
            val id = repo.saveRound(session, original, startedAtMs = 2_000, blind = true, metrics = computeRoundMetrics(original))
            assertEquals(original, repo.loadRecording(id))
        }
    }

    @Test
    fun `unknown round loads as null`() {
        Database.open(dir.resolve("a.db")).use { assertNull(RoundRepository(it).loadRecording(99)) }
    }

    @Test
    fun `rounds list their metrics and filter by session`() {
        Database.open(dir.resolve("a.db")).use { db ->
            val repo = RoundRepository(db)
            val s1 = repo.createSession(1, "valorant", 800.0, SessionKind.SENSITIVITY)
            val s2 = repo.createSession(2, "valorant", 800.0, SessionKind.ACCEL_AB)
            val r = recording()
            val m = computeRoundMetrics(r)
            repo.saveRound(s1, r, 10, blind = true, metrics = m)
            repo.saveRound(s1, recording(cm360 = 50.0), 20, blind = false, metrics = m)
            repo.saveRound(s2, r, 30, blind = true, metrics = m)

            assertEquals(3, repo.rounds().size)
            val first = repo.rounds(s1)
            assertEquals(listOf(40.0, 50.0), first.map { it.cm360 })
            assertEquals(listOf(true, false), first.map { it.blind })
            assertEquals(1.0, assertNotNull(first[0].metrics["hits"]))
            assertEquals(2.0, assertNotNull(first[0].metrics["shots"]))
            assertEquals(0.5, assertNotNull(first[0].metrics["accuracy"]), 1e-12)
            assertEquals(listOf(SessionKind.SENSITIVITY, SessionKind.ACCEL_AB), repo.sessions().map { it.kind })
        }
    }

    @Test
    fun `a failed save leaves nothing behind`() {
        Database.open(dir.resolve("a.db")).use { db ->
            val repo = RoundRepository(db)
            val r = recording()
            assertFailsWith<Exception> { repo.saveRound(sessionId = 12345, recording = r, startedAtMs = 1, blind = true, metrics = computeRoundMetrics(r)) }
            assertTrue(repo.rounds().isEmpty())
        }
    }

    @Test
    fun `default paths follow the platform`() {
        val win = AppPaths.default(mapOf("APPDATA" to "C:\\Users\\x\\AppData\\Roaming"), "Windows 11")
        assertEquals("AimTune", win.dataDir.fileName.toString())
        val custom = AppPaths.default(mapOf(AppPaths.DATA_DIR_ENV to "D:\\data", "APPDATA" to "ignored"), "Windows 11")
        assertEquals("D:\\data", custom.dataDir.toString())
        val linux = AppPaths.default(mapOf("XDG_DATA_HOME" to "/x"), "Linux")
        assertEquals("aimtune", linux.dataDir.fileName.toString())
        assertFailsWith<IllegalStateException> { AppPaths.default(emptyMap(), "Windows 11") }
    }
}
