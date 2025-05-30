package org.wikipedia.tests.search

import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.hamcrest.Matchers.allOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.wikipedia.Constants
import org.wikipedia.R
import org.wikipedia.TestUtil
import org.wikipedia.search.SearchActivity

@LargeTest
@RunWith(AndroidJUnit4::class)
class SearchIntentTest {

    @Rule
    @JvmField
    var mActivityTestRule = ActivityScenarioRule<SearchActivity>(
        SearchActivity.newIntent(
            ApplicationProvider.getApplicationContext(),
            Constants.InvokeSource.INTENT_SHARE, "barack obama"
        )
    )

    @Test
    fun testSearchActivityWithQuery() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        TestUtil.delay(5)

        onView(allOf(withId(R.id.page_list_item_title), withText("Barack Obama"), isDisplayed()))
            .check(matches(withText("Barack Obama")))

        TestUtil.delay(2)

        device.setOrientationRight()
        TestUtil.delay(2)

        Espresso.pressBack()
        TestUtil.delay(1)

        onView(allOf(withId(R.id.page_list_item_title), withText("Barack Obama"), isDisplayed()))
            .check(matches(withText("Barack Obama")))

        device.setOrientationNatural()
        TestUtil.delay(2)

        device.unfreezeRotation()

        TestUtil.delay(2)

        TestUtil.setAirplaneMode(true)

        TestUtil.delay(2)

        onView(allOf(withId(R.id.search_lang_button), isDisplayed()))
            .check(matches(withText("EN")))

        TestUtil.delay(1)

        onView(allOf(withId(R.id.search_lang_button_container), isDisplayed()))
            .perform(ViewActions.click())

        TestUtil.delay(1)

        onView(withId(R.id.wikipedia_languages_recycler))
            .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(2, ViewActions.click()))

        TestUtil.delay(1)

        onView(allOf(withId(R.id.menu_search_language), isDisplayed()))
            .perform(ViewActions.click())

        TestUtil.delay(1)

        onView(allOf(withId(androidx.appcompat.R.id.search_src_text), isDisplayed()))
            .perform(ViewActions.replaceText("rus"), ViewActions.closeSoftKeyboard())

        TestUtil.delay(1)

        TestUtil.delay(1)

        Espresso.pressBack()

        TestUtil.delay(1)

        onView(allOf(TestUtil.childAtPosition(TestUtil.childAtPosition(withId(R.id.horizontal_scroll_languages), 0), 1), isDisplayed()))
            .perform(ViewActions.click())

        TestUtil.delay(1)

        onView(allOf(withText("Retry"), isDisplayed()))
            .check(matches(isDisplayed()))

        TestUtil.setAirplaneMode(false, 5)

        TestUtil.delay(5)

        onView(allOf(withId(R.id.page_list_item_title), withText("Обама, Барак"), isDisplayed()))
            .check(matches(withText("Обама, Барак")))

        TestUtil.delay(2)
    }
}
