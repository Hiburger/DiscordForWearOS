package com.zaffox.discordwear

import com.zaffox.discordwear.api.MessageNotification
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPolicyTest {

    private fun notif(isDm: Boolean) = MessageNotification(
        channelId = "ch1",
        channelName = if (isDm) null else "general",
        guildId = if (isDm) null else "guild1",
        guildName = if (isDm) null else "Test Guild",
        authorName = "alice",
        content = "hello",
        isDm = isDm
    )

    @Test
    fun `master off silences everything`() {
        assertFalse(
            NotificationPolicy.shouldNotify(
                masterEnabled = false, notifyDms = true, notifyMentions = true, notif = notif(isDm = true)
            )
        )
        assertFalse(
            NotificationPolicy.shouldNotify(
                masterEnabled = false, notifyDms = true, notifyMentions = true, notif = notif(isDm = false)
            )
        )
    }

    @Test
    fun `dm notifies when dms on`() {
        assertTrue(
            NotificationPolicy.shouldNotify(
                masterEnabled = true, notifyDms = true, notifyMentions = false, notif = notif(isDm = true)
            )
        )
    }

    @Test
    fun `dm silenced when dms off`() {
        assertFalse(
            NotificationPolicy.shouldNotify(
                masterEnabled = true, notifyDms = false, notifyMentions = true, notif = notif(isDm = true)
            )
        )
    }

    @Test
    fun `mention notifies when mentions on`() {
        assertTrue(
            NotificationPolicy.shouldNotify(
                masterEnabled = true, notifyDms = false, notifyMentions = true, notif = notif(isDm = false)
            )
        )
    }

    @Test
    fun `mention silenced when mentions off`() {
        assertFalse(
            NotificationPolicy.shouldNotify(
                masterEnabled = true, notifyDms = true, notifyMentions = false, notif = notif(isDm = false)
            )
        )
    }

    @Test
    fun `dms and mentions are independent toggles`() {
        // DM toggle on must not leak into guild messages
        assertFalse(
            NotificationPolicy.shouldNotify(
                masterEnabled = true, notifyDms = true, notifyMentions = false, notif = notif(isDm = false)
            )
        )
        // Mention toggle on must not leak into DMs
        assertFalse(
            NotificationPolicy.shouldNotify(
                masterEnabled = true, notifyDms = false, notifyMentions = true, notif = notif(isDm = true)
            )
        )
    }
}
