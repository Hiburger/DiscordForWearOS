package com.hiburger.discordwear.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DiscordGateway(private val token: String) {
    private val _events = MutableSharedFlow<GatewayEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<GatewayEvent> = _events
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Guards connect/disconnect/reconnect state; held only across
    // non-suspending sections (never across delay()).
    private val lifecycleLock = Any()
    private var ws: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private val reconnectAttempt = AtomicInteger(0)
    private val seq = AtomicInteger(-1)
    private var sessionId: String? = null
    private var resumeUrl: String? = null

    // Set by the repository so retries can pause while the watch has no
    // usable network; the network callback calls onNetworkAvailable() instead.
    var isOnline: () -> Boolean = { true }

    @Volatile
    private var connected = false

    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private object Op {
        const val DISPATCH = 0
        const val HEARTBEAT = 1
        const val IDENTIFY = 2
        const val RESUME = 6
        const val RECONNECT = 7
        const val INVALID_SESSION = 9
        const val HELLO = 10
        const val HEARTBEAT_ACK = 11
    }

    fun connect() = synchronized(lifecycleLock) {
        reconnectJob?.cancel()
        reconnectJob = null
        // Close any socket from a previous generation so we never end up with
        // two live connections (double heartbeats, duplicated dispatches).
        ws?.let { runCatching { it.close(1000, "reconnect") } }
        ws = null
        // Resume on the server-provided gateway when we have one.
        val url = resumeUrl ?: DiscordHttp.GATEWAY_URL
        val request = Request.Builder().url(url).build()
        ws = http.newWebSocket(request, Listener())
    }

    fun disconnect() = synchronized(lifecycleLock) {
        connected = false
        reconnectJob?.cancel()
        reconnectJob = null
        heartbeatJob?.cancel()
        // Null out ws before closing: the listener callbacks compare against
        // this field to detect stale sockets and skip cleanup.
        val old = ws
        ws = null
        old?.close(1000, "Goodbye")
    }

    /**
     * Event-driven reconnect after being offline. Resets the backoff since the
     * failures were almost certainly connectivity, not Discord refusing us.
     */
    fun onNetworkAvailable() = synchronized(lifecycleLock) {
        if (ws == null && reconnectJob?.isActive != true) {
            reconnectAttempt.set(0)
            connect()
        }
    }

    private fun scheduleReconnect() = synchronized(lifecycleLock) {
        // Single-flight: one pending retry at a time.
        if (reconnectJob?.isActive == true) return
        // Offline: stop retrying entirely; the network callback wakes us.
        if (!isOnline()) return
        val attempt = reconnectAttempt.incrementAndGet()
        reconnectJob = scope.launch {
            delay(ReconnectBackoff.jitteredDelayMs(attempt))
            connect()
        }
    }

    private fun send(op: Int, d: Any?) {
        val payload = JSONObject()
            .put("op", op)
            .put("d", d)
        ws?.send(payload.toString())
    }

    private fun sendHeartbeat() {
        val s = seq.get().takeIf { it >= 0 }
        send(Op.HEARTBEAT, s)
    }

    private fun sendIdentify() {
        val d = JSONObject()
            .put("token", token)
            .put("intents", INTENTS)
            .put(
                "properties", JSONObject()
                    .put("\$os", "Windows")
                    .put("\$browser", "Chrome")
                    .put("\$device", "")
            )
        send(Op.IDENTIFY, d)
    }

    private fun sendResume() {
        val d = JSONObject()
            .put("token", token)
            .put("session_id", sessionId)
            .put("seq", seq.get())
        send(Op.RESUME, d)
    }

    private inner class Listener : WebSocketListener() {

        // Callbacks may fire for a socket from a previous generation after a
        // reconnect; ignore everything that is not the current socket.
        private fun isCurrent(webSocket: WebSocket) = webSocket === ws

        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (!isCurrent(webSocket)) return
            connected = true
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent(webSocket)) return
            val payload = runCatching { JSONObject(text) }.getOrNull() ?: return
            val op = payload.getInt("op")
            val d = payload.opt("d")
            val s = payload.optInt("s", -1).takeIf { it >= 0 }
            val t = payload.optString("t").takeIf { it.isNotEmpty() }

            if (s != null) seq.set(s)

            when (op) {
                Op.HELLO -> {
                    val interval = (d as? JSONObject)?.getLong("heartbeat_interval") ?: 41250L
                    startHeartbeat(interval)
                    if (sessionId != null) sendResume() else sendIdentify()
                }

                Op.HEARTBEAT -> sendHeartbeat()

                Op.HEARTBEAT_ACK -> {}

                Op.RECONNECT -> {
                    disconnect()
                    scheduleReconnect()
                }

                Op.INVALID_SESSION -> {
                    val resumable = (d as? Boolean) == true
                    if (!resumable) {
                        sessionId = null; resumeUrl = null
                    }
                    // The server is closing this socket; forget it so later
                    // callbacks treat it as stale, then retry with backoff.
                    synchronized(lifecycleLock) { ws = null }
                    scheduleReconnect()
                }

                Op.DISPATCH -> {
                    if (t != null) handleDispatch(t, d as? JSONObject)
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrent(webSocket)) return
            connected = false
            ws = null
            heartbeatJob?.cancel()
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isCurrent(webSocket)) return
            connected = false
            ws = null
            heartbeatJob?.cancel()
            // Server closed the session (e.g. after INVALID_SESSION); retry
            // with backoff instead of dying silently.
            scheduleReconnect()
        }
    }

    private fun startHeartbeat(intervalMs: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            delay((intervalMs * Math.random()).toLong())
            while (isActive) {
                sendHeartbeat()
                delay(intervalMs)
            }
        }
    }

    private fun handleDispatch(eventName: String, d: JSONObject?) {
        if (d == null) return

        val event: GatewayEvent? = when (eventName) {
            "READY" -> {
                sessionId = d.optString("session_id")
                resumeUrl = d.optString("resume_gateway_url")
                reconnectAttempt.set(0) // we are in: reset the retry schedule
                val user = runCatching { DiscordUser.fromJson(d.getJSONObject("user")) }.getOrNull()
                val readState = mutableMapOf<String, ChannelUnreadState>()
                val rsArr = d.optJSONArray("read_state")
                    ?: d.optJSONObject("read_state")?.optJSONArray("entries")
                if (rsArr != null) {
                    for (i in 0 until rsArr.length()) {
                        runCatching {
                            val rs = rsArr.getJSONObject(i)
                            val chId = rs.optString("id").takeIf { it.isNotEmpty() } ?: return@runCatching
                            val lastRead = rs.optString("last_message_id").takeIf { it.isNotEmpty() && it != "null" }
                                ?: return@runCatching
                            val mentionCount = rs.optInt("mention_count", 0)
                            readState[chId] = ChannelUnreadState(lastRead, mentionCount)
                        }
                    }
                }
                val presences = mutableListOf<UserPresence>()
                val presArr = d.optJSONArray("presences")
                if (presArr != null) {
                    for (i in 0 until presArr.length()) {
                        runCatching { presences.add(UserPresence.fromJson(presArr.getJSONObject(i))) }
                    }
                }

                val readyGuilds = mutableListOf<Guild>()
                val readyChannels = mutableListOf<Channel>()
                runCatching {
                    val gArr = d.optJSONArray("guilds") ?: return@runCatching
                    for (i in 0 until gArr.length()) {
                        runCatching {
                            val g = gArr.getJSONObject(i)
                            if (g.optBoolean("unavailable", false)) return@runCatching
                            val guild = Guild.fromJson(g)
                            readyGuilds.add(guild)
                            // Embedded channels carry no guild_id; fill it from the parent.
                            val chArr = g.optJSONArray("channels") ?: return@runCatching
                            for (j in 0 until chArr.length()) {
                                runCatching {
                                    readyChannels.add(
                                        Channel.fromJson(chArr.getJSONObject(j)).copy(guildId = guild.id)
                                    )
                                }
                            }
                        }
                    }
                }
                if (user != null) GatewayEvent.Ready(user, readState, presences, readyGuilds, readyChannels) else null
            }

            "MESSAGE_CREATE" -> runCatching {
                val msg = DiscordMessage.fromJson(d)
                val memberObj = d.optJSONObject("member")
                val rolesArr = memberObj?.optJSONArray("roles")
                val roleIds = if (rolesArr != null)
                    (0 until rolesArr.length()).map { rolesArr.getString(it) }
                else emptyList()
                val guildId = d.optString("guild_id").takeIf { it.isNotEmpty() && it != "null" }
                GatewayEvent.MessageCreate(msg, roleIds, guildId)
            }.getOrNull()

            "MESSAGE_UPDATE" -> runCatching {
                GatewayEvent.MessageUpdate(DiscordMessage.fromJson(d))
            }.getOrNull()

            "MESSAGE_DELETE" -> runCatching {
                GatewayEvent.MessageDelete(
                    id = d.getString("id"),
                    channelId = d.getString("channel_id")
                )
            }.getOrNull()

            "MESSAGE_REACTION_ADD" -> runCatching {
                GatewayEvent.ReactionAdd(
                    messageId = d.getString("message_id"),
                    channelId = d.getString("channel_id"),
                    userId = d.getString("user_id"),
                    emoji = ReactionEmoji.fromJson(d.getJSONObject("emoji"))
                )
            }.getOrNull()

            "MESSAGE_REACTION_REMOVE" -> runCatching {
                GatewayEvent.ReactionRemove(
                    messageId = d.getString("message_id"),
                    channelId = d.getString("channel_id"),
                    userId = d.getString("user_id"),
                    emoji = ReactionEmoji.fromJson(d.getJSONObject("emoji"))
                )
            }.getOrNull()

            "TYPING_START" -> runCatching {
                fun String.realOrNull() = takeIf { it.isNotEmpty() && it != "null" }
                val memberObj = d.optJSONObject("member")
                val userObj = memberObj?.optJSONObject("user") ?: d.optJSONObject("user")
                val nick = memberObj?.optString("nick")?.realOrNull()
                val globalName = userObj?.optString("global_name")?.realOrNull()
                val username = userObj?.optString("username")?.realOrNull()
                val displayName = nick ?: globalName ?: username
                GatewayEvent.TypingStart(
                    channelId = d.getString("channel_id"),
                    userId = d.getString("user_id"),
                    displayName = displayName
                )
            }.getOrNull()

            "PRESENCE_UPDATE" -> runCatching {
                GatewayEvent.PresenceUpdate(UserPresence.fromJson(d))
            }.getOrNull()

            else -> GatewayEvent.Unknown(eventName)
        }

        if (event != null) {
            scope.launch { _events.emit(event) }
        }
    }

    companion object {
        private const val INTENTS =
            (1 or 256 or 512 or 1024 or 2048 or 4096 or 8192 or 16384 or 32768)
    }
}

sealed class GatewayEvent {
    data class Ready(
        val user: DiscordUser,
        val readState: Map<String, ChannelUnreadState> = emptyMap(),
        val presences: List<UserPresence> = emptyList(),
        val guilds: List<Guild> = emptyList(),
        val channels: List<Channel> = emptyList()
    ) : GatewayEvent()

    data class MessageCreate(
        val message: DiscordMessage,
        val memberRoleIds: List<String> = emptyList(),
        val guildId: String? = null
    ) : GatewayEvent()

    data class MessageUpdate(val message: DiscordMessage) : GatewayEvent()
    data class MessageDelete(val id: String, val channelId: String) : GatewayEvent()
    data class ReactionAdd(val messageId: String, val channelId: String, val userId: String, val emoji: ReactionEmoji) :
        GatewayEvent()

    data class ReactionRemove(
        val messageId: String,
        val channelId: String,
        val userId: String,
        val emoji: ReactionEmoji
    ) : GatewayEvent()

    data class TypingStart(val channelId: String, val userId: String, val displayName: String? = null) : GatewayEvent()
    data class PresenceUpdate(val presence: UserPresence) : GatewayEvent()
    data class Unknown(val name: String) : GatewayEvent()
}
