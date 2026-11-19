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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/**
 * Restyle 3 (2026-11-19): the genre strip on the Notflix home.
 *
 * One landscape tile per genre from the user's own dictated list (21 names,
 * in order), Matrix-style: 150×85 dp, ONE unified semi-transparent grey
 * fill (RGBA 150,150,150,150), the genre name in white, no pictures, no
 * per-genre colors. The focused tile gets a white border; the ACTIVE tile
 * (the one filtering the rows below) is white with black text, like
 * Netflix's selected pill. Grey pills and picture tiles were both rejected
 * by the user — this is the approved mockup (preview-tiles-grey.png).
 * No emoji, ever.
 *
 * The strip sits BELOW Continue watching on the home (Netflix puts its
 * genre bar there, not under the hero). Focus on a tile live-filters the
 * rows below to that genre; OK on a tile opens that genre's dedicated
 * browse screen ([NotflixGenreBrowseScreen]); BACK from there returns to
 * the home, BACK on the home clears the filter. The content inside must
 * express the genre's title: items match by TMDB genre, title words,
 * synopsis words, origin country (anime = Japanese animation) and
 * recency/rating (new & worthy). A row whose title names the genre feeds
 * its screen whole — except anime, whose row mixes in western animation.
 * There is no «الكل» tile: the dictated list has none.
 *
 * Language: the displayed name comes from [NotflixGenre.titleRes] (Arabic
 * by default, French when the app is French). [NotflixGenre.titleAr] stays
 * as MATCHING data only — catalog row titles are Arabic, so the row-title
 * match still needs the Arabic name.
 */
internal const val NOTFLIX_GENRES_ROW_KEY = "genres"

internal data class NotflixGenre(
    val tag: String,
    /** Arabic name — MATCHING data (catalog row titles are Arabic). Display
     *  uses [titleRes]; never display this field directly. */
    val titleAr: String,
    /** Localized display name (Arabic by default, French when French). */
    @androidx.annotation.StringRes val titleRes: Int,
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
    /** Arabic works: Arabic language, an Arab origin country, or an
     *  Arabic-script title (search-only category, restyle 3). */
    val matchArabic: Boolean = false,
    /** A row whose title names the genre feeds the browse screen whole.
     *  False for anime: the anime row mixes in western animation, so its
     *  items are matched one by one instead. */
    val wholeRowOnTitleMatch: Boolean = true
)

internal val NOTFLIX_GENRES: List<NotflixGenre> = listOf(
    // مزاج is a meta-category: the "mood" cluster, not one TMDB genre.
    NotflixGenre("mood", "مزاج", R.string.notflix_genre_mood,
        matchTerms = listOf("Drama", "Romance", "Comedy", "Music", "Family", "دراما", "رومانسي", "كوميديا", "موسيقي", "عائلي")),
    NotflixGenre("comedy", "كوميديا", R.string.notflix_genre_comedy,
        matchTerms = listOf("Comedy", "كوميديا")),
    NotflixGenre("fantasy", "فانتازيا", R.string.notflix_genre_fantasy,
        matchTerms = listOf("Fantasy", "فانتازيا")),
    // No TMDB genre for youth stories: Family + teen / coming-of-age signals.
    NotflixGenre("youth_stories", "قصص شبابية للأبد", R.string.notflix_genre_youth_stories,
        matchTerms = listOf("Family", "عائلي"),
        nameKeywords = listOf("teen", "high school", "coming of age", "coming-of-age", "youth", "شباب", "مراهق", "مراهقين", "ثانوية"),
        descriptionKeywords = listOf("coming-of-age", "coming of age", "teenager", "high school", "teen", "شباب", "مراهق")),
    NotflixGenre("sci_fi", "خيال علمي", R.string.notflix_genre_sci_fi,
        matchTerms = listOf("Science Fiction", "Sci-Fi", "خيال علمي")),
    NotflixGenre("holidays", "موسم الأعياد", R.string.notflix_genre_holidays,
        nameKeywords = listOf("christmas", "holiday", "holidays", "xmas", "santa", "halloween", "easter", "ramadan", "noel", "new year", "عيد", "كريسماس", "رأس السنة", "رمضان", "هالوين"),
        descriptionKeywords = listOf("christmas", "santa claus", "halloween", "عيد", "كريسماس", "رمضان")),
    // No TMDB genre for book adaptations: title words + adaptation phrases.
    // Bare "book"/"novel" are too loose ("reads from a book", "by-the-book",
    // "a novel twist"), so the synopsis must name the adaptation.
    NotflixGenre("books_on_screen", "كتبك المفضلة على الشاشة", R.string.notflix_genre_books_on_screen,
        nameKeywords = listOf("novel", "رواية", "كتاب"),
        descriptionKeywords = listOf("novel by", "bestselling novel", "best-selling novel", "book series", "the book by", "graphic novel", "مقتبس من رواية", "مقتبس عن رواية", "عن رواية"),
        descriptionPatterns = listOf(
            """based on[^.]{0,50}\b(novel|book|comics?)\b""",
            """adapted from[^.]{0,50}\b(novel|book|comics?)\b""",
            """inspired[^.]{0,40}\b(novel|book)\b""",
            // "comic book" as an adaptation signal — but not a comic book STORE.
            """\bcomic books?\b(?! store\b)"""
        )),
    NotflixGenre("romance", "رومانسي", R.string.notflix_genre_romance,
        matchTerms = listOf("Romance", "رومانسي")),
    // واقعي is true-story / realistic: Reality TV + Biography/History + "true story".
    NotflixGenre("reality", "واقعي", R.string.notflix_genre_reality,
        matchTerms = listOf("Reality", "Biography", "History", "واقعي"),
        descriptionKeywords = listOf("true story", "true events", "قصة حقيقية", "أحداث حقيقية", "مبني على قصة حقيقية")),
    NotflixGenre("kids_family", "الأطفال والعائلة", R.string.notflix_genre_kids_family,
        matchTerms = listOf("Family", "Kids", "عائلي", "أطفال")),
    NotflixGenre("animation", "رسوم متحركة", R.string.notflix_genre_animation,
        matchTerms = listOf("Animation", "رسوم متحركة")),
    // Anime is NOT all animation: Japanese origin (or the word "anime") only.
    // The anime row is NOT taken whole — it mixes in western animation.
    NotflixGenre("anime", "أنيمي", R.string.notflix_genre_anime,
        matchTerms = listOf("Anime", "أنيمي", "أنمي"),
        nameKeywords = listOf("anime", "أنيمي", "أنمي"),
        matchAnime = true,
        wholeRowOnTitleMatch = false),
    NotflixGenre("documentary", "وثائقيات", R.string.notflix_genre_documentary,
        matchTerms = listOf("Documentary", "وثائقي")),
    NotflixGenre("astrology", "علم التنجيم", R.string.notflix_genre_astrology,
        nameKeywords = listOf("astrology", "zodiac", "horoscope", "tarot", "psychic", "fortune teller", "تنجيم", "أبراج", "برج", "تاروت", "الفلك"),
        descriptionKeywords = listOf("astrology", "astrological", "astrologer", "zodiac", "horoscope", "horoscopes", "tarot", "fortune teller", "palmistry", "star sign", "star signs", "birth chart", "تنجيم", "أبراج", "تاروت", "الفلك", "العرافة", "قراءة الطالع")),
    NotflixGenre("wwe", "WWE", R.string.notflix_genre_wwe,
        nameKeywords = listOf("wwe", "wrestling", "wrestlemania", "smackdown", "مصارعة"),
        descriptionKeywords = listOf("wrestling", "wwe", "مصارعة")),
    NotflixGenre("improv", "ارتجالي", R.string.notflix_genre_improv,
        nameKeywords = listOf("improv", "improvisation", "stand-up", "stand up", "ارتجالي", "ستاند أب"),
        // "stand-up comedy/special/performance" only: plain "stand-up" in a
        // synopsis also matches dramas about a failed stand-up comedian (Joker).
        descriptionKeywords = listOf("improv", "improvisation", "stand-up comedy", "stand-up special", "stand-up performance", "stand-up show", "stand up comedy", "comedy special", "standup", "ارتجالي")),
    NotflixGenre("new_worthy", "الجديد والجدير بالمشاهدة", R.string.notflix_genre_new_worthy,
        matchRecent = true,
        minRating = 6.0f,
        rowTitleTerms = listOf("new", "latest", "recent", "جديد", "أحدث")),
    NotflixGenre("crime", "جريمة", R.string.notflix_genre_crime,
        matchTerms = listOf("Crime", "Noir", "جريمة")),
    NotflixGenre("action", "أكشن", R.string.notflix_genre_action,
        matchTerms = listOf("Action", "أكشن")),
    NotflixGenre("drama", "دراما", R.string.notflix_genre_drama,
        matchTerms = listOf("Drama", "دراما")),
    NotflixGenre("thrilling", "أعمال مشوقة", R.string.notflix_genre_thrilling,
        matchTerms = listOf("Thriller", "Mystery", "Suspense", "إثارة", "تشويق", "غموض"))
)

/**
 * Restyle 3: search-only categories — the quick categories on the search
 * screen that have no tile in the home strip (رعب، رياضة، أعمال عربية).
 * They reuse the same matcher and the same dedicated browse screen; the
 * strip itself stays the user's 21.
 */
internal val NOTFLIX_SEARCH_ONLY_GENRES = listOf(
    NotflixGenre("horror", "رعب", R.string.notflix_genre_horror,
        matchTerms = listOf("Horror", "رعب"),
        nameKeywords = listOf("horror", "haunted", "رعب", "مسكون"),
        descriptionKeywords = listOf("horror", "haunted", "possessed", "رعب", "مسكون", "شبح")),
    NotflixGenre("sports", "رياضة", R.string.notflix_genre_sports,
        matchTerms = listOf("Sport", "Sports", "رياضة"),
        nameKeywords = listOf("sport", "sports", "football", "soccer", "fifa", "nba", "رياضة", "كرة"),
        descriptionKeywords = listOf("football", "soccer", "world cup", "championship", "كرة القدم", "رياضة")),
    NotflixGenre("arabic", "أعمال عربية", R.string.notflix_genre_arabic,
        matchTerms = listOf("Arabic", "عربي"),
        nameKeywords = listOf("arab", "arabic", "عربي", "عربية"),
        matchArabic = true)
)

/** Every browsable genre: the 21 strip genres plus the search-only ones. */
internal fun notflixGenreByTag(tag: String): NotflixGenre? =
    NOTFLIX_GENRES.find { it.tag == tag } ?: NOTFLIX_SEARCH_ONLY_GENRES.find { it.tag == tag }

/** Arab origin countries (TMDB names + ISO codes) for the أعمال عربية match. */
private val notflixArabCountries = setOf(
    "Egypt", "EG", "Saudi Arabia", "SA", "Morocco", "MA", "Algeria", "DZ",
    "Tunisia", "TN", "United Arab Emirates", "AE", "Qatar", "QA", "Kuwait", "KW",
    "Lebanon", "LB", "Jordan", "JO", "Syria", "SY", "Iraq", "IQ", "Yemen", "YE",
    "Oman", "OM", "Bahrain", "BH", "Libya", "LY", "Sudan", "SD", "Mauritania", "MR",
    "Palestine", "PS"
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
    if (matchArabic) {
        // Arabic works: Arabic language, an Arab origin country, or an
        // Arabic-script title.
        if (item.language?.equals("ar", ignoreCase = true) == true) return true
        val firstCountry = item.country?.split(",")?.firstOrNull()?.trim()
        if (firstCountry != null && notflixArabCountries.any { it.equals(firstCountry, ignoreCase = true) }) return true
        if (item.name.any { it in '\u0600'..'\u06FF' }) return true
    }
    if (matchTerms.isNotEmpty()) {
        val genres = item.genres
        if (genres.any { genre -> matchTerms.any { term -> genre.equals(term, ignoreCase = true) || genre.contains(term, ignoreCase = true) } }) return true
    }
    return false
}

/** Restyle 2: focus on a tile live-filters the home rows to that genre.
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

/** The genres strip as a display row, so the home's focus machinery works free.
 *  The title is not rendered (the strip has no row title, like Netflix's mood
 *  bar) but stays localized for any surface that reads it. */
internal fun notflixGenreRow(title: String = "التصنيفات"): NotflixDisplayRow = NotflixDisplayRow(
    key = NOTFLIX_GENRES_ROW_KEY,
    loadKey = NOTFLIX_GENRES_ROW_KEY,
    title = title,
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
 * One genre TILE (restyle 3): a 150×85 dp landscape tile — 300×170 px on the
 * 1080p box, per the approved mockup — with ONE unified semi-transparent
 * grey fill (RGBA 150,150,150,150) and the localized genre name in white,
 * centered. No pictures, no per-genre colors. The ACTIVE tile (the one
 * filtering the rows below) is white with black text, like Netflix's
 * selected pill; focus only scales the tile and — on an inactive tile —
 * adds a white border. The fill never changes on focus.
 */
@Composable
internal fun NotflixGenreTile(genre: NotflixGenre, active: Boolean, modifier: Modifier = Modifier) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, tween(150), label = "genreTileScale")
    val shape = RoundedCornerShape(8.dp)
    val fill: Color = if (active) Color.White else Color(150, 150, 150, 150)
    Box(
        modifier
            .size(150.dp, 85.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(fill, shape)
            .border(2.dp, if (isFocused && !active) Color.White else Color.Transparent, shape)
            .onFocusChanged { isFocused = it.isFocused }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(genre.titleRes),
            color = if (active) Color.Black else Color.White,
            fontFamily = Spec.BodyFont,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The strip itself, mirroring [NotflixRow]'s focus wiring (requesters,
 * record-on-focus, direction keys to the home's onKey) with no row title —
 * like Netflix's mood bar, the strip is self-explanatory. Focus on a tile
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
