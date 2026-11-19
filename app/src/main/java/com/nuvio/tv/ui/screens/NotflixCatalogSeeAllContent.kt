package com.nuvio.tv.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.nuvio.tv.R
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.ui.appearance.notflix.NotflixCategoryMessage
import com.nuvio.tv.ui.appearance.notflix.NotflixCategoryPage
import com.nuvio.tv.ui.appearance.notflix.NotflixCategorySkeleton
import com.nuvio.tv.ui.appearance.notflix.NotflixCategoryTab
import com.nuvio.tv.ui.appearance.notflix.notflixCategoryPosterKeys
import com.nuvio.tv.ui.appearance.notflix.toNotflixCategoryHeader
import com.nuvio.tv.ui.appearance.notflix.toNotflixPosterSpec
import com.nuvio.tv.ui.components.posteroptions.PosterOptionsController
import com.nuvio.tv.ui.components.posteroptions.PosterOptionsHost
import com.nuvio.tv.ui.screens.home.NotflixCardActionsHost
import com.nuvio.tv.ui.screens.home.NotflixPreviewSession

/**
 * Etiqueta del add-on que ya pinta la pantalla oficial («de <add-on>»). Se respeta el ajuste
 * `catalogAddonNameEnabled` y no se inventa nada si el add-on no tiene nombre.
 */
internal fun notflixCatalogCollectionLabel(addonName: String?, enabled: Boolean): String? =
    addonName?.trim()?.takeIf { enabled && it.isNotBlank() }

/**
 * Visto de un póster de «Ver todo»: en modo búsqueda se usan los sets del resultado de búsqueda y,
 * en el resto, el `movieWatchedStatus` de Inicio; son las mismas fuentes que la pantalla oficial.
 */
internal fun notflixCatalogItemWatched(
    item: MetaPreview,
    searchMode: Boolean,
    searchWatchedMovieIds: Set<String>,
    searchWatchedSeriesIds: Set<String>,
    movieWatchedStatus: Map<String, Boolean>
): Boolean {
    if (searchMode) {
        val isSeries = item.apiType.equals("series", ignoreCase = true) ||
            item.apiType.equals("tv", ignoreCase = true)
        return if (isSeries) {
            item.id in searchWatchedSeriesIds
        } else {
            item.id in searchWatchedMovieIds
        }
    }
    return movieWatchedStatus[
        com.nuvio.tv.ui.screens.home.homeItemStatusKey(item.id, item.apiType)
    ] == true
}

/**
 * Restyle 2: one Kaptain-style section of a genre screen — a tab of the
 * shared category page (e.g. «أفلام جديدة», «أفضل المسلسلات»).
 */
internal data class NotflixSeeAllSection(val key: String, val label: String, val items: List<MetaPreview>)

/**
 * M49: rama Notflix de «Ver todo» de una fila de catálogo. Comparte la página interna con la
 * carpeta de colección; no pide datos: los elementos, la paginación y las acciones son las mismas
 * del `HomeViewModel`/`SearchViewModel` que usa la pantalla oficial.
 *
 * Restyle 2: when [sections] is given, the page shows one tab per section
 * instead of the single [items] tab; [items] still drives the loading and
 * empty states. The official «Ver todo» caller passes no sections and is
 * unaffected.
 */
@Composable
internal fun NotflixCatalogSeeAllScreen(
    catalogKey: String,
    title: String,
    collectionLabel: String?,
    items: List<MetaPreview>,
    isLoading: Boolean,
    hasMore: Boolean,
    notFound: Boolean,
    onRetry: (() -> Unit)?,
    onLoadMore: () -> Unit,
    watched: (MetaPreview) -> Boolean,
    onOpen: (MetaPreview) -> Unit,
    onLongPress: (MetaPreview) -> Unit,
    onNavigateToDetail: (String, String, String) -> Unit,
    posterController: PosterOptionsController,
    /** M63: misma tarjeta abierta y mismo tráiler que la carpeta de colección. */
    previewSession: NotflixPreviewSession? = null,
    cardActions: NotflixCardActionsHost? = null,
    onCardPlay: ((MetaPreview) -> Unit)? = null,
    canPlay: Boolean = false,
    sections: List<NotflixSeeAllSection>? = null,
    selectedTabIndex: Int = 0,
    onSelectTab: (Int) -> Unit = {}
) {
    val posterOptionsState by posterController.state.collectAsState()
    val keys = remember(items) { notflixCategoryPosterKeys(items) }
    val itemsByKey = remember(items, keys) { keys.zip(items).toMap() }
    val specs = items.mapIndexed { index, item ->
        item.toNotflixPosterSpec(watched = watched(item))
            .copy(key = keys.getOrElse(index) { "${item.apiType}:${item.id}" })
    }
    // Sections share titles with «All», so their specs are built once over
    // the union (deduped by title identity — the genre screen dedupes) and
    // each tab picks its own subset. The single-tab path is unchanged.
    val tabs = sections?.let { list ->
        val union = list.flatMap { it.items }.distinctBy { "${it.apiType}:${it.id}" }
        val unionKeys = notflixCategoryPosterKeys(union)
        val unionSpecByIdentity = union.mapIndexed { index, item ->
            val key = unionKeys.getOrElse(index) { "${item.apiType}:${item.id}" }
            "${item.apiType}:${item.id}" to item.toNotflixPosterSpec(watched = watched(item)).copy(key = key)
        }.toMap()
        list.map { section ->
            NotflixCategoryTab(
                key = section.key,
                label = section.label,
                items = section.items.mapNotNull { item -> unionSpecByIdentity["${item.apiType}:${item.id}"] }
            )
        }
    } ?: listOf(
        NotflixCategoryTab(
            key = "catalog",
            label = title,
            items = specs,
            isLoading = isLoading,
            hasMore = hasMore
        )
    )
    when {
        isLoading && items.isEmpty() -> NotflixCategorySkeleton()

        notFound -> NotflixCategoryMessage(
            title = stringResource(R.string.notflix_category_error_title),
            actionLabel = onRetry?.let { stringResource(R.string.notflix_retry) },
            onAction = onRetry
        )

        else -> NotflixCategoryPage(
            title = title,
            collectionLabel = collectionLabel,
            tabs = tabs,
            selectedTabIndex = selectedTabIndex,
            saveScope = "catalog-$catalogKey",
            onSelectTab = onSelectTab,
            headerFor = { spec -> itemsByKey[spec.key]?.toNotflixCategoryHeader() },
            onPosterClick = { _, spec ->
                itemsByKey[spec.key]?.let(onOpen)
            },
            onPosterLongClick = { _, spec ->
                itemsByKey[spec.key]?.let(onLongPress)
            },
            onLoadMore = { onLoadMore() },
            onRetry = {},
            emptyTitle = stringResource(R.string.catalog_see_all_empty_title),
            emptyMessage = stringResource(R.string.catalog_see_all_empty_subtitle),
            itemsByKey = itemsByKey,
            cardActions = cardActions,
            previewSession = previewSession,
            onCardPlay = onCardPlay,
            canPlay = canPlay
        )
    }

    PosterOptionsHost(
        state = posterOptionsState,
        controller = posterController,
        onNavigateToDetail = { id, type, addonBaseUrl ->
            onNavigateToDetail(id, type, addonBaseUrl)
        }
    )
}
