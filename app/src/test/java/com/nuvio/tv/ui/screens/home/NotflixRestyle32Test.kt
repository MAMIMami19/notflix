package com.nuvio.tv.ui.screens.home

import com.nuvio.tv.domain.model.CatalogRow
import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import org.junit.Assert.*
import org.junit.Test

/**
 * Restyle 3.2 (2026-11-19): the pure functions behind the 10 items —
 * French catalog-name mapping, channel-row filtering, synthetic genre
 * rows, the tab-hero fallback, and the «الجديد» tab builder.
 */
class NotflixRestyle32Test {

    private fun item(
        id: String,
        type: ContentType = ContentType.MOVIE,
        name: String = "Title $id",
        genres: List<String> = emptyList(),
        background: String? = null,
        poster: String? = null,
        rating: Float? = null,
        released: String? = null,
        description: String? = null
    ) = MetaPreview(
        id = id, type = type, name = name, poster = poster,
        posterShape = PosterShape.POSTER, background = background, logo = null,
        description = description, releaseInfo = released, imdbRating = rating,
        genres = genres, released = released
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

    private val arabicThemes = NotflixThemeTitles(
        trending = "الأكثر رواجاً الآن", popular = "رائج الآن",
        topRated = "الأعلى تقييماً", featured = "مختارات", new = "جديد",
        watchlist = "قائمتي", calendar = "التقويم",
        recentlyWatched = "شاهدتها مؤخراً", hintMovies = "أفلام", hintSeries = "مسلسلات"
    )
    private val frenchThemes = NotflixThemeTitles(
        trending = "Tendances actuelles", popular = "Populaire en ce moment",
        topRated = "Les mieux notés", featured = "Sélection", new = "Nouveautés",
        watchlist = "Ma liste", calendar = "Calendrier",
        recentlyWatched = "Récemment regardés", hintMovies = "Films", hintSeries = "Séries"
    )

    // ---- Item 3: French catalog names -> app-language theme titles ----

    @Test fun frenchThemeNamesMapToTheirEnglishTwins() {
        assertEquals("trending", notflixFrenchThemeOf("Tendances actuelles"))
        assertEquals("popular", notflixFrenchThemeOf("Populaire en ce moment"))
        assertEquals("toprated", notflixFrenchThemeOf("Les mieux notés"))
        assertEquals("new", notflixFrenchThemeOf("Nouveautés"))
        assertEquals("featured", notflixFrenchThemeOf("Sélection"))
        assertEquals("trending", notflixFrenchThemeOf("  TENDANCES ACTUELLES "))
        assertNull(notflixFrenchThemeOf("Comédie"))
        assertNull(notflixFrenchThemeOf("English HD Movies"))
    }

    @Test fun frenchCatalogNamesLocalizeToArabicTitles() {
        assertEquals("الأكثر رواجاً الآن", notflixNetflixTitleForCatalog("Tendances actuelles", arabicThemes))
        assertEquals("رائج الآن", notflixNetflixTitleForCatalog("Populaire en ce moment", arabicThemes))
        assertEquals("الأعلى تقييماً", notflixNetflixTitleForCatalog("Les mieux notés", arabicThemes))
        assertEquals("جديد", notflixNetflixTitleForCatalog("Nouveautés", arabicThemes))
        assertEquals("مختارات", notflixNetflixTitleForCatalog("Sélection", arabicThemes))
    }

    @Test fun frenchCatalogNamesStayFrenchInFrenchUi() {
        assertEquals("Tendances actuelles", notflixNetflixTitleForCatalog("Tendances actuelles", frenchThemes))
        assertEquals("Populaire en ce moment", notflixNetflixTitleForCatalog("Populaire en ce moment", frenchThemes))
        assertEquals("Nouveautés", notflixNetflixTitleForCatalog("Nouveautés", frenchThemes))
    }

    @Test fun frenchCatalogNamesFallBackToArabicWithoutTitles() {
        assertEquals("الأكثر رواجاً الآن", notflixNetflixTitleForCatalog("Tendances actuelles", null))
        assertEquals("جديد", notflixNetflixTitleForCatalog("Nouveautés", null))
    }

    // ---- Item 1: Channel rows never render on home ----

    @Test fun projectFiltersChannelRowsAndUnlistedAddonRows() {
        val live = catalog(name = "Live TV", type = ContentType.CHANNEL, items = listOf(item("c1", ContentType.CHANNEL)))
        val movies = catalog(name = "M3U Movies", type = ContentType.MOVIE, items = listOf(item("m1")))
        val series = catalog(name = "M3U Series", type = ContentType.SERIES, items = listOf(item("s1", ContentType.SERIES)))
        // Restyle 3.3: unlisted addon rows leave the home (their addons stay
        // installed); a whitelisted genre row survives. Distinct addon for
        // the drama row: same-type catalogs from one addon share a legacy key.
        val drama = catalog(addon = "b", name = "دراما", type = ContentType.MOVIE, items = listOf(item("d1")))
        val rows = listOf(live, movies, series, drama).map(::rowFor)
        val result = projectNotflixCatalogs(
            NotflixCatalogSnapshot(1, 1L, true, rows),
            rows.map { it.loadKey }, listOf(live, movies, series, drama), emptyList(), emptySet(),
            emptyMap(), rows.map(::descriptor), showTypeSuffix = true,
            movieLabel = "فيلم", seriesLabel = "مسلسل"
        )
        val titles = result.rows.map { it.title }
        assertFalse(titles.any { it.contains("Live TV") })
        assertFalse(titles.any { it.contains("M3U Movies") })
        assertFalse(titles.any { it.contains("M3U Series") })
        assertTrue(titles.any { it.contains("دراما") })
    }

    @Test fun channelPlaceholderWithoutPayloadIsFilteredToo() {
        val live = catalog(name = "Live TV", type = ContentType.CHANNEL)
        val pending = rowFor(live, NotflixRowPhase.PENDING).copy(catalog = null)
        val result = projectNotflixCatalogs(
            NotflixCatalogSnapshot(1, 1L, true, listOf(pending)),
            listOf(pending.loadKey), emptyList(), emptyList(), emptySet(),
            emptyMap(), listOf(descriptor(rowFor(live))), showTypeSuffix = true,
            movieLabel = "فيلم", seriesLabel = "مسلسل"
        )
        assertTrue(result.rows.isEmpty())
    }

    // ---- Item 5: synthetic rows for the 7 genres with no home row ----

    @Test fun missingGenreGetsMovieAndSeriesRowsInTilesOrder() {
        val pool = listOf(
            catalog(name = "Popular", type = ContentType.MOVIE, items = listOf(
                item("fm1", genres = listOf("Fantasy")), item("fm2", genres = listOf("Fantasy"))
            )),
            catalog(name = "Popular", type = ContentType.SERIES, items = listOf(
                item("fs1", ContentType.SERIES, genres = listOf("Fantasy")),
                item("fs2", ContentType.SERIES, genres = listOf("Fantasy"))
            ))
        )
        // Comedy (tile index 1) sits before fantasy (2), romance (7) after.
        val rows = listOf(
            rowFor(catalog(name = "كوميديا", items = listOf(item("c1", genres = listOf("Comedy"))))),
            rowFor(catalog(name = "رومانسي", items = listOf(item("r1", genres = listOf("Romance")))))
        )
        val out = insertNotflixSyntheticGenreRows(rows, pool, mapOf("fantasy" to "فانتازيا"),
            showTypeSuffix = true, movieLabel = "فيلم", seriesLabel = "مسلسل")
        val titles = out.map { it.title }
        assertEquals(listOf("كوميديا", "فانتازيا - فيلم", "فانتازيا - مسلسل", "رومانسي"), titles)
        val fantasyMovies = out.first { it.title == "فانتازيا - فيلم" }
        assertEquals(listOf("fm1", "fm2"), fantasyMovies.catalog!!.items.map { it.id })
        assertEquals("notflix", fantasyMovies.catalog!!.addonId)
    }

    @Test fun coveredGenreGetsNoSyntheticRow() {
        val pool = listOf(catalog(name = "Popular", items = listOf(
            item("fm1", genres = listOf("Fantasy")), item("fm2", genres = listOf("Fantasy"))
        )))
        val rows = listOf(rowFor(catalog(name = "فانتازيا", items = listOf(item("x1")))))
        val out = insertNotflixSyntheticGenreRows(rows, pool, mapOf("fantasy" to "فانتازيا"),
            showTypeSuffix = true, movieLabel = "فيلم", seriesLabel = "مسلسل")
        assertEquals(listOf("فانتازيا"), out.map { it.title })
    }

    @Test fun thinGenreGetsNoRowButMixedFallbackCoversSplitCounts() {
        // One title only: nothing to show.
        val thin = listOf(catalog(name = "Popular", items = listOf(
            item("fm1", genres = listOf("Fantasy"))
        )))
        val noRows = insertNotflixSyntheticGenreRows(emptyList(), thin, mapOf("fantasy" to "فانتازيا"),
            showTypeSuffix = true, movieLabel = "فيلم", seriesLabel = "مسلسل")
        assertTrue(noRows.isEmpty())
        // One movie + one series: too thin per type, enough together — one
        // combined row with the bare genre title.
        val split = listOf(
            catalog(name = "Popular", type = ContentType.MOVIE, items = listOf(
                item("fm1", genres = listOf("Fantasy"))
            )),
            catalog(name = "Popular", type = ContentType.SERIES, items = listOf(
                item("fs1", ContentType.SERIES, genres = listOf("Fantasy"))
            ))
        )
        val mixed = insertNotflixSyntheticGenreRows(emptyList(), split, mapOf("fantasy" to "فانتازيا"),
            showTypeSuffix = true, movieLabel = "فيلم", seriesLabel = "مسلسل")
        assertEquals(listOf("فانتازيا"), mixed.map { it.title })
        assertEquals(2, mixed.single().catalog!!.items.size)
    }

    // ---- Item 4: the tab hero never stays black ----

    @Test fun browseHeroFallsBackToBestRatedWhenNothingHasArt() {
        val items = listOf(
            item("a", rating = 5.0f),
            item("b", rating = 8.5f),
            item("c", rating = 7.0f)
        )
        assertEquals("b", notflixBrowseHero(items)?.id)
        assertNull(notflixBrowseHero(emptyList()))
        // Art still wins over a higher rating without art.
        val withArt = items + item("d", background = "https://x.invalid/b.jpg", rating = 4.0f)
        assertEquals("d", notflixBrowseHero(withArt)?.id)
    }

    // ---- Item 10: the «الجديد» tab ----

    @Test fun newCatalogDetectionCoversEnglishFrenchAndArabic() {
        assertTrue(notflixNewCatalogRow("Nouveautés"))
        assertTrue(notflixNewCatalogRow("New Releases"))
        assertTrue(notflixNewCatalogRow("Latest movies"))
        assertTrue(notflixNewCatalogRow("أفلام جديدة"))
        assertFalse(notflixNewCatalogRow("Tendances actuelles"))
        assertFalse(notflixNewCatalogRow("English HD Movies"))
        assertFalse(notflixNewCatalogRow("Calendrier"))
    }

    @Test fun newTabBuildsCatalogRowsFreshRowsAndHero() {
        val year = java.time.Year.now().value
        val freshMovie = item("n1", released = "$year-03-01", rating = 7.5f,
            background = "https://x.invalid/n1.jpg")
        val freshMovies = listOf(
            freshMovie,
            item("n2", released = "$year-01-15", rating = 6.0f),
            item("n3", released = "${year - 1}-11-20", rating = 8.0f)
        )
        val newCatalog = catalog(name = "Nouveautés", type = ContentType.MOVIE, items = freshMovies)
        val oldCatalog = catalog(name = "Popular", type = ContentType.MOVIE, items = listOf(
            item("o1", released = "1999-06-01", rating = 9.0f)
        ))
        val (hero, rows) = notflixNewTabRows(
            listOf(newCatalog, oldCatalog),
            newMoviesTitle = "أفلام جديدة", newSeriesTitle = "مسلسلات جديدة",
            newWorthyTitle = "الجديد والجدير بالمشاهدة",
            themeTitles = arabicThemes, movieLabel = "فيلم", seriesLabel = "مسلسل",
            showTypeSuffix = true
        )
        // The hero is the best-rated FRESH title with art — not the 9.0 old one.
        assertEquals("n1", hero?.id)
        val titles = rows.map { it.title }
        assertTrue("catalog row: $titles", titles.contains("جديد - فيلم"))
        assertTrue("fresh movies row: $titles", titles.contains("أفلام جديدة"))
        assertFalse("no series row without fresh series", titles.contains("مسلسلات جديدة"))
        // The new-&-worthy row needs a fresh title rated >= 6: n1/n2/n3 qualify.
        assertTrue("worthy row: $titles", titles.contains("الجديد والجدير بالمشاهدة"))
    }

    @Test fun newTabSkipsChannelRowsEntirely() {
        val live = catalog(name = "Live TV", type = ContentType.CHANNEL, items = listOf(
            item("c1", ContentType.CHANNEL, released = "${java.time.Year.now().value}-01-01")
        ))
        val (hero, rows) = notflixNewTabRows(
            listOf(live),
            newMoviesTitle = "أفلام جديدة", newSeriesTitle = "مسلسلات جديدة",
            newWorthyTitle = "الجديد والجدير بالمشاهدة",
            themeTitles = arabicThemes, movieLabel = "فيلم", seriesLabel = "مسلسل",
            showTypeSuffix = true
        )
        assertNull(hero)
        assertTrue(rows.isEmpty())
    }
}
