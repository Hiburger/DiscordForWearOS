package com.zaffox.discordwear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import coil.ImageLoader
import com.zaffox.discordwear.api.*
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.zaffox.discordwear.screens.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    var activeChannelId: String? = null

    // Channel to open from a notification tap (channelId, channelName, guildId)
    private val pendingChat = MutableStateFlow<Triple<String, String, String?>?>(null)

    // Open the settings screen from the update notification
    private val pendingSettings = MutableStateFlow(false)
    private var settingsScrollToUpdate = false

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && SetupPreferences.getToken(this) != null) {
            NotificationService.start(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.hasExtra(EXTRA_MOCK) == true && BuildConfig.DEBUG) {
            SetupPreferences.setMockMode(this, intent.getBooleanExtra(EXTRA_MOCK, false))
        }
        val mock = BuildConfig.DEBUG && SetupPreferences.isMockMode(this)
        val token = SetupPreferences.getToken(this)
        when {
            mock -> discordApp.initMockRepository()
            token != null -> discordApp.initRepository(token)
        }

        consumeIntentExtras(intent)
        requestNotificationPermissionIfNeeded()

        setContent {
            MaterialTheme {
                AppScaffold(timeText = { TimeText() }) {
                    val navController = rememberSwipeDismissableNavController()
                    SwipeDismissableNavHost(
                        navController = navController,
                        startDestination = if (token != null || mock) "home" else "Welcome"
                    ) {

                        composable("home") {
                            activeChannelId = null
                            discordApp.repository?.suppressNotificationsFor = null
                            HomeScreen(
                                onNavigateToDms = { navController.navigate("DMs") },
                                onNavigateToServers = { navController.navigate("servers") },
                                onNavigateToWelcome = { navController.navigate("Welcome") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToMentions = { navController.navigate("mentions") },
                                onNavigateToChat = { chId, chName, guildId ->
                                    val guildSeg = guildId ?: "dm"
                                    navController.navigate("chatscreen/$chId/$chName/$guildSeg")
                                }
                            )
                        }

                        composable("mentions") {
                            MentionsScreen(onNavigateToChat = { chId, chName, guildId ->
                                val guildSeg = guildId ?: "dm"
                                navController.navigate("chatscreen/$chId/$chName/$guildSeg")
                            })
                        }

                        composable("Welcome") {
                            WelcomeScreen(onSetupComplete = {
                                navController.navigate("home") {
                                    popUpTo("Welcome") { inclusive = true }
                                }
                            }, onNavigateToQrLogin = {
                                navController.navigate("qrlogin")
                            })
                        }

                        composable("qrlogin") {
                            QrLoginScreen(
                                onSetupComplete = {
                                    navController.navigate("home") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            activeChannelId = null
                            discordApp.repository?.suppressNotificationsFor = null
                            SettingsScreen(
                                onLogOut = {
                                    navController.navigate("Welcome") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                scrollToUpdate = settingsScrollToUpdate,
                                onUpdateShown = { settingsScrollToUpdate = false }
                            )
                        }

                        composable("chatscreen/{channelId}/{channelName}/{guildId}") { back ->
                            val channelId = back.arguments?.getString("channelId")   ?: return@composable
                            val channelName = back.arguments?.getString("channelName") ?: channelId
                            val guildIdArg = back.arguments?.getString("guildId")
                            val guildId = if (guildIdArg == "dm") null else guildIdArg
                            activeChannelId = channelId
                            discordApp.repository?.suppressNotificationsFor = channelId

                            ChatScreen(
                                channelId = channelId,
                                channelName = channelName,
                                guildId = guildId,
                                navEntry = back,
                                onNavigateToProfile = { userId, user ->
                                    val encodedName = java.net.URLEncoder.encode(user?.displayName ?: userId, "UTF-8")
                                    navController.navigate("userprofile/$userId/$encodedName")
                                },
                                onOpenCompose = { navController.navigate("compose/$channelId") },
                                onOpenMessageOptions = { msgId ->
                                    navController.navigate("msgoptions/$channelId/$msgId")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("compose/{channelId}") { back ->
                            val channelId = back.arguments?.getString("channelId") ?: return@composable
                            val repo = discordApp.repository ?: return@composable
                            val guildSeg = repo.getChannelGuilds()[channelId] ?: "dm"
                            ComposeActionsScreen(
                                onOpenEmoji = {
                                    navController.navigate("emojipicker/$channelId/$guildSeg/0") {
                                        popUpTo("chatscreen/{channelId}/{channelName}/{guildId}") {
                                            inclusive = false
                                        }
                                    }
                                },
                                onOpenStickers = {
                                    navController.navigate("emojipicker/$channelId/$guildSeg/1") {
                                        popUpTo("chatscreen/{channelId}/{channelName}/{guildId}") {
                                            inclusive = false
                                        }
                                    }
                                },
                                onOpenPhoto = {
                                    navController.navigate("photopicker/$channelId") {
                                        popUpTo("chatscreen/{channelId}/{channelName}/{guildId}") {
                                            inclusive = false
                                        }
                                    }
                                },
                                onVoice = {
                                    navController.previousBackStackEntry?.savedStateHandle
                                        ?.set("startRecording", true)
                                    navController.popBackStack()
                                },
                                onDismiss = { navController.popBackStack() }
                            )
                        }

                        composable("emojipicker/{channelId}/{guildId}/{tab}?react={react}") { back ->
                            val channelId = back.arguments?.getString("channelId") ?: return@composable
                            val guildIdArg = back.arguments?.getString("guildId")
                            val guildId = if (guildIdArg == "dm") null else guildIdArg
                            val tab = back.arguments?.getString("tab")?.toIntOrNull() ?: 0
                            val reactMsgId = back.arguments?.getString("react")
                            val repo = discordApp.repository ?: return@composable
                            val scope = rememberCoroutineScope()
                            EmojiStickerScreen(
                                tab = tab,
                                guildId = guildId,
                                hasNitro = repo.currentUser.value?.hasNitro ?: false,
                                sendAnimatedAsGif = SetupPreferences.getSendAnimatedAsGif(applicationContext),
                                reactMode = reactMsgId != null,
                                onEmojiPicked = { insertText ->
                                    val target = reactMsgId
                                    if (target != null) {
                                        val reactionEmoji = parseInsertTextToReactionEmoji(insertText)
                                        if (reactionEmoji != null) {
                                            scope.launch {
                                                repo.toggleReaction(channelId, target, reactionEmoji)
                                            }
                                        }
                                        navController.popBackStack()
                                    } else if (insertText.startsWith("<a:") && SetupPreferences.getSendAnimatedAsGif(applicationContext)) {
                                        scope.launch {
                                            repo.sendMessage(channelId, buildEmojiLink(insertText))
                                        }
                                        navController.popBackStack()
                                    } else {
                                        navController.previousBackStackEntry?.savedStateHandle
                                            ?.set("insertEmoji", insertText)
                                        navController.popBackStack()
                                    }
                                },
                                onUnicodeEmojiPicked = { unicode ->
                                    val target = reactMsgId
                                    if (target != null) {
                                        scope.launch {
                                            repo.toggleReaction(
                                                channelId, target,
                                                ReactionEmoji(id = null, name = unicode, animated = false)
                                            )
                                        }
                                        navController.popBackStack()
                                    } else {
                                        navController.previousBackStackEntry?.savedStateHandle
                                            ?.set("insertEmoji", unicode)
                                        navController.popBackStack()
                                    }
                                },
                                onStickerPicked = { stickerId ->
                                    scope.launch {
                                        repo.sendSticker(channelId, stickerId)
                                    }
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("photopicker/{channelId}") { back ->
                            val channelId = back.arguments?.getString("channelId") ?: return@composable
                            val repo = discordApp.repository ?: return@composable
                            val scope = rememberCoroutineScope()
                            var uploadError by remember { mutableStateOf("") }
                            Box(modifier = Modifier.fillMaxSize()) {
                                PhotoPickerScreen(
                                    imageLoader = remember {
                                        ImageLoader.Builder(applicationContext)
                                            .components {
                                                if (Build.VERSION.SDK_INT >= 28)
                                                    add(ImageDecoderDecoder.Factory())
                                                else
                                                    add(GifDecoder.Factory())
                                            }.build()
                                    },
                                    onImageSelected = { uri, mime ->
                                        scope.launch {
                                            runCatching {
                                                val ext = when {
                                                    mime.contains("png") -> "png"
                                                    mime.contains("gif") -> "gif"
                                                    mime.contains("webp") -> "webp"
                                                    else -> "jpg"
                                                }
                                                val bytes = contentResolver.openInputStream(uri)?.readBytes()
                                                    ?: throw Exception("Cannot read image")
                                                repo.sendFileAttachment(channelId, bytes, "image.$ext", mime)
                                            }.onSuccess { navController.popBackStack() }
                                                .onFailure { uploadError = "Upload failed: ${it.message}" }
                                        }
                                    },
                                    onDismiss = { navController.popBackStack() }
                                )
                                if (uploadError.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(bottom = 8.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Text(
                                            uploadError,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier
                                                .clickable { uploadError = "" }
                                                .padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        composable("msgoptions/{channelId}/{msgId}") { back ->
                            val channelId = back.arguments?.getString("channelId") ?: return@composable
                            val msgId = back.arguments?.getString("msgId") ?: return@composable
                            val repo = discordApp.repository ?: return@composable
                            val scope = rememberCoroutineScope()
                            val msg = repo.messages.value[channelId]?.firstOrNull { it.id == msgId }
                            LaunchedEffect(Unit) {
                                if (msg == null) navController.popBackStack()
                            }
                            if (msg != null) {
                                MessageOptionsScreen(
                                    msg = msg,
                                    isOwn = msg.author.id == repo.currentUser.value?.id,
                                    channelId = channelId,
                                    onReply = {
                                        navController.previousBackStackEntry?.savedStateHandle
                                            ?.set("replyToId", msg.id)
                                        navController.popBackStack()
                                    },
                                    onReact = {
                                        val guildSeg = repo.getChannelGuilds()[channelId] ?: "dm"
                                        navController.navigate("emojipicker/$channelId/$guildSeg/0?react=${msg.id}") {
                                            popUpTo("chatscreen/{channelId}/{channelName}/{guildId}") {
                                                inclusive = false
                                            }
                                        }
                                    },
                                    onEdit = {
                                        navController.navigate("editchat/$channelId/${msg.id}") {
                                            popUpTo("chatscreen/{channelId}/{channelName}/{guildId}") {
                                                inclusive = false
                                            }
                                        }
                                    },
                                    onDismiss = { navController.popBackStack() }
                                )
                            }
                        }

                        composable("editchat/{channelId}/{msgId}") { back ->
                            val channelId = back.arguments?.getString("channelId") ?: return@composable
                            val msgId = back.arguments?.getString("msgId") ?: return@composable
                            val repo = discordApp.repository ?: return@composable
                            val msg = repo.messages.value[channelId]?.firstOrNull { it.id == msgId }
                            LaunchedEffect(Unit) {
                                if (msg == null) navController.popBackStack()
                            }
                            if (msg != null) {
                                EditChatScreen(
                                    channelId = channelId,
                                    msg = msg,
                                    onDismiss = { navController.popBackStack() }
                                )
                            }
                        }

                        composable("userprofile/{userId}/{displayName}") { back ->
                            val userId = back.arguments?.getString("userId") ?: return@composable
                            val displayName = back.arguments?.getString("displayName")
                                ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: userId
                            activeChannelId = null
                            discordApp.repository?.suppressNotificationsFor = null
                            UserProfileScreen(
                                userId = userId,
                                onNavigateToChat = { chId, chName ->
                                    navController.navigate("chatscreen/$chId/$chName/dm") {
                                        popUpTo("userprofile/$userId/$displayName") { inclusive = true }
                                    }
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("ServerChannels/{guildId}/{guildName}") { back ->
                            val guildId = back.arguments?.getString("guildId")   ?: return@composable
                            val guildName = back.arguments?.getString("guildName") ?: guildId
                            activeChannelId = null
                            discordApp.repository?.suppressNotificationsFor = null
                            ServerChannels(
                                guildId = guildId,
                                guildName = guildName,
                                onNavigateToChatScreen = { chId, chName ->
                                    navController.navigate("chatscreen/$chId/$chName/$guildId")
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("DMs") {
                            activeChannelId = null
                            discordApp.repository?.suppressNotificationsFor = null
                            DmsScreen(onNavigateToChatScreen = { chId, chName ->
                                navController.navigate("chatscreen/$chId/$chName/dm")
                            })
                        }

                        composable("servers") {
                            activeChannelId = null
                            discordApp.repository?.suppressNotificationsFor = null
                            ServerScreen(onNavigateToChannels = { gId, gName ->
                                navController.navigate("ServerChannels/$gId/$gName")
                            })
                        }
                    }

                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        pendingChat.collect { target ->
                            if (target != null) {
                                val (chId, chName, gId) = target
                                pendingChat.value = null
                                navController.navigate("chatscreen/$chId/$chName/${gId ?: "dm"}")
                            }
                        }
                    }

                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        pendingSettings.collect { open ->
                            if (open) {
                                pendingSettings.value = false
                                if (navController.currentBackStackEntry?.destination?.route != "settings") {
                                    settingsScrollToUpdate = true
                                    navController.navigate("settings") { launchSingleTop = true }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun consumeIntentExtras(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SETTINGS, false) == true) {
            pendingSettings.value = true
            return
        }
        val channelId = intent?.getStringExtra(EXTRA_CHANNEL_ID) ?: return
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: channelId
        val guildId = intent.getStringExtra(EXTRA_GUILD_ID)
        pendingChat.value = Triple(channelId, channelName, guildId)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeIntentExtras(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        discordApp.repository?.refreshOnResume(activeChannelId)
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_GUILD_ID = "extra_guild_id"
        const val EXTRA_OPEN_SETTINGS = "extra_open_settings"
        const val EXTRA_MOCK = "extra_mock"
    }
}
