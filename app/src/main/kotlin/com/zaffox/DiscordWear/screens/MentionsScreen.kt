package com.zaffox.discordwear.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import com.zaffox.discordwear.api.Ping
import com.zaffox.discordwear.discordApp

// Full list of unread mentions using readState-derived cards & live pings
// Opened from the "View all (ping_amnt)" home button
@Composable
fun MentionsScreen(
    onNavigateToChat: (channelId: String, channelName: String, guildId: String?) -> Unit
) {
    val context = LocalContext.current
    val repo = context.discordApp.repository ?: return
    val listState = rememberScalingLazyListState()

    val pings by repo.pings.collectAsState()
    val readState by repo.readState.collectAsState()
    val dmChannels by repo.dmChannels.collectAsState()
    val guilds by repo.guilds.collectAsState()

    val dmIds = remember(dmChannels) { dmChannels.map { it.id }.toSet() }
    val channelNames = remember(readState) { repo.getChannelNames() }
    val channelGuilds = remember(readState) { repo.getChannelGuilds() }

    val mentionEntries = remember(readState, dmIds, channelNames, channelGuilds, guilds) {
        buildMentionEntries(readState, dmIds, channelNames, channelGuilds, guilds)
    }

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState) {
            item {
                Text(
                    text = "Mentions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            items(mentionEntries.size) { index ->
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
            val uncovered = uncoveredPings(pings, mentionEntries)
            items(uncovered.size) { index ->
                val ping = uncovered[index]
                PingCard(ping = ping, onClick = {
                    onNavigateToChat(ping.message.channelId, ping.channelName, ping.message.guildId)
                })
            }
            if (mentionEntries.isEmpty() && pings.isEmpty()) {
                item {
                    Text(
                        "No mentions yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun MentionCard(label: String, count: Int, onClick: () -> Unit) {
    TitleCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (count > 99) "99+" else "$count",
                    style = MaterialTheme.typography.labelSmall,
                    color = androidx.compose.ui.graphics.Color(0xFFF23F43),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) {
        Text(
            text = "Tap to open",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun PingCard(ping: Ping, onClick: () -> Unit) {
    val location = if (ping.guildName != null) "${ping.guildName} • #${ping.channelName}"
    else "DM • ${ping.channelName}"

    TitleCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ping.message.author.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = location,
                    style = MaterialTheme.typography.bodyExtraSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    ) {
        Text(
            text = ping.message.content.take(80),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
