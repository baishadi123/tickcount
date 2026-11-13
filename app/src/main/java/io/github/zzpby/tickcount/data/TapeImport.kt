package io.github.zzpby.tickcount.data

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns

/**
 * What one import brings back: the recording already shelved in [TapeAudio]'s
 * directory, its length, its wave, the name it came with, and the day it was
 * last touched — a tape ready for the wall, whatever it arrived as.
 */
data class ImportedTape(
    val fileName: String,
    val seconds: Int,
    /** 0–100, one bar to a breath of speech. */
    val bars: List<Int>,
    val displayName: String,
    val takenAt: Long,
)

/**
 * The wall's back door: a file picked from anywhere — a song, a video of
 * her talking — becomes a tape. A video keeps only its sound: the audio
 * track is lifted out and remuxed, a copy, not a re-encode, so a minute
 * takes a moment. An audio file is taken as it is. Either way the length
 * is read from the player and the wave from the samples themselves.
 */
object TapeImport {

    /** Takes [uri] in; a tape's parts out — or null when nothing could be made of it. */
    fun import(context: Context, uri: Uri, tapeId: String): ImportedTape? = runCatching {
        val info = describe(context, uri) ?: return null
        // A video's audio is lifted out before the file exists, because which
        // container it lands in depends on the codec inside the video.
        val file = if (info.video) {
            extractAudio(context, uri, tapeId) ?: return null
        } else {
            val copied = TapeAudio.newFile(context, tapeId, info.extension)
            if (!copyAsIs(context, uri, copied)) {
                copied.delete()
                return null
            }
            copied
        }

        val seconds = durationOf(file.absolutePath)
        if (seconds <= 0) {
            file.delete()
            return null
        }
        val bars = waveOf(context, Uri.fromFile(file), seconds)
        ImportedTape(
            fileName = file.name,
            seconds = seconds,
            bars = bars,
            displayName = info.displayName,
            takenAt = info.lastModified,
        )
    }.getOrNull()

    private class SourceInfo(
        val displayName: String,
        val video: Boolean,
        val extension: String,
        val lastModified: Long,
    )

    private fun describe(context: Context, uri: Uri): SourceInfo? {
        var name: String? = null
        var lastModified = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) name = cursor.getString(nameIdx)
                val modIdx = cursor.getColumnIndex("last_modified")
                if (modIdx >= 0 && !cursor.isNull(modIdx)) {
                    lastModified = cursor.getLong(modIdx) * 1000L
                }
            }
        }
        val shown = name ?: uri.lastPathSegment ?: "tape"
        val base = shown.substringBeforeLast('.')
        val ext = shown.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val mime = context.contentResolver.getType(uri).orEmpty()
        // Every container a phone's gallery or a download folder hands out,
        // so one of them is never mistaken for plain audio and copied whole.
        val video = mime.startsWith("video/") ||
            ext in setOf(
                "mp4", "mov", "mkv", "webm", "m4v", "3gp", "3gpp",
                "avi", "flv", "wmv", "mpg", "mpeg", "ts", "m2ts", "mts",
                "rmvb", "rm", "asf", "ogv",
            )
        val audioExt = when {
            mime.startsWith("audio/") && mime.contains("mpeg") -> "mp3"
            mime.startsWith("audio/") && mime.contains("wav") -> "wav"
            mime.startsWith("audio/") && mime.contains("ogg") -> "ogg"
            mime.startsWith("audio/") && mime.contains("flac") -> "flac"
            else -> ext.ifEmpty { "m4a" }
        }
        return SourceInfo(
            displayName = base,
            video = video,
            extension = if (video) "m4a" else audioExt,
            lastModified = if (lastModified > 0L) lastModified else System.currentTimeMillis(),
        )
    }

    /** A plain copy for what is already audio. */
    private fun copyAsIs(context: Context, uri: Uri, target: java.io.File): Boolean =
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } != null && target.length() > 0
        }.getOrDefault(false)

    /**
     * The container a track's codec can actually live in. MP4 is where AAC and
     * the AMR family belong; Opus and Vorbis belong in WebM, and asking MP4 to
     * hold them is what used to make a .webm of her talking fail. Each video is
     * tried in its natural container first and the other second, so a
     * mislabelled file still gets a second chance.
     */
    private class Container(val extension: String, val format: Int)

    private val Mp4 = Container("m4a", MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    private val Webm = Container("webm", MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM)

    private fun containersFor(mime: String): List<Container> = when {
        mime == "audio/opus" || mime == "audio/vorbis" -> listOf(Webm, Mp4)
        else -> listOf(Mp4, Webm)
    }

    /**
     * Lifts the audio track out of a video — into MP4 or WebM, whichever its
     * codec calls home — and answers the file it made. Samples move untouched:
     * the track's own bits, a lighter container, which is why this is quick and
     * why a video with no readable audio track answers null rather than a file
     * of silence.
     */
    private fun extractAudio(context: Context, uri: Uri, tapeId: String): java.io.File? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val candidate = extractor.getTrackFormat(i)
                if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    format = candidate
                    break
                }
            }
            if (trackIndex < 0 || format == null) return null
            extractor.selectTrack(trackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            for (container in containersFor(mime)) {
                val file = TapeAudio.newFile(context, tapeId, container.extension)
                if (muxTrack(extractor, format, file, container.format)) return file
                file.delete()
                // The other container gets a second pass over the same samples.
                runCatching { extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC) }
            }
            return null
        } catch (e: Exception) {
            return null
        } finally {
            runCatching { extractor.release() }
        }
    }

    /** One pass: the selected track's samples written into [file]. */
    private fun muxTrack(
        extractor: MediaExtractor,
        format: MediaFormat,
        file: java.io.File,
        outputFormat: Int,
    ): Boolean {
        val muxer = runCatching { MediaMuxer(file.absolutePath, outputFormat) }.getOrNull() ?: return false
        try {
            val outTrack = muxer.addTrack(format)
            muxer.start()

            val buffer = java.nio.ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            var wrote = false
            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                info.presentationTimeUs = extractor.sampleTime
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(outTrack, buffer, info)
                wrote = true
                extractor.advance()
            }
            muxer.stop()
            return wrote
        } catch (e: Exception) {
            return false
        } finally {
            runCatching { muxer.release() }
        }
    }

    private fun durationOf(path: String): Int =
        runCatching {
            val player = MediaPlayer()
            try {
                player.setDataSource(path)
                player.prepare()
                (player.duration / 1000).coerceIn(1, 3600)
            } finally {
                runCatching { player.release() }
            }
        }.getOrDefault(0)

    /**
     * The wave, read from the samples: the decoder walks the track and the
     * loudness of each tenth of a second becomes a bar. A long tape is
     * read whole but carried away in a hundred-and-twenty bars — the
     * meter's own resolution.
     */
    private fun waveOf(context: Context, uri: Uri, seconds: Int): List<Int> {
        val buckets = seconds * 10
        if (buckets <= 0) return emptyList()
        val sums = LongArray(buckets)
        val counts = IntArray(buckets)

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var trackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIndex = i
                    format = f
                    break
                }
            }
            if (trackIndex < 0) return emptyList()
            extractor.selectTrack(trackIndex)
            val mime = format!!.getString(MediaFormat.KEY_MIME)!!
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var saw = 0
            while (!outputDone && saw < 60_000) {
                saw++
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> Unit
                    MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                    else -> {
                        if (outIndex >= 0) {
                            val buffer = codec.getOutputBuffer(outIndex)!!
                            val shorts = buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val chunk = ShortArray(info.size / 2)
                            shorts.get(chunk)
                            val bucket = ((info.presentationTimeUs / 100_000).toInt())
                            if (bucket in 0 until buckets) {
                                var sum = 0.0
                                var n = 0
                                var i = 0
                                while (i < chunk.size) {
                                    sum += (chunk[i] / 32768.0) * (chunk[i] / 32768.0)
                                    n++
                                    i += 8
                                }
                                if (n > 0) {
                                    sums[bucket] += (Math.sqrt(sum / n) * 100000).toLong()
                                    counts[bucket]++
                                }
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // A wave that cannot be read is a wall of seeded silence, not a
            // failed import.
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }

        val raw = sums.indices.map { i ->
            if (counts[i] > 0) {
                ((sums[i] / counts[i]) / 1000f).coerceIn(4f, 100f).toInt()
            } else {
                4
            }
        }
        if (raw.isEmpty()) return emptyList()
        // Carried at the meter's own resolution.
        val target = 120
        val step = raw.size / target + 1
        return raw.withIndex()
            .groupBy { it.index / step }
            .values
            .map { group -> (group.sumOf { it.value } / group.size).coerceIn(4, 100) }
            .take(target)
    }
}
