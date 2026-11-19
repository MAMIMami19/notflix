package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.legacyKey
import com.nuvio.tv.core.profile.isKidsAgeLimit
import com.nuvio.tv.core.util.withAppLocale
import com.nuvio.tv.R

/** Captured beside the official rows; stale generation results never publish removals. */
internal fun HomeViewModel.notflixSnapshot(
    captured: NotflixCatalogSnapshot, ordered: List<String>, fullRows: List<CatalogRow>, collections: List<Collection>
): NotflixCatalogSnapshot {
    configureNotflixCollectionRows()
    val scopedCollections = if (notflixCollectionsProfile == captured.context) collections else emptyList()
    // Restyle 3.2: the titles on screen are built HERE (projectNotflixCatalogs),
    // so THIS is where the app locale must be applied. Restyle 3.1 fixed only
    // the placeholder displayTitle in loadAllCatalogsPipeline — which is why
    // «كوميديا - Film» survived on device: appContext keeps the SYSTEM locale
    // (French box), and these getString calls resolved French.
    val localizedContext = appContext.withAppLocale()
    val themeTitles = NotflixThemeTitles(
        trending = localizedContext.getString(R.string.notflix_theme_trending),
        popular = localizedContext.getString(R.string.notflix_theme_popular),
        topRated = localizedContext.getString(R.string.notflix_theme_top_rated),
        featured = localizedContext.getString(R.string.notflix_theme_featured),
        new = localizedContext.getString(R.string.notflix_theme_new),
        watchlist = localizedContext.getString(R.string.notflix_theme_watchlist),
        calendar = localizedContext.getString(R.string.notflix_theme_calendar),
        recentlyWatched = localizedContext.getString(R.string.notflix_theme_recently_watched),
        hintMovies = localizedContext.getString(R.string.notflix_theme_hint_movies),
        hintSeries = localizedContext.getString(R.string.notflix_theme_hint_series)
    )
    // Restyle 3.2: localized genre names for the synthetic rows of genres
    // with no addon catalog (item 5); null in unit tests means no synthesis.
    val genreTitles = NOTFLIX_GENRES.associate { it.tag to localizedContext.getString(it.titleRes) }
    return projectNotflixCatalogs(captured, ordered, fullRows, scopedCollections, disabledHomeCatalogKeys,
        customCatalogTitles, synchronized(catalogStateLock) { placeholderDescriptors.toList() },
        _uiState.value.catalogTypeSuffixEnabled, localizedContext.getString(R.string.type_movie),
        localizedContext.getString(R.string.type_series), notflixCollectionRows.snapshot(),
        kidsHome = isKidsAgeLimit(ageGate.currentLimit()), themeTitles = themeTitles,
        genreTitles = genreTitles)
        .copy(collectionRowsKnown = _uiState.value.notflixPresentationActive && notflixCollectionsProfile == captured.context)
}

/** Restyle 3: localized Netflix-style theme titles for the stock catalog
 *  rows. Resolved once per snapshot from the app locale; null falls back
 *  to the Arabic literals (unit tests, which pass no titles). */
internal data class NotflixThemeTitles(
    val trending: String,
    val popular: String,
    val topRated: String,
    val featured: String,
    val new: String,
    val watchlist: String,
    val calendar: String,
    val recentlyWatched: String,
    val hintMovies: String,
    val hintSeries: String
)

/** Presentation over captured official inputs only; never reads/writes preferences or starts a load. */
internal fun projectNotflixCatalogs(
    captured: NotflixCatalogSnapshot, ordered: List<String>, fullRows: List<CatalogRow>, collections: List<Collection>,
    disabled: Set<String>, customTitles: Map<String, String>, descriptors: List<HomeViewModel.PlaceholderDescriptor>,
    showTypeSuffix: Boolean, movieLabel: String, seriesLabel: String,
    projectedFolders: List<NotflixCatalogRow> = emptyList(),
    kidsHome: Boolean = false,
    themeTitles: NotflixThemeTitles? = null,
    /** Restyle 3.2: localized genre names (tag -> title); when non-null, rows are
     *  synthesized for strip genres with no addon catalog. Null (unit tests) disables it. */
    genreTitles: Map<String, String>? = null
): NotflixCatalogSnapshot {
    val filtered = fullRows.associateBy { it.legacyKey() }
    val names = descriptors.associateBy { it.catalogKey }
    // Restyle 3.2: Notflix is films-and-series only — Channel rows (the M3U
    // Mux live catalog, stuck on «loading» forever) never render on home.
    // The type comes from the placeholder descriptor while loading, else
    // from the loaded catalog; movie/series rows are untouched.
    val channelFreeRows = captured.rows.filterNot { row ->
        val type = names[row.loadKey]?.apiType ?: row.catalog?.apiType
        type.equals("channel", ignoreCase = true)
    }
    val catalog = captured.copy(rows = channelFreeRows.map { row ->
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
                notflixNetflixTitleForCatalog(name, themeTitles) ?: name
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
    // Restyle 3.2: rows for strip genres with no addon catalog (مزاج، قصص
    // شبابية، موسم الأعياد، كتبك المفضلة، علم التنجيم، WWE، ارتجالي) —
    // synthesized from the loaded catalogs with the genre matcher, in tiles
    // order. Never for the kids home (M66: its own row group only).
    val finalRows = if (genreTitles != null && !kidsHome) {
        insertNotflixSyntheticGenreRows(
            mergedRows, fullRows, genreTitles, showTypeSuffix, movieLabel, seriesLabel
        )
    } else mergedRows
    // Restyle 3.3: the home is a strict whitelist — the 5 theme pairs, then
    // the 21 strip genres in tiles order (collections/folders pass through
    // first, untouched). Every other catalog row (M3U Movies/Series,
    // English HD Movies/Series, Récemment regardés, Calendrier, …) is
    // dropped: the same content already shows in the genre rows. Duplicates
    // of one (genre, type) collapse into a single merged row. Never for the
    // kids home (M66: its own row group only).
    if (kidsHome) return catalog.copy(rows = finalRows, focusAliases = mergedAliases)
    val (whitelistedRows, whitelistAliases) = notflixHomeWhitelist(
        finalRows, mergedAliases,
        catalogNameOf = { row -> names[row.loadKey]?.catalogName ?: row.catalog?.catalogName },
        typeOf = { row -> names[row.loadKey]?.apiType ?: row.catalog?.apiType },
        genreTitles = genreTitles
    )
    return catalog.copy(rows = whitelistedRows, focusAliases = whitelistAliases)
}

/**
 * Restyle 3.2: the five stock theme catalogs as the addons serve them in
 * French. The addon localizes its own catalog names (the request already
 * carries the app locale in Accept-Language — see NetworkModule — so this
 * is a local DISPLAY mapping, not a fetch change). Exact names only;
 * anything else passes through untouched. Returns the theme key shared
 * with the English tokens, so the French rows merge and title exactly
 * like their English twins: in a French UI the names come back unchanged
 * (titles.trending IS «Tendances actuelles»), in Arabic they translate.
 * Provisional wordings — the user may correct them.
 */
internal fun notflixFrenchThemeOf(name: String): String? = when (name.trim().lowercase()) {
    "tendances actuelles" -> "trending"
    "populaire en ce moment" -> "popular"
    "les mieux notés" -> "toprated"
    "nouveautés" -> "new"
    "sélection" -> "featured"
    else -> null
}

/** Restyle 3.3: the five stock theme catalogs, in the user's agreed home order. */
internal val NOTFLIX_HOME_THEME_ORDER = listOf("trending", "popular", "toprated", "new", "featured")

/**
 * Restyle 3.3: the theme key of a catalog name, shared by the theme merge
 * and the home whitelist so both classify a row the same way. French exact
 * names first («Tendances actuelles»…), then the English tokens. Anything
 * else returns null.
 */
internal fun notflixThemeKeyForCatalogName(name: String): String? {
    notflixFrenchThemeOf(name)?.let { return it }
    val tokens = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
    return when {
        "trending" in tokens -> "trending"
        "popular" in tokens -> "popular"
        ("top" in tokens && "rated" in tokens) || "toprated" in tokens -> "toprated"
        "featured" in tokens -> "featured"
        "new" in tokens -> "new"
        else -> null
    }
}

/**
 * Restyle 3.3: the strip genre a home row belongs to, if any. The catalog
 * name (from the placeholder descriptor while loading, so present even
 * for a pending row) must contain the genre's Arabic name — or its
 * localized name, for a synthetic row in a French UI. Title-only, as in
 * the synthesis coverage check: مزاج's loose match terms would
 * false-positive on «دراما». A custom title cannot smuggle a row in: the
 * match is on the catalog name, never the display title.
 */
internal fun notflixGenreTagForCatalogName(
    catalogName: String?,
    genreTitles: Map<String, String>?
): String? {
    if (catalogName == null) return null
    return NOTFLIX_GENRES.firstOrNull { genre ->
        catalogName.contains(genre.titleAr) ||
            (genreTitles?.get(genre.tag)?.takeIf { it != genre.titleAr }?.let { catalogName.contains(it) } == true)
    }?.tag
}

/**
 * Restyle 3.3: the dedupe/order type of a row. A synthetic row covering
 * both types (bare genre title, catalog id ends in "-mixed") is "mixed";
 * "tv" counts as series, as everywhere else.
 */
internal fun notflixRowTypeKey(row: NotflixCatalogRow, apiType: String?): String {
    val catalog = row.catalog
    if (catalog != null && catalog.addonId == "notflix" && catalog.catalogId.endsWith("-mixed")) return "mixed"
    return when (val t = apiType?.lowercase()) {
        "series", "tv" -> "series"
        "movie" -> "movie"
        null -> "unknown"
        else -> t
    }
}

private enum class NotflixHomeSlotKind { PASS_THROUGH, THEME, GENRE }

private data class NotflixHomeSlot(
    val row: NotflixCatalogRow,
    val kind: NotflixHomeSlotKind,
    /** Theme key or genre tag; null for pass-through rows. */
    val key: String?,
    /** movie / series / mixed / other / unknown. */
    val type: String
)

/**
 * Restyle 3.3: the home whitelist — the user's agreed home, nothing else.
 * A row survives only as (a) a collection/folder row, passed through first
 * in its original order (a pinned collection stays pinned), (b) one of the
 * 5 theme pairs, or (c) a row for one of the 21 strip genres. Every other
 * catalog row (M3U Movies/Series, English HD Movies/Series, Récemment
 * regardés, Calendrier, …) is dropped — the same content already shows in
 * the genre rows. The addons stay installed; only the home rows go.
 *
 * Order is canonical, not the addons' order: the 5 theme pairs in the
 * agreed order (movie before series), then the genre rows in tiles order
 * (movie, series, mixed). Duplicates of one (kind, key, type) — a catalog
 * row plus its synthetic twin, or the same genre from two addons — merge
 * into one row (items deduped by apiType:id), keeping the real catalog
 * row's key/title so focus stays stable; removed keys alias to the kept
 * one, like the theme merge.
 */
internal fun notflixHomeWhitelist(
    rows: List<NotflixCatalogRow>,
    previousAliases: Map<String, String>,
    catalogNameOf: (NotflixCatalogRow) -> String?,
    typeOf: (NotflixCatalogRow) -> String?,
    genreTitles: Map<String, String>?
): Pair<List<NotflixCatalogRow>, Map<String, String>> {
    val slots = rows.mapNotNull { row ->
        when {
            row.collection != null || row.folderTarget != null ->
                NotflixHomeSlot(row, NotflixHomeSlotKind.PASS_THROUGH, null, "other")
            else -> {
                val catalogName = catalogNameOf(row)
                val theme = catalogName?.let(::notflixThemeKeyForCatalogName)
                if (theme != null) {
                    NotflixHomeSlot(row, NotflixHomeSlotKind.THEME, theme, notflixRowTypeKey(row, typeOf(row)))
                } else {
                    val tag = notflixGenreTagForCatalogName(catalogName, genreTitles)
                    if (tag != null) {
                        NotflixHomeSlot(row, NotflixHomeSlotKind.GENRE, tag, notflixRowTypeKey(row, typeOf(row)))
                    } else null // Not on the agreed home: dropped.
                }
            }
        }
    }
    // Dedupe: one row per (kind, key, type). Pass-through rows are unique
    // by key, so they never merge.
    val removed = mutableMapOf<String, String>()
    val mergedSlots = slots.groupBy { slot ->
        when (slot.kind) {
            NotflixHomeSlotKind.PASS_THROUGH -> "pass:${slot.row.key}"
            else -> "${slot.kind}:${slot.key}:${slot.type}"
        }
    }.values.map { members ->
        val first = members.first()
        if (members.size == 1) return@map first
        // Keep the real catalog row when one exists (stable key across
        // snapshots); a synthetic twin never wins over it, so see-all
        // survives the merge.
        val kept = members.firstOrNull { it.row.catalog?.addonId != "notflix" && it.row.catalog != null }
            ?.row ?: first.row
        val seen = mutableSetOf<String>()
        val items = members.flatMap { it.row.catalog?.items.orEmpty() }
            .filter { item -> seen.add(notflixMetaKey(item)) }
        val bestCatalog = members.mapNotNull { it.row.catalog }
            .sortedWith(compareByDescending<CatalogRow> { it.addonId != "notflix" }.thenByDescending { it.items.size })
            .firstOrNull()
        val phase = if (members.any { it.row.phase == NotflixRowPhase.READY }) NotflixRowPhase.READY else kept.phase
        members.filter { it.row.key != kept.key }.forEach { removed[it.row.key] = kept.key }
        first.copy(row = kept.copy(catalog = bestCatalog?.copy(items = items), phase = phase))
    }
    val themeOrder = NOTFLIX_HOME_THEME_ORDER.withIndex().associate { it.value to it.index }
    val genreOrder = NOTFLIX_GENRES.withIndex().associate { it.value.tag to it.index }
    val typeOrder = mapOf("movie" to 0, "series" to 1, "mixed" to 2)
    val ordered = mergedSlots.sortedWith(
        compareBy<NotflixHomeSlot> { slot ->
            when (slot.kind) {
                NotflixHomeSlotKind.PASS_THROUGH -> 0
                NotflixHomeSlotKind.THEME -> 1
                NotflixHomeSlotKind.GENRE -> 2
            }
        }.thenBy { slot ->
            when (slot.kind) {
                NotflixHomeSlotKind.THEME -> themeOrder[slot.key] ?: Int.MAX_VALUE
                NotflixHomeSlotKind.GENRE -> genreOrder[slot.key] ?: Int.MAX_VALUE
                else -> 0
            }
        }.thenBy { typeOrder[it.type] ?: 3 }
    )
    val keys = ordered.map { it.row.key }.toSet()
    val aliases = (previousAliases + removed + removed.entries.associate { it.value to it.key })
        .filter { (from, to) -> from in keys || to in keys }.entries.toList().takeLast(24).associate { it.toPair() }
    return ordered.map { it.row } to aliases
}

/** Minimum titles for a synthesized genre row to be worth showing. */
private const val NOTFLIX_SYNTHETIC_ROW_MIN_ITEMS = 2
/** Items kept per synthesized row (home rows truncate at 24 anyway). */
private const val NOTFLIX_SYNTHETIC_ROW_MAX_ITEMS = 24
/** Items scanned per catalog when matching (bounds the matcher cost). */
private const val NOTFLIX_SYNTHETIC_POOL_PER_ROW = 30

/** A catalog row whose name names this genre (same rule as the genre browse screen). */
private fun notflixGenreRowTitleMatches(genre: NotflixGenre, catalogName: String): Boolean =
    catalogName.contains(genre.titleAr) ||
        (genre.matchTerms + genre.nameKeywords + genre.rowTitleTerms).any {
            catalogName.contains(it, ignoreCase = true)
        } ||
        catalogName.contains(genre.tag, ignoreCase = true)

/**
 * A catalog row that IS this genre's own row: its name is the genre's Arabic
 * name. Title-only — the looser browse-screen terms would false-positive
 * (مزاج matches Drama, so a «دراما» row would «cover» مزاج). Catalog names
 * are Arabic whatever the UI language, so this works in French too.
 */
private fun notflixGenreOwnRow(genre: NotflixGenre, catalogName: String): Boolean =
    catalogName.contains(genre.titleAr)

/**
 * Restyle 3.2: for every strip genre with no catalog row of its own,
 * synthesize home rows from the loaded catalogs — the same matcher the
 * genre screens and the live filter use (a row whose title names the genre
 * feeds it whole, except anime; otherwise items match one by one),
 * deduped by apiType:id. Per-type rows like the existing genre pairs
 * («مزاج - فيلم» / «مزاج - مسلسل»); when neither side reaches the minimum
 * but the genre has enough titles across both, one combined row with the
 * bare genre name. A genre with almost nothing in the catalogs gets no
 * row — honestly, rather than a one-tile row. Inserted in tiles order:
 * each row goes before the first genre row with a higher tile index, or
 * after the last genre row.
 */
internal fun insertNotflixSyntheticGenreRows(
    rows: List<NotflixCatalogRow>,
    fullRows: List<CatalogRow>,
    genreTitles: Map<String, String>,
    showTypeSuffix: Boolean,
    movieLabel: String,
    seriesLabel: String
): List<NotflixCatalogRow> {
    // Restyle 3.3: a PENDING row covers its genre too. Only 4 catalogs load
    // eagerly; the rest are lazy placeholders with catalog == null — the
    // 3.2 check (catalog name only) missed them, so a synthetic row was
    // created next to the placeholder and the genre showed TWICE on device
    // (دراما / أعمال مشوقة, 3.2 dumps r32-d12…d15). The display title is
    // already computed from the placeholder descriptor, so it names the
    // genre even before the catalog loads. The localized title covers a
    // French-UI row the same way. Title-only, as before: مزاج's loose
    // match terms would false-positive on «دراما».
    val coveredTags = NOTFLIX_GENRES.filter { genre ->
        val localized = genreTitles[genre.tag]
        rows.any { row ->
            notflixGenreOwnRow(genre, row.catalog?.catalogName.orEmpty()) ||
                notflixGenreOwnRow(genre, row.title) ||
                (localized != null && localized != genre.titleAr && row.title.contains(localized))
        }
    }.map { it.tag }.toSet()
    val missing = NOTFLIX_GENRES.filter { it.tag !in coveredTags }
    if (missing.isEmpty()) return rows
    // Candidate pool: loaded catalog items, channel rows excluded, deduped.
    // The per-row cap bounds the matcher cost on a TV CPU.
    val pool = LinkedHashMap<String, MetaPreview>()
    for (row in fullRows) {
        if (row.apiType.equals("channel", ignoreCase = true)) continue
        for (item in row.items.take(NOTFLIX_SYNTHETIC_POOL_PER_ROW)) {
            pool.putIfAbsent(notflixMetaKey(item), item)
        }
    }
    if (pool.isEmpty()) return rows
    // Rows whose title names a genre feed it whole (browse-screen rule);
    // precompute per missing genre so the pool scan stays a single pass.
    val wholeRowKeysByGenre = missing.associate { genre ->
        genre.tag to fullRows
            .filter { row -> genre.wholeRowOnTitleMatch && notflixGenreRowTitleMatches(genre, row.catalogName) }
            .flatMap { row -> row.items.map { notflixMetaKey(it) } }
            .toSet()
    }
    val isSeries: (MetaPreview) -> Boolean = {
        it.apiType.equals("series", ignoreCase = true) || it.apiType.equals("tv", ignoreCase = true)
    }
    fun syntheticRow(genre: NotflixGenre, typeSuffix: String, title: String, items: List<MetaPreview>): NotflixCatalogRow {
        val series = typeSuffix == "series" ||
            (typeSuffix == "mixed" && items.count(isSeries) > items.size / 2)
        val key = "notflix-genre-${genre.tag}-$typeSuffix"
        return NotflixCatalogRow(
            key = key,
            loadKey = key,
            title = title,
            phase = NotflixRowPhase.READY,
            catalog = CatalogRow(
                addonId = "notflix",
                addonName = "Notflix",
                addonBaseUrl = "",
                catalogId = "genre-${genre.tag}-$typeSuffix",
                catalogName = genreTitles[genre.tag] ?: genre.titleAr,
                type = if (series) ContentType.SERIES else ContentType.MOVIE,
                rawType = if (series) "series" else "movie",
                items = items,
                isLoading = false,
                hasMore = false
            )
        )
    }
    val tileIndexOf: (NotflixCatalogRow) -> Int? = { row ->
        val name = row.catalog?.catalogName.orEmpty()
        NOTFLIX_GENRES.indexOfFirst { genre -> notflixGenreOwnRow(genre, name) }
            .takeIf { it >= 0 }
    }
    val out = rows.toMutableList()
    for (genre in missing) {
        val genreTitle = genreTitles[genre.tag] ?: genre.titleAr
        val wholeKeys = wholeRowKeysByGenre[genre.tag].orEmpty()
        val matched = pool.values.filter { item ->
            notflixMetaKey(item) in wholeKeys || genre.matchesItem(item)
        }
        if (matched.size < NOTFLIX_SYNTHETIC_ROW_MIN_ITEMS) continue
        val movies = matched.filterNot(isSeries).take(NOTFLIX_SYNTHETIC_ROW_MAX_ITEMS)
        val series = matched.filter(isSeries).take(NOTFLIX_SYNTHETIC_ROW_MAX_ITEMS)
        val made = buildList {
            fun titled(label: String) =
                if (showTypeSuffix) "$genreTitle - $label" else genreTitle
            if (movies.size >= NOTFLIX_SYNTHETIC_ROW_MIN_ITEMS) {
                add(syntheticRow(genre, "movie", titled(movieLabel), movies))
            }
            if (series.size >= NOTFLIX_SYNTHETIC_ROW_MIN_ITEMS) {
                add(syntheticRow(genre, "series", titled(seriesLabel), series))
            }
            if (isEmpty()) {
                // Too thin split across both types, but enough together.
                add(syntheticRow(genre, "mixed", genreTitle,
                    matched.take(NOTFLIX_SYNTHETIC_ROW_MAX_ITEMS)))
            }
        }
        // Tiles order: before the first genre row with a higher tile index,
        // else after the last genre row, else at the end.
        val myIndex = NOTFLIX_GENRES.indexOf(genre)
        val at = out.indexOfFirst { row ->
            val other = tileIndexOf(row)
            other != null && other > myIndex
        }.takeIf { it >= 0 } ?: run {
            val lastGenre = out.indexOfLast { tileIndexOf(it) != null }
            if (lastGenre >= 0) lastGenre + 1 else out.size
        }
        out.addAll(at, made)
    }
    return out
}

/**
 * Restyle: Netflix-style titles for the stock theme rows, in the app
 * language ([titles], resolved from resources; null falls back to the
 * Arabic literals). Only the theme word is mapped; a movies/series hint
 * is kept when the catalog name carries one, so «Trending Movies» and
 * «Trending Series» stay distinct. Anything unmapped returns null and
 * keeps its original name.
 */
internal fun notflixNetflixTitleForCatalog(name: String, titles: NotflixThemeTitles? = null): String? {
    // Restyle 3.2: the French names the addons serve («Tendances actuelles»…)
    // map to the same theme titles as their English twins — in a French UI
    // they come back unchanged, in Arabic they translate. French names carry
    // no movie/series hint; the type suffix handles that.
    notflixFrenchThemeOf(name)?.let { theme ->
        return when (theme) {
            "trending" -> titles?.trending ?: "الأكثر رواجاً الآن"
            "popular" -> titles?.popular ?: "رائج الآن"
            "toprated" -> titles?.topRated ?: "الأعلى تقييماً"
            "new" -> titles?.new ?: "جديد"
            "featured" -> titles?.featured ?: "مختارات"
            else -> null
        }
    }
    val tokens = name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toSet()
    val theme = when {
        "trending" in tokens -> titles?.trending ?: "الأكثر رواجاً الآن"
        "popular" in tokens -> titles?.popular ?: "رائج الآن"
        ("top" in tokens && "rated" in tokens) || "toprated" in tokens -> titles?.topRated ?: "الأعلى تقييماً"
        "featured" in tokens -> titles?.featured ?: "مختارات"
        "new" in tokens -> titles?.new ?: "جديد"
        "watchlist" in tokens -> titles?.watchlist ?: "قائمة المشاهدة"
        "calendar" in tokens -> titles?.calendar ?: "التقويم"
        ("last" in tokens && "videos" in tokens) || "recently" in tokens -> titles?.recentlyWatched ?: "شوهد مؤخراً"
        else -> return null
    }
    val typeHint = when {
        tokens.intersect(setOf("movie", "movies", "film", "films")).isNotEmpty() -> titles?.hintMovies ?: " · أفلام"
        tokens.intersect(setOf("series", "tv", "show", "shows")).isNotEmpty() -> titles?.hintSeries ?: " · مسلسلات"
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
        else row.catalog?.let { catalog ->
            notflixThemeKeyForCatalogName(catalog.catalogName)?.let { "$it:${catalog.apiType.lowercase()}" }
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
