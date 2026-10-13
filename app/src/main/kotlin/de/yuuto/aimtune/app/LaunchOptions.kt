package de.yuuto.aimtune.app

import java.nio.file.Path
import java.nio.file.Paths

enum class SourceKind { AUTO, RAW, GLFW }

class LaunchOptionsException(message: String) : Exception(message)

/**
 * Developer options from the command line. Everything about the user (DPI, game, sensitivity)
 * comes from onboarding and settings, not from here.
 */
data class LaunchOptions(
    /** First scenario seed; null means a fresh random seed per run (logged when a round starts). */
    val seed: Long?,
    val source: SourceKind,
    val vsync: Boolean,
    /** When set, the app walks through its screens with sample data, saves a PNG of each here and exits. */
    val screenshotDir: Path?,
) {
    companion object {
        const val USAGE = """Usage: aimtune [options]
  --seed <n>           scenario seed of the first round, default random
  --source <s>         auto (default), raw (Windows Raw Input) or glfw
  --no-vsync           disable vertical sync
  --screenshots <dir>  save a PNG of every screen into <dir> using sample data, then exit"""
    }
}

private val VALUE_OPTIONS = setOf("--seed", "--source", "--screenshots")

fun parseLaunchOptions(args: Array<String>): LaunchOptions {
    val values = mutableMapOf<String, String>()
    var vsync = true
    var i = 0
    while (i < args.size) {
        val arg = args[i]
        when {
            arg == "--no-vsync" -> vsync = false
            arg in VALUE_OPTIONS && i + 1 < args.size -> values[arg] = args[++i]
            arg in VALUE_OPTIONS -> throw LaunchOptionsException("missing value for $arg")
            else -> throw LaunchOptionsException("unknown option: $arg")
        }
        i++
    }
    val source = when (val raw = values["--source"]?.lowercase()) {
        null, "auto" -> SourceKind.AUTO
        "raw" -> SourceKind.RAW
        "glfw" -> SourceKind.GLFW
        else -> throw LaunchOptionsException("--source must be auto, raw or glfw, was '$raw'")
    }
    val seed = values["--seed"]?.let { it.toLongOrNull() ?: throw LaunchOptionsException("--seed must be an integer, was '$it'") }
    return LaunchOptions(seed, source, vsync, values["--screenshots"]?.let { Paths.get(it) })
}
