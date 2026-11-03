package dev.vereda.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
            val showIntro by vm.showIntro.collectAsStateWithLifecycle()
            if (showIntro) IntroDialog(vm::dismissIntro)
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
    val reach by vm.reach.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = menuOpen) { menuOpen = false }
    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (tabbed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MenuButton { menuOpen = true }
                Spacer(Modifier.width(10.dp))
                Image(
                    painter = painterResource(R.drawable.blip_logo),
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(42.dp),
                    alignment = Alignment.Center,
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.app_name), color = Bone, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
                    Text(headline(phase, links, status, reach), color = BoneDim, fontFamily = Mono, fontSize = 11.sp)
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
            composable("ajustes") { SettingsScreen(vm) }
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
    if (menuOpen) {
        AppMenu(
            route = route,
            onClose = { menuOpen = false },
            onPick = { target ->
                menuOpen = false
                if (route != target) {
                    nav.navigate(target) {
                        popUpTo("canal") { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            },
        )
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
        listOf(
            "canal" to stringResource(R.string.tab_channel),
            "radio" to stringResource(R.string.tab_radio),
            "lugares" to stringResource(R.string.tab_map),
            "pessoas" to stringResource(R.string.tab_people),
        ).forEach { (target, label) ->
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

@Composable
private fun MenuButton(onClick: () -> Unit) {
    val label = stringResource(R.string.cd_menu)
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Surface)
            .semantics { contentDescription = label }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            repeat(3) {
                Box(
                    Modifier
                        .padding(vertical = 2.dp)
                        .width(16.dp)
                        .height(2.dp)
                        .background(Bone),
                )
            }
        }
    }
}

@Composable
private fun AppMenu(route: String, onClose: () -> Unit, onPick: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onClose),
        )
        Column(
            Modifier
                .fillMaxHeight()
                .width(280.dp)
                .background(Night)
                .padding(start = 20.dp, end = 16.dp, top = 28.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                color = Bone,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
            )
            Spacer(Modifier.height(28.dp))
            MenuRow(stringResource(R.string.settings_title), route == "ajustes") { onPick("ajustes") }
        }
    }
}

@Composable
private fun MenuRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Ink else Bone,
        fontFamily = Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(Pill)
            .background(if (selected) Phosphor else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun chip(phase: RadioPhase, links: Int): String {
    return when (phase) {
        RadioPhase.FAILED -> stringResource(R.string.chip_error)
        RadioPhase.OFF -> stringResource(R.string.chip_off)
        RadioPhase.STARTING -> stringResource(R.string.chip_starting)
        RadioPhase.ON -> if (links == 0) stringResource(R.string.chip_on_air) else "$links"
    }
}

@Composable
private fun headline(phase: RadioPhase, links: Int, status: String, reach: String): String {
    return when (phase) {
        RadioPhase.FAILED -> status
        RadioPhase.OFF -> stringResource(R.string.radio_off)
        RadioPhase.STARTING -> stringResource(R.string.radio_starting)
        RadioPhase.ON -> when {
            reach == dev.vereda.app.data.Profile.REACH_INTERNET -> status
            links == 0 -> stringResource(R.string.radio_none)
            links == 1 -> stringResource(R.string.radio_one)
            else -> stringResource(R.string.radio_many, links)
        }
    }
}
