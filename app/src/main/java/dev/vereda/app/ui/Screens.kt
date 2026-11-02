package dev.vereda.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            error = "Sem a permissão de aparelhos por perto o rádio não liga."
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
                contentDescription = "Blip",
                modifier = Modifier.size(148.dp),
                alignment = Alignment.Center,
                contentScale = ContentScale.Fit,
            )
            Text(
                "Blip",
                color = Bone,
                fontFamily = Display,
                fontWeight = FontWeight.SemiBold,
                fontSize = 64.sp,
                lineHeight = 64.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Fala com quem está perto.\nCom ou sem internet.",
                color = BoneDim,
                fontFamily = Display,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("SEU NOME NO RÁDIO", color = BoneDim, fontFamily = Mono, fontSize = 11.sp, letterSpacing = 1.2.sp)
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
                "O Android pede acesso aos aparelhos por perto para achar outros rádios. A notificação fica no alto enquanto o Blip está ligado.",
                color = BoneDim,
                fontFamily = Display,
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
            if (error != null) {
                Text(error ?: "", color = Clay, fontFamily = Display, fontSize = 15.sp)
                Text(
                    "Abrir configurações",
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
                        error = "Usa um nome curto, de até 32 bytes."
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
                Text("Ligar o rádio", fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
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
        onShort = { vm.report("Segura um pouco mais.") },
    )
    var showCreate by remember { mutableStateOf(false) }
    var lockTarget by remember { mutableStateOf<dev.vereda.app.data.RoomEntity?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.sendImageCurrent(uri)
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (!capture.start()) vm.report("Não consegui abrir o microfone.")
        } else {
            vm.report("Sem o microfone não dá para gravar.")
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
        TalkScreen(
            messages = messages,
            title = null,
            placeholder = if (title == "Aqui") "Fala no canal" else "Fala em $title",
            banner = banner,
            empty = "Ninguém falou ainda. Quando outro aparelho entrar no alcance, a frase aparece aqui.",
            nameOf = { message ->
                when {
                    message.senderId == vm.selfId -> nickname.ifBlank { "você" }
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
            title = "Nova sala",
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
        onShort = { vm.report("Segura um pouco mais.") },
    )
    val pickDirect = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.sendImage(peerId, uri)
    }
    val directMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            if (!capture.start()) vm.report("Não consegui abrir o microfone.")
        } else {
            vm.report("Sem o microfone não dá para gravar.")
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
                    if (peer?.direct == true) "cifrada · no alcance" else "cifrada · por outro aparelho",
                    color = BoneDim,
                    fontFamily = Mono,
                    fontSize = 11.sp,
                )
            }
        }
        TalkScreen(
            messages = messages,
            title = null,
            placeholder = "Mensagem direta",
            banner = banner,
            empty = "Isto só abre no aparelho de ${peer?.nickname ?: "destino"}.",
            nameOf = { message ->
                if (message.senderId == vm.selfId) nickname.ifBlank { "você" } else peer?.nickname ?: "rádio"
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
            "Limpar este chat",
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
                        "Imagem",
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
                        if (recording) "Parar e enviar" else "Áudio",
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
        ScreenTitle("PERTO DE VOCÊ", "Pessoas")
        Text("Toque para abrir uma conversa cifrada.", color = BoneDim, fontFamily = Display, fontSize = 15.sp)
        if (peers.isEmpty()) {
            EmptyLine("Nenhum rádio por perto. Deixa os dois aparelhos com o Blip aberto.")
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
                            if (peer.direct) "no alcance" else "por outro aparelho",
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
    val log by vm.log.collectAsStateWithLifecycle()
    val nickname by vm.nickname.collectAsStateWithLifecycle()
    var draft by remember(nickname) { mutableStateOf(nickname) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("AJUSTES", "Rádio")
        Text(status, color = if (phase == RadioPhase.FAILED) Clay else BoneDim, fontFamily = Display, fontSize = 16.sp)
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().clip(PanelShape).background(Surface).padding(16.dp)) {
            SectionLabel("IMPRESSÃO")
            Text(vm.fingerprint, color = Phosphor, fontFamily = Mono, fontSize = 16.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("NOME")
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = draft,
            onValueChange = { if (it.length <= 32) draft = it },
            singleLine = true,
            textStyle = TextStyle(color = Bone, fontFamily = Display, fontSize = 18.sp),
            cursorBrush = SolidColor(Phosphor),
            modifier = Modifier.fillMaxWidth().clip(PanelShape).background(Surface).padding(horizontal = 16.dp, vertical = 14.dp),
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error ?: "", color = Clay, fontFamily = Display, fontSize = 14.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Guardar nome", SurfaceHigh, Bone) {
                error = vm.rename(draft)
            }
            val running = phase == RadioPhase.ON || phase == RadioPhase.STARTING
            ActionButton(
                label = if (running) "Desligar" else "Ligar",
                container = if (running) SurfaceHigh else Phosphor,
                content = if (running) Bone else Ink,
            ) {
                if (running) MeshService.stop(context) else MeshService.start(context)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Em alguns aparelhos o rádio para com a tela apagada se a bateria estiver otimizada. Se o outro sumir, tira a otimização do Blip nas configurações do sistema.",
            color = BoneDim,
            fontFamily = Display,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel("DIÁRIO")
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            Modifier
                .weight(1f)
                .clip(PanelShape)
                .background(Surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(log.asReversed()) { line ->
                Text(line, color = BoneDim, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
        Text("Blip 0.1.0", color = Bark, fontFamily = Mono, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        var panicOpen by remember { mutableStateOf(false) }
        var panicWord by remember { mutableStateOf("") }
        Text(
            "Apagar tudo neste aparelho",
            color = Clay,
            fontFamily = Mono,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable { panicOpen = true },
        )
        if (panicOpen) {
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Escreve LIMPAR para apagar chats, salas, mídia e a chave deste aparelho.", color = BoneDim, fontFamily = Display, fontSize = 14.sp)
                BasicTextField(
                    value = panicWord,
                    onValueChange = { panicWord = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Bone, fontFamily = Mono, fontSize = 16.sp),
                    cursorBrush = SolidColor(Clay),
                    modifier = Modifier.fillMaxWidth().clip(PanelShape).background(Surface).padding(12.dp),
                )
                ActionButton("Confirmar pânico", Clay, Bone) {
                    if (panicWord.trim() == "LIMPAR") vm.panic()
                }
            }
        }
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
            Text(title, color = Bone, fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
            Text("Limpar", color = Clay, fontFamily = Mono, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClear))
        }
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp),
        ) {
            item {
                RoomChip("Aqui", here, onHere)
            }
            items(rooms, key = { it.id }) { room ->
                RoomChip(room.name, !here && title == room.name, { onRoom(room) })
            }
            item {
                RoomChip("+ Sala", false, onCreate)
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
                        if (name.isEmpty()) Text("Nome da sala", color = BoneDim, fontFamily = Display)
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
                    if (password.isEmpty()) Text(if (passwordOnly) "Senha" else "Senha, se quiser", color = BoneDim, fontFamily = Display)
                    inner()
                }
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Cancelar", Surface, Bone, onDismiss)
            ActionButton("Entrar", Phosphor, Ink) { onConfirm(name, password) }
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
        if (take != null) onTake(take) else onError("Segura um pouco mais.")
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
    if (!capture.start()) onError("Não consegui abrir o microfone.")
}
