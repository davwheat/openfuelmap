/**
 * Open Fuel Map
 * Copyright (C) 2026  David Wheatley
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.davwheat.openfuelmap.common.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.core.net.toUri

/**
 * Shows a small subset of HTML as text. A link in the text opens in a Custom Tab.
 *
 * The map attribution is the primary user of this: the style gives its credit as HTML with anchors,
 * and the licences make those links necessary.
 */
@Composable
fun HtmlText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    linkColor: Color = Color.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign = TextAlign.Start,
) {
    val context = LocalContext.current

    // The same sequence of default values as the Text composable of Material 3.
    val textColor = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    val linkTextColor = linkColor.takeOrElse { textColor }

    val annotatedText =
        remember(text, style, linkTextColor, context) {
            val annotated =
                AnnotatedString.fromHtml(
                    text,
                    linkStyles =
                        TextLinkStyles(
                            style =
                                style
                                    .copy(
                                        color = linkTextColor,
                                        textDecoration = TextDecoration.Underline,
                                        fontWeight = FontWeight.Normal,
                                    )
                                    .toSpanStyle()
                        ),
                    linkInteractionListener = {
                        CustomTabsIntent.Builder()
                            .build()
                            .launchUrl(context, (it as LinkAnnotation.Url).url.toUri())
                    },
                )

            // The HTML parser puts a \n at the end of each paragraph. That character adds an empty
            // line to the layout, thus remove it.
            val endIndex =
                if (annotated.lastOrNull() == '\n') annotated.length - 1 else annotated.length

            annotated.subSequence(TextRange(start = 0, end = endIndex))
        }

    Text(
        text = annotatedText,
        modifier = modifier,
        color = textColor,
        style = style,
        lineHeight = lineHeight,
        textAlign = textAlign,
    )
}
