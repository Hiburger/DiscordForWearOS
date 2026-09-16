package com.zaffox.discordwear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zaffox.discordwear.api.MessageNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the Discord gateway alive while the app is
 * closed and raises Wear OS notifications for new DMs and mentions.
 */
class NotificationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(
            PERSISTENT_ID,
            buildPersistentNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        )
        observeNotifications()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // (Re)connect the repository if the process was restarted by the system
        if (discordApp.repository == null) {
            SetupPreferences.getToken(this)?.let { token ->
                discordApp.initRepository(token)
            }
        }
        // Already-running services skip onCreate; stay foreground on restarts
        startForeground(
            PERSISTENT_ID,
            buildPersistentNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
        )
        // Repository may have just been created; (re)start observing
        observeNotifications()
        return START_STICKY
    }

    private var observedRepo: com.zaffox.discordwear.api.DiscordRepository? = null

    private fun observeNotifications() {
        val repo = discordApp.repository ?: return
        if (observedRepo === repo) return
        observedRepo = repo
        scope.launch {
            repo.notifications.collect { notif ->
                if (shouldNotify(notif)) postNotification(notif)
            }
        }
    }

    private fun shouldNotify(notif: MessageNotification): Boolean =
        NotificationPolicy.shouldNotify(
            masterEnabled = SetupPreferences.isNotificationsEnabled(this),
            notifyDms = SetupPreferences.isNotifyDms(this),
            notifyMentions = SetupPreferences.isNotifyMentions(this),
            notif = notif
        )

    private fun postNotification(notif: MessageNotification) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_CHANNEL_ID, notif.channelId)
            putExtra(MainActivity.EXTRA_CHANNEL_NAME, notif.channelName ?: notif.channelId)
            putExtra(MainActivity.EXTRA_GUILD_ID, notif.guildId)
        }
        val pending = PendingIntent.getActivity(
            this,
            notif.channelId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val where = if (notif.isDm) notif.authorName
        else "${notif.guildName ?: "Server"} • ${notif.channelName ?: ""}"

        val notification = NotificationCompat.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.announce)
            .setContentTitle(where)
            .setContentText("${notif.authorName}: ${notif.content}".take(180))
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("${notif.authorName}: ${notif.content}"))
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .extend(NotificationCompat.WearableExtender())
            .build()

        runCatching {
            NotificationManagerCompat.from(this)
                .notify(notif.channelId.hashCode(), notification)
        }
    }

    private fun buildPersistentNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.announce)
            .setContentTitle(getString(R.string.notif_listening_title))
            .setContentText(getString(R.string.notif_listening_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .extend(NotificationCompat.WearableExtender())
            .build()

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, getString(R.string.notif_channel_status),
                NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, getString(R.string.notif_channel_messages),
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.notif_channel_messages_desc)
            }
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_STATUS = "discord_status"
        private const val CHANNEL_MESSAGES = "discord_messages"
        private const val PERSISTENT_ID = 42

        fun start(context: android.content.Context) {
            val intent = Intent(context, NotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, NotificationService::class.java))
        }
    }
}
