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
import com.nuvio.tv.ui.screens.home.NotflixSimpleRow
import com.nuvio.tv.ui.screens.home.NotflixTabHero
import com.nuvio.tv.ui.screens.home.notflixBrowseHero
import com.nuvio.tv.ui.screens.home.notflixBrowseRows

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
    modifier: Modifier = Modifier
) {
    val results = uiState.discoverResults
    val trendingTitle = stringResource(R.string.notflix_theme_trending)
    val newTitle = stringResource(R.string.notflix_theme_new)
    // Localized genre titles, resolved in the composable body (a plain
    // lambda may not call stringResource).
    val genreTitles = mutableMapOf<String, String>()
    for (genre in NOTFLIX_GENRES) {
        genreTitles[genre.tag] = stringResource(genre.titleRes)
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
    val rows = remember(results, trendingTitle, newTitle, genreTitles) {
        notflixBrowseRows(results, trendingTitle, newTitle) { genre ->
            genreTitles[genre.tag] ?: genre.titleAr
        }
    }

    Box(modifier.fillMaxSize().background(Spec.Background).testTag("notflix-discover")) {
        if (!uiState.discoverInitialized || results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(
                        // Restyle 3.1: while a fetch (or a fallback to the next catalog)
                        // is running, show loading — not «no content» for a tab that
                        // is about to fill.
                        if (!uiState.discoverInitialized) R.string.notflix_loading_catalogs
                        else if (uiState.discoverLoading) R.string.notflix_loading
                        else R.string.notflix_no_content
                    ),
                    color = Spec.Secondary, fontSize = 16.sp
                )
            }
            return@Box
        }
        val hero = remember(results) { notflixBrowseHero(results) }
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
