package com.nuvio.tv.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.appearance.NotflixVisualSpec as Spec
import com.nuvio.tv.ui.screens.home.NOTFLIX_GENRES
import com.nuvio.tv.ui.screens.home.NotflixDisplayRow
import com.nuvio.tv.ui.screens.home.NotflixRowPhase
import com.nuvio.tv.ui.screens.home.NotflixSimpleRow
import com.nuvio.tv.ui.screens.home.NotflixTabHero
import com.nuvio.tv.ui.screens.home.notflixBrowseGenreRows
import com.nuvio.tv.ui.screens.home.notflixBrowseHero
import com.nuvio.tv.ui.screens.home.notflixBrowseRows
import com.nuvio.tv.ui.screens.home.notflixTileFromMeta

/**
 * Restyle 3 (2026-11-19): the Notflix Movies / Series tab — Netflix-style.
 * One inset hero billboard for the tab's best title, then Netflix rows
 * (trending, new, genre rows) with the home's own expanded focused cards —
 * instead of the official Discover grid + filter dropdowns. The type is
 * fixed by the tab, so the filters have no home here. The official
 * Discover route (no pre-selected type) keeps its own screen byte-for-byte.
 */
@Composable
internal fun NotflixDiscoverContent(
    uiState: SearchUiState,
    onNavigateToDetail: (String, String, String) -> Unit,
    onPlayItem: (MetaPreview) -> Unit,
    onItemFocused: (MetaPreview) -> Unit,
    onItemLongPress: (MetaPreview, String) -> Unit,
    onLoadMore: () -> Unit,
    /** Restyle 3.2: the tab's per-catalog rows load lazily, only while a tab is shown. */
    onEnsureTabRows: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val results = uiState.discoverResults
    val tabRows = uiState.discoverTabRows
    val trendingTitle = stringResource(R.string.notflix_theme_trending)
    val newTitle = stringResource(R.string.notflix_theme_new)
    // Localized genre titles, resolved in the composable body (a plain
    // lambda may not call stringResource).
    val genreTitles = mutableMapOf<String, String>()
    for (genre in NOTFLIX_GENRES) {
        genreTitles[genre.tag] = stringResource(genre.titleRes)
    }
    LaunchedEffect(uiState.selectedDiscoverType, uiState.discoverInitialized) {
        if (uiState.discoverInitialized) onEnsureTabRows()
    }
    // Rows need a fuller page than the first load gives; ask for one more
    // page per catalog/type while the list is still thin.
    var extraLoadRequested by remember(uiState.selectedDiscoverCatalogKey, uiState.selectedDiscoverType) {
        mutableStateOf(false)
    }
    LaunchedEffect(results.size, uiState.discoverHasMore, extraLoadRequested) {
        if (!extraLoadRequested && uiState.discoverHasMore && results.size < 40) {
            extraLoadRequested = true
            onLoadMore()
        }
    }
    // Restyle 3.2: when the per-catalog rows are in, the tab shows one row
    // per catalog (Trending, Popular, Top Rated, New, genre rows…) plus
    // derived genre rows only for the genres with no catalog row of their
    // own. Until then (or if the fetch fails), the old derived rows from
    // the selected catalog still fill the tab — never an empty screen.
    val mergedItems = remember(results, tabRows) {
        (tabRows.flatMap { it.items } + results).distinctBy { "${it.apiType}:${it.id}" }
    }
    val rows = remember(tabRows, mergedItems, results, trendingTitle, newTitle, genreTitles) {
        if (tabRows.isNotEmpty()) {
            val catalogRows = tabRows.map { tabRow ->
                NotflixDisplayRow(tabRow.key, tabRow.key, tabRow.title, NotflixRowPhase.READY,
                    tabRow.items.map { notflixTileFromMeta(it, tabRow.key) })
            }
            val coveredTags = NOTFLIX_GENRES.filter { genre ->
                tabRows.any { it.title.contains(genre.titleAr) }
            }.map { it.tag }.toSet()
            val genreRows = notflixBrowseGenreRows(mergedItems, { genre ->
                genreTitles[genre.tag] ?: genre.titleAr
            }, excludeTags = coveredTags)
            catalogRows + genreRows
        } else {
            notflixBrowseRows(results, trendingTitle, newTitle) { genre ->
                genreTitles[genre.tag] ?: genre.titleAr
            }
        }
    }

    Box(modifier.fillMaxSize().background(Spec.Background).testTag("notflix-discover")) {
        if (!uiState.discoverInitialized || (results.isEmpty() && tabRows.isEmpty())) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(
                        // Restyle 3.1: while a fetch (or a fallback to the next catalog)
                        // is running, show loading — not «no content» for a tab that
                        // is about to fill.
                        if (!uiState.discoverInitialized) R.string.notflix_loading_catalogs
                        else if (uiState.discoverLoading || uiState.discoverTabRowsLoading) R.string.notflix_loading
                        else R.string.notflix_no_content
                    ),
                    color = Spec.Secondary, fontSize = 16.sp
                )
            }
            return@Box
        }
        // Restyle 3.2: the hero picks from everything the tab loaded, not
        // just the selected catalog.
        val hero = remember(mergedItems) { notflixBrowseHero(mergedItems) }
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
                    onLongPress = { tile ->
                        tile.meta?.let { meta ->
                            onItemLongPress(meta, meta.sourceAddonBaseUrl.orEmpty())
                        }
                    },
                    onFocusedTile = { tile -> tile.meta?.let(onItemFocused) }
                )
            }
        }
    }
}
