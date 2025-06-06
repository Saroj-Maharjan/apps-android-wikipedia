package org.wikipedia.talk

import android.annotation.SuppressLint
import android.app.Activity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuPopupHelper
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.analytics.eventplatform.PatrollerExperienceEvent
import org.wikipedia.auth.AccountUtil
import org.wikipedia.dataclient.ServiceFactory
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.history.HistoryEntry
import org.wikipedia.page.ExclusiveBottomSheetPresenter
import org.wikipedia.page.Namespace
import org.wikipedia.page.PageActivity
import org.wikipedia.page.PageTitle
import org.wikipedia.page.linkpreview.LinkPreviewDialog
import org.wikipedia.staticdata.UserTalkAliasData
import org.wikipedia.suggestededits.SuggestedEditsRecentEditsActivity
import org.wikipedia.suggestededits.SuggestionsActivity
import org.wikipedia.usercontrib.UserContribListActivity
import org.wikipedia.usercontrib.UserInformationDialog
import org.wikipedia.util.FeedbackUtil
import org.wikipedia.util.log.L

@SuppressLint("RestrictedApi")
object UserTalkPopupHelper {

    fun show(activity: AppCompatActivity, title: PageTitle, anon: Boolean, anchorView: View,
              invokeSource: Constants.InvokeSource, historySource: Int, revisionId: Long? = null, pageId: Int? = null, showUserInfo: Boolean = false) {
        val pos = IntArray(2)
        anchorView.getLocationInWindow(pos)
        show(activity, title, anon, pos[0], pos[1], invokeSource, historySource, revisionId = revisionId, pageId = pageId, showUserInfo = showUserInfo)
    }

    fun show(activity: AppCompatActivity, title: PageTitle, anon: Boolean, x: Int, y: Int, invokeSource: Constants.InvokeSource,
             historySource: Int, showContribs: Boolean = true, showUserInfo: Boolean = false, revisionId: Long? = null, pageId: Int? = null) {
        if (title.namespace() == Namespace.USER_TALK || title.namespace() == Namespace.TALK) {
            activity.startActivity(TalkTopicsActivity.newIntent(activity, title, invokeSource))
        } else if (title.namespace() == Namespace.USER) {
            val rootView = activity.window.decorView
            val anchorView = View(activity)
            anchorView.x = (x - rootView.left).toFloat()
            anchorView.y = (y - rootView.top).toFloat()
            (rootView as ViewGroup).addView(anchorView)

            val helper = getPopupHelper(activity, title, anon, anchorView, invokeSource, historySource,
                showContribs, showUserInfo, revisionId = revisionId, pageId = pageId)
            helper.setOnDismissListener {
                rootView.removeView(anchorView)
            }

            helper.show()
        } else {
            ExclusiveBottomSheetPresenter.show(activity.supportFragmentManager,
                    LinkPreviewDialog.newInstance(HistoryEntry(title, historySource)))
        }
    }

    private fun showThankDialog(activity: AppCompatActivity, title: PageTitle, revisionId: Long, pageId: Int) {
        val parent = FrameLayout(activity)
        val dialog =
            MaterialAlertDialogBuilder(activity)
                .setView(parent)
                .setPositiveButton(R.string.thank_dialog_positive_button_text) { _, _ ->
                    sendPatrollerExperienceEvent(activity, "thank_confirm")
                    sendThanks(activity, title.wikiSite, revisionId, title)
                }
                .setNegativeButton(R.string.thank_dialog_negative_button_text) { _, _ ->
                    sendPatrollerExperienceEvent(activity, "thank_cancel")
                }
                .create()
        dialog.layoutInflater.inflate(R.layout.view_thank_dialog, parent)
        dialog.show()
    }

    private fun sendThanks(activity: AppCompatActivity, wikiSite: WikiSite, revisionId: Long?, title: PageTitle) {
        activity.lifecycleScope.launch(CoroutineExceptionHandler { _, throwable ->
            L.e(throwable)
        }) {
            val token = ServiceFactory.get(wikiSite).getToken().query?.csrfToken()
            if (revisionId != null && token != null) {
                ServiceFactory.get(wikiSite).postThanksToRevision(revisionId, token)
                FeedbackUtil.showMessage(activity, activity.getString(R.string.thank_success_message, title.text))
            }
        }
    }

    private fun getPopupHelper(activity: AppCompatActivity, title: PageTitle, anon: Boolean,
                               anchorView: View, invokeSource: Constants.InvokeSource,
                               historySource: Int, showContribs: Boolean = true,
                               showUserInfo: Boolean = false, revisionId: Long? = null, pageId: Int? = null): MenuPopupHelper {
        val builder = MenuBuilder(activity)
        activity.menuInflater.inflate(R.menu.menu_user_talk_popup, builder)
        builder.setCallback(object : MenuBuilder.Callback {
            override fun onMenuItemSelected(menu: MenuBuilder, item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.menu_user_profile_page -> {
                        sendPatrollerExperienceEvent(activity, "menu_user_page_click")
                        val entry = HistoryEntry(title, historySource)
                        activity.startActivity(PageActivity.newIntentForNewTab(activity, entry, title))
                    }
                    R.id.menu_user_talk_page -> {
                        sendPatrollerExperienceEvent(activity, "menu_talk_page_click")
                        val newTitle = PageTitle(UserTalkAliasData.valueFor(title.wikiSite.languageCode), title.text, title.wikiSite)
                        activity.startActivity(TalkTopicsActivity.newIntent(activity, newTitle, invokeSource))
                    }
                    R.id.menu_user_information -> {
                        sendPatrollerExperienceEvent(activity, "menu_user_info_click")
                        UserInformationDialog.newInstance(title.text).show(activity.supportFragmentManager, null)
                    }
                    R.id.menu_user_contributions_page -> {
                        sendPatrollerExperienceEvent(activity, "menu_user_contribs_click")
                        activity.startActivity(UserContribListActivity.newIntent(activity, title.text))
                    }
                    R.id.menu_user_thank -> {
                        sendPatrollerExperienceEvent(activity, "menu_user_thank_click")
                        if (pageId != null && revisionId != null) {
                            showThankDialog(activity, title, revisionId, pageId)
                        }
                    }
                }
                return true
            }

            override fun onMenuModeChange(menu: MenuBuilder) { }
        })

        builder.findItem(R.id.menu_user_profile_page).isVisible = !anon
        builder.findItem(R.id.menu_user_contributions_page).isVisible = showContribs
        builder.findItem(R.id.menu_user_information).isVisible = showUserInfo && !anon
        builder.findItem(R.id.menu_user_thank).isVisible = revisionId != null && !anon && AccountUtil.isLoggedIn
        val helper = MenuPopupHelper(activity, builder, anchorView)
        helper.setForceShowIcon(true)
        return helper
    }

    private fun sendPatrollerExperienceEvent(activity: Activity, action: String) {
        if (activity is SuggestedEditsRecentEditsActivity || activity is SuggestionsActivity) {
            PatrollerExperienceEvent.logAction(action, if (activity is SuggestionsActivity) "pt_edit" else "pt_recent_changes")
        }
    }
}
