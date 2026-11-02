package dev.vereda.app.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vereda.app.data.MessageEntity
import dev.vereda.core.RadioPhase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clock: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val BubbleMine = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 8.dp)
private val BubbleOther = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 22.dp)
val PanelShape = RoundedCornerShape(22.dp)
val Pill = RoundedCornerShape(28.dp)

fun formatTime(epoch: Long): String {
    return Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(clock)
}

fun statusLabel(message: MessageEntity): String? {
    if (!message.outgoing) return null
    return when (message.status) {
        "pending" -> "a caminho"
        "delivered" -> "entregue"
        "failed" -> "não saiu"
        else -> null
    }
}

@Composable
fun Lamp(phase: RadioPhase, links: Int, modifier: Modifier = Modifier) {
    val searching = phase == RadioPhase.STARTING || (phase == RadioPhase.ON && links == 0)
    val pulse by rememberInfiniteTransition(label = "lamp").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "lamp-alpha",
    )
    val color = when (phase) {
        RadioPhase.FAILED -> Clay
        RadioPhase.OFF -> BoneDim
        else -> Phosphor
    }
    Canvas(
        modifier
            .size(8.dp)
            .semantics { contentDescription = "estado do rádio" },
    ) {
        drawCircle(color.copy(alpha = if (searching) pulse else 1f))
    }
}

@Composable
fun TranscriptLine(
    time: String,
    name: String,
    body: String,
    mine: Boolean,
    status: String?,
) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(
            Modifier
                .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 300.dp)
                .clip(if (mine) BubbleMine else BubbleOther)
                .background(if (mine) Phosphor.copy(alpha = 0.16f) else Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                name,
                color = if (mine) Phosphor else Bone,
                fontFamily = Mono,
                fontSize = 11.sp,
                letterSpacing = 0.4.sp,
            )
            Text(
                body,
                color = Bone,
                fontFamily = Display,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                Modifier.align(Alignment.End).padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (status != null) {
                    Text(
                        status,
                        color = if (status == "não saiu") Clay else BoneDim,
                        fontFamily = Mono,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Text(time, color = BoneDim, fontFamily = Mono, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    placeholder: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(Pill)
            .background(Surface)
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            textStyle = TextStyle(color = Bone, fontFamily = Display, fontSize = 16.sp),
            cursorBrush = SolidColor(Phosphor),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            singleLine = true,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = BoneDim, fontFamily = Display, fontSize = 16.sp)
                    }
                    inner()
                }
            },
        )
        Box(
            Modifier
                .padding(start = 8.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Phosphor)
                .semantics { contentDescription = "Enviar" }
                .clickable(onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Text("↑", color = Ink, fontSize = 20.sp, fontFamily = Mono, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = BoneDim, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.4.sp)
}

@Composable
fun EmptyLine(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 36.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, color = BoneDim, fontFamily = Display, fontSize = 18.sp, lineHeight = 26.sp)
    }
}

@Composable
fun MessageCard(message: MessageEntity, name: String, mine: Boolean, onWave: (String, String) -> Unit = { _, _ -> }) {
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(
            Modifier
                .align(if (mine) Alignment.CenterEnd else Alignment.CenterStart)
                .widthIn(max = 280.dp)
                .clip(if (mine) BubbleMine else BubbleOther)
                .background(if (mine) Phosphor.copy(alpha = 0.16f) else Surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(name, color = if (mine) Phosphor else Bone, fontFamily = Mono, fontSize = 11.sp)
            when (message.kind) {
                "image" -> {
                    val bitmap by produceState<ImageBitmap?>(null, message.mediaPath) {
                        val path = message.mediaPath
                        value = withContext(Dispatchers.IO) { path?.let(::decodePreview) }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!,
                            contentDescription = "Imagem",
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                }
                "audio" -> AudioMessage(message, mine, onWave)
                else -> {
                    Text(
                        message.body,
                        color = Bone,
                        fontFamily = Display,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            val status = statusLabel(message)
            Row(Modifier.align(Alignment.End).padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (status != null) {
                    Text(status, color = if (status == "não saiu") Clay else BoneDim, fontFamily = Mono, fontSize = 10.sp, modifier = Modifier.padding(end = 8.dp))
                }
                Text(formatTime(message.sentAt), color = BoneDim, fontFamily = Mono, fontSize = 10.sp)
            }
        }
    }
}

private fun decodePreview(path: String): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    val options = BitmapFactory.Options().apply {
        inSampleSize = when {
            longest > 1800 -> 4
            longest > 900 -> 2
            else -> 1
        }
    }
    return BitmapFactory.decodeFile(path, options)?.asImageBitmap()
}

@Composable
fun ScreenTitle(kicker: String, title: String) {
    Column(Modifier.padding(bottom = 14.dp)) {
        SectionLabel(kicker)
        Text(
            title,
            color = Bone,
            fontFamily = Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 34.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
