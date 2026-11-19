package com.nuvio.tv.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.stableKey
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec

/**
 * Restyle 3.2 (2026-11-19): the «الجديد» tab — the SIMPLIFIED Netflix New
 * tab. One hero + rows of new content from the existing new-release
 * catalogs (Nouveautés…), plus fresh movies / fresh series / new-&-worthy
 * rows derived from the loaded home catalogs. NO «ذكّرني» (Remind Me):
 * no reminder infrastructure exists in the app, and a fake button would
 * be worse than none. No fake dates — recency comes from the items' own
 * release years. Data comes from the shared HomeViewModel's full catalog
 * rows (already age/hidden-filtered at the source), like the genre
 * browse screen.
 */

/** A catalog whose name marks it as new releases (English, French, Arabic). */
internal fun notflixNewCatalogRow(catalogName: String): Boolean {
    val lower = catalogName.lowercase()
    // French «Nouveautés» (the é breaks the ASCII token split below).
    if (lower.contains("nouveaut")) return true
    if (lower.contains("جديد") || lower.contains("أحدث")) return true
    val tokens = lower.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
    return tokens.intersect(setOf("new", "latest", "recent", "fresh")).isNotEmpty()
}

private val notflixNewYearRegex = Regex("(19|20)\\d{2}")

private fun notflixNewYear(item: MetaPreview): Int? {
    val raw = item.released ?: item.releaseInfo ?: return null
    return notflixNewYearRegex.find(raw)?.value?.toIntOrNull()
}

private fun notflixNewIsSeries(item: MetaPreview): Boolean =
    item.apiType.equals("series", ignoreCase = true) ||
        item.apiType.equals("tv", ignoreCase = true)

/**
 * Pure builder, so the tab's content is unit-testable: the hero (best-rated
 * fresh title with art) + the new-release catalog rows (localized title +
 * type suffix — the tab mixes types, so the suffix disambiguates) +
 * derived fresh-movies / fresh-series / new-&-worthy rows. A row only
 * appears with at least 3 titles.
 */
internal fun notflixNewTabRows(
    fullRows: List<CatalogRow>,
    newMoviesTitle: String,
    newSeriesTitle: String,
    newWorthyTitle: String,
    themeTitles: NotflixThemeTitles?,
    movieLabel: String,
    seriesLabel: String,
    showTypeSuffix: Boolean
): Pair<MetaPreview?, List<NotflixDisplayRow>> {
    val year = java.time.Year.now().value
    val usable = fullRows.filter { !it.apiType.equals("channel", ignoreCase = true) }
    val rows = buildList {
        usable.filter { row -> row.items.isNotEmpty() && notflixNewCatalogRow(row.catalogName) }
            .forEach { row ->
                val name = row.catalogName.replaceFirstChar { it.uppercase() }
                val displayName = notflixNetflixTitleForCatalog(name, themeTitles) ?: name
                val suffix = when (row.apiType.lowercase()) {
                    "movie" -> movieLabel
                    "series", "tv" -> seriesLabel
                    else -> ""
                }
                val title = if (showTypeSuffix && suffix.isNotBlank()) "$displayName - $suffix" else displayName
                val key = "new-catalog-${row.stableKey()}"
                add(NotflixDisplayRow(key, key, title, NotflixRowPhase.READY,
                    row.items.map { notflixTileFromMeta(it, key) }))
            }
        val pool = usable.flatMap { it.items }.distinctBy { notflixMetaKey(it) }
        val fresh = pool.filter { (notflixNewYear(it) ?: 0) >= year - 1 }
        val freshMovies = fresh.filterNot(::notflixNewIsSeries)
            .sortedWith(compareByDescending<MetaPreview> { notflixNewYear(it) ?: 0 }
                .thenByDescending { it.imdbRating ?: -1f })
            .take(20)
        if (freshMovies.size >= 3) {
            add(NotflixDisplayRow("new-fresh-movies", "new-fresh-movies", newMoviesTitle,
                NotflixRowPhase.READY, freshMovies.map { notflixTileFromMeta(it, "new-fresh-movies") }))
        }
        val freshSeries = fresh.filter(::notflixNewIsSeries)
            .sortedWith(compareByDescending<MetaPreview> { notflixNewYear(it) ?: 0 }
                .thenByDescending { it.imdbRating ?: -1f })
            .take(20)
        if (freshSeries.size >= 3) {
            add(NotflixDisplayRow("new-fresh-series", "new-fresh-series", newSeriesTitle,
                NotflixRowPhase.READY, freshSeries.map { notflixTileFromMeta(it, "new-fresh-series") }))
        }
        val worthyGenre = NOTFLIX_GENRES.firstOrNull { it.tag == "new_worthy" }
        if (worthyGenre != null) {
            val worthy = pool.filter { worthyGenre.matchesItem(it) }
                .sortedWith(compareByDescending<MetaPreview> { notflixNewYear(it) ?: 0 }
                    .thenByDescending { it.imdbRating ?: -1f })
                .take(20)
            if (worthy.size >= 3) {
                add(NotflixDisplayRow("new-worthy", "new-worthy", newWorthyTitle,
                    NotflixRowPhase.READY, worthy.map { notflixTileFromMeta(it, "new-worthy") }))
            }
        }
    }
    val heroPool = usable.flatMap { it.items }.distinctBy { notflixMetaKey(it) }
        .filter { (notflixNewYear(it) ?: 0) >= year - 1 }
    val hero = notflixBrowseHero(heroPool.ifEmpty { usable.flatMap { it.items } })
    return hero to rows
}

@Composable
internal fun NotflixNewContent(
    fullRows: List<CatalogRow>,
    loading: Boolean,
    onNavigateToDetail: (String, String, String) -> Unit,
    onPlayItem: (MetaPreview) -> Unit,
    onItemFocused: (MetaPreview) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val newMoviesTitle = stringResource(R.string.notflix_genre_section_new_movies)
    val newSeriesTitle = stringResource(R.string.notflix_genre_section_new_series)
    val newWorthyTitle = stringResource(R.string.notflix_genre_new_worthy)
    val themeTitles = NotflixThemeTitles(
        trending = stringResource(R.string.notflix_theme_trending),
        popular = stringResource(R.string.notflix_theme_popular),
        topRated = stringResource(R.string.notflix_theme_top_rated),
        featured = stringResource(R.string.notflix_theme_featured),
        new = stringResource(R.string.notflix_theme_new),
        watchlist = stringResource(R.string.notflix_theme_watchlist),
        calendar = stringResource(R.string.notflix_theme_calendar),
        recentlyWatched = stringResource(R.string.notflix_theme_recently_watched),
        hintMovies = stringResource(R.string.notflix_theme_hint_movies),
        hintSeries = stringResource(R.string.notflix_theme_hint_series)
    )
    val movieLabel = stringResource(R.string.type_movie)
    val seriesLabel = stringResource(R.string.type_series)
    val (hero, rows) = remember(fullRows, newMoviesTitle, newSeriesTitle, newWorthyTitle,
        themeTitles, movieLabel, seriesLabel) {
        notflixNewTabRows(fullRows, newMoviesTitle, newSeriesTitle, newWorthyTitle,
            themeTitles, movieLabel, seriesLabel, showTypeSuffix = true)
    }
    Box(modifier.fillMaxSize().background(Spec.Background).testTag("notflix-new")) {
        if (fullRows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (loading) R.string.notflix_loading_catalogs else R.string.notflix_no_content),
                    color = Spec.Secondary, fontSize = 16.sp
                )
            }
            return@Box
        }
        LazyColumn(Modifier.fillMaxSize()) {
            if (hero != null) {
                item("hero") {
                    NotflixTabHero(
                        item = hero,
                        onPlay = { onPlayItem(hero) },
                        onInfo = {
                            onNavigateToDetail(hero.id, hero.apiType, hero.sourceAddonBaseUrl.orEmpty())
                        }
                    )
                }
            }
            items(rows, key = { it.key }) { row ->
                NotflixSimpleRow(
                    row = row,
                    onOpen = { tile ->
                        tile.meta?.let { meta ->
                            onNavigateToDetail(meta.id, meta.apiType, meta.sourceAddonBaseUrl.orEmpty())
                        }
                    },
                    onFocusedTile = { tile -> tile.meta?.let(onItemFocused) }
                )
            }
        }
    }
}
