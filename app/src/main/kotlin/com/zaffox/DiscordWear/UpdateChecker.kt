package com.zaffox.discordwear

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val body: String,
    val apkUrl: String?,
    val htmlUrl: String,
    val publishedAt: String
)

object UpdateChecker {
    private const val GITHUB_OWNER = "Hiburger"
    private const val GITHUB_REPO = "DiscordForWearOS"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    val CURRENT_VERSION = BuildConfig.VERSION_NAME
    private const val PREFS_NAME = "update_checker"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_LATEST_TAG = "latest_tag"
    private const val KEY_LATEST_APK = "latest_apk_url"
    private const val KEY_LATEST_HTML = "latest_html_url"
    private const val KEY_LATEST_NAME = "latest_release_name"
    private const val KEY_NOTIFIED_TAG = "notified_tag"
    private const val CHECK_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L
    private const val CHANNEL_UPDATES = "app_updates"
    private const val UPDATE_NOTIF_ID = 43

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    sealed class UpdateState {
        object Idle : UpdateState()
        object Checking : UpdateState()
        object UpToDate : UpdateState()
        data class UpdateAvailable(val release: ReleaseInfo) : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)


    fun start(context: Context) {
        scope.launch {
            restoreCachedState(context)

            val appContext = context.applicationContext
            while (true) {
                val prefs = prefs(appContext)
                val lastCheckMs = prefs.getLong(KEY_LAST_CHECK, 0L)
                val nowMs = System.currentTimeMillis()
                val dueMs = (lastCheckMs + CHECK_INTERVAL_MS - nowMs).coerceAtLeast(0L)

                if (dueMs > 0) delay(dueMs)

                check(appContext)
            }
        }
    }

    fun checkNow(context: Context) {
        scope.launch { check(context.applicationContext) }
    }

    private fun restoreCachedState(context: Context) {
        val p = prefs(context)
        val tag = p.getString(KEY_LATEST_TAG, null) ?: return
        if (isNewer(tag, CURRENT_VERSION)) {
            _state.value = UpdateState.UpdateAvailable(
                ReleaseInfo(
                    tagName = tag,
                    name = p.getString(KEY_LATEST_NAME, tag) ?: tag,
                    body = "",
                    apkUrl = p.getString(KEY_LATEST_APK, null),
                    htmlUrl = p.getString(KEY_LATEST_HTML, "") ?: "",
                    publishedAt = ""
                )
            )
        } else {
            _state.value = UpdateState.UpToDate
        }
    }

    private suspend fun check(context: Context) {
        _state.value = UpdateState.Checking
        runCatching {
            val request = Request.Builder()
                .url(API_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            val body = http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body?.string() ?: error("Empty body")
            }

            val json = JSONObject(body)
            val tag = json.getString("tag_name").trimStart('v')
            val name = json.optString("name", tag)
            val releaseBody = json.optString("body", "")
            val htmlUrl = json.getString("html_url")

            val assets = json.optJSONArray("assets") ?: JSONArray()
            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                if (asset.getString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.getString("browser_download_url")
                    break
                }
            }

            prefs(context).edit()
                .putLong(KEY_LAST_CHECK, System.currentTimeMillis())
                .putString(KEY_LATEST_TAG, tag)
                .putString(KEY_LATEST_NAME, name)
                .putString(KEY_LATEST_APK, apkUrl)
                .putString(KEY_LATEST_HTML, htmlUrl)
                .apply()

            val release = ReleaseInfo(tag, name, releaseBody, apkUrl, htmlUrl, json.optString("published_at"))
            val isUpdate = isNewer(tag, CURRENT_VERSION)
            _state.value = if (isUpdate)
                UpdateState.UpdateAvailable(release)
            else
                UpdateState.UpToDate

            // Notif once per new release
            if (isUpdate && prefs(context).getString(KEY_NOTIFIED_TAG, null) != tag) {
                notifyNewVersion(context, release)
                prefs(context).edit().putString(KEY_NOTIFIED_TAG, tag).apply()
            }
        }.onFailure { e ->
            _state.value = UpdateState.Error(e.message ?: "Unknown error")
        }
    }

    private fun notifyNewVersion(context: Context, release: ReleaseInfo) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureUpdateChannel(context)

        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_SETTINGS, true),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.down)
            .setContentTitle(context.getString(R.string.notif_update_title, release.tagName))
            .setContentText(context.getString(R.string.notif_update_text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .extend(NotificationCompat.WearableExtender())
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(UPDATE_NOTIF_ID, notification)
        }
    }

    private fun ensureUpdateChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATES,
                context.getString(R.string.notif_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_updates_desc)
            }
        )
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val clean = { v: String -> v.substringBefore("-").substringBefore("_") }
        val cParts = clean(candidate).split(".").mapNotNull { it.toIntOrNull() }
        val oParts = clean(current).split(".").mapNotNull { it.toIntOrNull() }
        if (cParts.isEmpty() || oParts.isEmpty()) return candidate != current
        val len = maxOf(cParts.size, oParts.size)
        for (i in 0 until len) {
            val c = cParts.getOrElse(i) { 0 }
            val o = oParts.getOrElse(i) { 0 }
            if (c > o) return true
            if (c < o) return false
        }
        return false
    }
}
