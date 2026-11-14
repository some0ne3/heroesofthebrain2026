package com.heroesofthebrain.halo.calibration

import org.junit.Test

class CalibrationReplayTest {

    private fun printResult(result: CalibrationResult) {
        result.analysis.parameters.forEach {
            println("${it.param}: ${it.param.format(it.value)} (default ${it.param.format(it.defaultValue)}) ${it.status} ${it.validTrials}/${it.trials} ${it.note}")
        }
        println("holds: ${result.clenchHolds}")
        result.scores.forEach { println("${it.gesture}: ${it.hits}/${it.trials} other=${it.otherDetections} cancelled=${it.cancelled} defaults ${it.defaultHits}/${it.trials} other=${it.defaultOtherDetections}") }
        println("rest: ${result.rest}")
    }

    @Test
    fun `full calibration from the replay recordings`() {
        printResult(CalibrationScorer.evaluate(ReplayCaptures.full()))
        println("---- flipped")
        printResult(CalibrationScorer.evaluate(ReplayCaptures.full(flippedTurns = true)))
    }
}
