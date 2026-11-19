package com.nuvio.tv.ui.screens.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.ContinueWatchingItem
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.appearance.NotflixWordmark
import com.nuvio.tv.ui.appearance.notflixRating
import kotlinx.coroutines.launch

/**
 * Restyle 3 (2026-11-19): the shared Netflix-style browse pieces for the
 * tab screens (Movies, Series, My Space) — a light row + hero that reuse
 * the home's own expanded card and billboard look, without the home's
 * focus-restoration machinery (these screens navigate with plain Compose
 * focus, like the existing Discover grid and the genre browse screen).
 */

/** A [MetaPreview] as a home tile, so [NotflixCard] renders it identically. */
internal fun notflixTileFromMeta(meta: MetaPreview, keyPrefix: String): NotflixTile =
    NotflixTile(
        key = "$keyPrefix:${meta.apiType}:${meta.id}",
        canonical = notflixMetaKey(meta),
        title = meta.name,
        poster = meta.poster,
        backdrop = meta.background ?: meta.landscapePoster,
        meta = meta
    )

private val notflixBrowseYearRegex = Regex("(19|20)\\d{2}")

private fun notflixBrowseYear(item: MetaPreview): Int? {
    val raw = item.released ?: item.releaseInfo ?: return null
    return notflixBrowseYearRegex.find(raw)?.value?.toIntOrNull()
}

/**
 * Netflix-style rows from one flat list (a Discover catalog page): the
 * best-rated first, then the newest, then the genre rows that actually
 * have content — in the strip's own genre order. A row only appears when
 * it has enough titles to be worth browsing.
 */
internal fun notflixBrowseRows(
    items: List<MetaPreview>,
    trendingTitle: String,
    newTitle: String,
    genreTitle: (NotflixGenre) -> String
): List<NotflixDisplayRow> = buildList {
    val deduped = items.distinctBy { "${it.apiType}:${it.id}" }
    if (deduped.isEmpty()) return@buildList
    val year = java.time.Year.now().value
    val trending = deduped.filter { it.imdbRating != null }
        .sortedWith(compareByDescending<MetaPreview> { it.imdbRating ?: -1f }.thenByDescending { it.voteCount ?: -1 })
        .take(20)
    if (trending.size >= 3) {
        add(NotflixDisplayRow("browse-trending", "browse-trending", trendingTitle, NotflixRowPhase.READY,
            trending.map { notflixTileFromMeta(it, "browse-trending") }))
    }
    val fresh = deduped.filter { (notflixBrowseYear(it) ?: 0) >= year - 1 }
        .sortedWith(compareByDescending<MetaPreview> { notflixBrowseYear(it) ?: 0 }
            .thenByDescending { it.imdbRating ?: -1f })
        .take(20)
    if (fresh.size >= 3) {
        add(NotflixDisplayRow("browse-new", "browse-new", newTitle, NotflixRowPhase.READY,
            fresh.map { notflixTileFromMeta(it, "browse-new") }))
    }
    var genreRows = 0
    for (genre in NOTFLIX_GENRES) {
        if (genreRows >= 4) break
        val matched = deduped.filter { genre.matchesItem(it) }.take(20)
        if (matched.size >= 4) {
            genreRows++
            add(NotflixDisplayRow("browse-genre-${genre.tag}", "browse-genre-${genre.tag}",
                genreTitle(genre), NotflixRowPhase.READY,
                matched.map { notflixTileFromMeta(it, "browse-genre-${genre.tag}") }))
        }
    }
}

/** The tab hero pick: the best-rated title with a backdrop, else the first with one. */
internal fun notflixBrowseHero(items: List<MetaPreview>): MetaPreview? {
    val withArt = items.filter { !(it.background ?: it.landscapePoster).isNullOrBlank() }
    if (withArt.isEmpty()) return items.firstOrNull()
    return withArt.filter { it.imdbRating != null }.maxByOrNull { it.imdbRating ?: 0f } ?: withArt.first()
}

/** Taste-store entries (rated titles / watched trailers) as poster tiles. */
internal fun notflixTasteTiles(
    entries: List<com.nuvio.tv.data.local.NotflixTasteEntry>,
    keyPrefix: String
): List<NotflixTile> =
    entries.map { entry ->
        NotflixTile(
            key = "$keyPrefix:${entry.key}",
            canonical = entry.key,
            title = entry.name,
            poster = entry.poster,
            backdrop = entry.backdrop
        )
    }

/** Continue-watching items as wide tiles (the home's own CW mapping). */
internal fun notflixCwTiles(items: List<ContinueWatchingItem>, episodePattern: String): List<NotflixTile> =
    items.map { item ->
        when (item) {
            is ContinueWatchingItem.InProgress -> item.progress.let { p ->
                NotflixTile(continueWatchingItemKey(item), "${p.contentType}:${p.contentId}", p.name,
                    p.poster, item.episodeThumbnail ?: p.backdrop,
                    p.season?.let { episodePattern.format(it, p.episode ?: "?") }, cw = item, progress = p.progressPercentage)
            }
            is ContinueWatchingItem.NextUp -> item.info.let { p ->
                NotflixTile(continueWatchingItemKey(item), "${p.contentType}:${p.contentId}", p.name,
                    p.poster, p.thumbnail ?: p.backdrop, episodePattern.format(p.season, p.episode), cw = item)
            }
        }
    }

/**
 * One browse row: the row title + a horizontal strip of the home's own
 * [NotflixCard]s. The focused card expands (meta + synopsis + progress),
 * like on the home; OK opens it. No trailer preview and no action cursor —
 * the panel shows the meta and synopsis only.
 */
@Composable
internal fun NotflixSimpleRow(
    row: NotflixDisplayRow,
    onOpen: (NotflixTile) -> Unit,
    onLongPress: ((NotflixTile) -> Unit)? = null,
    onFocusedTile: ((NotflixTile) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cardSize = LocalNotflixPresentation.current.cardSize
    val scope = rememberCoroutineScope()
    val rowState = rememberLazyListState()
    var focusedIndex by remember(row.key) { mutableStateOf(-1) }
    // Keep the expanded card inside the viewport, like the home row does.
    LaunchedEffect(focusedIndex, cardSize) {
        val target = focusedIndex
        if (target < 0) return@LaunchedEffect
        val info = rowState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target } ?: return@LaunchedEffect
        val overflow = info.offset + with(density) {
            (Spec.expandedWidth(cardSize) + 8).dp.toPx()
        } - rowState.layoutInfo.viewportEndOffset
        if (overflow > 0) scope.launch { rowState.animateScrollBy(overflow, tween(150)) }
    }
    Column(modifier.fillMaxWidth().testTag("notflix-browse-row-${row.key}")) {
        Text(row.title, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 14.sp, lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spec.GUTTER.dp, end = Spec.GUTTER.dp, bottom = 2.dp))
        LazyRow(state = rowState, contentPadding = PaddingValues(horizontal = Spec.GUTTER.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            itemsIndexed(row.tiles, key = { _, tile -> tile.key }) { index, tile ->
                NotflixCard(
                    tile = tile,
                    identity = "${row.key}|${tile.key}",
                    wide = row.wide,
                    expanded = index == focusedIndex,
                    modifier = Modifier
                        .onFocusChanged { if (it.isFocused) { focusedIndex = index; onFocusedTile?.invoke(tile) } }
                        .testTag("notflix-browse-card-${tile.key}"),
                    onLongClick = onLongPress?.let { { it(tile) } },
                    onClick = { onOpen(tile) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * The tab hero: the same inset rounded-rectangle card as the home hero
 * (restyle 3), but static — one title, no auto-advance, no dots — with
 * Play (عرض) + Info (المزيد من المعلومات) buttons. Plain focusable
 * buttons, so UP/DOWN move to the top bar / rows with normal focus.
 */
@Composable
internal fun NotflixTabHero(
    item: MetaPreview,
    onPlay: (() -> Unit)?,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxWidth().padding(horizontal = Spec.GUTTER.dp).padding(top = 56.dp)
        .height((Spec.HERO_HEIGHT - 56).dp).clip(RoundedCornerShape(10.dp))
        .testTag("notflix-tab-hero")) {
        val art = item.background ?: item.landscapePoster
        if (!art.isNullOrBlank()) {
            AsyncImage(art, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -.6f))
        }
        Box(Modifier.fillMaxHeight().fillMaxWidth().background(Brush.horizontalGradient(
            0f to Spec.Background.copy(.92f), .28f to Spec.Background.copy(.60f),
            .58f to Spec.Background.copy(.08f), 1f to Color.Transparent)))
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().fillMaxHeight(.42f).background(Brush.verticalGradient(
            0f to Color.Transparent, (.86f - .58f) / .42f to Spec.Background.copy(.75f), 1f to Spec.Background)))
        Column(Modifier.align(Alignment.BottomStart).width(420.dp).padding(start = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                NotflixWordmark(height = 10.dp)
                if (item.apiType in setOf("movie", "series")) Text(
                    stringResource(if (item.apiType == "series") R.string.notflix_billboard_series else R.string.notflix_billboard_film),
                    color = Spec.Text, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.sp, fontFamily = Spec.BodyFont)
            }
            var logoFailed by remember(item.logo) { mutableStateOf(false) }
            if (!item.logo.isNullOrBlank() && !logoFailed) AsyncImage(item.logo, item.name,
                Modifier.widthIn(max = 280.dp).height(74.dp), contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart, onError = { logoFailed = true })
            else Text(item.name, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 36.sp, lineHeight = 40.sp,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                notflixRating(item.imdbRating)?.let { rating ->
                    Text(notflixBillboardRatingText(item.ratingSource, rating), color = Spec.Text,
                        fontSize = 13.sp, lineHeight = 17.sp, fontFamily = Spec.BodyFont)
                }
                val facts = listOfNotNull(item.releaseInfo, item.runtime?.let { value ->
                    value.toIntOrNull()?.let { stringResource(R.string.notflix_runtime_minutes, it) } ?: value },
                    item.ageRating).filter { it.isNotBlank() }.joinToString(" · ")
                if (facts.isNotEmpty()) Text(facts, color = Spec.Secondary, fontFamily = Spec.BodyFont,
                    fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item.genres.isNotEmpty()) Text(item.genres.take(3).joinToString(" · "), color = Spec.Secondary,
                fontFamily = Spec.BodyFont, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            item.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Spec.Text, fontFamily = Spec.BodyFont, fontSize = 14.sp, lineHeight = 18.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onPlay != null) {
                    NotflixTabHeroButton(
                        label = stringResource(R.string.notflix_billboard_play),
                        symbol = "▶", primary = true, tag = "notflix-tab-hero-play", onClick = onPlay)
                }
                NotflixTabHeroButton(
                    label = stringResource(R.string.notflix_billboard_info),
                    symbol = "ⓘ", primary = onPlay == null, tag = "notflix-tab-hero-info", onClick = onInfo)
            }
        }
    }
}

@Composable
private fun NotflixTabHeroButton(label: String, symbol: String, primary: Boolean, tag: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text("$symbol $label", color = if (primary) Spec.Background else Spec.Text, fontFamily = Spec.BodyFont,
        fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .border(2.dp, if (focused) Spec.Text else Color.Transparent, RoundedCornerShape(4.dp))
            .padding(3.dp)
            .background(if (primary) Spec.Text else Spec.Surface, RoundedCornerShape(3.dp))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(tag))
}
