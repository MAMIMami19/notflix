package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.ui.theme.NuvioTheme
import com.nuvio.tv.ui.screens.home.HeroBackdropState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.DiscoverLocation
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.theme.NuvioColors
import kotlin.math.roundToInt

@Composable
fun DiscoverScreen(
    viewModel: SearchViewModel = hiltViewModel(),
    showBuiltInHeader: Boolean = true,
    /** Restyle 2: the top-bar Series/Movies tabs pre-select their type
     *  ("series" / "movie"); null keeps the default Discover behaviour. */
    initialType: String? = null,
    onNavigateToDetail: (String, String, String) -> Unit,
    /** Restyle 3: the tab hero's Play button — detail with playOnLoad. */
    onPlayItem: (com.nuvio.tv.domain.model.MetaPreview) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val watchedMovieIds by viewModel.watchedMovieIds.collectAsState()
    val watchedSeriesIds by viewModel.watchedSeriesIds.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val discoverFirstItemFocusRequester = remember { FocusRequester() }
    var discoverFocusedItemIndex by rememberSaveable { mutableStateOf(0) }
    var restoreDiscoverFocus by rememberSaveable { mutableStateOf(false) }
    var pendingDiscoverRestoreOnResume by rememberSaveable { mutableStateOf(false) }
    var initialTypeApplied by rememberSaveable(initialType) { mutableStateOf(false) }

    val posterCardStyle = remember(uiState.posterCardWidthDp, uiState.posterCardCornerRadiusDp) {
        val computedHeightDp = (uiState.posterCardWidthDp * 1.5f).roundToInt()
        PosterCardStyle(
            width = uiState.posterCardWidthDp.dp,
            height = computedHeightDp.dp,
            cornerRadius = uiState.posterCardCornerRadiusDp.dp,
            focusedBorderWidth = PosterCardDefaults.Style.focusedBorderWidth,
            focusedScale = PosterCardDefaults.Style.focusedScale
        )
    }

    LaunchedEffect(uiState.discoverLocation) {
        if (uiState.discoverLocation != DiscoverLocation.OFF) {
            viewModel.ensureDiscoverLoaded()
        }
    }

    // Restyle 2: apply the tab's type once the catalogs are loaded — before
    // that there is nothing to pick from, and the initial load would
    // overwrite the selection with its own default.
    LaunchedEffect(uiState.discoverInitialized, initialType) {
        if (initialType != null && !initialTypeApplied && uiState.discoverInitialized) {
            initialTypeApplied = true
            if (uiState.selectedDiscoverType != initialType) {
                viewModel.onEvent(SearchEvent.SelectDiscoverType(initialType))
            }
        }
    }

    val latestPendingDiscoverRestore by rememberUpdatedState(pendingDiscoverRestoreOnResume)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && latestPendingDiscoverRestore) {
                restoreDiscoverFocus = true
                pendingDiscoverRestoreOnResume = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NuvioTheme.colors.Background)
    ) {
        if (uiState.discoverLocation == DiscoverLocation.OFF) {
            EmptyScreenState(
                title = stringResource(R.string.discover_disabled_title),
                subtitle = stringResource(R.string.discover_disabled_subtitle),
                icon = Icons.Default.Search
            )
        } else if (com.nuvio.tv.ui.appearance.LocalNotflixPresentation.current.active && initialType != null) {
            // Restyle 3: the Movies / Series tabs get the Netflix treatment
            // (hero + rows). The plain Discover route keeps the official
            // grid + filters byte-for-byte.
            NotflixDiscoverContent(
                uiState = uiState,
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    pendingDiscoverRestoreOnResume = true
                    onNavigateToDetail(itemId, itemType, addonBaseUrl)
                },
                onPlayItem = onPlayItem,
                onItemFocused = { item -> viewModel.prefetchMetaOnFocus(item.id, item.rawType) },
                onItemLongPress = { item, addonBaseUrl ->
                    viewModel.posterOptions.show(item, addonBaseUrl)
                },
                onLoadMore = { viewModel.onEvent(SearchEvent.LoadNextDiscoverResults) },
                // Restyle 3.2: the tab's per-catalog rows load lazily.
                onEnsureTabRows = { viewModel.ensureTabRowsLoaded() }
            )
        } else {
            DiscoverSection(
                uiState = uiState,
                posterCardStyle = posterCardStyle,
                watchedMovieIds = watchedMovieIds,
                watchedSeriesIds = watchedSeriesIds,
                focusResults = false,
                showBuiltInHeader = showBuiltInHeader,
                firstItemFocusRequester = discoverFirstItemFocusRequester,
                focusedItemIndex = discoverFocusedItemIndex,
                shouldRestoreFocusedItem = restoreDiscoverFocus,
                blockFilterFocus = restoreDiscoverFocus || pendingDiscoverRestoreOnResume,
                onRestoreFocusedItemHandled = { restoreDiscoverFocus = false },
                onNavigateToDetail = { itemId, itemType, addonBaseUrl ->
                    pendingDiscoverRestoreOnResume = true
                    onNavigateToDetail(itemId, itemType, addonBaseUrl)
                },
                onDiscoverItemFocused = { index ->
                    discoverFocusedItemIndex = index
                    uiState.discoverResults.getOrNull(index)?.let { item ->
                        viewModel.prefetchMetaOnFocus(item.id, item.rawType)
                    }
                },
                onSelectType = {
                    discoverFocusedItemIndex = 0
                    viewModel.onEvent(SearchEvent.SelectDiscoverType(it))
                },
                onSelectCatalog = {
                    discoverFocusedItemIndex = 0
                    viewModel.onEvent(SearchEvent.SelectDiscoverCatalog(it))
                },
                onSelectGenre = {
                    discoverFocusedItemIndex = 0
                    viewModel.onEvent(SearchEvent.SelectDiscoverGenre(it))
                },
                onLoadMore = { viewModel.onEvent(SearchEvent.LoadNextDiscoverResults) },
                onItemLongPress = { item, addonBaseUrl ->
                    viewModel.posterOptions.show(item, addonBaseUrl)
                },
                modifier = Modifier.padding(top = NuvioTheme.spacing.lg)
            )
        }

        val posterOptionsState by viewModel.posterOptions.state.collectAsState()
        com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost(
            state = posterOptionsState,
            controller = viewModel.posterOptions,
            onNavigateToDetail = { id, type, addonBaseUrl ->
                pendingDiscoverRestoreOnResume = true
                val clickedItem = uiState.discoverResults.firstOrNull { it.id == id }
                HeroBackdropState.update(clickedItem?.backdropUrl)
                onNavigateToDetail(id, type, addonBaseUrl)
            }
        )
    }
}
