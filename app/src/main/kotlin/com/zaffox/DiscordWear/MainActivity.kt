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
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.zaffox.discordwear.screens.*
import kotlinx.coroutines.flow.MutableStateFlow

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
                                onNavigateToProfile = { userId, user ->
                                    val encodedName = java.net.URLEncoder.encode(user?.displayName ?: userId, "UTF-8")
                                    navController.navigate("userprofile/$userId/$encodedName")
                                },
                                onBack = { navController.popBackStack() }
                            )
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
                                }
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
