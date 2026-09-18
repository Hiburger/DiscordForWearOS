package com.zaffox.discordwear.api

import android.util.Base64
import org.json.JSONObject

/**
 * Fingerprint constants shared by all Discord HTTP/WebSocket traffic so the
 * app looks like the official web client (see upstream issue: "Why not use API v9").
 */
object DiscordHttp {
    // The stable client-facing REST version used by the official web client
    const val REST_BASE_URL = "https://discord.com/api/v9"

    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"

    // The web client connects its gateway with v10
    const val GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json"

    val superProperties: String by lazy {
        val json = JSONObject()
            .put("os", "Windows")
            .put("browser", "Chrome")
            .put("device", "")
            .put("system_locale", "en-US")
            .put("browser_user_agent", USER_AGENT)
            .put("browser_version", "151.0.0.0")
            .put("os_version", "10")
            .put("referrer", "")
            .put("referring_domain", "")
            .put("referrer_current", "")
            .put("referring_domain_current", "")
            .put("search_engine_current", "google")
            .put("mp_keyword_current", "discord")
            .put("release_channel", "stable")
            .put("client_build_number", 396858)
            .put("client_event_source", JSONObject.NULL)
            .put("has_client_mods", false)
            .toString()
        Base64.encodeToString(json.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }
}
