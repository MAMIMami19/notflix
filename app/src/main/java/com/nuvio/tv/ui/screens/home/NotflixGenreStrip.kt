package com.nuvio.tv.ui.screens.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/**
 * Restyle (2026-11-18): the genre strip on the Notflix home.
 *
 * One tile per genre from the user's own dictated list (21 names, in order),
 * modelled on Matrix's Productions row: a LANDSCAPE tile (248x140) whose
 * artwork is the name itself on a dark per-genre gradient, animated while
 * focused (scale + brighten + a slow diagonal shine sweep). No emoji, ever.
 *
 * Tapping a tile filters the home rows in place (Netflix mood-bar behaviour):
 * rows whose title is the genre stay whole; other rows keep only the items
 * whose TMDB genres (or title keywords, for genres TMDB has no code for)
 * match. Tapping the selected tile again, or pressing BACK, clears the
 * filter. There is no «الكل» tile: the dictated list has none.
 */
internal const val NOTFLIX_GENRES_ROW_KEY = "genres"

internal data class NotflixGenre(
    val tag: String,
    val titleAr: String,
    val gradientTop: Color,
    val gradientBottom: Color,
    /** TMDB genre names (English + Arabic) an item must carry to match. */
    val matchTerms: List<String> = emptyList(),
    /** Title keywords, for genres with no TMDB genre code (WWE, holidays, ...). */
    val nameKeywords: List<String> = emptyList(),
    /** Matches items released this year or last (الجديد والجدير بالمشاهدة). */
    val matchRecent: Boolean = false
)

internal val NOTFLIX_GENRES: List<NotflixGenre> = listOf(
    // مزاج is a meta-category: the "mood" cluster, not one TMDB genre.
    NotflixGenre("mood", "مزاج", Color(72, 24, 64), Color(28, 10, 26),
        matchTerms = listOf("Drama", "Romance", "Comedy", "Music", "Family", "دراما", "رومانسي", "كوميديا", "موسيقي", "عائلي")),
    NotflixGenre("comedy", "كوميديا", Color(74, 46, 10), Color(31, 18, 4),
        matchTerms = listOf("Comedy", "كوميديا")),
    NotflixGenre("fantasy", "فانتازيا", Color(58, 31, 92), Color(22, 10, 40),
        matchTerms = listOf("Fantasy", "فانتازيا")),
    // No TMDB genre for youth stories; Family is the closest signal.
    NotflixGenre("youth_stories", "قصص شبابية للأبد", Color(15, 58, 58), Color(7, 26, 26),
        matchTerms = listOf("Family", "عائلي")),
    NotflixGenre("sci_fi", "خيال علمي", Color(18, 41, 77), Color(8, 15, 34),
        matchTerms = listOf("Science Fiction", "Sci-Fi", "خيال علمي")),
    NotflixGenre("holidays", "موسم الأعياد", Color(15, 58, 31), Color(7, 26, 12),
        nameKeywords = listOf("christmas", "holiday", "xmas", "عيد", "كريسماس", "رأس السنة")),
    // No TMDB genre for book adaptations; title keywords are the only signal.
    NotflixGenre("books_on_screen", "كتبك المفضلة على الشاشة", Color(58, 36, 16), Color(24, 14, 5),
        nameKeywords = listOf("novel", "book", "رواية", "كتاب")),
    NotflixGenre("romance", "رومانسي", Color(77, 18, 38), Color(32, 8, 18),
        matchTerms = listOf("Romance", "رومانسي")),
    NotflixGenre("reality", "واقعي", Color(46, 52, 64), Color(20, 23, 29),
        matchTerms = listOf("Reality", "واقعي")),
    NotflixGenre("kids_family", "الأطفال والعائلة", Color(18, 58, 77), Color(8, 24, 34),
        matchTerms = listOf("Family", "Kids", "عائلي", "أطفال")),
    NotflixGenre("animation", "رسوم متحركة", Color(77, 36, 8), Color(32, 14, 3),
        matchTerms = listOf("Animation", "رسوم متحركة")),
    NotflixGenre("anime", "أنيمي", Color(58, 16, 56), Color(24, 8, 22),
        matchTerms = listOf("Anime", "Animation", "أنيمي", "رسوم متحركة")),
    NotflixGenre("documentary", "وثائقيات", Color(42, 51, 32), Color(18, 22, 12),
        matchTerms = listOf("Documentary", "وثائقي")),
    NotflixGenre("astrology", "علم التنجيم", Color(26, 26, 77), Color(10, 10, 32),
        nameKeywords = listOf("astrology", "zodiac", "horoscope", "تنجيم", "أبراج", "برج")),
    NotflixGenre("wwe", "WWE", Color(77, 16, 16), Color(32, 7, 7),
        nameKeywords = listOf("wwe")),
    NotflixGenre("improv", "ارتجالي", Color(58, 42, 8), Color(24, 17, 4),
        nameKeywords = listOf("improv", "ارتجالي")),
    NotflixGenre("new_worthy", "الجديد والجدير بالمشاهدة", Color(15, 58, 40), Color(7, 26, 18),
        matchRecent = true),
    NotflixGenre("crime", "جريمة", Color(16, 24, 40), Color(7, 11, 18),
        matchTerms = listOf("Crime", "جريمة")),
    NotflixGenre("action", "أكشن", Color(77, 15, 20), Color(32, 6, 7),
        matchTerms = listOf("Action", "أكشن")),
    NotflixGenre("drama", "دراما", Color(58, 16, 32), Color(24, 7, 13),
        matchTerms = listOf("Drama", "دراما")),
    NotflixGenre("thrilling", "أعمال مشوقة", Color(36, 26, 51), Color(14, 10, 20),
        matchTerms = listOf("Thriller", "Mystery", "Suspense", "إثارة", "تشويق", "غموض"))
)

private val notflixYearRegex = Regex("(19|20)\\d{2}")

private fun notflixReleaseYear(item: MetaPreview): Int? {
    val raw = item.released ?: item.releaseInfo ?: return null
    return notflixYearRegex.find(raw)?.value?.toIntOrNull()
}

internal fun NotflixGenre.matchesItem(item: MetaPreview): Boolean {
    if (nameKeywords.isNotEmpty()) {
        val name = item.name.lowercase()
        if (nameKeywords.any { name.contains(it.lowercase()) }) return true
    }
    if (matchRecent) {
        val year = notflixReleaseYear(item)
        if (year != null && year >= java.time.Year.now().value - 1) return true
    }
    if (matchTerms.isNotEmpty()) {
        val genres = item.genres
        if (genres.any { genre -> matchTerms.any { term -> genre.equals(term, ignoreCase = true) || genre.contains(term, ignoreCase = true) } }) return true
    }
    return false
}

/** The genres strip as a display row, so the home's focus machinery works free. */
internal fun notflixGenreRow(): NotflixDisplayRow = NotflixDisplayRow(
    key = NOTFLIX_GENRES_ROW_KEY,
    loadKey = NOTFLIX_GENRES_ROW_KEY,
    title = "التصنيفات",
    phase = NotflixRowPhase.READY,
    tiles = NOTFLIX_GENRES.map { genre ->
        NotflixTile(
            key = "genre-${genre.tag}",
            canonical = "genre:${genre.tag}",
            title = genre.titleAr,
            poster = null,
            tileShape = PosterShape.LANDSCAPE,
            hideTitle = true
        )
    },
    wide = true
)

/**
 * In-place genre filter over the home's display rows. The strip itself stays
 * first; Continue watching, airing and collection rows are dropped while a
 * genre is active; a catalog row whose title names the genre stays whole,
 * otherwise only its matching items survive (and its see-all tile goes, since
 * it would open the unfiltered catalog).
 */
internal fun notflixFilterRowsByGenre(rows: List<NotflixDisplayRow>, genre: NotflixGenre): List<NotflixDisplayRow> {
    return buildList {
        rows.forEach { row ->
            when {
                row.key == NOTFLIX_GENRES_ROW_KEY -> add(row)
                row.key == "cw" -> Unit
                row.key == NOTFLIX_AIRING_ROW_KEY -> Unit
                row.key.startsWith("collection_") -> Unit
                row.folderTarget != null -> Unit
                else -> {
                    val titleMatches = row.title.contains(genre.titleAr) ||
                        (genre.matchTerms + genre.nameKeywords).any { row.title.contains(it, ignoreCase = true) } ||
                        row.title.contains(genre.tag, ignoreCase = true)
                    if (titleMatches) add(row)
                    else {
                        val kept = row.tiles.filter { tile ->
                            tile.key != "see-all" && tile.meta?.let { genre.matchesItem(it) } == true
                        }
                        if (kept.isNotEmpty()) add(row.copy(tiles = kept, empty = false))
                    }
                }
            }
        }
    }
}

private fun Color.brighten(factor: Float): Color =
    copy(red = (red * factor).coerceAtMost(1f), green = (green * factor).coerceAtMost(1f), blue = (blue * factor).coerceAtMost(1f))

private val genreStripDirections = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

/**
 * One genre tile, Matrix Productions style: landscape, the genre name as the
 * artwork on a dark gradient, animated on focus (scale + brighten + shine
 * sweep) and marked with a red border while its filter is active.
 */
@Composable
internal fun NotflixGenreTile(genre: NotflixGenre, selected: Boolean, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.06f else 1f, tween(180), label = "genreScale")
    val brighten by animateFloatAsState(
        if (isFocused) 1.45f else if (selected) 1.2f else 1f, tween(180), label = "genreBrighten")
    val shineProgress = if (isFocused) {
        val transition = rememberInfiniteTransition(label = "genreShine")
        transition.animateFloat(0f, 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2600, easing = LinearEasing), repeatMode = RepeatMode.Restart),
            label = "genreShineProgress").value
    } else 0f
    val cardSize = LocalNotflixPresentation.current.cardSize
    val top = genre.gradientTop.brighten(brighten)
    val bottom = genre.gradientBottom.brighten(brighten)
    val borderColor = when {
        isFocused -> Color.White
        selected -> Spec.Accent
        else -> Color.Transparent
    }
    BoxWithConstraints(
        modifier
            .size(Spec.wideWidth(cardSize).dp, Spec.wideHeight(cardSize).dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(5.dp))
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .border(2.dp, borderColor, RoundedCornerShape(5.dp))
            .onFocusChanged { isFocused = it.isFocused },
        contentAlignment = Alignment.Center
    ) {
        if (isFocused) {
            val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
            Box(
                Modifier
                    .width(maxWidth * 0.45f)
                    .height(maxHeight * 2.2f)
                    .graphicsLayer {
                        translationX = -widthPx * 1.1f + shineProgress * widthPx * 2.2f
                        rotationZ = 20f
                    }
                    .background(Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.16f), Color.Transparent)
                    ))
            )
        }
        Text(
            text = genre.titleAr,
            color = Color.White,
            fontFamily = Spec.BodyFont,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

/**
 * The strip itself, mirroring [NotflixRow]'s focus wiring (requesters,
 * record-on-focus, direction keys to the home's onKey) with no row title —
 * like Netflix's mood bar, the strip is self-explanatory.
 */
@Composable
internal fun NotflixGenreStripRow(
    row: NotflixDisplayRow,
    horizontal: LazyListState,
    requesters: MutableMap<String, FocusRequester>,
    selectedGenre: NotflixGenre?,
    onActualFocus: (String, Boolean) -> Unit,
    onFocused: (Int, NotflixTile) -> Unit,
    onKey: (Int, Key) -> Unit,
    onGenreClick: (NotflixGenre) -> Unit
) {
    Column(Modifier.fillMaxWidth().testTag("notflix-row-${row.key}")) {
        LazyRow(
            state = horizontal,
            contentPadding = PaddingValues(horizontal = Spec.GUTTER.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            itemsIndexed(row.tiles, key = { _, tile -> tile.key }) { index, tile ->
                val genre = NOTFLIX_GENRES.getOrNull(index) ?: return@itemsIndexed
                val identity = "${row.key}|${tile.key}"
                val requester = remember { FocusRequester() }
                DisposableEffect(identity) {
                    requesters[identity] = requester
                    onDispose { requesters.remove(identity) }
                }
                NotflixGenreTile(
                    genre = genre,
                    selected = selectedGenre == genre,
                    modifier = Modifier
                        .focusRequester(requester)
                        .onFocusChanged {
                            if (it.isFocused) onFocused(index, tile)
                            onActualFocus(identity, it.isFocused)
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key in genreStripDirections) {
                                onKey(index, event.key)
                                true
                            } else false
                        }
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { onGenreClick(genre) }
                        .testTag("notflix-card-$identity")
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
