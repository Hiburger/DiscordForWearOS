package com.zaffox.discordwear.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.zaffox.discordwear.R
import com.zaffox.discordwear.SetupPreferences
import com.zaffox.discordwear.api.CategoryGroup
import com.zaffox.discordwear.api.Channel
import com.zaffox.discordwear.api.ChannelType
import com.zaffox.discordwear.discordApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
private fun ChannelIcon(ch: Channel, allChannels: List<Channel>) {
    when {
        ch.type == ChannelType.GUILD_NEWS ->
            Icon(
                painter = painterResource(id = R.drawable.announce),
                contentDescription = "Announcement",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )

        ch.name.contains("rule", ignoreCase = true) &&
                allChannels.firstOrNull { it.name.contains("rule", ignoreCase = true) }?.id == ch.id ->
            Icon(
                painter = painterResource(id = R.drawable.rules),
                contentDescription = "Rules",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )

        else ->
            Text("#", fontSize = 16.sp)
    }
}

@Composable
fun ServerChannels(
    guildId: String,
    guildName: String,
    onNavigateToChatScreen: (channelId: String, channelName: String) -> Unit,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val repo = context.discordApp.repository
    val listState = rememberScalingLazyListState()
    val scope = rememberCoroutineScope()
    val showMentionBadges = remember { SetupPreferences.getShowMentionBadges(context) }
    val readState by (repo?.readState ?: return).collectAsState()

    var groups by remember { mutableStateOf<List<CategoryGroup>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(guildId) {
        scope.launch(Dispatchers.IO) {
            // Check for cached channels first
            val cached = repo?.getCachedChannels(guildId)

            if (!cached.isNullOrEmpty()) {
                groups = cached
                loading = false
                repo?.cacheChannelNames(cached)
                return@launch  // Already have cached data, skip network call
            }

            // No cache or cache is empty -> fetch from network
            repo?.rest?.getGuildChannels(guildId)
                ?.onSuccess {
                    groups = it
                    loading = false
                    repo.cacheChannelNames(it)
                    repo.saveChannels(guildId, it)
                }
                ?.onFailure {
                    if (groups.isEmpty()) {
                        error = it.message ?: "Error"; loading = false
                    }
                }
                ?: run {
                    if (groups.isEmpty()) {
                        error = "Not connected"; loading = false
                    }
                }
        }
    }

    val allChannels = remember(groups) { groups.flatMap { it.channels } }

    ScreenScaffold(scrollState = listState) {
        ScalingLazyColumn(state = listState) {
            item {
                Text(
                    guildName,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            when {
                loading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }

                error.isNotEmpty() -> item {
                    Text(
                        error, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                groups.isEmpty() -> item {
                    Text(
                        "No text channels", style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                else -> {
                    for (group in groups) {
                        if (group.category != null) {
                            item {
                                Text(
                                    text = "▸ ${group.category.name.uppercase()}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 1.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 2.dp)
                                )
                            }
                        }

                        items(group.channels.size) { idx ->
                            val ch = group.channels[idx]
                            if (!ch.hasAccess) return@items
                            val rs = if (showMentionBadges) readState[ch.id] else null
                            val mentionCount = rs?.mentionCount ?: 0
                            val hasUnread = rs != null && ch.lastMessageId != null &&
                                    ch.lastMessageId > rs.lastMessageId
                            Button(
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(),
                                onClick = { onNavigateToChatScreen(ch.id, ch.name) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        ChannelIcon(ch, allChannels)
                                        Text(
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            text = ch.name,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (mentionCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .offset(x = 3.dp)
                                                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                                .background(Color(0xFFF23F43), CircleShape)
                                                .padding(horizontal = 5.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (mentionCount > 99) "99+" else mentionCount.toString(),
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                            )
                                        }
                                    } else if (hasUnread) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(Color.White.copy(alpha = 0.8f), CircleShape)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledIconButton(
                        onClick = onBack,
                        modifier = Modifier.height(40.dp).width(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.back),
                            contentDescription = "Back"
                        )
                    }
                }
            }
        }
    }
}
