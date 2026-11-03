package dev.vereda.app.ui

import android.media.AudioAttributes
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vereda.app.R
import dev.vereda.app.data.MessageEntity
import dev.vereda.core.Waveform
import java.io.File
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_NOTE_MS = 32_000
private const val MAX_NOTE_BYTES = 96_000

data class VoiceTake(val file: File, val wave: String)

class VoiceCapture(context: android.content.Context) {
    private val cache = context.applicationContext.cacheDir
    private val app = context.applicationContext
    var levels by mutableStateOf(IntArray(Waveform.BARS) { Waveform.REST })
        private set
    var elapsedMs by mutableStateOf(0L)
        private set
    var active by mutableStateOf(false)
        private set

    @Volatile
    var hitLimit: Boolean = false
        private set

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private val samples = ArrayList<Int>(480)
    private var startedAt = 0L

    fun start(): Boolean {
        if (recorder != null) discard()
        val target = File(cache, "blip-${System.currentTimeMillis()}.m4a")
        return try {
            val created = MediaRecorder(app).apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioChannels(1)
                setAudioEncodingBitRate(24_000)
                setAudioSamplingRate(16_000)
                setOutputFile(target.absolutePath)
                setMaxDuration(MAX_NOTE_MS)
                setMaxFileSize(MAX_NOTE_BYTES.toLong())
                setOnInfoListener { _, what, _ ->
                    if (
                        what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED ||
                        what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED
                    ) {
                        hitLimit = true
                    }
                }
                prepare()
                start()
            }
            recorder = created
            file = target
            samples.clear()
            startedAt = SystemClock.elapsedRealtime()
            elapsedMs = 0L
            hitLimit = false
            levels = IntArray(Waveform.BARS) { Waveform.REST }
            active = true
            true
        } catch (_: Exception) {
            target.delete()
            false
        }
    }

    fun sample() {
        val current = recorder ?: return
        val amplitude = try {
            current.maxAmplitude
        } catch (_: RuntimeException) {
            0
        }
        samples.add(Waveform.level(amplitude))
        val shown = IntArray(Waveform.BARS)
        val from = (samples.size - Waveform.BARS).coerceAtLeast(0)
        val pad = Waveform.BARS - (samples.size - from)
        for (index in from until samples.size) {
            shown[pad + index - from] = samples[index]
        }
        levels = shown
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        elapsedMs = elapsed
        if (elapsed >= MAX_NOTE_MS) hitLimit = true
    }

    fun stop(): VoiceTake? {
        val current = recorder ?: return null
        val out = file
        recorder = null
        file = null
        active = false
        hitLimit = false
        try {
            current.stop()
        } catch (_: Exception) {
            // The recorder already stopped itself when it hit the time or size cap.
        }
        current.release()
        val taken = samples.toList()
        samples.clear()
        levels = IntArray(Waveform.BARS) { Waveform.REST }
        elapsedMs = 0L
        if (out == null || !out.exists() || out.length() < 500 || taken.size < 3) {
            out?.delete()
            return null
        }
        return VoiceTake(out, Waveform.encode(taken))
    }

    fun discard() {
        val current = recorder ?: return
        recorder = null
        active = false
        hitLimit = false
        file?.delete()
        file = null
        samples.clear()
        levels = IntArray(Waveform.BARS) { Waveform.REST }
        elapsedMs = 0L
        try {
            current.stop()
        } catch (_: Exception) {
        }
        current.release()
    }
}

object AudioPeaks {
    fun read(path: String): IntArray? {
        val chunks = chunkPeaks(path) ?: return null
        if (chunks.isEmpty()) return null
        if (chunks.max() < 400) return IntArray(Waveform.BARS) { Waveform.REST }
        return Waveform.decode(Waveform.encode(chunks))
    }

    private fun chunkPeaks(path: String): List<Int>? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            val source = MediaExtractor()
            extractor = source
            source.setDataSource(path)
            var track = -1
            var format: MediaFormat? = null
            for (index in 0 until source.trackCount) {
                val candidate = source.getTrackFormat(index)
                val mime = candidate.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    track = index
                    format = candidate
                    break
                }
            }
            if (track < 0 || format == null) return null
            source.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val decoder = MediaCodec.createDecoderByType(mime)
            codec = decoder
            decoder.configure(format, null, null, 0)
            decoder.start()
            val peaks = ArrayList<Int>(128)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var stalls = 0
            val deadline = SystemClock.elapsedRealtime() + 2_500
            while (stalls < 30 && SystemClock.elapsedRealtime() < deadline) {
                if (!inputDone) {
                    val inIndex = decoder.dequeueInputBuffer(4_000)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex) ?: return peaks
                        val read = source.readSampleData(buffer, 0)
                        if (read < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, read, source.sampleTime.coerceAtLeast(0), 0)
                            source.advance()
                        }
                    }
                }
                val outIndex = decoder.dequeueOutputBuffer(info, 4_000)
                if (outIndex >= 0) {
                    stalls = 0
                    val buffer = decoder.getOutputBuffer(outIndex)
                    if (buffer != null && info.size >= 2) {
                        val view = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                        view.position(info.offset)
                        view.limit(info.offset + info.size - (info.size % 2))
                        var peak = 0
                        while (view.remaining() >= 2) {
                            val sample = view.short.toInt()
                            val abs = if (sample < 0) -sample else sample
                            if (abs > peak) peak = abs
                        }
                        peaks.add(peak)
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                } else {
                    stalls += 1
                }
            }
            return peaks
        } catch (_: Exception) {
            return null
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            codec?.release()
            extractor?.release()
        }
    }
}

internal object VoicePlayback {
    var activePath by mutableStateOf<String?>(null)
    var playing by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun play(path: String) {
        if (activePath == path && playing) return
        if (activePath == path && player != null) {
            try {
                player?.seekTo(0)
                player?.start()
                playing = true
                player?.let { tick(it) }
            } catch (_: Exception) {
            }
            return
        }
        toggle(path)
    }

    fun toggle(path: String) {
        if (!File(path).exists()) return
        val current = player
        if (activePath == path && current != null) {
            try {
                if (playing) {
                    current.pause()
                    playing = false
                    ticker?.cancel()
                } else {
                    current.start()
                    playing = true
                    tick(current)
                }
            } catch (_: Exception) {
                release()
            }
            return
        }
        release()
        val created = MediaPlayer()
        player = created
        activePath = path
        progress = 0f
        try {
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            created.setDataSource(path)
            created.setOnPreparedListener { ready ->
                if (player !== created) return@setOnPreparedListener
                ready.start()
                playing = true
                tick(created)
            }
            created.setOnCompletionListener {
                if (player !== created) return@setOnCompletionListener
                playing = false
                progress = 0f
                ticker?.cancel()
                try {
                    created.seekTo(0)
                } catch (_: Exception) {
                }
            }
            created.setOnErrorListener { _, _, _ ->
                if (player === created) {
                    playing = false
                    progress = 0f
                }
                true
            }
            created.prepareAsync()
        } catch (_: Exception) {
            created.release()
            if (player === created) player = null
            activePath = null
        }
    }

    private fun tick(current: MediaPlayer) {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive && playing && player === current) {
                val duration = current.duration
                if (duration > 0) progress = current.currentPosition.toFloat() / duration
                delay(48)
            }
        }
    }

    private fun release() {
        ticker?.cancel()
        player?.run {
            setOnPreparedListener(null)
            setOnCompletionListener(null)
            setOnErrorListener(null)
            try {
                stop()
            } catch (_: Exception) {
            }
            release()
        }
        player = null
        playing = false
        progress = 0f
        activePath = null
    }
}

@Composable
fun LiveWave(capture: VoiceCapture) {
    val levels = capture.levels
    val elapsed = capture.elapsedMs
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(Pill)
            .background(Surface)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(Clay))
        WaveBars(
            levels = levels,
            progress = null,
            active = Phosphor,
            idle = Phosphor,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(36.dp),
        )
        Text(clock(elapsed), color = Bone, fontFamily = Mono, fontSize = 12.sp)
    }
}

@Composable
fun AudioMessage(message: MessageEntity, mine: Boolean, onWave: (String, String) -> Unit) {
    val path = message.mediaPath
    var bars by remember(message.id) { mutableStateOf(Waveform.decode(message.body)) }
    LaunchedEffect(message.id, path) {
        if (bars != null || path == null) return@LaunchedEffect
        val decoded = withContext(Dispatchers.IO) { AudioPeaks.read(path) } ?: return@LaunchedEffect
        bars = decoded
        onWave(message.id, decoded.joinToString(","))
    }
    val shown = bars ?: remember { IntArray(Waveform.BARS) { Waveform.REST } }
    val duration = remember(path) { if (path == null) 0L else mediaDuration(path) }
    val activePath = VoicePlayback.activePath
    val isThis = path != null && activePath == path
    val playing = isThis && VoicePlayback.playing
    val held = if (isThis) VoicePlayback.progress else 0f
    val showHead = playing || (isThis && held > 0.01f)
    val playLabel = stringResource(if (playing) R.string.cd_pause else R.string.cd_play)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .padding(top = 8.dp, end = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(if (mine) Phosphor else SurfaceHigh)
                .semantics { contentDescription = playLabel }
                .clickable(enabled = path != null) { if (path != null) VoicePlayback.toggle(path) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (playing) "II" else "▶",
                color = if (mine) Ink else Phosphor,
                fontFamily = Mono,
                fontSize = 12.sp,
            )
        }
        Column(Modifier.padding(top = 8.dp)) {
            WaveBars(
                levels = shown,
                progress = if (showHead) held else null,
                active = Phosphor,
                idle = if (mine) Phosphor.copy(alpha = 0.35f) else Bone.copy(alpha = 0.28f),
                modifier = Modifier.width(168.dp).height(36.dp),
            )
            Text(clock(duration), color = BoneDim, fontFamily = Mono, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun WaveBars(
    levels: IntArray,
    progress: Float?,
    active: Color,
    idle: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val count = levels.size.coerceAtLeast(1)
        val gap = 1.5.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
        val minHeight = 3.dp.toPx()
        for (index in 0 until count) {
            val fraction = (index + 1f) / count
            val played = progress != null && fraction <= progress.coerceIn(0f, 1f) + (1f / count)
            val magnitude = (size.height * (levels[index].coerceIn(0, 100) / 100f)).coerceAtLeast(minHeight)
            val x = index * (barWidth + gap)
            drawRoundRect(
                color = if (progress == null || played) active else idle,
                topLeft = Offset(x, (size.height - magnitude) / 2f),
                size = Size(barWidth, magnitude),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}

internal fun clock(ms: Long): String {
    val seconds = (ms / 1000L).toInt().coerceAtLeast(0)
    val minutes = seconds / 60
    val rest = seconds % 60
    return "$minutes:${rest.toString().padStart(2, '0')}"
}

private fun mediaDuration(path: String): Long {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
    } catch (_: Exception) {
        0L
    } finally {
        retriever.release()
    }
}
