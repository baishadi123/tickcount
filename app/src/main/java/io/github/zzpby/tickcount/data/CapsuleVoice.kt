package io.github.zzpby.tickcount.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * The voice a capsule carries, kept in the app's private directory.
 *
 * One capsule carries one voice letter: an AAC recording in an MPEG-4 file,
 * written straight to disk by the recorder, plus the waveform the editor
 * sampled while it listened — the wave travels inside the capsule record, the
 * sound itself stays here. A backup carries the name and the wave; the sound,
 * like every pixel and every note in this app, stays behind.
 */
object CapsuleVoice {

    private const val DIR = "capsule-voice"
    private const val PREFIX = "cv-"

    private fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    // A name is only ours if this store minted it: a hand-made backup must not
    // walk out of the directory with a separator in the name.
    private fun isOurs(name: String): Boolean =
        name.startsWith(PREFIX) && name.none { it == '/' || it == '\\' }

    /** A fresh file to record into, owned by [capsuleId]'s capsule. */
    fun newFile(context: Context, capsuleId: String): File =
        File(dir(context), "$PREFIX$capsuleId-${System.nanoTime()}.m4a")

    /** The voice file called [name], or null when it is gone or not one of ours. */
    fun file(context: Context, name: String?): File? {
        if (name == null || !isOurs(name)) return null
        return File(dir(context), name).takeIf { it.isFile }
    }

    /** Deletes the voice file called [name], if it is one of ours. */
    fun delete(context: Context, name: String?) {
        if (name == null || !isOurs(name)) return
        File(dir(context), name).delete()
    }
}

/**
 * Records one take into [output]: AAC audio at a quality a voice letter
 * deserves and a phone can spare. [start] prepares and begins; [amplitude]
 * is polled while it listens, and the polled values become the wave the
 * letter shows; [stop] closes the file and says whether the take is usable —
 * a tap too quick to hold a word is not.
 */
class VoiceRecorder(context: Context, output: File) {

    private val recorder: MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private var started = false

    init {
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        recorder.setAudioEncodingBitRate(96_000)
        recorder.setAudioSamplingRate(44_100)
        recorder.setOutputFile(output.absolutePath)
    }

    fun start(): Boolean =
        runCatching {
            recorder.prepare()
            recorder.start()
            started = true
        }.isSuccess

    /** The loudest sound since the last poll, 0–32767. */
    fun amplitude(): Int =
        if (started) runCatching { recorder.maxAmplitude }.getOrDefault(0) else 0

    /** Stops recording; true when a usable file was written. */
    fun stop(): Boolean =
        if (started) runCatching { recorder.stop() }.isSuccess else false

    fun release() {
        recorder.release()
    }
}
