package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.legacyKey
import com.nuvio.tv.core.profile.isKidsAgeLimit
import com.nuvio.tv.R

/** Captured beside the official rows; stale generation results never publish removals. */
internal fun HomeViewModel.notflixSnapshot(
    captured: NotflixCatalogSnapshot, ordered: List<String>, fullRows: List<CatalogRow>, collections: List<Collection>
): NotflixCatalogSnapshot {
    configureNotflixCollectionRows()
    val scopedCollections = if (notflixCollectionsProfile == captured.context) collections else emptyList()
    return projectNotflixCatalogs(captured, ordered, fullRows, scopedCollections, disabledHomeCatalogKeys,
        customCatalogTitles, synchronized(catalogStateLock) { placeholderDescriptors.toList() },
        _uiState.value.catalogTypeSuffixEnabled, appContext.getString(R.string.type_movie),
        appContext.getString(R.string.type_series), notflixCollectionRows.snapshot(),
        kidsHome = isKidsAgeLimit(ageGate.currentLimit()))
        .copy(collectionRowsKnown = _uiState.value.notflixPresentationActive && notflixCollectionsProfile == captured.context)
}

/** Presentation over captured official inputs only; never reads/writes preferences or starts a load. */
internal fun projectNotflixCatalogs(
    captured: NotflixCatalogSnapshot, ordered: List<String>, fullRows: List<CatalogRow>, collections: List<Collection>,
    disabled: Set<String>, customTitles: Map<String, String>, descriptors: List<HomeViewModel.PlaceholderDescriptor>,
    showTypeSuffix: Boolean, movieLabel: String, seriesLabel: String,
    projectedFolders: List<NotflixCatalogRow> = emptyList(),
    kidsHome: Boolean = false
): NotflixCatalogSnapshot {
    val filtered = fullRows.associateBy { it.legacyKey() }
    val names = descriptors.associateBy { it.catalogKey }
    val catalog = captured.copy(rows = captured.rows.map { row ->
        val latest = filtered[row.loadKey]
        val descriptor = names[row.loadKey]
        val base = customTitles[row.loadKey]?.takeIf { it.isNotBlank() }
            ?: descriptor?.catalogName ?: latest?.catalogName
        val type = descriptor?.apiType ?: (latest ?: row.catalog)?.apiType
        // Match the official display-title convention, including default names and optional type suffix.
        val title = if (base != null && type != null) {
            val name = base.replaceFirstChar { it.uppercase() }
            // Restyle: Netflix-style Arabic titles for the stock theme rows;
            // a custom title (or an unmapped name) passes through untouched.
            val displayName = if (customTitles[row.loadKey].isNullOrBlank()) {
                notflixNetflixTitleForCatalog(name) ?: name
            } else name
            val suffix = when (type.lowercase()) {
                "movie" -> movieLabel.ifBlank { type.replaceFirstChar { it.uppercase() } }
                "series" -> seriesLabel.ifBlank { type.replaceFirstChar { it.uppercase() } }
                else -> type.replaceFirstChar { it.uppercase() }
            }
            if (showTypeSuffix) "$displayName - $suffix" else displayName
        } else row.title
        row.copy(title = title, catalog = latest ?: row.catalog)
    })
    val collectionRows = collections.filter { "collection_${it.id}" !in disabled }
        .map { NotflixCatalogRow("collection_${it.id}", "collection_${it.id}", it.title, NotflixRowPhase.READY, collection = it) }
    val byKey = (catalog.rows + collectionRows).associateBy { it.loadKey }
    val pinned = collections.filter { it.pinToTop }.map { "collection_${it.id}" }
    val folderGroups = projectedFolders.groupBy { it.folderTarget?.first }
    // M66: the kids home is only its own row group; the account's catalogs and collections are
    // not projected at all, so their rows cannot leak into a KIDS profile.
    val expanded = if (kidsHome) {
        projectedFolders
    } else {
        (pinned + ordered).distinct().mapNotNull { byKey[it] }
            .filter { it.loadKey !in disabled }.flatMap { row ->
                if (row.collection?.id == "notflix-inicio") folderGroups[row.collection.id] ?: listOf(row) else listOf(row)
            }
    }
    val (rows, aliases) = suppressReplacedPopular(expanded, captured.focusAliases)
    val (mergedRows, mergedAliases) = mergeNotflixThemeRows(rows, aliases)
    return catalog.copy(rows = mergedRows, focusAliases = mergedAliases)
}

/**
 * Restyle: Netflix-style Arabic titles for the stock theme rows. Only the
 * theme word is mapped; a movies/series hint is kept when the catalog name
 * carries one, so «Trending Movies» and «Trending Series» stay distinct.
 * Anything unmapped returns null and keeps its original name.
 */
internal fun notflixNetflixTitleForCatalog(name: String): String? {
    val tokens = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
    val theme = when {
        "trending" in tokens -> "الأكثر رواجاً الآن"
        "popular" in tokens -> "رائج الآن"
        ("top" in tokens && "rated" in tokens) || "toprated" in tokens -> "الأعلى تقييماً"
        "featured" in tokens -> "مختارات"
        "new" in tokens -> "جديد"
        "watchlist" in tokens -> "قائمة المشاهدة"
        "calendar" in tokens -> "التقويم"
        ("last" in tokens && "videos" in tokens) || "recently" in tokens -> "شوهد مؤخراً"
        else -> return null
    }
    val typeHint = when {
        tokens.intersect(setOf("movie", "movies", "film", "films")).isNotEmpty() -> " · أفلام"
        tokens.intersect(setOf("series", "tv", "show", "shows")).isNotEmpty() -> " · مسلسلات"
        else -> ""
    }
    return theme + typeHint
}

/**
 * Restyle: merge same-theme catalog rows from different addons into one
 * Netflix-style row (e.g. Trending from Cinemeta + MediaFusion), keeping the
 * first row's key/title and aliasing the removed keys to it so saved focus
 * still resolves. Items dedupe by apiType:id; movies and series never mix.
 * Collection and folder-target rows pass through untouched.
 */
internal fun mergeNotflixThemeRows(rows: List<NotflixCatalogRow>, previousAliases: Map<String, String>): Pair<List<NotflixCatalogRow>, Map<String, String>> {
    val themeOf: (NotflixCatalogRow) -> String? = { row ->
        if (row.collection != null || row.folderTarget != null) null
        else {
            val catalog = row.catalog ?: return@themeOf null
            val tokens = catalog.catalogName.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
            val theme = when {
                "trending" in tokens -> "trending"
                "popular" in tokens -> "popular"
                ("top" in tokens && "rated" in tokens) || "toprated" in tokens -> "toprated"
                "featured" in tokens -> "featured"
                "new" in tokens -> "new"
                else -> null
            }
            theme?.let { "$it:${catalog.apiType.lowercase()}" }
        }
    }
    val groups = rows.mapNotNull { row -> themeOf(row)?.let { it to row } }
        .groupBy({ it.first }, { it.second })
        .filter { it.value.size > 1 }
    if (groups.isEmpty()) return rows to previousAliases
    val removed = mutableMapOf<String, String>()
    val mergedByKey = mutableMapOf<String, NotflixCatalogRow>()
    groups.forEach { (_, members) ->
        val kept = members.first()
        val seen = mutableSetOf<String>()
        val items = members.flatMap { it.catalog?.items.orEmpty() }
            .filter { item -> seen.add(notflixMetaKey(item)) }
        val mergedCatalog = kept.catalog?.copy(items = items)
        val phase = if (members.any { it.phase == NotflixRowPhase.READY }) NotflixRowPhase.READY else kept.phase
        mergedByKey[kept.key] = kept.copy(catalog = mergedCatalog, phase = phase)
        members.drop(1).forEach { removed[it.key] = kept.key }
    }
    val visible = rows.filter { it.key !in removed }.map { mergedByKey[it.key] ?: it }
    val keys = visible.map { it.key }.toSet()
    val aliases = (previousAliases + removed + removed.entries.associate { it.value to it.key })
        .filter { (from, to) -> from in keys || to in keys }.entries.toList().takeLast(24).associate { it.toPair() }
    return visible to aliases
}
