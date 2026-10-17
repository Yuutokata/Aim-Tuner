package de.yuuto.aimtune.storage

import de.yuuto.aimtune.core.input.MouseEvent
import de.yuuto.aimtune.core.accel.AccelSpec
import de.yuuto.aimtune.core.accel.NaturalCurve
import de.yuuto.aimtune.core.math.Vec3
import de.yuuto.aimtune.core.metrics.computeRoundMetrics
import de.yuuto.aimtune.core.recording.RoundRecording
import de.yuuto.aimtune.core.scenario.ScenarioKind
import de.yuuto.aimtune.core.scenario.Target
import de.yuuto.aimtune.core.scenario.TargetEvent
import de.yuuto.aimtune.core.sim.HeadlessRound
import de.yuuto.aimtune.core.sim.SimulatedPlayer
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
    fun `earlier plain acceleration rounds at the same setup are found, newest first`() {
        Database.open(dir.resolve("a.db")).use { db ->
            val repo = RoundRepository(db)
            val accelSession = repo.createSession(1_000, "valorant", 800.0, SessionKind.ACCEL_AB)
            val sensitivitySession = repo.createSession(1_000, "valorant", 800.0, SessionKind.SENSITIVITY)
            fun save(session: Long, at: Long, recording: RoundRecording) =
                repo.saveRound(session, recording, startedAtMs = at, blind = false, metrics = computeRoundMetrics(recording))
            val plain = recording(cm360 = 40.0)
            val old = save(accelSession, 2_000, plain)
            val near = save(accelSession, 3_000, plain.copy(cm360 = 40.8))
            save(accelSession, 4_000, plain.copy(accel = NaturalCurve(0.2, 1.4, inputOffset = 1.0)))
            save(accelSession, 5_000, plain.copy(cm360 = 50.0))
            save(accelSession, 6_000, plain.copy(scenarioId = "tracking"))
            save(accelSession, 7_000, plain.copy(dpi = 400.0))
            save(sensitivitySession, 8_000, plain)

            val found = repo.plainAccelRoundIds(800.0, 40.0, listOf("flick", "switching"), tolerance = 0.03, limit = 10)
            assertEquals(listOf(near, old), found)
            assertEquals(listOf(near), repo.plainAccelRoundIds(800.0, 40.0, listOf("flick"), tolerance = 0.03, limit = 1))
            assertEquals(emptyList(), repo.plainAccelRoundIds(800.0, 40.0, emptyList(), tolerance = 0.03, limit = 10))
            assertEquals(400.0 to 40.0, repo.latestPlainAccelSetup(listOf("flick")), "the newest plain round decides the setup")
            assertNull(repo.latestPlainAccelSetup(listOf("micro")))
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
    fun `the acceleration of a round is stored and loaded`() {
        Database.open(dir.resolve("a.db")).use { db ->
            val repo = RoundRepository(db)
            val session = repo.createSession(1, "valorant", 800.0, SessionKind.SENSITIVITY)
            val curve = NaturalCurve(0.2, 1.4, inputOffset = 1.0)
            val plain = recording()
            val accelerated = plain.copy(accel = curve)
            val plainId = repo.saveRound(session, plain, 1, blind = true, metrics = computeRoundMetrics(plain))
            val accelId = repo.saveRound(session, accelerated, 2, blind = true, metrics = computeRoundMetrics(accelerated))

            assertNull(repo.loadRecording(plainId)?.accel)
            assertEquals(curve, repo.loadRecording(accelId)?.accel)
            assertEquals(listOf(null, AccelSpec.encode(curve)), repo.rounds(session).map { it.accel })
        }
    }

    @Test
    fun `a database from before acceleration is migrated and keeps its rounds`() {
        val file = dir.resolve("old.db")
        java.sql.DriverManager.getConnection("jdbc:sqlite:$file").use { c ->
            c.createStatement().use { s ->
                s.execute("CREATE TABLE session (id INTEGER PRIMARY KEY, started_at_ms INTEGER NOT NULL, game_id TEXT, dpi REAL NOT NULL, kind TEXT NOT NULL)")
                s.execute("INSERT INTO session VALUES (1, 5, 'valorant', 800.0, 'sensitivity')")
                s.execute(
                    "CREATE TABLE round (id INTEGER PRIMARY KEY, session_id INTEGER NOT NULL REFERENCES session(id) ON DELETE CASCADE, scenario TEXT NOT NULL, " +
                        "seed INTEGER NOT NULL, cm360 REAL NOT NULL, dpi REAL NOT NULL, blind INTEGER NOT NULL, started_at_ms INTEGER NOT NULL, start_nanos INTEGER NOT NULL, " +
                        "duration_nanos INTEGER NOT NULL, mouse_event_count INTEGER NOT NULL, mouse_events BLOB NOT NULL, metrics_version INTEGER NOT NULL)",
                )
                s.execute("INSERT INTO round VALUES (1, 1, 'flick', 3, 45.0, 800.0, 1, 6, 0, 1000, 0, x'', 1)")
                s.execute("CREATE TABLE target_event (round_id INTEGER NOT NULL, seq INTEGER NOT NULL, type TEXT NOT NULL, time_offset_nanos INTEGER NOT NULL, target_id INTEGER, x REAL, y REAL, z REAL, radius REAL, PRIMARY KEY (round_id, seq))")
                s.execute("CREATE TABLE metric (round_id INTEGER NOT NULL, name TEXT NOT NULL, value REAL NOT NULL, PRIMARY KEY (round_id, name))")
                s.execute("PRAGMA user_version = 1")
            }
        }
        Database.open(file).use { db ->
            assertEquals(Database.CURRENT_VERSION, db.schemaVersion)
            val rounds = RoundRepository(db).rounds()
            assertEquals(listOf(45.0), rounds.map { it.cm360 })
            assertNull(rounds.single().accel)
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
    fun `blind rounds are gathered across sessions of one game and dpi`() {
        Database.open(dir.resolve("a.db")).use { db ->
            val repo = RoundRepository(db)
            val m = computeRoundMetrics(recording())
            val a = repo.createSession(1, "valorant", 800.0, SessionKind.SENSITIVITY)
            val b = repo.createSession(2, "valorant", 800.0, SessionKind.SENSITIVITY)
            val otherGame = repo.createSession(3, "cs2", 800.0, SessionKind.SENSITIVITY)
            val otherDpi = repo.createSession(4, "valorant", 1600.0, SessionKind.SENSITIVITY)
            val accel = repo.createSession(5, "valorant", 800.0, SessionKind.ACCEL_AB)
            repo.saveRound(a, recording(cm360 = 30.0), 10, blind = true, metrics = m)
            repo.saveRound(a, recording(cm360 = 35.0), 11, blind = false, metrics = m)
            repo.saveRound(b, recording(cm360 = 45.0), 20, blind = true, metrics = m)
            for (other in listOf(otherGame, otherDpi, accel)) repo.saveRound(other, recording(cm360 = 99.0), 30, blind = true, metrics = m)

            val rounds = repo.blindRounds("valorant", 800.0)
            assertEquals(listOf(30.0, 45.0), rounds.map { it.cm360 })
            assertEquals(listOf(a, b), rounds.map { it.sessionId })
            val inputs = rounds.first().scoreInputs()
            // The recording misses once and then hits: one hit in two clicks, but the first shot of the reach missed.
            assertEquals(0.5, rounds.first().metrics.getValue("accuracy"), 1e-12)
            assertEquals(0.0, inputs.getValue(de.yuuto.aimtune.core.score.ScoreMetric.ACCURACY), 1e-12)
            assertEquals(m.meanTimeToHitMs, inputs[de.yuuto.aimtune.core.score.ScoreMetric.TIME_TO_HIT])
        }
    }

    @Test
    fun `a tracking round keeps its waypoints and its tracking score input`() {
        Database.open(dir.resolve("a.db")).use { db ->
            val repo = RoundRepository(db)
            val session = repo.createSession(1, "valorant", 800.0, SessionKind.SENSITIVITY)
            val original = HeadlessRound.of(ScenarioKind.TRACKING, SimulatedPlayer(), 800.0, 50.0, seed = 3, roundSeconds = 10)
            val metrics = computeRoundMetrics(original)
            assertTrue(original.targetEvents.any { it is TargetEvent.Waypoint })
            val id = repo.saveRound(session, original, startedAtMs = 5, blind = true, metrics = metrics)

            assertEquals(original, repo.loadRecording(id))
            val row = repo.blindRounds("valorant", 800.0).single()
            assertEquals("tracking", row.scenario)
            assertEquals(metrics.trackingOnTargetRatio, row.scoreInputs()[de.yuuto.aimtune.core.score.ScoreMetric.TRACKING])
            assertNull(row.scoreInputs()[de.yuuto.aimtune.core.score.ScoreMetric.ACCURACY])
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

    @Test
    fun `rounds stored with an older metric version are recomputed from their raw events`() {
        Database.open(dir.resolve("refresh.db")).use { db ->
            val repo = RoundRepository(db)
            val session = repo.createSession(1, "valorant", 800.0, SessionKind.SENSITIVITY)
            val recording = recording()
            val current = repo.saveRound(session, recording, 1, true, computeRoundMetrics(recording))
            val old = repo.saveRound(session, recording, 2, true, computeRoundMetrics(recording))
            db.transaction { c ->
                c.createStatement().use { it.execute("UPDATE round SET metrics_version = 1 WHERE id = $old") }
                c.createStatement().use { it.execute("UPDATE metric SET value = -1 WHERE round_id = $old AND name = 'hits'") }
                c.createStatement().use { it.execute("UPDATE metric SET value = -1 WHERE round_id = $current AND name = 'hits'") }
            }
            val refresh = repo.refreshMetrics()
            assertEquals(MetricsRefresh(updated = 1, failed = 0), refresh)
            val rows = repo.rounds().associateBy { it.id }
            assertEquals(1.0, rows.getValue(old).metrics.getValue("hits"))
            assertEquals(-1.0, rows.getValue(current).metrics.getValue("hits"), "rounds of the current version are left alone")
            assertEquals(MetricsRefresh(0, 0), repo.refreshMetrics(), "nothing is stale the second time")
        }
    }

    @Test
    fun `a round with unreadable events keeps its old metrics and is reported`() {
        Database.open(dir.resolve("corrupt.db")).use { db ->
            val repo = RoundRepository(db)
            val session = repo.createSession(1, "valorant", 800.0, SessionKind.SENSITIVITY)
            val id = repo.saveRound(session, recording(), 1, true, computeRoundMetrics(recording()))
            db.transaction { c ->
                c.createStatement().use { it.execute("UPDATE round SET metrics_version = 1, mouse_event_count = 99 WHERE id = $id") }
            }
            assertEquals(MetricsRefresh(updated = 0, failed = 1), repo.refreshMetrics())
        }
    }
}
