package com.nuvio.tv.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/**
 * Restyle 3 (2026-11-19): the Netflix-style vertical «المزيد مثل هذا»
 * list on the detail screen — one row per similar title: landscape thumb
 * + title + meta (year • runtime • rating) + Arabic synopsis. The tabbed
 * horizontal row stays as it is; this list sits below it, always visible
 * in the Notflix look.
 */
@Composable
internal fun NotflixMoreLikeThisList(
    items: List<MetaPreview>,
    onItemClick: (MetaPreview) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spec.GUTTER.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.notflix_detail_more_like), color = Spec.Text,
            fontFamily = Spec.BodyFont, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("notflix-more-like-title"))
        items.take(12).forEach { item ->
            NotflixMoreLikeThisRow(item = item, onClick = { onItemClick(item) })
        }
    }
}

@Composable
private fun NotflixMoreLikeThisRow(item: MetaPreview, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Spec.Surface else Color.Transparent, RoundedCornerShape(8.dp))
            .border(2.dp, if (focused) Spec.Text else Color.Transparent, RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(8.dp)
            .testTag("notflix-more-like-${item.id}"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val art = item.background ?: item.landscapePoster ?: item.poster
        Box(
            Modifier.size(160.dp, 90.dp).clip(RoundedCornerShape(6.dp))
                .background(Spec.Surface)
        ) {
            if (!art.isNullOrBlank()) {
                AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(item.name, color = Spec.Text, fontFamily = Spec.BodyFont,
                fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                item.releaseInfo?.takeIf { it.isNotBlank() },
                item.runtime?.takeIf { it.isNotBlank() },
                item.imdbRating?.let { String.format(java.util.Locale.US, "%.1f IMDb", it) },
                item.ageRating?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = Spec.Secondary, fontFamily = Spec.BodyFont,
                    fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            item.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Spec.Secondary, fontFamily = Spec.BodyFont,
                    fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
