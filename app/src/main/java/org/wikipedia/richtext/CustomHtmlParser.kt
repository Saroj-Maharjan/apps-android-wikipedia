package org.wikipedia.richtext

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.Html.ImageGetter
import android.text.Html.TagHandler
import android.text.Spannable
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.text.style.ParagraphStyle
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.widget.TextView
import androidx.core.graphics.applyCanvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.text.HtmlCompat
import androidx.core.text.getSpans
import androidx.core.text.parseAsHtml
import androidx.core.text.toSpanned
import org.wikipedia.dataclient.Service
import org.wikipedia.dataclient.WikiSite
import org.wikipedia.extensions.maybeDimImage
import org.wikipedia.util.DimenUtil
import org.wikipedia.util.ResourceUtil
import org.wikipedia.util.log.L
import org.wikipedia.views.imageservice.ImageService
import org.xml.sax.Attributes
import org.xml.sax.ContentHandler
import org.xml.sax.Locator
import org.xml.sax.XMLReader
import java.util.Stack

class CustomHtmlParser(private val handler: TagHandler) : TagHandler, ContentHandler {
    interface TagHandler {
        fun handleTag(opening: Boolean, tag: String?, output: Editable?, attributes: Attributes?): Boolean
    }

    private var wrapped: ContentHandler? = null
    private var text: Editable? = null
    private val tagStatus = ArrayDeque<Boolean>()

    override fun handleTag(opening: Boolean, tag: String?, output: Editable?, xmlReader: XMLReader) {
        if (wrapped == null) {
            text = output
            wrapped = xmlReader.contentHandler
            xmlReader.contentHandler = this

            // Note: If it becomes necessary to inject a starting tag (see comments below), then
            // explicitly add a status to our queue to account for this tag:
            // tagStatus.addLast(false)
        }
    }

    override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
        val isHandled = handler.handleTag(true, localName, text, attributes)
        tagStatus.addLast(isHandled)
        if (!isHandled) {
            wrapped?.startElement(uri, localName, qName, attributes)
        }
    }

    override fun endElement(uri: String?, localName: String?, qName: String?) {
        if (tagStatus.isNotEmpty() && !tagStatus.removeLast()) {
            wrapped?.endElement(uri, localName, qName)
        }
        handler.handleTag(false, localName, text, null)
    }

    override fun setDocumentLocator(locator: Locator?) {
        wrapped?.setDocumentLocator(locator)
    }

    override fun startDocument() {
        wrapped?.startDocument()
    }

    override fun endDocument() {
        wrapped?.endDocument()
    }

    override fun startPrefixMapping(prefix: String?, uri: String?) {
        wrapped?.startPrefixMapping(prefix, uri)
    }

    override fun endPrefixMapping(prefix: String?) {
        wrapped?.endPrefixMapping(prefix)
    }

    override fun characters(ch: CharArray?, start: Int, length: Int) {
        wrapped?.characters(ch, start, length)
    }

    override fun ignorableWhitespace(ch: CharArray?, start: Int, length: Int) {
        wrapped?.ignorableWhitespace(ch, start, length)
    }

    override fun processingInstruction(target: String?, data: String?) {
        wrapped?.processingInstruction(target, data)
    }

    override fun skippedEntity(name: String?) {
        wrapped?.skippedEntity(name)
    }

    class CustomTagHandler(private val view: TextView?) : TagHandler {
        private var lastAClass = ""
        private var listItemCounts = Stack<Int>()
        private val listParents = mutableListOf<String>()

        override fun handleTag(opening: Boolean, tag: String?, output: Editable?, attributes: Attributes?): Boolean {
            if (tag == "img" && view == null) {
                return true
            } else if (tag == "img" && opening && view != null) {
                var imgWidth = DimenUtil.htmlPxToInt(getValue(attributes, "width").orEmpty())
                var imgHeight = DimenUtil.htmlPxToInt(getValue(attributes, "height").orEmpty())
                val imgSrc = getValue(attributes, "src").orEmpty()

                if (imgWidth < MIN_IMAGE_SIZE || imgHeight < MIN_IMAGE_SIZE) {
                    return true
                }

                imgWidth = DimenUtil.roundedDpToPx(imgWidth.toFloat())
                imgHeight = DimenUtil.roundedDpToPx(imgHeight.toFloat())

                if (imgWidth > 0 && imgHeight > 0 && imgSrc.isNotEmpty()) {
                    val bmpMap = contextBmpMap.getOrPut(view.context) { mutableMapOf() }
                    var drawable = bmpMap[imgSrc]

                    if (drawable == null || drawable.bitmap.isRecycled) {
                        // give it a placeholder drawable of the appropriate size
                        drawable = createBitmap(imgWidth, imgHeight, Bitmap.Config.RGB_565)
                            .toDrawable(view.context.resources)
                        bmpMap[imgSrc] = drawable

                        drawable.setBounds(0, 0, imgWidth, imgHeight)

                        var uri = imgSrc
                        if (uri.startsWith("//")) {
                            uri = WikiSite.DEFAULT_SCHEME + ":" + uri
                        } else if (uri.startsWith("./")) {
                            uri = Service.COMMONS_URL + uri.replace("./", "")
                        }

                        ImageService.imagePipeLineBitmapGetter(view.context, uri, onSuccess = { bitmap ->
                        val newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                        if (!drawable.bitmap.isRecycled) {
                                drawable.bitmap.applyCanvas {
                                    drawBitmap(newBitmap, Rect(0, 0, bitmap.width, bitmap.height), drawable.bounds, null)
                                }
                                drawable.bitmap.maybeDimImage()
                                view.postInvalidate()
                            }
                        })
                    }
                }
            } else if (tag == "a") {
                if (opening) {
                    lastAClass = getValue(attributes, "class").orEmpty()
                } else if (!output.isNullOrEmpty()) {
                    val spans = output.getSpans<URLSpan>(output.length - 1)
                    if (spans.isNotEmpty()) {
                        val span = spans.last()
                        val start = output.getSpanStart(span)
                        val end = output.getSpanEnd(span)
                        output.removeSpan(span)
                        val color = if (lastAClass == "new" && view != null) ResourceUtil.getThemedColor(view.context, androidx.appcompat.R.attr.colorError) else -1
                        output.setSpan(URLSpanNoUnderline(span.url, color), start, end, 0)
                    }
                }
            } else if (tag == "code" && output != null) {
                if (opening) {
                    output.setSpan(TypefaceSpan("monospace"), output.length, output.length, Spannable.SPAN_INCLUSIVE_INCLUSIVE)
                } else {
                    val spans = output.getSpans<TypefaceSpan>(output.length)
                    if (spans.isNotEmpty()) {
                        val span = spans.last()
                        val start = output.getSpanStart(span)
                        output.removeSpan(span)
                        output.setSpan(TypefaceSpan("monospace"), start, output.length, 0)
                    }
                }
            } else if (tag == "ol") {
                if (opening) {
                    listParents.add(tag)
                    listItemCounts.push(0)
                } else {
                    listParents.remove(tag)
                    listItemCounts.pop()
                }
            } else if (tag == "li" && listParents.isNotEmpty() && !opening && output != null) {
                handleListTag(output)
            }
            return false
        }

        private fun handleListTag(output: Editable) {
            if (listParents.last() == "ol") {
                val count = (if (listItemCounts.size > 0) listItemCounts.pop() else 0) + 1
                listItemCounts.push(count)
                // TODO: improve this logic to no longer require explicitly inserting the count
                // into the output text. This requires manual and fragile manipulation of any
                // existing spans that may be present in the output text.
                val paragraphSpans = output.getSpans<ParagraphStyle>(output.length)
                var lastLeadingSpan: LeadingMarginSpan? = null
                paragraphSpans.forEach {
                    if (it !is LeadingMarginSpan) {
                        output.removeSpan(it)
                    } else {
                        lastLeadingSpan = it
                    }
                }
                lastLeadingSpan?.let { span ->
                    val spanStart = output.getSpanStart(span)
                    output.removeSpan(span)
                    output.insert(spanStart, "$count. ")
                    output.setSpan(LeadingMarginSpan.Standard(DimenUtil.roundedDpToPx(16f)), spanStart, output.length, 0)
                }
            }
        }
    }

    class CustomImageGetter(private val context: Context) : ImageGetter {
        override fun getDrawable(source: String?): Drawable {
            var bmp: BitmapDrawable? = null
            try {
                if (contextBmpMap.containsKey(context)) {
                    if (contextBmpMap[context]!!.containsKey(source)) {
                        bmp = contextBmpMap[context]!![source]
                    }
                }
            } catch (e: Exception) {
                L.e(e)
            }
            if (bmp == null) {
                bmp = BlankBitmapPlaceholder(context.resources, null)
            }
            return bmp
        }
    }

    internal class BlankBitmapPlaceholder(res: Resources, bitmap: Bitmap?) : BitmapDrawable(res, bitmap) {
        override fun draw(canvas: Canvas) {
        }
    }

    companion object {
        private const val MIN_IMAGE_SIZE = 64
        private val contextBmpMap = mutableMapOf<Context, MutableMap<String, BitmapDrawable>>()

        fun fromHtml(html: String?, view: TextView? = null): Spanned {
            var sourceStr = html.orEmpty()

            if ("<" !in sourceStr && "&" !in sourceStr) {
                // If the string doesn't contain any hints of HTML entities, then skip the expensive
                // processing that fromHtml() performs.
                return sourceStr.toSpanned()
            }

            // Replace a few HTML entities that are not handled automatically by the parser.
            sourceStr = sourceStr.replace("&#8206;", "\u200E")
                .replace("&#8207;", "\u200F")
                .replace("&amp;", "&")

            // Add <small> tag in the <sub> or <sup> tags to make the text 3 times smaller
            sourceStr = sourceStr.replace("<sub>", "<sub><small><small><small>").replace("</sub>", "</small></small></small></sub>")
                .replace("<sup>", "<sup><small><small><small>").replace("</sup>", "</small></small></small></sup>")

            // TODO: Investigate if it's necessary to inject a dummy tag at the beginning of the
            // text, since there are reports that XmlReader ignores the first tag by default?
            // This would become something like "<inject/>$sourceStr".parseAsHtml(...)
            return sourceStr.parseAsHtml(HtmlCompat.FROM_HTML_MODE_LEGACY,
                if (view == null) null else CustomImageGetter(view.context),
                CustomHtmlParser(CustomTagHandler(view)))
        }

        fun pruneBitmaps(context: Context) {
            contextBmpMap[context]?.let { map ->
                map.values.forEach {
                    try {
                        it.bitmap.recycle()
                    } catch (e: Exception) {
                        L.e(e)
                    }
                }
                map.clear()
                contextBmpMap.remove(context)
            }
        }

        private fun getValue(attributes: Attributes?, name: String): String? {
            if (attributes != null) {
                for (i in 0 until attributes.length) {
                    if (name == attributes.getLocalName(i)) return attributes.getValue(i)
                }
            }
            return null
        }
    }
}
