package com.zaffox.discordwear.screens

import com.zaffox.discordwear.api.ChannelUnreadState
import com.zaffox.discordwear.api.Guild
import com.zaffox.discordwear.api.Ping

internal data class MentionEntry(
    val channelId: String,
    val channelName: String?,
    val guildName: String?,
    val count: Int,
    val isDm: Boolean
) {
    fun label(): String =
        if (isDm) "DM • ${channelName ?: "Unknown"}"
        else channelName?.let { "${guildName ?: "Server"} • #$it" }
            ?: (guildName ?: "Server")
}

internal fun buildMentionEntries(
    readState: Map<String, ChannelUnreadState>,
    dmIds: Set<String>,
    channelNames: Map<String, String>,
    channelGuilds: Map<String, String>,
    guilds: List<Guild>
): List<MentionEntry> =
    readState.entries
        .filter { it.value.mentionCount > 0 }
        .map { (channelId, state) ->
            val isDm = channelId in dmIds
            val guildId = channelGuilds[channelId]
            val guildName = guildId?.let { id -> guilds.firstOrNull { it.id == id }?.name }
            MentionEntry(channelId, channelNames[channelId], guildName, state.mentionCount, isDm)
        }
        .sortedByDescending { it.count }

// live pings that don't already have a card built from  readState
internal fun uncoveredPings(pings: List<Ping>, entries: List<MentionEntry>): List<Ping> =
    pings.filter { ping -> entries.none { it.channelId == ping.message.channelId } }
