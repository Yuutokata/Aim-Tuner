package de.yuuto.aimtune.app

import de.yuuto.aimtune.core.units.Fov
import de.yuuto.aimtune.core.units.FovType

enum class SourceKind { AUTO, RAW, GLFW }

class LaunchOptionsException(message: String) : Exception(message)

/**
 * Temporary configuration for the milestone-2 trainer, replaced by onboarding and settings in M4.
 * Nothing user-specific has a default: DPI, sensitivity and FOV must be given explicitly.
 */
data class LaunchOptions(
    val dpi: Double,
    val cm360: Double,
    val fov: Fov,
    val fovReferenceAspect: Double,
    val roundSeconds: Int,
    val seed: Long,
    val source: SourceKind,
    val vsync: Boolean,
) {
    companion object {
        const val USAGE = """Usage: --dpi <n> --cm360 <n> --fov <degrees> [options]
  --dpi <n>            mouse DPI (required)
  --cm360 <n>          fixed sensitivity in cm per 360 degrees (required)
  --fov <degrees>      field of view of the game to match (required)
  --fov-type <t>       horizontal (default) or vertical
  --fov-aspect <r>     aspect ratio the FOV refers to, default 16:9 as 1.7778
  --duration <s>       round length in seconds, default 45
  --seed <n>           scenario seed, default random (logged at start)
  --source <s>         auto (default), raw (Windows Raw Input) or glfw
  --no-vsync           disable vertical sync"""
    }
}

private const val DEFAULT_ASPECT = 16.0 / 9.0
private const val DEFAULT_ROUND_SECONDS = 45

fun parseLaunchOptions(args: Array<String>, randomSeed: () -> Long = System::nanoTime): LaunchOptions {
    val values = mutableMapOf<String, String>()
    val flags = mutableSetOf<String>()
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        when {
            arg == "--no-vsync" -> flags += arg
            arg.startsWith("--") && i + 1 < args.size -> values[arg] = args[++i]
            else -> throw LaunchOptionsException("unexpected argument or missing value: $arg")
        }
        i++
    }
    val known = setOf("--dpi", "--cm360", "--fov", "--fov-type", "--fov-aspect", "--duration", "--seed", "--source")
    values.keys.firstOrNull { it !in known }?.let { throw LaunchOptionsException("unknown option: $it") }

    fun number(name: String): Double {
        val raw = values[name] ?: throw LaunchOptionsException("missing required option $name")
        return raw.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
            ?: throw LaunchOptionsException("$name must be a positive number, was '$raw'")
    }

    val fovType = when (val raw = values["--fov-type"]?.lowercase()) {
        null, "horizontal" -> FovType.HORIZONTAL
        "vertical" -> FovType.VERTICAL
        else -> throw LaunchOptionsException("--fov-type must be horizontal or vertical, was '$raw'")
    }
    val fov = try {
        Fov(number("--fov"), fovType)
    } catch (e: IllegalArgumentException) {
        throw LaunchOptionsException("--fov: ${e.message}")
    }
    val source = when (val raw = values["--source"]?.lowercase()) {
        null, "auto" -> SourceKind.AUTO
        "raw" -> SourceKind.RAW
        "glfw" -> SourceKind.GLFW
        else -> throw LaunchOptionsException("--source must be auto, raw or glfw, was '$raw'")
    }
    val duration = values["--duration"]?.let {
        it.toIntOrNull()?.takeIf { s -> s > 0 } ?: throw LaunchOptionsException("--duration must be a positive integer, was '$it'")
    } ?: DEFAULT_ROUND_SECONDS
    val seed = values["--seed"]?.let {
        it.toLongOrNull() ?: throw LaunchOptionsException("--seed must be an integer, was '$it'")
    } ?: randomSeed()

    return LaunchOptions(
        dpi = number("--dpi"),
        cm360 = number("--cm360"),
        fov = fov,
        fovReferenceAspect = if ("--fov-aspect" in values) number("--fov-aspect") else DEFAULT_ASPECT,
        roundSeconds = duration,
        seed = seed,
        source = source,
        vsync = "--no-vsync" !in flags,
    )
}
