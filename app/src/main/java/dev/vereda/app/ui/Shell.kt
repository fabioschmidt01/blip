package dev.vereda.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.vereda.app.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.vereda.app.service.MeshService
import dev.vereda.core.RadioPhase

@Composable
fun VeredaRoot(vm: RadioViewModel = viewModel()) {
    val ready by vm.ready.collectAsStateWithLifecycle()
    VeredaTheme {
        Box(
            Modifier
                .fillMaxSize()
                .background(Night)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            Canvas(Modifier.fillMaxWidth().height(320.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Phosphor.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.72f, 0f),
                        radius = size.width * 0.85f,
                    ),
                    radius = size.width * 0.85f,
                    center = Offset(size.width * 0.72f, 0f),
                )
            }
            if (!ready) {
                OnboardingScreen(vm::completeOnboarding)
            } else {
                val context = LocalContext.current
                LaunchedEffect(Unit) { MeshService.start(context) }
                Home(vm)
            }
        }
    }
}

@Composable
private fun Home(vm: RadioViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val tabbed = !route.startsWith("dm/")
    val phase by vm.phase.collectAsStateWithLifecycle()
    val links by vm.linkCount.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (tabbed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.blip_logo),
                    contentDescription = "Blip",
                    modifier = Modifier.size(42.dp),
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Blip", color = Bone, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                    Text(headline(phase, links, status), color = BoneDim, fontFamily = Mono, fontSize = 11.sp)
                }
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(Surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Lamp(phase, links)
                    Spacer(Modifier.width(8.dp))
                    Text(chip(phase, links), color = Bone, fontFamily = Mono, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        NavHost(
            navController = nav,
            startDestination = "canal",
            modifier = Modifier.weight(1f),
        ) {
            composable("canal") { ChannelScreen(vm) }
            composable("lugares") { PlacesScreen(vm) { nav.navigate("canal") } }
            composable("pessoas") {
                val peers by vm.peers.collectAsStateWithLifecycle()
                PeopleScreen(peers) { id -> nav.navigate("dm/$id") }
            }
            composable("radio") { RadioScreen(vm) }
            composable("dm/{peerId}") { back ->
                ConversationScreen(
                    vm = vm,
                    peerId = back.arguments?.getString("peerId").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }
        if (tabbed) {
            Tabs(route = route.ifBlank { "canal" }, nav = nav)
        }
    }
}

@Composable
private fun Tabs(route: String, nav: NavHostController) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(Pill)
            .background(Surface)
            .padding(4.dp),
    ) {
        listOf("canal" to "Canal", "lugares" to "Mapa", "pessoas" to "Pessoas", "radio" to "Rádio").forEach { (target, label) ->
            val selected = route == target
            Box(
                Modifier
                    .weight(1f)
                    .clip(Pill)
                    .background(if (selected) Phosphor else Color.Transparent)
                    .clickable {
                        nav.navigate(target) {
                            popUpTo("canal") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Ink else BoneDim,
                    fontFamily = Mono,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

private fun chip(phase: RadioPhase, links: Int): String {
    return when (phase) {
        RadioPhase.FAILED -> "erro"
        RadioPhase.OFF -> "off"
        RadioPhase.STARTING -> "..."
        RadioPhase.ON -> if (links == 0) "no ar" else "$links"
    }
}

private fun headline(phase: RadioPhase, links: Int, status: String): String {
    return when (phase) {
        RadioPhase.FAILED -> status
        RadioPhase.OFF -> "Rádio desligado"
        RadioPhase.STARTING -> "Ligando o rádio"
        RadioPhase.ON -> when (links) {
            0 -> "Nenhum rádio no alcance"
            1 -> "1 rádio no alcance"
            else -> "$links rádios no alcance"
        }
    }
}
