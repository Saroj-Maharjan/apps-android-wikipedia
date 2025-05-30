package org.wikipedia.feed.onthisday

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import org.wikipedia.Constants.InvokeSource
import org.wikipedia.R
import org.wikipedia.databinding.ViewCardOnThisDayBinding
import org.wikipedia.feed.view.CardFooterView
import org.wikipedia.feed.view.DefaultFeedCardView
import org.wikipedia.feed.view.FeedAdapter
import org.wikipedia.history.HistoryEntry
import org.wikipedia.readinglist.LongPressMenu
import org.wikipedia.readinglist.ReadingListBehaviorsUtil
import org.wikipedia.readinglist.database.ReadingListPage
import org.wikipedia.util.DateUtil
import org.wikipedia.util.StringUtil
import org.wikipedia.util.TransitionUtil
import org.wikipedia.views.ImageZoomHelper

class OnThisDayCardView(context: Context) : DefaultFeedCardView<OnThisDayCard>(context), CardFooterView.Callback {

    private val binding = ViewCardOnThisDayBinding.inflate(LayoutInflater.from(context), this, true)
    private var age = 0

    init {
        binding.onThisDayCardViewClickContainer.setOnClickListener { view -> onCardClicked(view) }
    }

    override fun onFooterClicked() {
        card?.let {
            val options = ActivityOptions.makeSceneTransitionAnimation(context as Activity,
                binding.cardHeader.titleView, context.getString(R.string.transition_on_this_day))
            context.startActivity(OnThisDayActivity.newIntent(context, age, -1,
                it.wikiSite(), InvokeSource.ON_THIS_DAY_CARD_FOOTER), options.toBundle())
        }
    }

    override var callback: FeedAdapter.Callback? = null
        set(value) {
            field = value
            binding.cardHeader.setCallback(value)
        }

    override var card: OnThisDayCard? = null
        set(value) {
            field = value
            value?.let {
                age = it.age
                setLayoutDirectionByWikiSite(it.wikiSite(), binding.rtlContainer)
                binding.eventLayout.yearsText.text = DateUtil.getYearDifferenceString(context, it.year(), it.wikiSite().languageCode)
                updateOtdEventUI(it)
                header(it)
                footer(it)
            }
        }

    private fun header(card: OnThisDayCard) {
        binding.cardHeader
            .setTitle(card.title())
            .setLangCode(card.wikiSite().languageCode)
            .setCard(card)
            .setCallback(callback)
        binding.eventLayout.text.text = card.text()
        binding.eventLayout.year.text = DateUtil.yearToStringWithEra(card.year())
    }

    private fun footer(card: OnThisDayCard) {
        binding.eventLayout.pagesIndicator.visibility = GONE
        binding.cardFooterView.setFooterActionText(
            card.footerActionText(),
            card.wikiSite().languageCode
        )
        binding.cardFooterView.callback = this
    }

    private fun onCardClicked(view: View) {
        card?.let {
            val isYearClicked = view.id == R.id.year
            val options = ActivityOptions.makeSceneTransitionAnimation(context as Activity,
                binding.cardHeader.titleView, context.getString(R.string.transition_on_this_day))
            context.startActivity(OnThisDayActivity.newIntent(context, age, if (isYearClicked) it.year() else -1, it.wikiSite(),
                    if (isYearClicked) InvokeSource.ON_THIS_DAY_CARD_YEAR else InvokeSource.ON_THIS_DAY_CARD_BODY), options.toBundle()
            )
        }
    }

    private fun updateOtdEventUI(card: OnThisDayCard) {
        binding.eventLayout.pagesPager.visibility = GONE
        if (card.pages().isEmpty()) {
            binding.eventLayout.onThisDayPage.root.visibility = GONE
            return
        }
        binding.eventLayout.onThisDayPage.root.visibility = VISIBLE
        val chosenPage = card.pages().find { it.thumbnailUrl != null }
        chosenPage?.let { page ->
            if (page.thumbnailUrl.isNullOrEmpty()) {
                binding.eventLayout.onThisDayPage.imageContainer.visibility = GONE
            } else {
                binding.eventLayout.onThisDayPage.imageContainer.visibility = VISIBLE
                binding.eventLayout.onThisDayPage.image.loadImage(Uri.parse(page.thumbnailUrl))
                ImageZoomHelper.setViewZoomable(binding.eventLayout.onThisDayPage.image)
            }
            binding.eventLayout.onThisDayPage.description.text = page.description
            binding.eventLayout.onThisDayPage.description.visibility =
                if (page.description.isNullOrEmpty()) GONE else VISIBLE
            binding.eventLayout.onThisDayPage.title.maxLines =
                if (page.description.isNullOrEmpty()) 2 else 1
            binding.eventLayout.onThisDayPage.title.text = StringUtil.fromHtml(page.displayTitle)
            binding.eventLayout.onThisDayPage.root.setOnClickListener {
                callback?.onSelectPage(card,
                    HistoryEntry(page.getPageTitle(card.wikiSite()), HistoryEntry.SOURCE_ON_THIS_DAY_CARD),
                    TransitionUtil.getSharedElements(context, binding.eventLayout.onThisDayPage.image)
                )
            }
            binding.eventLayout.onThisDayPage.root.setOnLongClickListener { view ->
                if (ImageZoomHelper.isZooming) {
                    ImageZoomHelper.dispatchCancelEvent(binding.eventLayout.onThisDayPage.root)
                } else {
                    val pageTitle = page.getPageTitle(card.wikiSite())
                    val entry = HistoryEntry(pageTitle, HistoryEntry.SOURCE_ON_THIS_DAY_CARD)
                    LongPressMenu(view, callback = object : LongPressMenu.Callback {
                        override fun onOpenLink(entry: HistoryEntry) {
                            callback?.onSelectPage(
                                card,
                                entry,
                                TransitionUtil.getSharedElements(
                                    context,
                                    binding.eventLayout.onThisDayPage.image
                                )
                            )
                        }

                        override fun onOpenInNewTab(entry: HistoryEntry) {
                            callback?.onSelectPage(card, entry, true)
                        }

                        override fun onAddRequest(entry: HistoryEntry, addToDefault: Boolean) {
                            ReadingListBehaviorsUtil.addToDefaultList(context as Activity, entry.title, addToDefault, InvokeSource.ON_THIS_DAY_CARD_BODY)
                        }

                        override fun onMoveRequest(page: ReadingListPage?, entry: HistoryEntry) {
                            page?.let {
                                ReadingListBehaviorsUtil.moveToList(context as Activity, page.listId, entry.title, InvokeSource.ON_THIS_DAY_CARD_BODY)
                            }
                        }
                    }).show(entry)
                }
                true
            }
        }
    }
}
