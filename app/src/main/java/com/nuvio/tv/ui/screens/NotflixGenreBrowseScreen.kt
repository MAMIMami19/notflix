@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.nuvio.tv.ui.screens

import com.nuvio.tv.ui.theme.NuvioTheme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.local.hiddenTitleKey
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.appearance.LocalNotflixPresentation
import com.nuvio.tv.ui.components.EmptyScreenState
import com.nuvio.tv.ui.components.GridContentCard
import com.nuvio.tv.ui.components.PosterCardDefaults
import com.nuvio.tv.ui.components.PosterCardStyle
import com.nuvio.tv.ui.components.posteroptions.PosterOptionsViewModel
import com.nuvio.tv.domain.model.LibrarySourceMode
import com.nuvio.tv.ui.components.posteroptions.posterCardStatusKey
import com.nuvio.tv.ui.screens.home.HomeViewModel
import com.nuvio.tv.ui.screens.home.notflixGenreByTag
import com.nuvio.tv.ui.screens.home.NotflixCardStatus
import com.nuvio.tv.ui.screens.home.allowsForAge
import com.nuvio.tv.ui.screens.home.homeItemStatusKey
import com.nuvio.tv.ui.screens.home.matchesItem
import com.nuvio.tv.ui.screens.home.notflixCardActionHost
import com.nuvio.tv.ui.screens.home.rememberNotflixPreviewSession
import com.nuvio.tv.ui.screens.home.toNotflixCardStatus
import kotlin.math.roundToInt

/**
 * Restyle 2 (2026-11-19): the dedicated genre browse screen.
 *
 * OK on a genre pill opens this screen — that genre's titles across every
 * home catalog row — instead of restyle 1's in-place home filter. Items come
 * from the shared [HomeViewModel.fullCatalogRows] (the same data the home
 * already shows): a row whose title names the genre contributes all its
 * items, other rows only their matching ones, deduped by apiType:id. Anime
 * is the exception: its row mixes in western animation, so its items are
 * matched one by one (Animation + Japanese origin). BACK returns to the home
 * (popBackStack).
 *
 * Inside, the titles are split into Kaptain-style sections — New, Popular,
 * Top All Time, Top of Year, movies and series apart — as tabs of the shared
 * category page, so the open card, the audible trailer and the card actions
 * all work exactly as in the official «Ver todo» screen. Only non-empty
 * sections appear; «الكل» always comes first with everything.
 */
private fun notflixGenreIsSeries(item: MetaPreview): Boolean =
    item.apiType.equals("series", ignoreCase = true) || item.apiType.equals("tv", ignoreCase = true)

private val notflixGenreSectionYearRegex = Regex("(19|20)\\d{2}")

private fun notflixGenreSectionYear(item: MetaPreview): Int? {
    val raw = item.released ?: item.releaseInfo ?: return null
    return notflixGenreSectionYearRegex.find(raw)?.value?.toIntOrNull()
}

/** Best first; unrated titles sort last, votes break ties. */
private val notflixGenreRatingOrder: Comparator<MetaPreview> =
    compareByDescending<MetaPreview> { it.imdbRating ?: -1f }
        .thenByDescending { it.voteCount ?: -1 }

/** Most-voted first; rating breaks ties. */
private val notflixGenrePopularityOrder: Comparator<MetaPreview> =
    compareByDescending<MetaPreview> { it.voteCount ?: -1 }
        .thenByDescending { it.imdbRating ?: -1f }
@Composable
fun NotflixGenreBrowseScreen(
    genreTag: String,
    viewModel: HomeViewModel = hiltViewModel(),
    posterOptionsViewModel: PosterOptionsViewModel = hiltViewModel(),
    onNavigateToDetail: (String, String, String) -> Unit,
    onNavigateToPlay: ((String, String, String, Boolean) -> Unit)? = null,
    onBackPress: () -> Unit
) {
    val genre = remember(genreTag) { notflixGenreByTag(genreTag) }
    val posterOptionsController = posterOptionsViewModel.controller
    val uiState by viewModel.uiState.collectAsState()
    val fullCatalogRows by viewModel.fullCatalogRows.collectAsState()

    BackHandler { onBackPress() }

    if (genre == null) {
        EmptyScreenState(
            title = stringResource(R.string.catalog_see_all_empty_title),
            subtitle = stringResource(R.string.catalog_see_all_empty_subtitle),
            icon = Icons.Default.GridView
        )
        return
    }

    val genreItems = remember(fullCatalogRows, genre, uiState.ageLimit, uiState.ageRatings, uiState.hiddenTitleKeys) {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<MetaPreview>()
        for (row in fullCatalogRows) {
            val titleMatches = row.catalogName.contains(genre.titleAr) ||
                (genre.matchTerms + genre.nameKeywords + genre.rowTitleTerms).any { row.catalogName.contains(it, ignoreCase = true) } ||
                row.catalogName.contains(genre.tag, ignoreCase = true)
            for (item in row.items) {
                val key = "${item.apiType}:${item.id}"
                if (key in seen) continue
                // A row whose title names the genre feeds the screen whole —
                // except anime, whose row mixes in western animation.
                val keep = if (titleMatches && genre.wholeRowOnTitleMatch) true else genre.matchesItem(item)
                if (!keep) continue
                seen += key
                out += item
            }
        }
        val ageItems = if (uiState.ageLimit == null) out else out.filter { uiState.allowsForAge(it) }
        if (uiState.hiddenTitleKeys.isEmpty()) ageItems
        else ageItems.filterNot { hiddenTitleKey(it.apiType, it.id) in uiState.hiddenTitleKeys }
    }
    LaunchedEffect(genreItems) { viewModel.ageGate.prefetch(genreItems) }

    // Kaptain-style sections (New / Popular / Top All Time / Top of Year,
    // movies and series apart), as tabs of the shared category page. Only
    // non-empty sections appear; «الكل» always comes first with everything.
    var selectedSection by remember(genreTag) { mutableStateOf(0) }
    val sectionAll = stringResource(R.string.notflix_genre_section_all)
    val sectionNewMovies = stringResource(R.string.notflix_genre_section_new_movies)
    val sectionNewSeries = stringResource(R.string.notflix_genre_section_new_series)
    val sectionPopularMovies = stringResource(R.string.notflix_genre_section_popular_movies)
    val sectionPopularSeries = stringResource(R.string.notflix_genre_section_popular_series)
    val sectionTopMovies = stringResource(R.string.notflix_genre_section_top_movies)
    val sectionTopSeries = stringResource(R.string.notflix_genre_section_top_series)
    val sectionTopYearMovies = stringResource(R.string.notflix_genre_section_top_year_movies)
    val sectionTopYearSeries = stringResource(R.string.notflix_genre_section_top_year_series)
    val genreSections = remember(genreItems, sectionAll, sectionNewMovies, sectionNewSeries,
        sectionPopularMovies, sectionPopularSeries, sectionTopMovies, sectionTopSeries,
        sectionTopYearMovies, sectionTopYearSeries) {
        val year = java.time.Year.now().value
        fun sectionsFor(
            list: List<MetaPreview>, prefix: String,
            newLabel: String, popularLabel: String, topLabel: String, topYearLabel: String
        ): List<NotflixSeeAllSection> = buildList {
            val fresh = list.filter { (notflixGenreSectionYear(it) ?: 0) >= year - 1 }
                .sortedWith(compareByDescending<MetaPreview> { notflixGenreSectionYear(it) ?: 0 }
                    .then(notflixGenreRatingOrder))
                .take(100)
            if (fresh.isNotEmpty()) add(NotflixSeeAllSection("${prefix}_new", newLabel, fresh))
            val popular = list.sortedWith(notflixGenrePopularityOrder).take(100)
            if (popular.isNotEmpty()) add(NotflixSeeAllSection("${prefix}_popular", popularLabel, popular))
            val top = list.filter { it.imdbRating != null }.sortedWith(notflixGenreRatingOrder).take(100)
            if (top.isNotEmpty()) add(NotflixSeeAllSection("${prefix}_top", topLabel, top))
            val topYear = list.filter { notflixGenreSectionYear(it) == year && it.imdbRating != null }
                .sortedWith(notflixGenreRatingOrder).take(100)
            if (topYear.isNotEmpty()) add(NotflixSeeAllSection("${prefix}_top_year", topYearLabel, topYear))
        }
        buildList {
            add(NotflixSeeAllSection("all", sectionAll, genreItems))
            addAll(sectionsFor(genreItems.filterNot(::notflixGenreIsSeries), "movie",
                sectionNewMovies, sectionPopularMovies, sectionTopMovies, sectionTopYearMovies))
            addAll(sectionsFor(genreItems.filter(::notflixGenreIsSeries), "series",
                sectionNewSeries, sectionPopularSeries, sectionTopSeries, sectionTopYearSeries))
        }
    }

    // Notflix branch: the same category page as «Ver todo», with the genre's
    // own title. Data, pagination and the card menu are the home's own.
    if (LocalNotflixPresentation.current.active) {
        val notflixPosterOptions by posterOptionsController.state.collectAsState()
        val notflixAutoplay by viewModel.effectiveAutoplayEnabled.collectAsStateWithLifecycle(initialValue = false)
        val notflixResolver = remember(viewModel) {
            com.nuvio.tv.ui.screens.home.OfficialNotflixPreviewResolver(
                com.nuvio.tv.ui.screens.home.HomeNotflixPreviewRequestHost(viewModel, requiresHomePresentation = false)
            )
        }
        val notflixPreview = rememberNotflixPreviewSession(
            resolver = notflixResolver,
            dialog = notflixPosterOptions.target != null || notflixPosterOptions.listPickerActive,
            routeActive = true, validContext = { true }, lockContext = true
        )
        DisposableEffect(posterOptionsController) { onDispose { posterOptionsController.stopCardStatus() } }
        val latestNotflixCardStatus = posterOptionsController.cardStatus.collectAsState()
        val notflixCardActions = remember(posterOptionsController, notflixPosterOptions.librarySourceMode) {
            notflixCardActionHost(
                status = { item ->
                    latestNotflixCardStatus.value[posterCardStatusKey(item.apiType, item.id)]
                        ?.toNotflixCardStatus() ?: NotflixCardStatus()
                },
                manageLists = notflixPosterOptions.librarySourceMode != LibrarySourceMode.LOCAL,
                onExpanded = { item -> posterOptionsController.refreshCardStatus(item) },
                onLibrary = { item, base -> posterOptionsController.toggleLibraryFor(item, base) },
                onWatched = { item -> posterOptionsController.toggleWatchedFor(item) },
                onHide = { item -> viewModel.hideTitle(item) }
            )
        }
        Box(Modifier.fillMaxSize()) {
            NotflixCatalogSeeAllScreen(
                catalogKey = "genre-$genreTag",
                title = stringResource(genre.titleRes),
                collectionLabel = null,
                items = genreItems,
                isLoading = false,
                hasMore = false,
                notFound = false,
                onRetry = null,
                onLoadMore = {},
                watched = { item ->
                    notflixCatalogItemWatched(
                        item = item,
                        searchMode = false,
                        searchWatchedMovieIds = emptySet(),
                        searchWatchedSeriesIds = emptySet(),
                        movieWatchedStatus = uiState.movieWatchedStatus
                    )
                },
                onOpen = { item ->
                    onNavigateToDetail(item.id, item.apiType, item.sourceAddonBaseUrl.orEmpty())
                },
                onLongPress = { item ->
                    posterOptionsController.show(item, item.sourceAddonBaseUrl.orEmpty())
                },
                onNavigateToDetail = onNavigateToDetail,
                posterController = posterOptionsController,
                previewSession = notflixPreview,
                cardActions = notflixCardActions,
                onCardPlay = onNavigateToPlay?.let { play ->
                    { item -> play(item.id, item.apiType, item.sourceAddonBaseUrl.orEmpty(), !notflixAutoplay) }
                },
                canPlay = onNavigateToPlay != null,
                sections = genreSections,
                selectedTabIndex = selectedSection,
                onSelectTab = { selectedSection = it }
            )
            uiState.hiddenTitleUndo?.takeIf { it.profileId == viewModel.profileManager.activeProfileId.value }?.let { undo ->
                com.nuvio.tv.ui.screens.home.HiddenTitleUndoNotice(undo, viewModel::undoHideTitle,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = NuvioTheme.spacing.xl), autoFocus = false)
            }
        }
        return
    }

    // Stock appearance (never active in the custom build, which is gated to
    // Notflix): a plain poster grid over the same items.
    val computedHeightDp = (uiState.posterCardWidthDp * 1.5f).roundToInt()
    val posterCardStyle = PosterCardStyle(
        width = uiState.posterCardWidthDp.dp,
        height = computedHeightDp.dp,
        cornerRadius = uiState.posterCardCornerRadiusDp.dp,
        focusedBorderWidth = PosterCardDefaults.Style.focusedBorderWidth,
        focusedScale = PosterCardDefaults.Style.focusedScale
    )
    val gridState = rememberLazyGridState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = NuvioTheme.spacing.xl)
    ) {
        Text(
            text = stringResource(genre.titleRes),
            style = MaterialTheme.typography.headlineLarge,
            color = NuvioTheme.colors.TextPrimary,
            modifier = Modifier.padding(horizontal = NuvioTheme.spacing.xxxl)
        )
        Spacer(modifier = Modifier.height(NuvioTheme.spacing.xl))
        if (genreItems.isNotEmpty()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = posterCardStyle.width),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = NuvioTheme.spacing.xxxl,
                    end = NuvioTheme.spacing.xl,
                    top = NuvioTheme.spacing.md,
                    bottom = NuvioTheme.spacing.xxl
                ),
                horizontalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.md),
                verticalArrangement = Arrangement.spacedBy(NuvioTheme.spacing.lg)
            ) {
                items(
                    items = genreItems,
                    key = { item -> "${item.apiType}:${item.id}" }
                ) { item ->
                    GridContentCard(
                        item = item,
                        posterCardStyle = posterCardStyle,
                        showLabel = uiState.posterLabelsEnabled,
                        isWatched = uiState.movieWatchedStatus[homeItemStatusKey(item.id, item.apiType)] == true,
                        onClick = {
                            onNavigateToDetail(item.id, item.apiType, item.sourceAddonBaseUrl.orEmpty())
                        },
                        onLongPress = {
                            posterOptionsController.show(item, item.sourceAddonBaseUrl.orEmpty())
                        }
                    )
                }
            }
        } else {
            EmptyScreenState(
                title = stringResource(R.string.catalog_see_all_empty_title),
                subtitle = stringResource(R.string.catalog_see_all_empty_subtitle),
                icon = Icons.Default.GridView
            )
        }
        val posterOptionsState by posterOptionsController.state.collectAsState()
        com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost(
            state = posterOptionsState,
            controller = posterOptionsController,
            onNavigateToDetail = { id, type2, addonBaseUrl ->
                onNavigateToDetail(id, type2, addonBaseUrl)
            }
        )
    }
}
