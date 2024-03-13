package org.fossify.messages.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.WAS_PROTECTION_HANDLED
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.messages.adapters.BlockedConversationsAdapter
import org.fossify.messages.databinding.ActivityBlockedConversationsBinding
import org.fossify.messages.extensions.config
import org.fossify.messages.extensions.conversationsDB
import org.fossify.messages.helpers.THREAD_ID
import org.fossify.messages.helpers.THREAD_TITLE
import org.fossify.messages.models.Conversation
import org.fossify.messages.models.Events
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class BlockedConversationsActivity : SimpleActivity() {
    private var bus: EventBus? = null
    private val binding by viewBinding(ActivityBlockedConversationsBinding::inflate)

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(
            mainCoordinatorLayout = binding.blockedCoordinator,
            nestedView = binding.conversationsList,
            useTransparentNavigation = true,
            useTopSearchMenu = false
        )
        setupMaterialScrollListener(scrollingView = binding.conversationsList, toolbar = binding.blockedToolbar)

        loadBlockedConversations()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.blockedToolbar, NavigationIcon.Arrow)
        updateMenuColors()

        loadBlockedConversations()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    private fun updateMenuColors() {
        updateStatusbarColor(getProperBackgroundColor())
    }

    private fun loadBlockedConversations() {
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getAllBlocked().toMutableList() as ArrayList<Conversation>
            } catch (e: Exception) {
                ArrayList()
            }

            runOnUiThread {
                setupConversations(conversations)
            }
        }

        bus = EventBus.getDefault()
        try {
            bus!!.register(this)
        } catch (ignored: Exception) {
        }
    }

    private fun getOrCreateConversationsAdapter(): BlockedConversationsAdapter {
        var currAdapter = binding.conversationsList.adapter
        if (currAdapter == null) {
            hideKeyboard()
            currAdapter = BlockedConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )

            binding.conversationsList.adapter = currAdapter
            if (areSystemAnimationsEnabled) {
                binding.conversationsList.scheduleLayoutAnimation()
            }
        }
        return currAdapter as BlockedConversationsAdapter
    }

    private fun setupConversations(conversations: ArrayList<Conversation>) {
        val sortedConversations = conversations.sortedWith(
            compareByDescending<Conversation> { config.pinnedConversations.contains(it.threadId.toString()) }
                .thenByDescending { it.date }
        ).toMutableList() as ArrayList<Conversation>

        showOrHidePlaceholder(conversations.isEmpty())

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(sortedConversations)
            }
        } catch (ignored: Exception) {
        }
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        binding.conversationsFastscroller.beGoneIf(show)
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.setTextColor(getProperTextColor())
        binding.noConversationsPlaceholder.text = "No blocked conversations found"
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged() {
        getOrCreateConversationsAdapter().notifyDataSetChanged()
    }

    private fun handleConversationClick(any: Any) {
        Intent(this, ThreadActivity::class.java).apply {
            val conversation = any as Conversation
            putExtra(THREAD_ID, conversation.threadId)
            putExtra(THREAD_TITLE, conversation.title)
            putExtra(WAS_PROTECTION_HANDLED, true)
            startActivity(this)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(event: Events.RefreshMessages) {
        loadBlockedConversations()
    }
}
