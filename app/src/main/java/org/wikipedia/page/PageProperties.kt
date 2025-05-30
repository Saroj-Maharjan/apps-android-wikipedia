package org.wikipedia.page

import android.location.Location
import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import org.wikipedia.auth.AccountUtil
import org.wikipedia.dataclient.page.PageSummary
import org.wikipedia.dataclient.page.Protection
import org.wikipedia.parcel.DateParceler
import org.wikipedia.util.DateUtil
import org.wikipedia.util.DimenUtil
import org.wikipedia.util.ImageUrlUtil
import org.wikipedia.util.UriUtil
import java.util.Date

@Parcelize
@TypeParceler<Date, DateParceler>()
data class PageProperties(
    val pageId: Int = 0,
    val namespace: Namespace,
    val revisionId: Long = 0,
    val lastModified: Date = Date(),
    val displayTitle: String = "",
    private var editProtectionStatus: String = "",
    val isMainPage: Boolean = false,
    /** Nullable URL with no scheme. For example, foo.bar.com/ instead of http://foo.bar.com/.  */
    val leadImageUrl: String? = null,
    val leadImageName: String? = null,
    val leadImageWidth: Int = 0,
    val leadImageHeight: Int = 0,
    val geo: Location? = null,
    val wikiBaseItem: String? = null,
    val descriptionSource: String? = null,
    // FIXME: This is not a true page property, since it depends on current user.
    var canEdit: Boolean = false
) : Parcelable {

    @IgnoredOnParcel
    var protection: Protection? = null
        set(value) {
            field = value
            editProtectionStatus = value?.firstAllowedEditorRole.orEmpty()
            canEdit = editProtectionStatus.isEmpty() || isLoggedInUserAllowedToEdit
        }

    /**
     * Side note: Should later be moved out of this class but I like the similarities with
     * PageProperties(JSONObject).
     */
    constructor(pageSummary: PageSummary) : this(
        pageSummary.pageId,
        pageSummary.ns,
        pageSummary.revision,
        if (pageSummary.timestamp.isEmpty()) Date() else DateUtil.iso8601DateParse(pageSummary.timestamp),
        pageSummary.displayTitle,
        isMainPage = pageSummary.type == PageSummary.TYPE_MAIN_PAGE,
        leadImageUrl = pageSummary.thumbnailUrl?.let { ImageUrlUtil.getUrlForPreferredSize(it, DimenUtil.calculateLeadImageWidth()) },
        leadImageName = UriUtil.decodeURL(pageSummary.leadImageName.orEmpty()),
        leadImageWidth = pageSummary.thumbnail?.width ?: 0,
        leadImageHeight = pageSummary.thumbnail?.height ?: 0,
        geo = pageSummary.coordinates,
        wikiBaseItem = pageSummary.wikiBaseItem,
        descriptionSource = pageSummary.descriptionSource
    )

    private val isLoggedInUserAllowedToEdit: Boolean
        get() = protection?.run { AccountUtil.isMemberOf(editRoles) } == true
}
