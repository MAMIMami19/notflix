package com.nuvio.tv.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/**
 * Restyle 2 (2026-11-19): the genre strip on the Notflix home.
 *
 * One pill per genre from the user's own dictated list (21 names, in order),
 * Netflix-style: a compact rounded pill with the Arabic name on a dark
 * per-genre color. NO pictures on the pills (his correction: «مش صورة في
 * الايقونات قصدي في محتوي لداخل» — the picture belongs to the content
 * inside). No emoji, ever.
 *
 * Focus on a pill live-filters the rows below to that genre (the pill turns
 * white, like Netflix's active pill); OK on a pill opens that genre's
 * dedicated browse screen ([NotflixGenreBrowseScreen]); BACK from there
 * returns to the home, BACK on the home clears the filter. The content
 * inside must express the genre's title: items match by TMDB genre,
 * title words, synopsis words, origin country (anime = Japanese animation)
 * and recency/rating (new & worthy). A row whose title names the genre feeds
 * its screen whole — except anime, whose row mixes in western animation.
 * There is no «الكل» pill: the dictated list has none.
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
    /** Synopsis keywords — matched against the item description. */
    val descriptionKeywords: List<String> = emptyList(),
    /** Synopsis regexes (case-insensitive) — for phrases keywords can't
     *  express, e.g. "based on …'s novel". */
    val descriptionPatterns: List<String> = emptyList(),
    /** Catalog-row titles that feed this genre whole (browse screen only). */
    val rowTitleTerms: List<String> = emptyList(),
    /** Matches items released this year or last (الجديد والجدير بالمشاهدة). */
    val matchRecent: Boolean = false,
    /** Minimum IMDb rating when one exists (الجديد «والجدير بالمشاهدة»). */
    val minRating: Float? = null,
    /** Anime: Animation genre + Japanese origin (or the word "anime"). */
    val matchAnime: Boolean = false,
    /** A row whose title names the genre feeds the browse screen whole.
     *  False for anime: the anime row mixes in western animation, so its
     *  items are matched one by one instead. */
    val wholeRowOnTitleMatch: Boolean = true
)

internal val NOTFLIX_GENRES: List<NotflixGenre> = listOf(
    // مزاج is a meta-category: the "mood" cluster, not one TMDB genre.
    NotflixGenre("mood", "مزاج", Color(88, 28, 76), Color(30, 10, 28),
        matchTerms = listOf("Drama", "Romance", "Comedy", "Music", "Family", "دراما", "رومانسي", "كوميديا", "موسيقي", "عائلي")),
    NotflixGenre("comedy", "كوميديا", Color(96, 58, 10), Color(38, 22, 4),
        matchTerms = listOf("Comedy", "كوميديا")),
    NotflixGenre("fantasy", "فانتازيا", Color(72, 38, 112), Color(26, 12, 46),
        matchTerms = listOf("Fantasy", "فانتازيا")),
    // No TMDB genre for youth stories: Family + teen / coming-of-age signals.
    NotflixGenre("youth_stories", "قصص شبابية للأبد", Color(16, 72, 72), Color(8, 30, 30),
        matchTerms = listOf("Family", "عائلي"),
        nameKeywords = listOf("teen", "high school", "coming of age", "coming-of-age", "youth", "شباب", "مراهق", "مراهقين", "ثانوية"),
        descriptionKeywords = listOf("coming-of-age", "coming of age", "teenager", "high school", "teen", "شباب", "مراهق")),
    NotflixGenre("sci_fi", "خيال علمي", Color(22, 52, 96), Color(9, 18, 40),
        matchTerms = listOf("Science Fiction", "Sci-Fi", "خيال علمي")),
    NotflixGenre("holidays", "موسم الأعياد", Color(18, 72, 38), Color(8, 30, 14),
        nameKeywords = listOf("christmas", "holiday", "holidays", "xmas", "santa", "halloween", "easter", "ramadan", "noel", "new year", "عيد", "كريسماس", "رأس السنة", "رمضان", "هالوين"),
        descriptionKeywords = listOf("christmas", "santa claus", "halloween", "عيد", "كريسماس", "رمضان")),
    // No TMDB genre for book adaptations: title words + adaptation phrases.
    // Bare "book"/"novel" are too loose ("reads from a book", "by-the-book",
    // "a novel twist"), so the synopsis must name the adaptation.
    NotflixGenre("books_on_screen", "كتبك المفضلة على الشاشة", Color(72, 46, 18), Color(28, 17, 6),
        nameKeywords = listOf("novel", "رواية", "كتاب"),
        descriptionKeywords = listOf("novel by", "bestselling novel", "best-selling novel", "book series", "the book by", "graphic novel", "مقتبس من رواية", "مقتبس عن رواية", "عن رواية"),
        descriptionPatterns = listOf(
            """based on[^.]{0,50}\b(novel|book|comics?)\b""",
            """adapted from[^.]{0,50}\b(novel|book|comics?)\b""",
            """inspired[^.]{0,40}\b(novel|book)\b""",
            // "comic book" as an adaptation signal — but not a comic book STORE.
            """\bcomic books?\b(?! store\b)"""
        )),
    NotflixGenre("romance", "رومانسي", Color(96, 22, 46), Color(38, 9, 20),
        matchTerms = listOf("Romance", "رومانسي")),
    // واقعي is true-story / realistic: Reality TV + Biography/History + "true story".
    NotflixGenre("reality", "واقعي", Color(58, 64, 78), Color(24, 27, 34),
        matchTerms = listOf("Reality", "Biography", "History", "واقعي"),
        descriptionKeywords = listOf("true story", "true events", "قصة حقيقية", "أحداث حقيقية", "مبني على قصة حقيقية")),
    NotflixGenre("kids_family", "الأطفال والعائلة", Color(22, 72, 96), Color(9, 28, 40),
        matchTerms = listOf("Family", "Kids", "عائلي", "أطفال")),
    NotflixGenre("animation", "رسوم متحركة", Color(96, 46, 10), Color(38, 17, 4),
        matchTerms = listOf("Animation", "رسوم متحركة")),
    // Anime is NOT all animation: Japanese origin (or the word "anime") only.
    // The anime row is NOT taken whole — it mixes in western animation.
    NotflixGenre("anime", "أنيمي", Color(72, 20, 70), Color(28, 9, 26),
        matchTerms = listOf("Anime", "أنيمي", "أنمي"),
        nameKeywords = listOf("anime", "أنيمي", "أنمي"),
        matchAnime = true,
        wholeRowOnTitleMatch = false),
    NotflixGenre("documentary", "وثائقيات", Color(52, 64, 38), Color(21, 26, 14),
        matchTerms = listOf("Documentary", "وثائقي")),
    NotflixGenre("astrology", "علم التنجيم", Color(32, 32, 96), Color(12, 12, 38),
        nameKeywords = listOf("astrology", "zodiac", "horoscope", "tarot", "psychic", "fortune teller", "تنجيم", "أبراج", "برج", "تاروت", "الفلك"),
        descriptionKeywords = listOf("astrology", "astrological", "astrologer", "zodiac", "horoscope", "horoscopes", "tarot", "fortune teller", "palmistry", "star sign", "star signs", "birth chart", "تنجيم", "أبراج", "تاروت", "الفلك", "العرافة", "قراءة الطالع")),
    NotflixGenre("wwe", "WWE", Color(96, 18, 18), Color(38, 8, 8),
        nameKeywords = listOf("wwe", "wrestling", "wrestlemania", "smackdown", "مصارعة"),
        descriptionKeywords = listOf("wrestling", "wwe", "مصارعة")),
    NotflixGenre("improv", "ارتجالي", Color(72, 52, 10), Color(28, 20, 4),
        nameKeywords = listOf("improv", "improvisation", "stand-up", "stand up", "ارتجالي", "ستاند أب"),
        // "stand-up comedy/special/performance" only: plain "stand-up" in a
        // synopsis also matches dramas about a failed stand-up comedian (Joker).
        descriptionKeywords = listOf("improv", "improvisation", "stand-up comedy", "stand-up special", "stand-up performance", "stand-up show", "stand up comedy", "comedy special", "standup", "ارتجالي")),
    NotflixGenre("new_worthy", "الجديد والجدير بالمشاهدة", Color(18, 72, 50), Color(8, 30, 21),
        matchRecent = true,
        minRating = 6.0f,
        rowTitleTerms = listOf("new", "latest", "recent", "جديد", "أحدث")),
    NotflixGenre("crime", "جريمة", Color(20, 30, 50), Color(8, 13, 22),
        matchTerms = listOf("Crime", "Noir", "جريمة")),
    NotflixGenre("action", "أكشن", Color(96, 18, 24), Color(38, 7, 9),
        matchTerms = listOf("Action", "أكشن")),
    NotflixGenre("drama", "دراما", Color(72, 20, 40), Color(28, 8, 15),
        matchTerms = listOf("Drama", "دراما")),
    NotflixGenre("thrilling", "أعمال مشوقة", Color(44, 32, 64), Color(16, 12, 24),
        matchTerms = listOf("Thriller", "Mystery", "Suspense", "إثارة", "تشويق", "غموض"))
)

private val notflixYearRegex = Regex("(19|20)\\d{2}")

private val notflixKeywordRegexCache = mutableMapOf<String, Regex>()
private val notflixPatternRegexCache = mutableMapOf<String, Regex>()

/**
 * Keyword match: Latin-script keywords match whole words only ("teen" must
 * not match "fifteen"), Arabic keywords match substrings (Arabic morphology
 * has no usable word boundary).
 */
private fun notflixKeywordMatches(text: String, keyword: String): Boolean {
    if (keyword.isEmpty()) return false
    if (keyword.all { it.code < 128 }) {
        val regex = notflixKeywordRegexCache.getOrPut(keyword) {
            Regex("(?i)(?<![a-z])${Regex.escape(keyword)}(?![a-z])")
        }
        return regex.containsMatchIn(text)
    }
    return text.contains(keyword, ignoreCase = true)
}

private fun notflixReleaseYear(item: MetaPreview): Int? {
    val raw = item.released ?: item.releaseInfo ?: return null
    return notflixYearRegex.find(raw)?.value?.toIntOrNull()
}

internal fun NotflixGenre.matchesItem(item: MetaPreview): Boolean {
    // "Worthy": a known low rating fails the genre outright (unrated passes).
    minRating?.let { floor ->
        val rating = item.imdbRating
        if (rating != null && rating < floor) return false
    }
    if (nameKeywords.isNotEmpty()) {
        if (nameKeywords.any { notflixKeywordMatches(item.name, it) }) return true
    }
    if (descriptionKeywords.isNotEmpty()) {
        val desc = item.description.orEmpty()
        if (desc.isNotEmpty() && descriptionKeywords.any { notflixKeywordMatches(desc, it) }) return true
    }
    if (descriptionPatterns.isNotEmpty()) {
        val desc = item.description.orEmpty()
        if (desc.isNotEmpty() && descriptionPatterns.any { pattern ->
            notflixPatternRegexCache.getOrPut(pattern) { Regex(pattern, RegexOption.IGNORE_CASE) }.containsMatchIn(desc)
        }) return true
    }
    if (matchAnime) {
        // Real anime: Animation + Japan FIRST in the origin countries (or the
        // word "anime"). "Contains Japan" is useless — nearly every US
        // animated film lists Japan later (distribution/dubbing); Japanese
        // productions list Japan first. Genre alone cannot tell anime from
        // western animation: no catalog tags "Anime".
        val isAnimation = item.genres.any {
            it.contains("animation", ignoreCase = true) || it.contains("anime", ignoreCase = true)
        }
        if (isAnimation) {
            val firstCountry = item.country?.split(",")?.firstOrNull()?.trim()
            val isJapanese = firstCountry?.equals("Japan", ignoreCase = true) == true ||
                item.country?.equals("JP", ignoreCase = true) == true
            val saysAnime = item.name.contains("anime", ignoreCase = true) ||
                item.name.contains("أنيمي") || item.name.contains("أنمي") ||
                item.description?.contains("anime", ignoreCase = true) == true
            if (isJapanese || saysAnime) return true
        }
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

/** Restyle 2: focus on a pill live-filters the home rows to that genre.
 *  The strip itself stays; Continue watching, the airing row, collections
 *  and folders are never filtered (they are not genre rows). A row whose
 *  title names the genre stays whole, other rows keep only matching tiles. */
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
                        (genre.matchTerms + genre.nameKeywords + genre.rowTitleTerms).any { row.title.contains(it, ignoreCase = true) } ||
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

private val genreStripDirections = setOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

/**
 * One genre pill: compact and rounded, the Arabic genre name on its own dark
 * per-genre color. The ACTIVE pill (the one filtering the rows below) is
 * white with black text, like Netflix's selected pill; focus only scales
 * the pill and — on an inactive pill — adds a white border. The fill never
 * changes on focus. No pictures on the pills — the artwork lives in the
 * content inside.
 */
@Composable
internal fun NotflixGenrePill(genre: NotflixGenre, active: Boolean, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, tween(150), label = "genrePillScale")
    val shape = RoundedCornerShape(20.dp)
    val fill: Brush = if (active) SolidColor(Color.White)
        else Brush.verticalGradient(listOf(genre.gradientTop, genre.gradientBottom))
    Box(
        modifier
            .height(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill, shape)
            .border(2.dp, if (isFocused && !active) Color.White else Color.Transparent, shape)
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = genre.titleAr,
            color = if (active) Color.Black else Color.White,
            fontFamily = Spec.BodyFont,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The strip itself, mirroring [NotflixRow]'s focus wiring (requesters,
 * record-on-focus, direction keys to the home's onKey) with no row title —
 * like Netflix's mood bar, the strip is self-explanatory. Focus on a pill
 * live-filters the rows below ([onGenreFocus]); OK opens the genre's own
 * browse screen ([onGenreClick]).
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
    onGenreFocus: (NotflixGenre) -> Unit,
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
                NotflixGenrePill(
                    genre = genre,
                    active = selectedGenre == genre,
                    modifier = Modifier
                        .focusRequester(requester)
                        .onFocusChanged {
                            if (it.isFocused) {
                                onFocused(index, tile)
                                onGenreFocus(genre)
                            }
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
