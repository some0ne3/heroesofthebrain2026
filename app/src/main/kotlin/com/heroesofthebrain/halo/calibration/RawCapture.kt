package com.heroesofthebrain.halo.calibration

import com.heroesofthebrain.halo.eeg.EEG_CHANNELS
import com.heroesofthebrain.halo.eeg.EegFrame
import com.heroesofthebrain.halo.eeg.ReplayCsv
import com.heroesofthebrain.halo.eeg.ReplayRecording
import kotlinx.serialization.Serializable
import java.io.File
import java.io.Writer

// Opt-in copy of what calibration recorded: one replay CSV per step (raw EEG in µV, contact flags, accelerometer,
// gyroscope, sample index, battery), plus trials.json with the cue windows, so ReplayCsv and the tests can replay it.
object RawCapture {
    const val MANIFEST = "trials.json"

    @Serializable
    data class Manifest(val version: Int = 1, val sampleRate: Int, val steps: List<Step>)

    @Serializable
    data class Step(
        val step: String,
        val file: String,
        // Replay CSVs always have gyro columns; this says whether they were real or zero-filled.
        val hasGyro: Boolean,
        val analysisStartMs: Long,
        val signalOkFraction: Float,
        val trials: List<TrialWindow>,
    )

    private const val BYTES_PER_ROW = 120L

    fun estimateBytes(captures: CalibrationCaptures): Long = captures.all.sumOf { it.sampleCount + 1L } * BYTES_PER_ROW

    fun write(dir: File, captures: CalibrationCaptures): List<File> {
        dir.mkdirs()
        val written = ArrayList<File>()
        val steps = captures.all.map { c ->
            val file = File(dir, "${c.step.key}.csv")
            file.bufferedWriter().use { writeCsv(it, c.frames) }
            written += file
            Step(c.step.key, file.name, c.hasGyro, c.analysisStartMs, c.signalOkFraction, c.trials)
        }
        val manifest = File(dir, MANIFEST)
        manifest.writeText(CalibrationStore.JSON.encodeToString(Manifest.serializer(), Manifest(sampleRate = captures.sampleRate ?: 0, steps = steps)))
        written += manifest
        return written
    }

    fun read(dir: File): CalibrationCaptures {
        val manifest = CalibrationStore.JSON.decodeFromString(Manifest.serializer(), File(dir, MANIFEST).readText())
        return manifest.steps.fold(CalibrationCaptures()) { acc, s ->
            val step = CalibrationStep.forKey(s.step) ?: return@fold acc
            val rec = File(dir, s.file).bufferedReader().use { ReplayCsv.parse(it, manifest.sampleRate) }
            val frames = frames(rec).map { if (s.hasGyro) it else it.withoutGyro() }
            if (frames.isEmpty()) acc else acc.with(StepCapture(step, manifest.sampleRate, frames, s.trials, s.analysisStartMs, s.signalOkFraction))
        }
    }

    // Float.toString round-trips exactly, so a replayed capture gives the same numbers as the live one.
    fun writeCsv(out: Writer, frames: List<EegFrame>) {
        out.write(ReplayCsv.COLUMNS.joinToString(","))
        out.write("\n")
        val sb = StringBuilder(160)
        for (f in frames) {
            val contact = f.contact.joinToString(",") { if (it) "1" else "0" }
            for (s in 0 until f.sampleCount) {
                sb.setLength(0)
                for (c in 0 until EEG_CHANNELS) sb.append(f.eeg(s, c)).append(',')
                sb.append(contact).append(',')
                appendXyz(sb, f.accel, s)
                appendXyz(sb, f.gyro, s)
                sb.append(f.firstSampleIndex + s).append(',')
                sb.append(if (f.isDummy[s]) '0' else '1').append(',')
                sb.append(f.batteryPct).append('\n')
                out.append(sb)
            }
        }
    }

    private fun appendXyz(sb: StringBuilder, values: FloatArray?, s: Int) {
        for (i in 0 until 3) {
            val v = values?.getOrNull(s * 3 + i) ?: 0f
            sb.append(v).append(',')
        }
    }

    // Chunks of the usual size that never straddle a jump in the sample index.
    private fun frames(rec: ReplayRecording): List<EegFrame> {
        val out = ArrayList<EegFrame>()
        var from = 0
        while (from < rec.size) {
            var to = from + 1
            while (to < rec.size && to - from < ReplayRecording.DEFAULT_CHUNK && rec.sampleIndex[to] == rec.sampleIndex[to - 1] + 1) to++
            out += rec.frame(from, to)
            from = to
        }
        return out
    }

    private fun EegFrame.withoutGyro() =
        EegFrame(firstSampleIndex, hostTimeNs, sampleRate, sampleCount, eeg, accel, null, contact, batteryPct, isDummy)
}
