package dev.vereda.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.vereda.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.vereda.app.data.MessageEntity
import kotlinx.coroutines.isActive
import dev.vereda.app.service.MeshService
import dev.vereda.core.PeerSnapshot
import dev.vereda.core.RadioPhase
import dev.vereda.core.TextRules

@Composable
fun OnboardingScreen(onEnter: (String) -> String?) {
    val context = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val name = pending
        if (name != null && RadioPermissions.missingRadio(context).isEmpty()) {
            error = onEnter(name)
        } else {
            error = context.getString(R.string.permission_denied)
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.blip_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(148.dp),
                alignment = Alignment.Center,
                contentScale = ContentScale.Fit,
            )
            Text(
                stringResource(R.string.app_name),
                color = Bone,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 64.sp,
                lineHeight = 64.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.tagline),
                color = BoneDim,
                fontFamily = Display,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.your_name), color = BoneDim, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.2.sp)
            BasicTextField(
                value = draft,
                onValueChange = { if (it.length <= 32) draft = it },
                singleLine = true,
                textStyle = TextStyle(color = Bone, fontFamily = Display, fontSize = 20.sp),
                cursorBrush = SolidColor(Phosphor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PanelShape)
                    .background(Surface)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
            Text(
                stringResource(R.string.permission_why),
                color = BoneDim,
                fontFamily = Display,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            if (error != null) {
                Text(error ?: "", color = Clay, fontFamily = Display, fontSize = 15.sp)
                Text(
                    stringResource(R.string.open_settings),
                    color = Phosphor,
                    fontFamily = Mono,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                )
            }
            Button(
                onClick = {
                    val clean = TextRules.nickname(draft)
                    if (clean == null) {
                        error = context.getString(R.string.name_short)
                        return@Button
                    }
                    pending = clean
                    val missing = RadioPermissions.missingRadio(context)
                    if (missing.isEmpty()) {
                        error = onEnter(clean)
                    } else {
                        launcher.launch(RadioPermissions.ask().toTypedArray())
                    }
                },
                shape = Pill,
                colors = ButtonDefaults.buttonColors(containerColor = Phosphor, contentColor = Ink),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.start_radio), fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
fun ChannelScreen(vm: RadioViewModel) {
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val title by vm.conversationTitle.collectAsStateWithLifecycle()
    val messagesFlow = remember(conversation) { vm.messagesFor(conversation) }
    val messages by messagesFlow.collectAsStateWithLifecycle(emptyList())
    val peers by vm.peers.collectAsStateWithLifecycle()
    val rooms by vm.rooms.collectAsStateWithLifecycle(emptyList())
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val capture = rememberMic(
        context,
        onTake = { take -> vm.sendAudioCurrent(take.file, take.wave) },
        onShort = { vm.report(context.getString(R.string.hold_longer)) },
    )
    var showCreate by remember { mutableStateOf(false) }
    var lockTarget by remember { mutableStateOf<dev.vereda.app.data.RoomEntity?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.sendImageCurrent(uri)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (!capture.start()) vm.report(context.getString(R.string.mic_fail))
        } else {
            vm.report(context.getString(R.string.mic_denied))
        }
    }
    Column(Modifier.fillMaxSize()) {
        RoomStrip(
            title = title,
            here = conversation == dev.vereda.core.MeshLimits.CONVERSATION_PUBLIC,
            rooms = rooms,
            onHere = vm::openPublic,
            onRoom = { room ->
                if (room.joined || !room.hasPassword) {
                    if (!room.joined) vm.joinRoom(room.id, room.name, "") {}
                    vm.openRoom(room.id, room.name)
                } else {
                    lockTarget = room
                }
            },
            onCreate = { showCreate = true },
            onClear = { vm.clearConversation(conversation) },
        )
        val youLabel = stringResource(R.string.you)
        TalkScreen(
            messages = messages,
            title = null,
            placeholder = if (conversation == dev.vereda.core.MeshLimits.CONVERSATION_PUBLIC) {
                stringResource(R.string.say_channel)
            } else {
                stringResource(R.string.say_room, title)
            },
            banner = banner,
            empty = stringResource(R.string.empty_channel),
            nameOf = { message ->
                when {
                    message.senderId == vm.selfId -> nickname.ifBlank { youLabel }
                    else -> peers.firstOrNull { it.id == message.senderId }?.nickname ?: message.senderId.take(8)
                }
            },
            mine = { it.senderId == vm.selfId },
            onSend = vm::sendCurrent,
            onClearBanner = vm::clearBanner,
            onPickImage = { pickImage.launch("image/*") },
            capture = capture,
            onToggleRecord = {
                toggleMic(
                    context,
                    capture,
                    micPermission,
                    onTake = { take -> vm.sendAudioCurrent(take.file, take.wave) },
                    onError = vm::report,
                )
            },
            onWave = vm::storeWave,
            modifier = Modifier.weight(1f),
        )
    }
    if (showCreate) {
        RoomDialog(
            title = stringResource(R.string.new_room_title),
            onDismiss = { showCreate = false },
            onConfirm = { name, password ->
                showCreate = false
                vm.createRoom(name, password)
            },
        )
    }
    val locked = lockTarget
    if (locked != null) {
        RoomDialog(
            title = locked.name,
            passwordOnly = true,
            onDismiss = { lockTarget = null },
            onConfirm = { _, password ->
                vm.joinRoom(locked.id, locked.name, password) { ok ->
                    if (ok) lockTarget = null
                }
            },
        )
    }
}

@Composable
fun ConversationScreen(vm: RadioViewModel, peerId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val capture = rememberMic(
        context,
        onTake = { take -> vm.sendAudio(peerId, take.file, take.wave) },
        onShort = { vm.report(context.getString(R.string.hold_longer)) },
    )
    val pickDirect = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.sendImage(peerId, uri)
    }
    val directMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (!capture.start()) vm.report(context.getString(R.string.mic_fail))
        } else {
            vm.report(context.getString(R.string.mic_denied))
        }
    }
    val thread = remember(peerId) { vm.direct(peerId) }
    val messages by thread.collectAsStateWithLifecycle(emptyList())
    val peers by vm.peers.collectAsStateWithLifecycle()
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val peer = peers.firstOrNull { it.id == peerId }
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Surface)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", color = Bone, fontFamily = Display, fontSize = 26.sp)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(peer?.nickname ?: peerId.take(8), color = Bone, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
                Text(
                    if (peer?.direct == true) stringResource(R.string.dm_near) else stringResource(R.string.dm_far),
                    color = BoneDim,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                )
            }
        }
        val youLabel = stringResource(R.string.you)
        val radioLabel = stringResource(R.string.peer_radio)
        TalkScreen(
            messages = messages,
            title = null,
            placeholder = stringResource(R.string.dm_placeholder),
            banner = banner,
            empty = stringResource(R.string.dm_empty, peer?.nickname ?: stringResource(R.string.dm_target)),
            nameOf = { message ->
                if (message.senderId == vm.selfId) nickname.ifBlank { youLabel } else peer?.nickname ?: radioLabel
            },
            mine = { it.senderId == vm.selfId },
            onSend = { vm.sendDirect(peerId, it) },
            onClearBanner = vm::clearBanner,
            onPickImage = { pickDirect.launch("image/*") },
            capture = capture,
            onToggleRecord = {
                toggleMic(
                    context,
                    capture,
                    directMic,
                    onTake = { take -> vm.sendAudio(peerId, take.file, take.wave) },
                    onError = vm::report,
                )
            },
            onWave = vm::storeWave,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.clear_chat),
            color = Clay,
            fontFamily = Mono,
            fontSize = 12.sp,
            modifier = Modifier.clickable { vm.clearConversation(peerId) }.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun TalkScreen(
    messages: List<MessageEntity>,
    title: String?,
    placeholder: String,
    banner: String?,
    empty: String,
    nameOf: (MessageEntity) -> String,
    mine: (MessageEntity) -> Boolean,
    onSend: (String) -> Unit,
    onClearBanner: () -> Unit,
    onPickImage: (() -> Unit)? = null,
    capture: VoiceCapture? = null,
    onToggleRecord: (() -> Unit)? = null,
    onWave: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex)
    }
    Column(modifier.fillMaxSize()) {
        if (title != null) {
            Text(title, color = Bone, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp)
        }
        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            if (messages.isEmpty()) {
                item { EmptyLine(empty) }
            }
            items(messages, key = { it.id }) { message ->
                MessageCard(message, nameOf(message), mine(message), onWave)
            }
        }
        if (banner != null) {
            Text(banner, color = Clay, fontFamily = Display, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        val recording = capture?.active == true
        if (capture != null && recording) LiveWave(capture)
        if (onPickImage != null || onToggleRecord != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                if (onPickImage != null) {
                    Text(
                        stringResource(R.string.image),
                        color = Ink,
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(Pill)
                            .background(Phosphor)
                            .clickable(onClick = onPickImage)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
                if (onToggleRecord != null) {
                    Text(
                        if (recording) stringResource(R.string.stop_send) else stringResource(R.string.audio),
                        color = if (recording) Bone else Ink,
                        fontFamily = Mono,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(Pill)
                            .background(if (recording) Clay else Phosphor)
                            .clickable(onClick = onToggleRecord)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Composer(
            value = draft,
            onValueChange = {
                if (it.length <= 800) draft = it
                if (banner != null) onClearBanner()
            },
            onSend = {
                val text = draft
                if (text.isBlank()) return@Composer
                draft = ""
                onSend(text)
            },
            placeholder = placeholder,
        )
    }
}

@Composable
fun PeopleScreen(peers: List<PeerSnapshot>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.people_kicker), stringResource(R.string.people_title))
        Text(stringResource(R.string.people_hint), color = BoneDim, fontFamily = Display, fontSize = 15.sp)
        if (peers.isEmpty()) {
            EmptyLine(stringResource(R.string.people_empty))
            return
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(peers, key = { it.id }) { peer ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(PanelShape)
                        .background(Surface)
                        .clickable { onOpen(peer.id) }
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (peer.direct) Phosphor else SurfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            peer.nickname.take(1).uppercase(),
                            color = if (peer.direct) Ink else Bone,
                            fontFamily = Display,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(peer.nickname, color = Bone, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                        Text(
                            if (peer.direct) stringResource(R.string.in_range) else stringResource(R.string.via_other),
                            color = BoneDim,
                            fontFamily = Mono,
                            fontSize = 12.sp,
                        )
                    }
                    Text("›", color = BoneDim, fontFamily = Display, fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun CanvasDot(solid: Boolean) {
    androidx.compose.foundation.Canvas(Modifier.height(10.dp).width(10.dp)) {
        if (solid) {
            drawCircle(Phosphor)
        } else {
            drawCircle(Phosphor, style = Stroke(width = 1.5.dp.toPx()))
        }
    }
}

@Composable
fun RadioScreen(vm: RadioViewModel) {
    val context = LocalContext.current
    val phase by vm.phase.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val peers by vm.peers.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val messagesFlow = remember { vm.messagesFor(dev.vereda.core.MeshLimits.CONVERSATION_PUBLIC) }
    val messages by messagesFlow.collectAsStateWithLifecycle(emptyList())
    val voices = messages.filter { it.kind == "audio" }
    val capture = rememberMic(
        context,
        onTake = { take -> vm.sendAudio(dev.vereda.core.MeshLimits.CONVERSATION_PUBLIC, take.file, take.wave) },
        onShort = { vm.report(context.getString(R.string.hold_longer)) },
    )
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (!capture.start()) vm.report(context.getString(R.string.mic_fail))
        } else {
            vm.report(context.getString(R.string.mic_denied))
        }
    }
    var primed by remember { mutableStateOf(false) }
    LaunchedEffect(voices.lastOrNull()?.id) {
        val latest = voices.lastOrNull()
        if (!primed) {
            primed = true
            return@LaunchedEffect
        }
        val path = latest?.mediaPath
        if (latest != null && latest.senderId != vm.selfId && path != null && !capture.active) {
            VoicePlayback.play(path)
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(stringResource(R.string.radio_live), stringResource(R.string.radio_title))
        Text(
            stringResource(R.string.radio_hint),
            color = BoneDim,
            fontFamily = Display,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(status, color = if (phase == RadioPhase.FAILED) Clay else Bone, fontFamily = Mono, fontSize = 12.sp)
        if (banner != null) {
            Text(banner ?: "", color = Clay, fontFamily = Display, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (voices.isEmpty()) {
                item { EmptyLine(stringResource(R.string.radio_empty)) }
            }
            items(voices.takeLast(12), key = { it.id }) { message ->
                val name = if (message.senderId == vm.selfId) {
                    stringResource(R.string.you)
                } else {
                    peers.firstOrNull { it.id == message.senderId }?.nickname ?: message.senderId.take(8)
                }
                MessageCard(message, name, message.senderId == vm.selfId, vm::storeWave)
            }
        }
        if (capture.active) LiveWave(capture)
        Spacer(Modifier.height(12.dp))
        TalkButton(
            holding = capture.active,
            onDown = {
                vm.clearBanner()
                if (capture.active) return@TalkButton
                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.RECORD_AUDIO,
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                } else if (!capture.start()) {
                    vm.report(context.getString(R.string.mic_fail))
                }
            },
            onUp = {
                if (!capture.active) return@TalkButton
                val take = capture.stop()
                if (take != null) {
                    vm.sendAudio(dev.vereda.core.MeshLimits.CONVERSATION_PUBLIC, take.file, take.wave)
                } else {
                    vm.report(context.getString(R.string.hold_longer))
                }
            },
        )
    }
}

@Composable
private fun TalkButton(holding: Boolean, onDown: () -> Unit, onUp: () -> Unit) {
    val label = stringResource(if (holding) R.string.radio_release else R.string.radio_hold)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .height(148.dp)
            .clip(PanelShape)
            .background(if (holding) Clay else Phosphor)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onDown()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.all { !it.pressed }) break
                    }
                    onUp()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (holding) Bone else Ink,
            fontFamily = Display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
        )
    }
}

@Composable
private fun PanicDialog(
    word: String,
    onWord: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    var mismatch by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80)
        focus.requestFocus()
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(PanelShape)
                    .background(SurfaceHigh)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.panic),
                    color = Clay,
                    fontFamily = Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp,
                )
                Text(
                    stringResource(R.string.panic_hint),
                    color = if (mismatch) Clay else BoneDim,
                    fontFamily = Display,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                )
                BasicTextField(
                    value = word,
                    onValueChange = {
                        mismatch = false
                        onWord(it)
                    },
                    singleLine = true,
                    textStyle = TextStyle(color = Bone, fontFamily = Mono, fontSize = 18.sp),
                    cursorBrush = SolidColor(Clay),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(Pill)
                        .background(Night)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .focusRequester(focus),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (word.isEmpty()) {
                                Text("LIMPAR", color = BoneDim, fontFamily = Mono, fontSize = 18.sp)
                            }
                            inner()
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(stringResource(R.string.cancel), Surface, Bone, onDismiss)
                    ActionButton(stringResource(R.string.panic_confirm), Clay, Bone) {
                        if (word.trim() == "LIMPAR") onConfirm() else mismatch = true
                    }
                }
            }
        }
    }
}

@Composable
fun IntroDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clip(PanelShape)
                    .background(SurfaceHigh)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    stringResource(R.string.intro_title),
                    color = Bone,
                    fontFamily = Display,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 26.sp,
                )
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        stringResource(R.string.intro_body),
                        color = BoneDim,
                        fontFamily = Display,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                    )
                }
                ActionButton(stringResource(R.string.intro_ok), Phosphor, Ink, onDismiss)
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: RadioViewModel) {
    val context = LocalContext.current
    val reach by vm.reach.collectAsStateWithLifecycle()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val language by vm.language.collectAsStateWithLifecycle()
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    var draft by remember(nickname) { mutableStateOf(nickname) }
    var error by remember { mutableStateOf<String?>(null) }
    var panicOpen by remember { mutableStateOf(false) }
    var panicWord by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            ScreenTitle(stringResource(R.string.settings_kicker), stringResource(R.string.settings_title))
            SectionLabel(stringResource(R.string.reach_title))
            Spacer(Modifier.height(8.dp))
            ReachChoice(
                title = stringResource(R.string.reach_both),
                hint = stringResource(R.string.reach_both_hint),
                selected = reach != dev.vereda.app.data.Profile.REACH_INTERNET,
                onClick = { vm.setReach(dev.vereda.app.data.Profile.REACH_BOTH) },
            )
            Spacer(Modifier.height(8.dp))
            ReachChoice(
                title = stringResource(R.string.reach_internet),
                hint = stringResource(R.string.reach_internet_hint),
                selected = reach == dev.vereda.app.data.Profile.REACH_INTERNET,
                onClick = { vm.setReach(dev.vereda.app.data.Profile.REACH_INTERNET) },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.reach_places),
                color = BoneDim,
                fontFamily = Display,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(20.dp))
            SectionLabel(stringResource(R.string.link_kicker))
            Text(
                stringResource(R.string.link_title),
                color = Bone,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
            )
            Text(status, color = if (phase == RadioPhase.FAILED) Clay else BoneDim, fontFamily = Display, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            val running = phase == RadioPhase.ON || phase == RadioPhase.STARTING
            ActionButton(
                label = if (running) stringResource(R.string.turn_off) else stringResource(R.string.turn_on),
                container = if (running) SurfaceHigh else Phosphor,
                content = if (running) Bone else Ink,
            ) {
                if (running) MeshService.stop(context) else MeshService.start(context)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.battery_hint),
                color = BoneDim,
                fontFamily = Display,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.diary))
        }
        items(log.asReversed()) { line ->
            Text(
                localizeDiary(context, line),
                color = BoneDim,
                fontFamily = Mono,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PanelShape)
                    .background(Surface)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        item {
            Spacer(Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.settings_language))
        }
        items(AppLanguages, key = { it.tag }) { languageOption ->
            val selected = language == languageOption.tag
            Text(
                languageOption.native,
                color = if (selected) Ink else Bone,
                fontFamily = Display,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(Pill)
                    .background(if (selected) Phosphor else Surface)
                    .clickable { vm.setLanguage(context, languageOption.tag) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        item {
            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.name_label))
            Spacer(Modifier.height(6.dp))
            BasicTextField(
                value = draft,
                onValueChange = { if (it.length <= 32) draft = it },
                singleLine = true,
                textStyle = TextStyle(color = Bone, fontFamily = Display, fontSize = 18.sp),
                cursorBrush = SolidColor(Phosphor),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(PanelShape)
                    .background(Surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
            if (error != null) {
                Spacer(Modifier.height(6.dp))
                Text(error ?: "", color = Clay, fontFamily = Display, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            ActionButton(stringResource(R.string.save_name), SurfaceHigh, Bone) {
                error = vm.rename(draft)
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.fingerprint_label))
            Text(
                vm.fingerprint,
                color = Phosphor,
                fontFamily = Mono,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.panic),
                color = Clay,
                fontFamily = Mono,
                fontSize = 13.sp,
                modifier = Modifier.clickable {
                    panicWord = ""
                    panicOpen = true
                },
            )
            Text(
                stringResource(R.string.version),
                color = Bark,
                fontFamily = Mono,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
    if (panicOpen) {
        PanicDialog(
            word = panicWord,
            onWord = { panicWord = it },
            onDismiss = { panicOpen = false },
            onConfirm = {
                if (panicWord.trim() == "LIMPAR") vm.panic()
            },
        )
    }
}

@Composable
private fun ReachChoice(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(if (selected) Phosphor else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = if (selected) Ink else Bone, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(hint, color = if (selected) Ink.copy(alpha = 0.75f) else BoneDim, fontFamily = Display, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun ActionButton(
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        shape = Pill,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        modifier = Modifier.height(46.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
    ) {
        Text(label, fontFamily = Mono, fontSize = 13.sp)
    }
}

@Composable
private fun RoomStrip(
    title: String,
    here: Boolean,
    rooms: List<dev.vereda.app.data.RoomEntity>,
    onHere: () -> Unit,
    onRoom: (dev.vereda.app.data.RoomEntity) -> Unit,
    onCreate: () -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (here) stringResource(R.string.here) else title,
                color = Bone,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 22.sp,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.clear), color = Clay, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClear))
        }
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp),
        ) {
            item {
                RoomChip(stringResource(R.string.here), here, onHere)
            }
            items(rooms, key = { it.id }) { room ->
                RoomChip(room.name, !here && title == room.name, { onRoom(room) })
            }
            item {
                RoomChip(stringResource(R.string.new_room), false, onCreate)
            }
        }
    }
}

@Composable
private fun RoomChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Ink else Bone,
        fontFamily = Mono,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(Pill)
            .background(if (selected) Phosphor else Surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun RoomDialog(
    title: String,
    passwordOnly: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (name: String, password: String) -> Unit,
) {
    var name by remember { mutableStateOf(if (passwordOnly) title else "") }
    var password by remember { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(PanelShape)
            .background(SurfaceHigh)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = Bone, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 20.sp)
        if (!passwordOnly) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(color = Bone, fontFamily = Display, fontSize = 16.sp),
                cursorBrush = SolidColor(Phosphor),
                modifier = Modifier.fillMaxWidth().clip(Pill).background(Night).padding(12.dp),
                decorationBox = { inner ->
                    Box {
                        if (name.isEmpty()) Text(stringResource(R.string.room_name_hint), color = BoneDim, fontFamily = Display)
                        inner()
                    }
                },
            )
        }
        BasicTextField(
            value = password,
            onValueChange = { password = it },
            singleLine = true,
            textStyle = TextStyle(color = Bone, fontFamily = Display, fontSize = 16.sp),
            cursorBrush = SolidColor(Phosphor),
            modifier = Modifier.fillMaxWidth().clip(Pill).background(Night).padding(12.dp),
            decorationBox = { inner ->
                Box {
                    if (password.isEmpty()) Text(if (passwordOnly) stringResource(R.string.room_password) else stringResource(R.string.room_password_optional), color = BoneDim, fontFamily = Display)
                    inner()
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(stringResource(R.string.cancel), Surface, Bone, onDismiss)
            ActionButton(stringResource(R.string.join), Phosphor, Ink) { onConfirm(name, password) }
        }
    }
}

@Composable
private fun rememberMic(
    context: android.content.Context,
    onTake: (VoiceTake) -> Unit,
    onShort: () -> Unit,
): VoiceCapture {
    val capture = remember { VoiceCapture(context) }
    DisposableEffect(capture) {
        onDispose { capture.discard() }
    }
    LaunchedEffect(capture.active) {
        if (!capture.active) return@LaunchedEffect
        while (isActive && capture.active && !capture.hitLimit) {
            capture.sample()
            if (capture.hitLimit) break
            kotlinx.coroutines.delay(80)
        }
        if (capture.hitLimit && capture.active) {
            val take = capture.stop()
            if (take != null) onTake(take) else onShort()
        }
    }
    return capture
}

private fun toggleMic(
    context: android.content.Context,
    capture: VoiceCapture,
    permission: androidx.activity.compose.ManagedActivityResultLauncher<String, Boolean>,
    onTake: (VoiceTake) -> Unit,
    onError: (String) -> Unit,
) {
    if (capture.active) {
        val take = capture.stop()
        if (take != null) onTake(take) else onError(context.getString(R.string.hold_longer))
        return
    }
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.RECORD_AUDIO,
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    if (!granted) {
        permission.launch(android.Manifest.permission.RECORD_AUDIO)
        return
    }
    if (!capture.start()) onError(context.getString(R.string.mic_fail))
}
