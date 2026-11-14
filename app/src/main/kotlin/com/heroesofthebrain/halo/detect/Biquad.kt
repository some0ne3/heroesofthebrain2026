package com.heroesofthebrain.halo.detect

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// RBJ cookbook biquad, transposed direct form II. Causal, one sample at a time.
class Biquad private constructor(
    private val b0: Double,
    private val b1: Double,
    private val b2: Double,
    private val a1: Double,
    private val a2: Double,
) {
    private var z1 = 0.0
    private var z2 = 0.0

    fun step(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun reset() {
        z1 = 0.0
        z2 = 0.0
    }

    companion object {
        const val BUTTERWORTH_Q = 0.7071067811865476

        // Q values of the two sections of a 4th-order Butterworth
        const val BUTTERWORTH4_Q1 = 0.5411961001461970
        const val BUTTERWORTH4_Q2 = 1.3065629648763766

        fun lowPass(fs: Double, f0: Double, q: Double = BUTTERWORTH_Q): Biquad {
            val w = 2 * PI * f0 / fs
            val cw = cos(w)
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            return Biquad((1 - cw) / 2 / a0, (1 - cw) / a0, (1 - cw) / 2 / a0, -2 * cw / a0, (1 - alpha) / a0)
        }

        fun highPass(fs: Double, f0: Double, q: Double = BUTTERWORTH_Q): Biquad {
            val w = 2 * PI * f0 / fs
            val cw = cos(w)
            val alpha = sin(w) / (2 * q)
            val a0 = 1 + alpha
            return Biquad((1 + cw) / 2 / a0, -(1 + cw) / a0, (1 + cw) / 2 / a0, -2 * cw / a0, (1 - alpha) / a0)
        }
    }
}
