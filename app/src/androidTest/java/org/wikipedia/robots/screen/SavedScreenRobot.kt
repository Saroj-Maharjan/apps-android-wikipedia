package org.wikipedia.robots.screen

import android.app.Activity
import android.util.Log
import org.wikipedia.R
import org.wikipedia.base.BaseRobot
import org.wikipedia.base.TestConfig

class SavedScreenRobot : BaseRobot() {

    fun clickOnFirstItemInTheList() = apply {
        clickRecyclerViewItemAtPosition(R.id.recycler_view, 0)
        delay(TestConfig.DELAY_LARGE)
    }

    fun dismissTooltip(activity: Activity) = apply {
        dismissTooltipIfAny(activity, viewId = R.id.buttonView)
    }

    fun assertIfListMatchesTheArticleTitle(text: String) = apply {
        checkWithTextIsDisplayed(viewId = R.id.page_list_item_title, text)
        delay(TestConfig.DELAY_SHORT)
    }

    fun openArticleWithTitle(text: String) = apply {
        clicksOnDisplayedViewWithText(viewId = R.id.page_list_item_title, text)
        delay(TestConfig.DELAY_LARGE)
    }

    fun dismissSyncReadingList() = apply {
        try {
            clickOnViewWithId(R.id.negativeButton)
            delay(TestConfig.DELAY_SHORT)
        } catch (e: Exception) {
            Log.e("SavedScreenRobot: ", "${e.message}")
        }
    }

    fun pressBack() = apply {
        goBack()
        delay(TestConfig.DELAY_SHORT)
    }
}
