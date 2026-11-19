package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.Collection
import com.nuvio.tv.domain.model.CollectionFolder
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import com.nuvio.tv.domain.model.legacyKey
import com.nuvio.tv.domain.model.stableKey
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

/**
 * Restyle 3.3 (2026-11-19): the home is a strict whitelist — the 5 theme
 * pairs in the agreed order, then the 21 strip genres in tiles order, and
 * nothing else. Unlisted addon rows (M3U, English HD, Récemment regardés,
 * Calendrier) leave the home; duplicates of one (genre, type) merge into
 * a single row; a PENDING row covers its genre, so no synthetic twin is
 * created next to it; يُعرض قريبًا renders last; «لأنك شاهدت» never renders.
 */
class NotflixRestyle33Test {

    private fun item(
        id: String,
        type: ContentType = ContentType.MOVIE,
        name: String = "Title $id",
        genres: List<String> = emptyList()
    ) = MetaPreview(
        id = id, type = type, name = name, poster = null,
        posterShape = PosterShape.POSTER, background = null, logo = null,
        description = null, releaseInfo = null, imdbRating = null,
        genres = genres, released = null
    )

    private fun catalog(
        addon: String = "a",
        name: String,
        type: ContentType = ContentType.MOVIE,
        items: List<MetaPreview> = emptyList()
    ) = CatalogRow(addon, "Fixture $addon", "https://$addon.invalid", "top", name, type, items = items)

    private fun rowFor(data: CatalogRow, phase: NotflixRowPhase = NotflixRowPhase.READY) =
        NotflixCatalogRow(data.stableKey(), data.legacyKey(), data.catalogName, phase, data)

    private fun descriptor(row: NotflixCatalogRow) =
        HomeViewModel.PlaceholderDescriptor(
            row.loadKey, row.catalog!!.addonId, row.catalog!!.addonName,
            row.catalog!!.addonBaseUrl, row.catalog!!.catalogId,
            row.catalog!!.catalogName, row.catalog!!.apiType, row.title
        )

    private fun project(rows: List<NotflixCatalogRow>, fullRows: List<CatalogRow>,
        descriptors: List<HomeViewModel.PlaceholderDescriptor>,
        collections: List<Collection> = emptyList(),
        genreTitles: Map<String, String>? = null) =
        projectNotflixCatalogs(
            NotflixCatalogSnapshot(1, 1L, true, rows),
            rows.map { it.loadKey } + collections.map { "collection_${it.id}" },
            fullRows, collections, emptySet(), emptyMap(), descriptors,
            showTypeSuffix = true, movieLabel = "فيلم", seriesLabel = "مسلسل",
            genreTitles = genreTitles
        )

    // ---- §1/§2: the whitelist — order, and what leaves the home ----

    @Test fun whitelistDropsUnlistedAddonRowsAndOrdersThemesThenGenres() {
        // Distinct addons: same-type catalogs from one addon would share a legacy key.
        val trending = catalog(addon = "a", name = "Tendances actuelles", items = listOf(item("t1")))
        val popularSeries = catalog(addon = "b", name = "Populaire en ce moment",
            type = ContentType.SERIES, items = listOf(item("p1", ContentType.SERIES)))
        val newMovies = catalog(addon = "c", name = "Nouveautés", items = listOf(item("n1")))
        val drama = catalog(addon = "d", name = "دراما", items = listOf(item("d1")))
        val comedySeries = catalog(addon = "e", name = "كوميديا",
            type = ContentType.SERIES, items = listOf(item("c1", ContentType.SERIES)))
        val m3uMovies = catalog(addon = "f", name = "M3U Movies", items = listOf(item("m1")))
        val englishMovies = catalog(addon = "g", name = "English HD Movies", items = listOf(item("e1")))
        val recently = catalog(addon = "h", name = "Récemment regardés",
            type = ContentType.SERIES, items = listOf(item("r1", ContentType.SERIES)))
        val calendar = catalog(addon = "i", name = "Calendrier",
            type = ContentType.SERIES, items = listOf(item("k1", ContentType.SERIES)))
        val live = catalog(addon = "j", name = "Live TV",
            type = ContentType.CHANNEL, items = listOf(item("l1", ContentType.CHANNEL)))
        val m3uSeries = catalog(addon = "k", name = "M3U Series",
            type = ContentType.SERIES, items = listOf(item("s1", ContentType.SERIES)))
        // Deliberately scrambled input order.
        val all = listOf(drama, m3uMovies, trending, englishMovies, popularSeries,
            calendar, newMovies, comedySeries, recently, live, m3uSeries)
        val rows = all.map(::rowFor)
        val result = project(rows, all, rows.map(::descriptor))
        assertEquals(
            listOf(
                "الأكثر رواجاً الآن - فيلم",
                "رائج الآن - مسلسل",
                "جديد - فيلم",
                "كوميديا - مسلسل",
                "دراما - فيلم"
            ),
            result.rows.map { it.title }
        )
    }

    @Test fun whitelistMergesDuplicateGenreRows() {
        val first = catalog(addon = "a", name = "دراما", items = listOf(item("d1")))
        val second = catalog(addon = "b", name = "دراما", items = listOf(item("d2")))
        val rows = listOf(first, second).map(::rowFor)
        val result = project(rows, listOf(first, second), rows.map(::descriptor))
        assertEquals(listOf("دراما - فيلم"), result.rows.map { it.title })
        assertEquals(listOf("d1", "d2"), result.rows.single().catalog!!.items.map { it.id })
    }

    @Test fun collectionRowsPassThroughFirst() {
        val trending = catalog(addon = "a", name = "Tendances actuelles", items = listOf(item("t1")))
        val rows = listOf(rowFor(trending))
        val collection = Collection(id = "c1", title = "My Collection",
            folders = listOf(CollectionFolder(id = "f1", title = "Folder")))
        val result = project(rows, listOf(trending), rows.map(::descriptor),
            collections = listOf(collection))
        assertEquals(listOf("My Collection", "الأكثر رواجاً الآن - فيلم"),
            result.rows.map { it.title })
    }

    // ---- §3: a pending row covers its genre (the 3.2 duplicate root cause) ----

    @Test fun pendingGenreRowCoversGenreSoNoSyntheticRow() {
        // Thriller items match ONLY أعمال مشوقة: "Drama" would also match
        // مزاج (loose terms), which would synthesize its own rows here.
        val pool = listOf(
            catalog(name = "Popular", type = ContentType.MOVIE, items = listOf(
                item("tm1", genres = listOf("Thriller")), item("tm2", genres = listOf("Thriller"))
            )),
            catalog(name = "Popular", type = ContentType.SERIES, items = listOf(
                item("ts1", ContentType.SERIES, genres = listOf("Thriller")),
                item("ts2", ContentType.SERIES, genres = listOf("Thriller"))
            ))
        )
        // Control: without any row, the pool WOULD synthesize the pair.
        val control = insertNotflixSyntheticGenreRows(emptyList(), pool, mapOf("thrilling" to "أعمال مشوقة"),
            showTypeSuffix = true, movieLabel = "فيلم", seriesLabel = "مسلسل")
        assertEquals(listOf("أعمال مشوقة - فيلم", "أعمال مشوقة - مسلسل"), control.map { it.title })
        // A pending catalog row (catalog == null, display title from the
        // placeholder descriptor) covers the genre: no synthetic twin.
        val pending = rowFor(catalog(name = "أعمال مشوقة"), NotflixRowPhase.PENDING)
            .copy(catalog = null, title = "أعمال مشوقة - فيلم")
        val out = insertNotflixSyntheticGenreRows(listOf(pending), pool, mapOf("thrilling" to "أعمال مشوقة"),
            showTypeSuffix = true, movieLabel = "فيلم", seriesLabel = "مسلسل")
        assertEquals(listOf("أعمال مشوقة - فيلم"), out.map { it.title })
    }

    @Test fun pendingThemeRowIsKeptByWhitelist() {
        val live = catalog(name = "Tendances actuelles", items = listOf(item("t1")))
        val pending = rowFor(live, NotflixRowPhase.PENDING).copy(catalog = null)
        val result = projectNotflixCatalogs(
            NotflixCatalogSnapshot(1, 1L, true, listOf(pending)),
            listOf(pending.loadKey), emptyList(), emptyList(), emptySet(),
            emptyMap(), listOf(descriptor(rowFor(live))), showTypeSuffix = true,
            movieLabel = "فيلم", seriesLabel = "مسلسل"
        )
        assertEquals(listOf("الأكثر رواجاً الآن - فيلم"), result.rows.map { it.title })
    }

    // ---- notflixRows: يُعرض قريبًا last, «لأنك شاهدت» never ----

    private fun displayState(rows: List<NotflixCatalogRow>,
        upcoming: List<ContinueWatchingItem> = emptyList()) =
        HomeUiState(
            notflixCatalog = NotflixCatalogSnapshot(configured = true, rows = rows),
            upcomingItems = upcoming
        )

    @Test fun becauseRowNeverRenders() {
        val drama = rowFor(catalog(name = "دراما", items = listOf(item("d1"))))
        val because = rowFor(catalog(addon = "b", name = "لأنك شاهدت X", items = listOf(item("x1"))))
        val rows = notflixRows(
            state = displayState(listOf(drama)),
            keepPendingCw = false, cwTitle = "متابعة المشاهدة",
            episodePattern = "S%1\$s:E%2\$s", seeAll = "عرض الكل",
            becauseRow = because
        )
        val titles = rows.map { it.title }
        assertTrue(titles.contains("دراما"))
        assertFalse(titles.any { it.contains("لأنك شاهدت") })
    }

    @Test fun airingRowRendersLastOnlyWhenItHasContent() {
        val drama = rowFor(catalog(name = "دراما", items = listOf(item("d1"))))
        // No upcoming episodes: no airing row at all.
        val without = notflixRows(
            state = displayState(listOf(drama)),
            keepPendingCw = false, cwTitle = "متابعة المشاهدة",
            episodePattern = "S%1\$s:E%2\$s", seeAll = "عرض الكل",
            airingTitle = "يُعرض قريبًا"
        )
        assertFalse(without.map { it.title }.contains("يُعرض قريبًا"))
        // One episode airing tomorrow: the airing row renders LAST.
        val nextUp = ContinueWatchingItem.NextUp(
            NextUpInfo(
                contentId = "tt1", contentType = "series", name = "Series tt1",
                poster = null, backdrop = null, logo = null, videoId = "tt1:1:1",
                season = 1, episode = 1, episodeTitle = "Pilot", thumbnail = null,
                hasAired = false, lastWatched = 0L, sortTimestamp = 0L,
                releaseTimestamp = System.currentTimeMillis() + 86_400_000L
            )
        )
        val with = notflixRows(
            state = displayState(listOf(drama), upcoming = listOf(nextUp)),
            keepPendingCw = false, cwTitle = "متابعة المشاهدة",
            episodePattern = "S%1\$s:E%2\$s", seeAll = "عرض الكل",
            airingTitle = "يُعرض قريبًا", airingNow = Instant.now()
        )
        assertEquals(listOf("التصنيفات", "دراما", "يُعرض قريبًا"), with.map { it.title })
    }
}
