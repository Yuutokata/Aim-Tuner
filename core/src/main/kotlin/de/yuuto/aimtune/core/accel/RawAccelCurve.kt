package de.yuuto.aimtune.core.accel

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** `accel_mode` of Raw Accel 1.7.1 (`common/rawaccel-base.hpp:246`); [json] is the name in `settings.json` (`wrapper/wrapper.cpp:37`). Only the modes AimTune reproduces. */
enum class RawAccelMode(val json: String) {
    CLASSIC("classic"),
    NATURAL("natural"),
    LUT("lut"),
    NOACCEL("noaccel"),
}

/** `cap_mode` of Raw Accel 1.7.1 (`common/rawaccel-base.hpp:256`); [json] is the name in `settings.json` (`wrapper/wrapper.cpp:43`). */
enum class RawAccelCapMode(val json: String) {
    IN_OUT("in_out"),
    INPUT("input"),
    OUTPUT("output"),
}

/**
 * The parameters of one Raw Accel 1.7.1 `accel_args` (`common/rawaccel-base.hpp:260`) that classic, natural, lut and noaccel read,
 * under the names and defaults of the C++ struct. [gain] is the "Gain / Velocity" switch: false is Velocity (`LEGACY`, the curve is
 * the multiplier), true is Gain (the curve is defined through its derivative). For [RawAccelMode.LUT] the flag decides what the
 * table's y values are: output speeds when true, multipliers when false (`accel-lookup.hpp:111`, `:136`). [lut] is the flat
 * `data` array x1, y1, x2, y2, ... as the floats Raw Accel stores.
 *
 * The init block applies the checks of `common/rawaccel-validate.hpp:38-131` to the fields kept here, so a value that Raw Accel
 * would refuse to load cannot be built.
 */
data class RawAccelArgs(
    val mode: RawAccelMode,
    val gain: Boolean = true,
    val acceleration: Double = 0.005,
    val exponentClassic: Double = 2.0,
    val inputOffset: Double = 0.0,
    val decayRate: Double = 0.1,
    val limit: Double = 1.5,
    val capX: Double = 15.0,
    val capY: Double = 1.5,
    val capMode: RawAccelCapMode = RawAccelCapMode.OUTPUT,
    val lut: List<Float> = emptyList(),
) {
    init {
        if (mode == RawAccelMode.LUT) {
            require(lut.size >= 4) { "lookup mode requires at least 2 points" }
            require(lut.size <= RAW_DATA_CAPACITY) { "too many data points (max=$MAX_LUT_POINTS)" }
        }
        require(lut.size <= RAW_DATA_CAPACITY) { "data size > max" }
        require(lut.size % 2 == 0) { "lut data must hold x, y pairs" }
        require(inputOffset >= 0) { "offset can not be negative" }
        require(capX >= 0) { "cap (input) can not be negative" }
        require(capY >= 0) { "cap (output) can not be negative" }
        if (mode == RawAccelMode.CLASSIC && capMode == RawAccelCapMode.IN_OUT) {
            require(capX != 0.0) { "cap (input) can not be 0" }
            require(capY != 0.0) { "cap (output) can not be 0" }
        }
        require(!(mode == RawAccelMode.CLASSIC && capX > 0 && capX < inputOffset && capMode != RawAccelCapMode.OUTPUT)) { "cap < offset" }
        require(acceleration > 0) { "acceleration must be positive" }
        require(decayRate > 0) { "decay rate must be positive" }
        require(exponentClassic > 1) { "exponent must be greater than 1" }
        require(limit > 0) { "limit must be positive" }
    }

    companion object {
        /** `LUT_RAW_DATA_CAPACITY` (`rawaccel-base.hpp:235`). */
        const val RAW_DATA_CAPACITY = 514
        const val MAX_LUT_POINTS = RAW_DATA_CAPACITY / 2
    }
}

/**
 * The multiplier of a Raw Accel 1.7.1 acceleration mode at a speed in inches per second, computed with the code of the driver:
 * classic Velocity/Gain `common/accel-classic.hpp:27-77` / `:79-170`, natural `accel-natural.hpp:25-37` / `:40-57`, lut
 * `accel-lookup.hpp:103-156`, noaccel returns 1. The callback that wraps all of them multiplies `(f(x) - 1)` by the range weight
 * (`rawaccel.hpp:451`), which is 1 for the profiles AimTune accepts. The derived parameters (the classic `a` of a cap mode that
 * fixes the curve by a point, the Gain cap and its integration constant) are computed once in the constructor, in the order of
 * the C++ constructors.
 *
 * The speed axis is the one of the driver: counts per ms times 1000 / *device* DPI (see [RawAccelProfile]).
 *
 * Beyond the last lut point the table is extended linearly, not held: the driver interpolates between the last two points with
 * t > 1 and `lerp` (`accel-lookup.hpp:93-101`) does not clamp in that direction. This differs from [LookupCurve], which holds the
 * last value and stays as it is for stored rounds.
 */
data class RawAccelCurve(val args: RawAccelArgs) : AccelCurve {
    private val evaluate: (Double) -> Double = when (args.mode) {
        RawAccelMode.CLASSIC -> if (args.gain) classicGain(args) else classicVelocity(args)
        RawAccelMode.NATURAL -> if (args.gain) naturalGain(args) else naturalVelocity(args)
        RawAccelMode.LUT -> lookup(args)
        RawAccelMode.NOACCEL -> { _ -> 1.0 }
    }

    /**
     * The classic acceleration the curve really has: the field itself for cap modes `input` and `output`, the value derived from
     * the cap point for `in_out` (`accel-classic.hpp:44`, `:99`). Null for the other modes.
     */
    val derivedAcceleration: Double? = when {
        args.mode != RawAccelMode.CLASSIC -> null
        args.capMode != RawAccelCapMode.IN_OUT -> args.acceleration
        args.gain -> classicGainAccel(args.capX, abs(args.capY - 1), args.exponentClassic, args.inputOffset)
        else -> classicBaseAccel(args.capX, abs(args.capY - 1), args)
    }

    /** Sensitivity multiplier at [speed] inches per second as the driver's accel function returns it. */
    override fun multiplier(speed: Double): Double = evaluate(speed)

    override fun toString(): String = "RawAccelCurve(${args.mode.json}, ${if (args.gain) "gain" else "velocity"})"

    private companion object {
        // accel-classic.hpp:12-15 (base_fn) and :17-21 (base_accel)
        fun classicBaseFn(x: Double, accelRaised: Double, args: RawAccelArgs) =
            accelRaised * (x - args.inputOffset).pow(args.exponentClassic) / x

        fun classicBaseAccel(x: Double, y: Double, args: RawAccelArgs): Double {
            val power = args.exponentClassic
            return (x * y * (x - args.inputOffset).pow(-power)).pow(1 / (power - 1))
        }

        // accel-classic.hpp:155-168
        fun classicGainValue(x: Double, accel: Double, power: Double, offset: Double) = power * (accel * (x - offset)).pow(power - 1)
        fun classicGainInverse(y: Double, accel: Double, power: Double, offset: Double) = (accel * offset + (y / power).pow(1 / (power - 1))) / accel
        fun classicGainAccel(x: Double, y: Double, power: Double, offset: Double) = -(y / power).pow(1 / (power - 1)) / (offset - x)

        /** `utility.hpp` `minsd`: the second argument unless the first is smaller. */
        fun minsd(a: Double, b: Double) = if (a < b) a else b

        // accel-classic.hpp:27-77
        fun classicVelocity(args: RawAccelArgs): (Double) -> Double {
            val p = args.exponentClassic
            var cap = Double.MAX_VALUE
            var sign = 1.0
            val accelRaised: Double
            when (args.capMode) {
                RawAccelCapMode.IN_OUT -> {
                    cap = args.capY - 1
                    if (cap < 0) {
                        cap = -cap
                        sign = -sign
                    }
                    accelRaised = classicBaseAccel(args.capX, cap, args).pow(p - 1)
                }
                RawAccelCapMode.INPUT -> {
                    accelRaised = args.acceleration.pow(p - 1)
                    if (args.capX > 0) cap = classicBaseFn(args.capX, accelRaised, args)
                }
                RawAccelCapMode.OUTPUT -> {
                    accelRaised = args.acceleration.pow(p - 1)
                    if (args.capY > 0) {
                        cap = args.capY - 1
                        if (cap < 0) {
                            cap = -cap
                            sign = -sign
                        }
                    }
                }
            }
            return { x -> if (x <= args.inputOffset) 1.0 else sign * minsd(classicBaseFn(x, accelRaised, args), cap) + 1 }
        }

        // accel-classic.hpp:79-153
        fun classicGain(args: RawAccelArgs): (Double) -> Double {
            val p = args.exponentClassic
            var capX = Double.MAX_VALUE
            var capY = Double.MAX_VALUE
            var constant = 0.0
            var sign = 1.0
            val accelRaised: Double
            when (args.capMode) {
                RawAccelCapMode.IN_OUT -> {
                    capX = args.capX
                    capY = args.capY - 1
                    if (capY < 0) {
                        capY = -capY
                        sign = -sign
                    }
                    accelRaised = classicGainAccel(capX, capY, p, args.inputOffset).pow(p - 1)
                    constant = (classicBaseFn(capX, accelRaised, args) - capY) * capX
                }
                RawAccelCapMode.INPUT -> {
                    accelRaised = args.acceleration.pow(p - 1)
                    if (args.capX > 0) {
                        capX = args.capX
                        capY = classicGainValue(capX, args.acceleration, p, args.inputOffset)
                        constant = (classicBaseFn(capX, accelRaised, args) - capY) * capX
                    }
                }
                RawAccelCapMode.OUTPUT -> {
                    accelRaised = args.acceleration.pow(p - 1)
                    if (args.capY > 0) {
                        capY = args.capY - 1
                        if (capY == 0.0) {
                            capX = 0.0
                        } else {
                            if (capY < 0) {
                                capY = -capY
                                sign = -sign
                            }
                            capX = classicGainInverse(capY, args.acceleration, p, args.inputOffset)
                            constant = (classicBaseFn(capX, accelRaised, args) - capY) * capX
                        }
                    }
                }
            }
            return { x ->
                if (x <= args.inputOffset) {
                    1.0
                } else {
                    val output = if (x < capX) classicBaseFn(x, accelRaised, args) else constant / x + capY
                    sign * output + 1
                }
            }
        }

        // accel-natural.hpp:13-18 (natural_base), :25-37
        fun naturalVelocity(args: RawAccelArgs): (Double) -> Double {
            val offset = args.inputOffset
            val limit = args.limit - 1
            val accel = args.decayRate / abs(limit)
            return { x ->
                if (x <= offset) {
                    1.0
                } else {
                    val offsetX = offset - x
                    val decay = exp(accel * offsetX)
                    limit * (1 - (offset - decay * offsetX) / x) + 1
                }
            }
        }

        // accel-natural.hpp:40-57
        fun naturalGain(args: RawAccelArgs): (Double) -> Double {
            val offset = args.inputOffset
            val limit = args.limit - 1
            val accel = args.decayRate / abs(limit)
            val constant = -limit / accel
            return { x ->
                if (x <= offset) {
                    1.0
                } else {
                    val offsetX = offset - x
                    val decay = exp(accel * offsetX)
                    (limit * (decay / accel - offsetX) + constant) / x + 1
                }
            }
        }

        // accel-lookup.hpp:93-101, with maxsd/minsd of utility.hpp
        fun lerp(a: Double, b: Double, t: Double): Double {
            val x = a + t * (b - a)
            return if ((t > 1) == (a < b)) (if (b < x) x else b) else minsd(x, b)
        }

        // accel-lookup.hpp:103-156; `velocity` there is args.gain
        fun lookup(args: RawAccelArgs): (Double) -> Double {
            val data = args.lut
            val size = data.size / 2
            val outputSpeed = args.gain
            fun px(i: Int) = data[2 * i].toDouble()
            fun py(i: Int) = data[2 * i + 1].toDouble()
            return fun(x: Double): Double {
                if (x <= 0) return 0.0
                var lo = 0
                var hi = size - 2
                while (lo <= hi) {
                    val mid = (lo + hi) / 2
                    if (x < px(mid)) {
                        hi = mid - 1
                    } else if (x > px(mid)) {
                        lo = mid + 1
                    } else {
                        return if (outputSpeed) py(mid) / x else py(mid)
                    }
                }
                if (lo > 0) {
                    val y = lerp(py(lo - 1), py(lo), (x - px(lo - 1)) / (px(lo) - px(lo - 1)))
                    return if (outputSpeed) y / x else y
                }
                return if (outputSpeed) py(0) / px(0) else py(0)
            }
        }
    }
}

/**
 * A Raw Accel profile as far as the trainer needs it: the acceleration of the whole (combined) input plus the two DPI values.
 * Raw Accel computes the speed of a report as counts per ms times 1000 / [deviceDpi] (the device's DPI setting, `driver.cpp:376`;
 * a device DPI of 0 means no normalization and is the same as 1000), so the speed axis of [curve] belongs to the DPI in the
 * file, and the trainer must run with that DPI to see the user's curve at the user's speeds. The counts are then multiplied by
 * the curve and by `outputDpi / deviceDpi` (`rawaccel.hpp:412`, `:429`).
 */
data class RawAccelProfile(
    val name: String,
    val args: RawAccelArgs,
    val outputDpi: Double,
    val deviceDpi: Double,
) {
    init {
        require(outputDpi > 0 && outputDpi.isFinite()) { "output DPI must be positive" }
        require(deviceDpi >= 0 && deviceDpi.isFinite()) { "device DPI must not be negative" }
    }

    val curve: RawAccelCurve = RawAccelCurve(args)

    /** The DPI the driver normalizes with: the device's, or 1000 when the file has none. */
    val effectiveDeviceDpi: Double get() = if (deviceDpi > 0) deviceDpi else AccelProcessor.NORMALIZED_DPI

    /** The factor the counts get at [speed] inches per second: the curve times `outputDpi / deviceDpi`. */
    fun totalMultiplier(speed: Double): Double = curve.multiplier(speed) * outputDpi / effectiveDeviceDpi
}
