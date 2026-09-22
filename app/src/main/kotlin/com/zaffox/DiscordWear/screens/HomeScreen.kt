package com.zaffox.discordwear.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import com.zaffox.discordwear.SetupPreferences
import com.zaffox.discordwear.api.Ping
import com.zaffox.discordwear.discordApp
import com.zaffox.discordwear.R
import androidx.compose.ui.res.painterResource


@Composable
fun HomeScreen(
    onNavigateToDms: () -> Unit,
    onNavigateToServers: () -> Unit,
    onNavigateToWelcome: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToMentions: () -> Unit,
    onNavigateToChat: (channelId: String, channelName: String, guildId: String?) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()

    LaunchedEffect(Unit) {
        if (!SetupPreferences.isSetupComplete(context)) {
            onNavigateToWelcome()
        }
    }

    val repo = context.discordApp.repository
    val currentUser by (repo?.currentUser ?: return).collectAsState()
    val pings by repo.pings.collectAsState()
    val readState by repo.readState.collectAsState()
    val dmChannels by repo.dmChannels.collectAsState()
    val guilds by repo.guilds.collectAsState()

    // Mention counts for badges
    val dmIds = remember(dmChannels) { dmChannels.map { it.id }.toSet() }
    val dmMentionCount = remember(readState, dmIds) {
        readState.entries.filter { it.key in dmIds }.sumOf { it.value.mentionCount }
    }
    val serverMentionCount = remember(readState, dmIds) {
        readState.entries.filter { it.key !in dmIds }.sumOf { it.value.mentionCount }
    }

    // Mention cards: combine live gateway pings with readState channels that have mention counts
    // so cards appear immediately on load, not only after a new message arrives
    val channelNames = remember(readState) { repo.getChannelNames() }
    val channelGuilds = remember(readState) { repo.getChannelGuilds() }

    val mentionEntries = remember(readState, dmIds, channelNames, channelGuilds, guilds) {
        buildMentionEntries(readState, dmIds, channelNames, channelGuilds, guilds)
    }

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState) {

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Discord",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(start = 6.dp)
                    )
                    FilledIconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.padding(end = 6.dp).height(36.dp).width(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.settings),
                            contentDescription = "Settings"
                        )
                    }
                }
            }

            item {
                Box {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToDms,
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.chat),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Direct Messages")
                    }
                    if (dmMentionCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 2.dp)
                                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                .background(
                                    androidx.compose.ui.graphics.Color(0xFFF23F43),
                                    androidx.compose.foundation.shape.CircleShape
                                )
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (dmMentionCount > 99) "99+" else dmMentionCount.toString(),
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Box {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onNavigateToServers,
                        colors = ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.groups),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Servers")
                    }
                    if (serverMentionCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 2.dp, end = 2.dp)
                                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                .background(
                                    androidx.compose.ui.graphics.Color(0xFFF23F43),
                                    androidx.compose.foundation.shape.CircleShape
                                )
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (serverMentionCount > 99) "99+" else serverMentionCount.toString(),
                                color = androidx.compose.ui.graphics.Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (mentionEntries.isNotEmpty() || pings.isNotEmpty()) {
                item {
                    Text(
                        text = "@  Mentions",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                    )
                }
                // Cap cards only show 4 latest mentions everything else in mentions screen
                val uncovered = uncoveredPings(pings, mentionEntries)
                val shownCount = minOf(mentionEntries.size, 4) + minOf(uncovered.size, 2)
                val totalCount = mentionEntries.size + uncovered.size

                items(minOf(mentionEntries.size, 4)) { index ->
                    val entry = mentionEntries[index]
                    MentionCard(
                        label = entry.label(),
                        count = entry.count,
                        onClick = {
                            onNavigateToChat(
                                entry.channelId,
                                entry.channelName ?: entry.channelId,
                                if (entry.isDm) null else channelGuilds[entry.channelId]
                            )
                        }
                    )
                }
                items(minOf(uncovered.size, 2)) { index ->
                    val ping = uncovered[index]
                    PingCard(ping = ping, onClick = {
                        onNavigateToChat(ping.message.channelId, ping.channelName, ping.message.guildId)
                    })
                }
                if (totalCount > shownCount) {
                    item {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onNavigateToMentions,
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) { Text("View all ($totalCount)") }
                    }
                }
            } else {
                item {
                    Text(
                        "No mentions yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
