@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package dev.vereda.app.ui

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import dev.vereda.core.Geohash
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

private val levels = listOf(2 to "País", 4 to "Estado", 5 to "Cidade", 6 to "Bairro")

@Composable
private fun Coverage(
    map: MapView,
    revision: androidx.compose.runtime.MutableIntState,
    precision: Int,
) {
    val tick = revision.intValue
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInteropFilter { false },
    ) {
        if (tick < 0) return@Canvas
        val center = map.mapCenter
        val box = Geohash.box(Geohash.encode(center.latitude, center.longitude, precision)) ?: return@Canvas
        val point = android.graphics.Point()
        fun corner(latitude: Double, longitude: Double): Offset {
            map.projection.toPixels(GeoPoint(latitude, longitude), point)
            return Offset(point.x.toFloat(), point.y.toFloat())
        }
        val path = Path().apply {
            val northWest = corner(box.north, box.west)
            moveTo(northWest.x, northWest.y)
            val northEast = corner(box.north, box.east)
            lineTo(northEast.x, northEast.y)
            val southEast = corner(box.south, box.east)
            lineTo(southEast.x, southEast.y)
            val southWest = corner(box.south, box.west)
            lineTo(southWest.x, southWest.y)
            close()
        }
        drawPath(path, Phosphor.copy(alpha = 0.22f))
        drawPath(path, Phosphor.copy(alpha = 0.92f), style = Stroke(width = 2.5.dp.toPx()))
    }
}

@Composable
fun PlacesScreen(vm: RadioViewModel, onOpenChannel: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var precision by remember { mutableIntStateOf(5) }
    var hash by remember { mutableStateOf("-----") }
    var query by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<PlaceHit>>(emptyList()) }
    var placeName by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var looking by remember { mutableStateOf(false) }
    var nameTicket by remember { mutableIntStateOf(0) }
    val revision = remember { mutableIntStateOf(0) }
    val map = remember {
        Configuration.getInstance().userAgentValue = "Blip/0.1 (Android)"
        Configuration.getInstance().load(context, context.getSharedPreferences("blip-map", 0))
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(3.0)
            controller.setCenter(GeoPoint(0.0, 20.0))
        }
    }
    DisposableEffect(map) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                revision.intValue++
                return false
            }
            override fun onZoom(event: ZoomEvent): Boolean {
                revision.intValue++
                return false
            }
        }
        map.addMapListener(listener)
        map.onResume()
        onDispose {
            map.removeMapListener(listener)
            map.onPause()
        }
    }

    fun paint(fit: Boolean): String {
        val center = map.mapCenter
        val code = Geohash.encode(center.latitude, center.longitude, precision)
        val box = Geohash.box(code)
        if (fit && box != null && map.width > 0 && map.height > 0) {
            val latHalf = abs(box.north - center.latitude).coerceAtLeast(abs(center.latitude - box.south)) * 2.7
            val lonHalf = abs(box.east - center.longitude).coerceAtLeast(abs(center.longitude - box.west)) * 2.7
            val north = (center.latitude + latHalf).coerceIn(-84.0, 84.0)
            val south = (center.latitude - latHalf).coerceIn(-84.0, 84.0)
            val east = (center.longitude + lonHalf).coerceIn(-179.0, 179.0)
            val west = (center.longitude - lonHalf).coerceIn(-179.0, 179.0)
            if (north > south && east > west) {
                map.zoomToBoundingBox(BoundingBox(north, east, south, west), true, 28)
            }
        }
        revision.intValue++
        return code
    }

    DisposableEffect(map) {
        var lastHeight = 0
        val layout = android.view.View.OnLayoutChangeListener { _, _, _, _, bottom, _, _, _, _ ->
            if (bottom > 0 && abs(bottom - lastHeight) > 48) {
                lastHeight = bottom
                map.post { hash = paint(fit = true) }
            }
        }
        map.addOnLayoutChangeListener(layout)
        onDispose { map.removeOnLayoutChangeListener(layout) }
    }

    fun settle() {
        val ticket = nameTicket + 1
        nameTicket = ticket
        val center = map.mapCenter
        val chosen = precision
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                try {
                    PlaceLookup.label(center.latitude, center.longitude, chosen)
                } catch (_: Exception) {
                    ""
                }
            }
            if (ticket == nameTicket && name.isNotBlank()) placeName = name
        }
    }

    fun search() {
        val clean = query.trim()
        if (clean.length < 2) {
            note = "Escreve um país, uma cidade ou um bairro."
            return
        }
        focus.clearFocus()
        looking = true
        note = null
        hits = emptyList()
        scope.launch {
            val found = withContext(Dispatchers.IO) {
                try {
                    PlaceLookup.search(clean)
                } catch (_: Exception) {
                    null
                }
            }
            looking = false
            when {
                found == null -> note = "Sem rede para buscar."
                found.isEmpty() -> note = "Não achei esse lugar."
                else -> hits = found
            }
        }
    }

    fun go(hit: PlaceHit) {
        focus.clearFocus()
        hits = emptyList()
        note = null
        query = hit.name.substringBefore(",")
        placeName = query
        map.controller.setCenter(GeoPoint(hit.latitude, hit.longitude))
        hash = paint(fit = true)
        settle()
    }

    LaunchedEffect(map) {
        repeat(12) {
            if (map.width > 0) return@repeat
            delay(40)
        }
        hash = paint(fit = true)
        settle()
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScreenTitle("LUGARES", "Mapa")
        Text(
            "O pin marca o lugar. A área verde é o que essa sala cobre.",
            color = BoneDim,
            fontFamily = Display,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = query,
                onValueChange = {
                    query = it
                    if (note != null) note = null
                },
                singleLine = true,
                textStyle = TextStyle(color = Bone, fontFamily = Display, fontSize = 16.sp),
                cursorBrush = SolidColor(Phosphor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier
                    .weight(1f)
                    .clip(Pill)
                    .background(Surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("País, cidade ou bairro", color = BoneDim, fontFamily = Display, fontSize = 16.sp)
                        }
                        inner()
                    }
                },
            )
            Text(
                if (looking) "…" else "Buscar",
                color = Ink,
                fontFamily = Mono,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(Pill)
                    .background(Phosphor)
                    .clickable(enabled = !looking, onClick = ::search)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
        if (note != null) {
            Text(note ?: "", color = Clay, fontFamily = Display, fontSize = 14.sp)
        }
        hits.forEach { hit ->
            Text(
                hit.name,
                color = Bone,
                fontFamily = Display,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface)
                    .clickable { go(hit) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp)
                .weight(1f)
                .clip(RoundedCornerShape(22.dp)),
        ) {
            AndroidView(
                factory = { map },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.setOnTouchListener { _, event ->
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            focus.clearFocus()
                            if (hits.isNotEmpty()) hits = emptyList()
                        }
                        if (event.action == MotionEvent.ACTION_UP) {
                            hash = Geohash.encode(view.mapCenter.latitude, view.mapCenter.longitude, precision)
                            settle()
                        }
                        false
                    }
                },
            )
            Coverage(map, revision, precision)
            androidx.compose.foundation.Canvas(
                Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .pointerInteropFilter { false },
            ) {
                val tip = Offset(size.width / 2f, size.height / 2f)
                val head = tip + Offset(0f, -15.dp.toPx())
                drawLine(Phosphor, head + Offset(0f, 6.dp.toPx()), tip, strokeWidth = 3.dp.toPx())
                drawCircle(Phosphor, radius = 7.dp.toPx(), center = head)
                drawCircle(Ink, radius = 2.5.dp.toPx(), center = head)
                drawCircle(Phosphor, radius = 4.dp.toPx(), center = tip)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            levels.forEach { (value, label) ->
                Text(
                    label,
                    color = if (precision == value) Ink else Bone,
                    fontFamily = Mono,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(Pill)
                        .background(if (precision == value) Phosphor else Surface)
                        .clickable {
                            precision = value
                            hash = paint(fit = true)
                            settle()
                        }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        Text(
            placeName.ifBlank { "Arrasta o mapa" },
            color = Bone,
            fontFamily = Display,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(hash, color = Phosphor, fontFamily = Mono, fontSize = 13.sp)
        Text(
            "Falar deste lugar",
            color = Ink,
            fontFamily = Display,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(Pill)
                .background(Phosphor)
                .clickable {
                    val center = map.mapCenter
                    val code = Geohash.encode(center.latitude, center.longitude, precision)
                    vm.openPlace(code)
                    onOpenChannel()
                }
                .padding(vertical = 14.dp),
        )
    }
}
