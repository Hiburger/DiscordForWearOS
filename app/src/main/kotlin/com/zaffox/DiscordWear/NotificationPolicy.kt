package com.zaffox.discordwear

import com.zaffox.discordwear.api.MessageNotification

// Pure decision logic for whether an inbound message should raise a notification
object NotificationPolicy {
    fun shouldNotify(
        masterEnabled: Boolean,
        notifyDms: Boolean,
        notifyMentions: Boolean,
        notif: MessageNotification
    ): Boolean {
        if (!masterEnabled) return false
        return if (notif.isDm) notifyDms else notifyMentions
    }
}
