package org.fossify.messages.receivers

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.bumptech.glide.Glide
import com.klinker.android.send_message.MmsReceivedReceiver
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.R
import org.fossify.messages.extensions.*
import org.fossify.messages.helpers.ReceiverUtils.isMessageFilteredOut
import org.fossify.messages.helpers.refreshMessages
import org.fossify.messages.models.Message

// more info at https://github.com/klinker41/android-smsmms
class MmsReceiver : MmsReceivedReceiver() {

    override fun isAddressBlocked(context: Context, address: String): Boolean {
        val normalizedAddress = address.normalizePhoneNumber()
        return context.isNumberBlocked(normalizedAddress)
    }

    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val mms = context.getLatestMMS() ?: return
        val address = mms.getSender()?.phoneNumbers?.first()?.normalizedNumber ?: ""

        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        val privateCursor = context.getMyContactsCursor(false, true)
        ensureBackgroundThread {
            if (context.baseConfig.blockUnknownNumbers) {
                val simpleContactsHelper = SimpleContactsHelper(context)
                simpleContactsHelper.exists(address, privateCursor) { exists ->
                    if (exists) {
                        handleMmsMessage(context, mms, size, address)
                    }
                }
            } else {
                handleMmsMessage(context, mms, size, address)
            }
        }
    }

    override fun onError(context: Context, error: String) = context.showErrorToast(context.getString(R.string.couldnt_download_mms))

    private fun handleMmsMessage(
        context: Context,
        mms: Message,
        size: Int,
        address: String
    ) {
        if (isMessageFilteredOut(context, mms.body)) {
            return
        }

        val glideBitmap = try {
            Glide.with(context)
                .asBitmap()
                .load(mms.attachment!!.attachments.first().getUri())
                .centerCrop()
                .into(size, size)
                .get()
        } catch (e: Exception) {
            null
        }

        Handler(Looper.getMainLooper()).post {
            context.showReceivedMessageNotification(mms.id, address, mms.body, mms.threadId, glideBitmap)
            val conversation = context.getConversations(mms.threadId).firstOrNull() ?: return@post
            ensureBackgroundThread {
                context.insertOrUpdateConversation(conversation)
                context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
                refreshMessages()
            }
        }
    }
}
