package com.zaffox.discordwear.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListItemScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.zaffox.discordwear.ApkInstaller
import com.zaffox.discordwear.SetupPreferences
import com.zaffox.discordwear.UpdateChecker
import com.zaffox.discordwear.discordApp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onLogOut: () -> Unit = {},
    scrollToUpdate: Boolean = false,
    onUpdateShown: () -> Unit = {}
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val repo = context.discordApp.repository
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadError by remember { mutableStateOf("") }


    var sendAnimatedAsGif by remember {
        mutableStateOf(SetupPreferences.getSendAnimatedAsGif(context))
    }
    var spoilerRevealOnTap by remember {
        mutableStateOf(SetupPreferences.getSpoilerRevealOnTap(context))
    }
    var showMentionBadges by remember {
        mutableStateOf(SetupPreferences.getShowMentionBadges(context))
    }
    var compactMode by remember {
        mutableStateOf(SetupPreferences.getCompactMode(context))
    }
    var notificationsEnabled by remember {
        mutableStateOf(SetupPreferences.isNotificationsEnabled(context))
    }
    var notifyDms by remember {
        mutableStateOf(SetupPreferences.isNotifyDms(context))
    }
    var notifyMentions by remember {
        mutableStateOf(SetupPreferences.isNotifyMentions(context))
    }

    var showLogoutConfirm by remember { mutableStateOf(false) }
    val updateState by UpdateChecker.state.collectAsState()

    if (showLogoutConfirm) {
        ScreenScaffold(scrollState = rememberScalingLazyListState()) {
            ScalingLazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Log out?",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Text(
                        "Your token will be cleared from this device.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                    )
                }
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching { repo?.rest?.logout() }
                                repo?.disconnect()
                                SetupPreferences.clearAll(context)
                                context.discordApp.clearRepository()
                                onLogOut()
                            }
                        },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) { Text("Log Out") }
                }
                item {
                    Button(
                        onClick = { showLogoutConfirm = false },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) { Text("Cancel") }
                }
            }
        }
        return
    }

    var updateStatusIndex = 0
    LaunchedEffect(Unit) {
        if (scrollToUpdate) {
            listState.scrollToItem(updateStatusIndex)
            onUpdateShown()
        }
    }

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {

            var itemIndex = 0
            fun indexedItem(content: @Composable ScalingLazyListItemScope.() -> Unit): Int {
                val index = itemIndex++
                item(content = content)
                return index
            }

            indexedItem {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            indexedItem {
                Text(
                    "GENERAL",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
                )
            }

            indexedItem {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    checked = showMentionBadges,
                    onCheckedChange = {
                        showMentionBadges = it
                        SetupPreferences.setShowMentionBadges(context, it)
                    },
                    label = { Text("Show mention badges", style = MaterialTheme.typography.bodySmall) }
                )
            }

            indexedItem {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    checked = spoilerRevealOnTap,
                    onCheckedChange = {
                        spoilerRevealOnTap = it
                        SetupPreferences.setSpoilerRevealOnTap(context, it)
                    },
                    label = { Text("Reveal spoilers on tap", style = MaterialTheme.typography.bodySmall) }
                )
            }

            indexedItem {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    checked = compactMode,
                    onCheckedChange = {
                        compactMode = it
                        SetupPreferences.setCompactMode(context, it)
                    },
                    label = { Text("Compact messages", style = MaterialTheme.typography.bodySmall) }
                )
            }

            indexedItem {
                Text(
                    "NOTIFICATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
                )
            }

            indexedItem {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    checked = notificationsEnabled,
                    onCheckedChange = {
                        notificationsEnabled = it
                        SetupPreferences.setNotificationsEnabled(context, it)
                    },
                    label = { Text("New message alerts", style = MaterialTheme.typography.bodySmall) }
                )
            }

            indexedItem {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    checked = notifyDms,
                    onCheckedChange = {
                        notifyDms = it
                        SetupPreferences.setNotifyDms(context, it)
                    },
                    label = { Text("Direct messages", style = MaterialTheme.typography.bodySmall) }
                )
            }

            indexedItem {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    checked = notifyMentions,
                    onCheckedChange = {
                        notifyMentions = it
                        SetupPreferences.setNotifyMentions(context, it)
                    },
                    label = { Text("Server mentions", style = MaterialTheme.typography.bodySmall) }
                )
            }

            indexedItem {
                Text(
                    "MESSAGES",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
                )
            }

            indexedItem {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    checked = sendAnimatedAsGif,
                    onCheckedChange = {
                        sendAnimatedAsGif = it
                        SetupPreferences.setSendAnimatedAsGif(context, it)
                    },
                    label = { Text("Send animated emoji as GIF", style = MaterialTheme.typography.bodySmall) }
                )
            }

            indexedItem {
                Text(
                    "ACCOUNT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
                )
            }

            indexedItem {
                Button(
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) { Text("Log Out") }
            }

            indexedItem {
                Text(
                    "UPDATE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp)
                )
            }

            updateStatusIndex = indexedItem {
                val statusText = when (val s = updateState) {
                    is UpdateChecker.UpdateState.Idle -> "v${UpdateChecker.CURRENT_VERSION}"
                    is UpdateChecker.UpdateState.Checking -> "Checking…"
                    is UpdateChecker.UpdateState.UpToDate -> "v${UpdateChecker.CURRENT_VERSION} (up to date)"
                    is UpdateChecker.UpdateState.UpdateAvailable -> "v${s.release.tagName} available!"
                    is UpdateChecker.UpdateState.Error -> "Check failed: ${s.message}"
                }
                val statusColor = when (updateState) {
                    is UpdateChecker.UpdateState.UpdateAvailable -> MaterialTheme.colorScheme.primary
                    is UpdateChecker.UpdateState.Error -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            indexedItem {
                Button(
                    onClick = { UpdateChecker.checkNow(context) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    enabled = updateState !is UpdateChecker.UpdateState.Checking,
                    colors = ButtonDefaults.filledTonalButtonColors()
                ) {
                    Text(
                        if (updateState is UpdateChecker.UpdateState.Checking) "Checking…" else "Check for updates",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (updateState is UpdateChecker.UpdateState.UpdateAvailable) {
                val release = (updateState as UpdateChecker.UpdateState.UpdateAvailable).release

                if (release.apkUrl != null) {
                    indexedItem {
                        Button(
                            onClick = {
                                if (!downloading) {
                                    downloading = true
                                    downloadProgress = 0f
                                    downloadError = ""
                                    scope.launch {
                                        ApkInstaller.downloadAndInstall(
                                            context = context,
                                            url = release.apkUrl,
                                            onProgress = { p ->
                                                downloadProgress = p
                                            }
                                        ).onFailure { downloadError = it.message ?: "Download failed" }
                                        downloading = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            enabled = !downloading,
                            colors = ButtonDefaults.buttonColors()
                        ) {
                            Text(
                                if (downloading) "Downloading…" else "Download & Install APK",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (downloading) {
                        indexedItem {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                                Text(
                                    text = "${(downloadProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    if (downloadError.isNotEmpty()) {
                        indexedItem {
                            Text(
                                downloadError,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                indexedItem {
                    Button(
                        onClick = { ApkInstaller.openInPhoneBrowser(context, release.htmlUrl) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Text("Open release on phone", style = MaterialTheme.typography.bodySmall)
                    }
                }

                indexedItem {
                    Text(
                        "Open on phone to download, then sideload via ADB:\nadb install DiscordWear.apk",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                    )
                }
            }

            indexedItem {
                Text(
                    "DiscordWear v${UpdateChecker.CURRENT_VERSION}",
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }
        }
    }
}

// good morning 😴
