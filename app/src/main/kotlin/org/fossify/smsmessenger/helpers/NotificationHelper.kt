package org.fossify.smsmessenger.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager.IMPORTANCE_HIGH
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.isNougatPlus
import org.fossify.commons.helpers.isOreoPlus
import org.fossify.smsmessenger.R
import org.fossify.smsmessenger.activities.ThreadActivity
import org.fossify.smsmessenger.extensions.config
import org.fossify.smsmessenger.messaging.isShortCodeWithLetters
import org.fossify.smsmessenger.receivers.DeleteSmsReceiver
import org.fossify.smsmessenger.receivers.DirectReplyReceiver
import org.fossify.smsmessenger.receivers.MarkAsReadReceiver

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.notificationManager
    private val soundUri get() = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    private val user = Person.Builder()
        .setName(context.getString(R.string.me))
        .build()

    @SuppressLint("NewApi")
    fun showMessageNotification(
        messageId: Long,
        address: String,
        body: String,
        threadId: Long,
        bitmap: Bitmap?,
        sender: String?,
        alertOnlyOnce: Boolean = false
    ) {
        val notificationId = threadId.hashCode()

        val hasCustomNotifications = context.config.customNotifications.contains(threadId.toString())
        val notificationChannel = if (hasCustomNotifications) notificationId.toString() else NOTIFICATION_CHANNEL
        if (!hasCustomNotifications) {
            maybeCreateChannel(notificationChannel, context.getString(R.string.channel_received_sms))
        }

        val contentIntent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent =
            PendingIntent.getActivity(context, notificationId, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        val markAsReadIntent = Intent(context, MarkAsReadReceiver::class.java).apply {
            action = MARK_AS_READ
            putExtra(THREAD_ID, threadId)
        }
        val markAsReadPendingIntent =
            PendingIntent.getBroadcast(context, notificationId, markAsReadIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        val deleteSmsIntent = Intent(context, DeleteSmsReceiver::class.java).apply {
            putExtra(THREAD_ID, threadId)
            putExtra(MESSAGE_ID, messageId)
        }
        val deleteSmsPendingIntent =
            PendingIntent.getBroadcast(context, notificationId, deleteSmsIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        var replyAction: NotificationCompat.Action? = null
        val isNoReplySms = isShortCodeWithLetters(address)
        if (isNougatPlus() && !isNoReplySms) {
            val replyLabel = context.getString(R.string.reply)
            val remoteInput = RemoteInput.Builder(REPLY)
                .setLabel(replyLabel)
                .build()

            val replyIntent = Intent(context, DirectReplyReceiver::class.java).apply {
                putExtra(THREAD_ID, threadId)
                putExtra(THREAD_NUMBER, address)
            }

            val replyPendingIntent =
                PendingIntent.getBroadcast(
                    context.applicationContext,
                    notificationId,
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            replyAction = NotificationCompat.Action.Builder(R.drawable.ic_send_vector, replyLabel, replyPendingIntent)
                .addRemoteInput(remoteInput)
                .build()
        }

        val largeIcon = bitmap ?: if (sender != null) {
            SimpleContactsHelper(context).getContactLetterIcon(sender)
        } else {
            null
        }
        val builder = NotificationCompat.Builder(context, notificationChannel).apply {
            when (context.config.lockScreenVisibilitySetting) {
                LOCK_SCREEN_SENDER_MESSAGE -> {
                    setLargeIcon(largeIcon)
                    setStyle(getMessagesStyle(address, body, notificationId, sender))
                }

                LOCK_SCREEN_SENDER -> {
                    setContentTitle(sender)
                    setLargeIcon(largeIcon)
                    val summaryText = context.getString(R.string.new_message)
                    setStyle(NotificationCompat.BigTextStyle().setSummaryText(summaryText).bigText(body))
                }
            }

            color = context.getProperPrimaryColor()
            setSmallIcon(R.drawable.ic_messenger)
            setContentIntent(contentPendingIntent)
            priority = NotificationCompat.PRIORITY_MAX
            setDefaults(Notification.DEFAULT_LIGHTS)
            setCategory(Notification.CATEGORY_MESSAGE)
            setAutoCancel(true)
            setOnlyAlertOnce(alertOnlyOnce)
            setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
        }

        if (replyAction != null && context.config.lockScreenVisibilitySetting == LOCK_SCREEN_SENDER_MESSAGE) {
            builder.addAction(replyAction)
        }

        builder.addAction(org.fossify.commons.R.drawable.ic_check_vector, context.getString(R.string.mark_as_read), markAsReadPendingIntent)
            .setChannelId(notificationChannel)
        if (isNoReplySms) {
            builder.addAction(
                org.fossify.commons.R.drawable.ic_delete_vector,
                context.getString(org.fossify.commons.R.string.delete),
                deleteSmsPendingIntent
            ).setChannelId(notificationChannel)
        }
        notificationManager.notify(notificationId, builder.build())
    }

    @SuppressLint("NewApi")
    fun showSendingFailedNotification(recipientName: String, threadId: Long) {
        val hasCustomNotifications = context.config.customNotifications.contains(threadId.toString())
        val notificationChannel = if (hasCustomNotifications) threadId.hashCode().toString() else NOTIFICATION_CHANNEL
        if (!hasCustomNotifications) {
            maybeCreateChannel(notificationChannel, context.getString(R.string.message_not_sent_short))
        }

        val notificationId = generateRandomId().hashCode()
        val intent = Intent(context, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, threadId)
        }
        val contentPendingIntent = PendingIntent.getActivity(context, notificationId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        val summaryText = String.format(context.getString(R.string.message_sending_error), recipientName)
        val largeIcon = SimpleContactsHelper(context).getContactLetterIcon(recipientName)
        val builder = NotificationCompat.Builder(context, notificationChannel)
            .setContentTitle(context.getString(R.string.message_not_sent_short))
            .setContentText(summaryText)
            .setColor(context.getProperPrimaryColor())
            .setSmallIcon(R.drawable.ic_messenger)
            .setLargeIcon(largeIcon)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setChannelId(notificationChannel)

        notificationManager.notify(notificationId, builder.build())
    }

    private fun maybeCreateChannel(id: String, name: String) {
        if (isOreoPlus()) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setLegacyStreamType(AudioManager.STREAM_NOTIFICATION)
                .build()

            val importance = IMPORTANCE_HIGH
            NotificationChannel(id, name, importance).apply {
                setBypassDnd(false)
                enableLights(true)
                setSound(soundUri, audioAttributes)
                enableVibration(true)
                notificationManager.createNotificationChannel(this)
            }
        }
    }

    private fun getMessagesStyle(address: String, body: String, notificationId: Int, name: String?): NotificationCompat.MessagingStyle {
        val sender = if (name != null) {
            Person.Builder()
                .setName(name)
                .setKey(address)
                .build()
        } else {
            null
        }

        return NotificationCompat.MessagingStyle(user).also { style ->
            getOldMessages(notificationId).forEach {
                style.addMessage(it)
            }
            val newMessage = NotificationCompat.MessagingStyle.Message(body, System.currentTimeMillis(), sender)
            style.addMessage(newMessage)
        }
    }

    private fun getOldMessages(notificationId: Int): List<NotificationCompat.MessagingStyle.Message> {
        if (!isNougatPlus()) {
            return emptyList()
        }
        val currentNotification = notificationManager.activeNotifications.find { it.id == notificationId }
        return if (currentNotification != null) {
            val activeStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(currentNotification.notification)
            activeStyle?.messages.orEmpty()
        } else {
            emptyList()
        }
    }
}
