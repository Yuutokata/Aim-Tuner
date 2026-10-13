package de.yuuto.aimtune.games

import de.yuuto.aimtune.core.scenario.AngleBucket
import de.yuuto.aimtune.core.scenario.PositiveRange
import de.yuuto.aimtune.core.scenario.TrainerGeometry
import de.yuuto.aimtune.core.units.Fov
import de.yuuto.aimtune.core.units.FovType
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.name

class GameProfileException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
private data class AspectDto(val width: Int, val height: Int)

@Serializable
private data class FovDto(val degrees: Double, val type: String, val aspect: AspectDto, val scaling: String, val trainerAspect: AspectDto? = null)

@Serializable
private data class SensitivityDto(val decimals: Int)

@Serializable
private data class GameProfileDto(
    val id: String,
    val name: String,
    val yaw: Double,
    val fov: FovDto,
    val sensitivity: SensitivityDto,
    val sources: List<String> = emptyList(),
    val notes: String? = null,
    val trainer: TrainerDto = TrainerDto(),
)

@Serializable
private data class RangeDto(val min: Double, val max: Double)

@Serializable
private data class AngleBucketDto(val maxDegrees: Double, val weight: Double)

/** The `trainer` block of a profile; every field is optional and falls back to [TrainerGeometry.DEFAULT]. */
@Serializable
private data class TrainerDto(
    val fieldYawDegrees: Double? = null,
    val fieldPitchDegrees: Double? = null,
    val clickTargetDiameterDegrees: RangeDto? = null,
    val clickTargetDistanceMeters: RangeDto? = null,
    val flickAngles: List<AngleBucketDto>? = null,
    val microStepDegrees: RangeDto? = null,
    val switchingSeparationDegrees: Double? = null,
    val trackingDiameterDegrees: RangeDto? = null,
    val trackingDistanceMeters: Double? = null,
    val strafeSpeedMetersPerSecond: RangeDto? = null,
    val stopProbability: Double? = null,
    val verified: Boolean = false,
) {
    fun toGeometry(): TrainerGeometry {
        val d = TrainerGeometry.DEFAULT
        fun RangeDto.range() = PositiveRange(min, max)
        return TrainerGeometry(
            fieldYawDegrees = fieldYawDegrees ?: d.fieldYawDegrees,
            fieldPitchDegrees = fieldPitchDegrees ?: d.fieldPitchDegrees,
            clickTargetDiameterDegrees = clickTargetDiameterDegrees?.range() ?: d.clickTargetDiameterDegrees,
            clickTargetDistanceMeters = clickTargetDistanceMeters?.range() ?: d.clickTargetDistanceMeters,
            flickAngles = flickAngles?.map { AngleBucket(it.maxDegrees, it.weight) } ?: d.flickAngles,
            microStepDegrees = microStepDegrees?.range() ?: d.microStepDegrees,
            switchingSeparationDegrees = switchingSeparationDegrees ?: d.switchingSeparationDegrees,
            trackingDiameterDegrees = trackingDiameterDegrees?.range() ?: d.trackingDiameterDegrees,
            trackingDistanceMeters = trackingDistanceMeters ?: d.trackingDistanceMeters,
            strafeSpeedMetersPerSecond = strafeSpeedMetersPerSecond?.range() ?: d.strafeSpeedMetersPerSecond,
            stopProbability = stopProbability ?: d.stopProbability,
            verified = verified,
        )
    }
}

@Serializable
private data class IndexDto(val games: List<String>)

private const val HOR_PLUS = "hor_plus"

private val json = Json { ignoreUnknownKeys = true }

/** Parses one profile file. @throws GameProfileException with a message naming the problem. */
fun parseGameProfile(text: String): GameProfile {
    val dto = try {
        json.decodeFromString<GameProfileDto>(text)
    } catch (e: SerializationException) {
        throw GameProfileException("not a valid game profile: ${e.message}", e)
    }
    try {
        require(dto.fov.scaling == HOR_PLUS) { "fov.scaling '${dto.fov.scaling}' is not supported (only '$HOR_PLUS')" }
        require(dto.fov.aspect.width > 0 && dto.fov.aspect.height > 0) { "fov.aspect must be positive" }
        val type = FovType.entries.firstOrNull { it.name.equals(dto.fov.type, ignoreCase = true) }
            ?: throw IllegalArgumentException("fov.type '${dto.fov.type}' must be horizontal or vertical")
        return GameProfile(
            id = dto.id,
            name = dto.name,
            yawDegreesPerCount = dto.yaw,
            fov = Fov(dto.fov.degrees, type),
            fovAspectRatio = dto.fov.aspect.width.toDouble() / dto.fov.aspect.height,
            sensitivityDecimals = dto.sensitivity.decimals,
            sources = dto.sources,
            notes = dto.notes,
            geometry = dto.trainer.toGeometry(),
            trainerAspectRatio = dto.fov.trainerAspect?.let { it.width.toDouble() / it.height } ?: (16.0 / 9.0),
        )
    } catch (e: IllegalArgumentException) {
        throw GameProfileException("invalid game profile '${dto.id}': ${e.message}", e)
    }
}

/** Bundled profiles plus any the user added; [problems] lists user files that were skipped and why. */
class GameProfiles(val profiles: List<GameProfile>, val problems: List<String>) {
    fun find(id: String): GameProfile? = profiles.firstOrNull { it.id == id }

    companion object {
        /**
         * Loads the profiles bundled in the app, then every `*.json` in [userDir] (if given). A user
         * profile with a bundled id replaces it, so values can be corrected without a new release.
         * A broken bundled profile is a bug and fails loudly; a broken user file is only reported.
         */
        fun load(userDir: Path? = null): GameProfiles {
            val byId = LinkedHashMap<String, GameProfile>()
            bundled().forEach { byId[it.id] = it }
            val problems = mutableListOf<String>()
            if (userDir != null && Files.isDirectory(userDir)) {
                Files.list(userDir).use { files -> files.filter { it.extension == "json" }.sorted().toList() }.forEach { file ->
                    try {
                        parseGameProfile(Files.readString(file)).also { byId[it.id] = it }
                    } catch (e: GameProfileException) {
                        problems += "${file.name}: ${e.message}"
                    } catch (e: IOException) {
                        problems += "${file.name}: could not be read (${e.message})"
                    }
                }
            }
            return GameProfiles(byId.values.toList(), problems)
        }

        private fun bundled(): List<GameProfile> {
            val index = json.decodeFromString<IndexDto>(resource("games/index.json"))
            return index.games.map { id ->
                try {
                    parseGameProfile(resource("games/$id.json"))
                } catch (e: GameProfileException) {
                    throw IllegalStateException("bundled game profile '$id' is broken: ${e.message}", e)
                }
            }
        }

        private fun resource(path: String): String =
            (GameProfiles::class.java.classLoader.getResourceAsStream(path) ?: error("missing bundled resource $path"))
                .use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
