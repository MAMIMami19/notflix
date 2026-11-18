# Notflix stream-panel + home-rows research (2026-11-14)

Source: DaoeZ/notflix @ `7495568` (1.0.0-beta.4.1, 2026-11-13) — same version as installed on the box.
Stack: native Android (Kotlin + Jetpack Compose), package `com.notflix.tv`, namespace `com.nuvio.tv`, versionCode 1076. No GitHub Actions workflows in repo.

## 1. How the streams panel is built (PRIORITY finding)

Entry: `ui/screens/stream/StreamScreenViewModel.kt` — `loadStreams()` → `StreamRepository.getStreamsFromAllAddons(type, videoId, season, episode)`.

`data/repository/StreamRepositoryImpl.kt`:
- `captureSourceConfiguration()` — takes ALL enabled installed addons (`addonRepository.getInstalledAddons().first().enabledAddons()`), plus enabled local scrapers (Nuvio plugins) and media-server sources.
- `fetchStreamsFromAllSources()` — filters addons by `addon.supportsStreamResource(type, videoId)` (`core/streams/PlaybackAvailability.kt`): the addon's manifest must declare a `stream` resource for this type AND an `idPrefixes` entry matching the videoId (or no prefixes). Each qualifying addon is queried **in parallel** with the SAME raw videoId: `GET {baseUrl}/stream/{type}/{videoId}.json`.
- Results stream in via a Channel (arrival order) and are grouped per addon as `AddonStreams(addonName, streams)`.
- **Fallback — inline streams from meta:** if an addon's stream endpoint returns EMPTY, `fetchInlineStreamsFromMeta()` fetches `meta/{type}/{metaId}.json` from the SAME addon and takes `videos[]` matching the videoId that carry inline `streams`. This is how a catalog addon (e.g. M3U Mux) can supply a playable stream for a title even when its stream endpoint has nothing for that id.
- **No title-matching, no id translation for Stremio addons.** The videoId passed to every addon is the title's own id, untouched. Comet/MediaFusion only answer IMDb (`tt…`) ids (their manifests declare `stream` with `tt` prefixes); for an M3U-native id they are not even queried (`supportsStreamResource` = false). Conversely an M3U provider's stream only appears for a title if its stream endpoint or its meta (inline-streams fallback) resolves that same id.
- **Plugins (mo7AmMeD64 scrapers):** resolved via TMDB — `tmdbService.ensureTmdbId(videoId, type)` (`core/tmdb/TmdbService.kt:224`) only converts `tt…` or numeric ids; anything else returns null (no fallbackImdbId is passed here), and non-tt ids can only run plugins for `kitsu:`/`anilist:`/`mal:` ids. So local scrapers contribute streams only for tt-id titles.
- **Dedup:** `mergeStreams()` uses `Stream.dedupKey()` (infoHash+fileIdx, else URL, else addonName:name:title) — but ONLY when merging results within the SAME addonName group. There is NO cross-addon dedup anywhere (the only `distinctBy` in StreamScreenViewModel is for subtitles). The same torrent from Comet and MediaFusion shows twice, once per addon.

## 2. Stream-panel ORDER — what controls it

`core/player/StreamAutoPlaySelector.kt` — `orderAddonStreams(streams, installedOrder, preferredNames)` is the single ordering function, used by the picker (`StreamScreenViewModel.kt:556,851`) AND in-player source switching (`PlayerRuntimeControllerStreams.kt`, 5 call sites). Order:
1. `preferredNames` (media-server preferred sources only — not addons)
2. Groups containing a direct-debrid stream (`Stream.isDirectDebrid()` = Nuvio client-side debrid resolve, cached — NOT PikPak links from MediaFusion; those are plain http URLs)
3. **Installed addons, in installed order** (`installedAddons.map { it.displayName }`)
4. Plugin (local scraper) groups last

The panel UI (`ui/screens/stream/StreamScreen.kt`) shows a FLAT list (`allStreams = mergedAddonStreams.flatMap { it.streams }`) with per-addon filter chips («الكل» = all). So group order = visual order.

**Installed order is user-controllable, no rebuild:** `data/local/AddonPreferences.kt` persists an ordered URL list (`installed_addon_urls_ordered`); `ui/screens/addon/AddonManagerViewModel.kt` `moveAddonUp/Down` → `addonRepository.setAddonOrder(...)`; `AddonManagerScreen.kt` exposes ↑/↓ per addon (hidden only in ESSENTIAL experience mode). New addons append at the end. → To get "addon streams first, M3U after": move M3U Mux to the BOTTOM of the installed list (Comet/MediaFusion above it). The autoplay priority list (`StreamAutoPlayPriority`, README "priority list") only affects AUTO-SELECTION, not panel order.

## 3. Vevo vs Yahoo — what the panel is missing

Yahoo (10 addons) panel showed all providers together because every stream-capable addon installed answered the same tt id. Vevo (4 addons) shows MediaFusion only because it is the only installed addon (besides M3U Mux, whose stream only appears when its own meta/stream resolves the id) that returns streams for tt ids. Missing stream contributors vs Yahoo: **Comet** (torrent streams for tt ids). OpenSubtitles v3 / SubSource are subtitles-only (no `stream` resource). Cinemeta / Nuvio Catalog / Showmax+ Sections / Bingecat AI / Anime Collection are catalog/meta-only (no `stream` resource) — they never contribute streams. mo7AmMeD64 plugins contribute via the TMDB plugin path (last group). AIOStreams was never in either Notflix account. → Installing Comet (+ optionally AIOStreams) in the Vevo account restores the multi-provider panel; no code change needed. M3U Mux's own stream for a given title depends on its server resolving that id (stream endpoint or meta inline fallback).

## 4. Home rows (secondary)

- One row per catalog: `ui/screens/home/HomeViewModelCatalogPipeline.kt` `loadAllCatalogsPipeline()` — `addons.flatMap { addon.catalogs.filter { shouldShowOnHome() && !disabled } }`; row key = `catalogKey(addonId, type, catalogId)`. Lazy loading: first N eager, rest placeholders.
- Row title = `displayTitle()` (same file ~L302): custom title if set, else `catalog.name`, plus a type suffix ` - Film/Series` when `catalogTypeSuffixEnabled` (default true). So "M3U Movies - Film" = manifest name + suffix.
- **Suffix can be switched off in-app, no rebuild:** Settings → Layout (واجهة العرض) → «إظهار نوع القوائم» / "Show Catalog Type" (`ui/screens/settings/LayoutHomeSettings.kt:160`, `LayoutSettingsViewModel.kt:660`, `LayoutPreferenceDataStore.setCatalogTypeSuffixEnabled`). Also «إظهار اسم الإضافة» (addon name under titles, non-Modern layouts only).
- **Custom row titles exist in the data layer but have NO in-app UI:** `LayoutPreferenceDataStore.customCatalogTitles` (key `custom_catalog_titles`, per-profile, synced via Nuvio account sync — `HomeCatalogSettingsSyncService`, `custom_title` field). `setCustomCatalogTitles()` has zero in-app callers; titles can only arrive via a sync payload from another client. The Catalog Order screen («إعادة ترتيب القوائم الرئيسية», `ui/screens/addon/CatalogOrderScreen.kt` + `CatalogOrderViewModel.kt`) supports reorder + hide rows, but NOT rename.
- Row order: `homeCatalogOrderKeys` (saved) or follow-addon-order mode; hide via `disabledHomeCatalogKeys`.
- Continue Watching: `ui/screens/home/HomeViewModelContinueWatching.kt` — `loadContinueWatchingPipeline()`, `removeContinueWatchingPipeline()` (long-press → إزالة works, as used on-box).
- Hero: `ui/screens/home/ModernHomeHero.kt` + `NotflixBillboard.kt`; hero catalogs selectable independently of home rows (hero-only catalogs load even if disabled from rows).
- Merging catalogs into unified type rows would mean changing the pipeline to group by type + merge items + dedup (dedup key available: TMDB id via enrichment, or title+year) — medium effort, needs a custom build (see §5). The Netflix-shape target (hero, Continue Watching, Trending Now, Top 10, genre rows) maps to existing row types + collections (`NotflixHomeCollectionCreator`).

## 5. Build / signing (if a custom build is ever wanted)

- Release signing: `app/build.gradle.kts:130-137,231-242` — keystore from env/local property `NUVIO_RELEASE_STORE_FILE`, default `../nuviotv.jks` (NOT in repo), alias default `nuviotv`. No CI workflows in repo.
- A fork build signs with a DIFFERENT key → it will NOT install over upstream Notflix (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`); the upstream app must be uninstalled first (local-only data lost; Nuvio account data — addons, profiles, home settings — re-syncs from the cloud on sign-in). Alternative: change `applicationId` to install side-by-side (loses nothing, but two Notflix icons and separate local state).
- 32-bit box: the app ships armeabi-v7a (it runs on the box today); a rebuild must keep the `armeabi-v7a` ABI in the splits.

## 6. Netflix (original) home study — captured on the box 2026-11-14 ~22:05 (Vevo profile, UK library)

Screenshots could NOT be saved (adb pull returned 0-byte files for the Netflix app); study below is from the live screens, text-only.

- **Top nav:** Search, Home, Shows, Movies, Games, My Netflix, New & Hot (badge 5).
- **Hero (billboard):** full-screen backdrop, big title treatment, badge line («Now the #1 TV Show in the UK Today», «New Season», «Coming December 11»), buttons: «Remind Me» / «More Info» (Play when available).
- **Row titles observed (in order):** Trending Now (portrait posters) · Netflix Originals (landscape) · Critically Acclaimed TV Dramas (landscape) · New on Netflix (landscape) · Top 10 Movies in the UK Today (big-number cards) · Award-Winning TV Shows (landscape) · Top 10 TV Shows in the UK Today (big-number cards) · Because You Watched "Adolescence" (personalized) · My List (landscape) · Award-Winning Films (landscape).
- **Row shapes:** portrait posters for Trending Now; landscape 16:9 cards for most rows; oversized number + portrait card for Top 10 rows.
- **What the user wants copied (21:43):** these generic Netflix-style row titles + merged content rows (no per-source rows like «English Series - Série» / «M3U Movies - Film»); all posters stay; tapping a title opens the merged streams panel. No Netflix name/logo may be used in the restyle (impersonation rule).
- **Note:** profile picker has 5 profiles (VEVo red locked, Vevo blue, Momo, Adem locked, Kids locked). Vevo profile entered without a household block this time.

## 7. Row titles the user dictated from the Netflix home (2026-11-14 ~22:15, Arabic UI)

He scrolled the real Netflix home and sent the rows he wants copied into the Notflix restyle — this is the row-title spec for the custom build:

أيقونات المزاج · كوميديا · فانتازيا · قصص شبابية للأبد · خيال علمي · موسم الأعياد · كتبك المفضلة على الشاشة · رومانسي · واقعي · الأطفال والعائلة · رسوم متحركة · أنيمي · وثائقيات · علم التنجيم · Ww (unclear — possibly WWE, as dictated) · ارتجالي · الجديد والجدير بالمشاهدة · جريمة · أكشن · دراما · أعمال مشوقة

Plus from earlier in the evening: «متابعة المشاهدة» (Continue Watching) as the first row under the hero, and Spanish-language films/series as content he wants (asked 22:08).

## 8. Full Netflix home walkthrough — user-recorded video 2026-11-14 ~22:27

Video: `~/workspace/user/media_library/video/14/148f926763955d21029247e8c3ac6dff778469e36093c2c448a539e26b23e2b4.mp4` (~2:55, phone recording of the TV, Arabic RTL UI, Vevo profile). He scrolled the whole home + the Movies tab + Search. Key structural findings:

- **Top nav (RTL):** الرئيسية · مسلسلات وبرامج · أفلام · محتواي على Netflix · بحث.
- **Hero:** full-width billboard (The Crown on Home; Transformers: The Last Knight on Movies tab) with «عرض» + «المزيد من المعلومات» buttons and an Arabic synopsis line.
- **متابعة المشاهدة (Continue Watching) is the FIRST row under the hero**, portrait posters with red progress bars (Mayor of Kingstown S2E7 21 min left, The Vampire Diaries, Lupin, The Rookie, The Good Doctor, WWE RAW, The Middle, Power, Dexter…). A second row titled **«لنواصل المشاهدة»** also appears (that's what his 22:07 message was dictating — a row title, not an instruction to watch).
- **المزاج (Mood) chip bar:** a horizontal scrollable row of genre/mood filter chips directly under the top rows — كوميديا، المزاج، أعمال مشوقة، دراما، أكشن، جريمة، الجديد والجدير بالمشاهدة، فانتازيا، خيال علمي، رومانسي، واقعي، الأطفال والعائلة، رسوم متحركة، أنيمي، وثائقيات، موسم الأعياد، كتبك المفضلة على الشاشة، قصص شبابية للأبد، WWE، ارتجالي، علم التنجيم… (this is the list he dictated at 22:15 — «Ww» was the WWE chip). Tapping a chip filters the home to that mood/genre.
- **Themed rows (in scroll order, as read from the video):** أعمال إثارة قاسية عن الجريمة · العروض المباشرة والفعالية: WWE (RAW / SmackDown / NXT with air times) · أعمال يمكنك مشاهدتها في عطلة هذا الأسبوع · دراما تلفزيونية عن الجرائم · برامج تلفزيونية حماسية · الجديد على Netflix · أعمال مشوقة لا تُمل · برامج تلفزيونية عن الجريمة · يعرض قريبًا · أعمال الأفلام الدرامية التلفزيونية · لأنك شاهدت Mayor of Kingstown · مسلسلات وبرامج خيال علمي وفانتازيا · دراما تلفزيونية · أعمال المشاهدة العائلية · أعمال درامية تلفزيونية عن التحقيقات الجنائية · دراما تلفزيونية عن العصابات · المسلسلات والبرامج الأكثر تكرارًا للمشاهدة · دراما تلفزيونية أوروبية · عروض تلفزيونية بوليسية · العودة إلى عقد جديد (?) · تشويق بإيقاع سريع من أول حلقة · مسلسلات تلفزيونية أمريكية مثيرة بالدراما · شاهد أحدث عروض الأكشن · أعمال حركة ومغامرات تلفزيونية عالمية · دراما تلفزيونية عالمية · عروض تلفزيونية حائزة على جوائز · المشاهدة خلال أسبوع واحد · روائع الأعمال · أعمال إثارة وكوري جنوبي تلفزيونية عالمية · مسلسلات تلفزيونية عن السحرة والأشباح (?) · لأنك شاهدت محتوى في «مارفل» (?) · عروض تلفزيونية ذات إيقاع شائق · مغامرة · بطولة شخصيات سوداء · أعمال حركة تجذب برومانسية · قصص عن الجريمة بدرجة رومانسية · **أفلام ومسلسلات تلفزيونية باللغة الإسبانية** (Narcos, La Casa de Papel — this is the row behind his 22:08 Spanish-content message).
- **Movies tab:** same structure with a film hero + film rows (أفلام أكشن ومغامرات، أفلام مثيرة للاهتمام…).
- **Search:** letter-grid keyboard; he typed «EL PROFESOR» — suggestions + «توصيات بناءً على عمليات بحثك».
- **Layout notes for the restyle:** rows are portrait posters (series) / landscape cards; focused card expands into a large preview pane on the right with synopsis + metadata (year, seasons, age rating, genres) — the same "expand on focus" pattern Notflix already uses. Row titles are descriptive/thematic, never source names.

## 9. TMDB "what's on Netflix in Algeria" script (user-sent 2026-11-14 ~22:48)

He pasted a Python script fetching Netflix titles per region from TMDB — a data source for the restyle's Netflix-style rows (e.g. a "on Netflix" row). Endpoint: `GET https://api.themoviedb.org/3/discover/movie?with_watch_providers=8&watch_region=DZ` (8 = Netflix in TMDB; DZ = Algeria, swappable to US/FR). Auth: `Authorization: Bearer <TMDB access token>` (his key — value never recorded; the script as sent has the placeholder YOUR_TMDB_ACCESS_TOKEN). Prints title + release date per result. NOT yet run — needs his token. Recorded for the restyle build; run only on his word.

## 10. In-app reorder + suffix fix DONE (2026-11-14 ~22:41–22:56, on-box, Vevo account)

- **Suffix toggle:** Settings → واجهة العرض → محتوى الرئيسية → «إظهار نوع القوائم» OFF (verified grey 22:41). Home now shows clean titles: «Popular», «New» — no «- Film»/«- Série».
- **Rename hunt:** no in-app rename exists — Catalog Order rows are not name-focusable (only ↑/↓/تعطيل); Layout has only the suffix toggle. True Netflix-style renaming needs the custom build (held at his «ديرها»).
- **Netflix-style catalog order applied + verified on the home:** hero → متابعة المشاهدة (3 real poster cards — the blank iptv: cards are GONE, confirmed 22:56) → Popular - Movie → Popular - Series → New - Movie → New - Series → Featured - Movie → Featured - Series → M3U Movies → M3U Series → PKP Watchlist - Movie → PKP Watchlist - Series → English HD Movies → English Series → (Notflix Collection, disabled) → Last videos → Calendar videos. Home spot-check confirmed Popular ×2 then New ×2 in that order with clean titles.
- **Account page re-confirmed:** vevo75vevo@gmail.com, profile GO, 6 إضافات, 3 مستوى المشاهدة.

## 11. Cyberflix Catalog addon — RETIRED, cannot be installed (checked 2026-11-14 ~22:52)

User asked for it 22:47. Verified dead: `cyberflix.elfhosted.com/manifest.json` 301-redirects to ElfHosted's "CyberFlix on ElfHosted (retired)" page (updated 24 Sept 2026: "Development stalled and the ElfHosted public instance was retired"). kamyroll.tech config host unreachable; 2024 Beamup manifest URLs dead; Stremio community directory no longer lists it. Source `marcojoao/cyberflix-server` (MIT, Python, self-hostable via Docker) last pushed 2024-12-26. It was catalog+metadata only (no streams): per-service rows (Netflix, Disney+, HBO Max, Prime, Apple TV+, Hulu, Paramount+, Peacock, Starz, Kids, Anime, Asian, Awards, trending_today). ElfHosted's recommended replacements: AIOMetadata, AIOLists, or the plain TMDB addon. (User's Yahoo account already has Anime Collection (AIOMetadata); Vevo does not.) rleroi's "Streaming Catalogs" is the closest surviving equivalent in concept — manifest NOT verified.

## 12. Mood-chip data model (user-sent 2026-11-14 ~22:55, Kotlin)

For the restyle's المزاج (Mood) chip bar (§8) — one item per chip/genre:

```kotlin
data class CategoryItem(
    val id: String,
    val title: String,
    val iconResId: Int, // أو String رابط URL إذا كانت الأيقونات مستضافة على سيرفر خارجي
    val tag: String
)
```

Notes: `tag` = the filter key (genre/mood) applied to the home when the chip is tapped; icon may be a local drawable resource OR a hosted URL (change type to String if hosted). Pairs with the §7 dictated chip list (كوميديا، فانتازيا، خيال علمي، أكشن، دراما، جريمة، رومانسي، واقعي، الأطفال والعائلة، رسوم متحركة، أنيمي، وثائقيات، موسم الأعياد، كتبك المفضلة على الشاشة، قصص شبابية للأبد، WWE، ارتجالي، علم التنجيم، الجديد والجدير بالمشاهدة، أعمال مشوقة).

## 13. Mood-chip RecyclerView adapter (user-sent 2026-11-14 ~22:56, Kotlin)

Pairs with the §12 CategoryItem model — the horizontal chips row for the restyle home:

```kotlin
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CategoryAdapter(
    private val categories: List<CategoryItem>,
    private val onItemClick: (CategoryItem) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    class CategoryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconImageView: ImageView = view.findViewById(R.id.imgCategoryIcon)
        val titleTextView: TextView = view.findViewById(R.id.txtCategoryTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_card, parent, false)
        return CategoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.titleTextView.text = category.title
        holder.iconImageView.setImageResource(category.iconResId) // يمكنك استبدالها بمكتبة مثل Glide/Coil للصور عبر الإنترنت

        holder.itemView.setOnClickListener {
            onItemClick(category)
        }
    }

    override fun getItemCount(): Int = categories.size
}
```

Notes: item layout `R.layout.item_category_card` with `imgCategoryIcon` + `txtCategoryTitle`; click passes the CategoryItem (its `tag` drives the home filter per §12). If icons become hosted URLs, swap `setImageResource` for Glide/Coil. TV note for the build: RecyclerView chips need DPAD focus handling (focusable items + focus-change scale/highlight) since this is a leanback home — the stock adapter has no focus visuals.

## 14. Mood-chip card layout `item_category_card.xml` (user-sent 2026-11-14 ~22:56)

Third piece of the chips set (§12 model + §13 adapter). Vertical card 160dp × 120dp, icon 48dp above a 2-line bold white title, background `@drawable/bg_category_card`, focusable + clickable (DPAD-ready for TV):

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="160dp"
    android:layout_height="120dp"
    android:orientation="vertical"
    android:gravity="center"
    android:background="@drawable/bg_category_card"
    android:focusable="true"
    android:clickable="true"
    android:layout_margin="8dp"
    android:padding="12dp">

    <ImageView
        android:id="@+id/imgCategoryIcon"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:scaleType="centerInside"
        android:contentDescription="Category Icon" />

    <TextView
        android:id="@+id/txtCategoryTitle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:textColor="@android:color/white"
        android:textSize="14sp"
        android:textStyle="bold"
        android:gravity="center"
        android:maxLines="2"
        android:ellipsize="end" />

</LinearLayout>
```

Still missing for the build: the `bg_category_card` drawable itself (shape/selector — should include a focused state for TV) and the icon set (drawables or hosted URLs per §12).

## 15. Wiring example `MainActivity` (user-sent 2026-11-14 ~22:56, Kotlin)

Fourth piece of the chips set (§12–§14): wires CategoryAdapter to a RecyclerView with a 2-column GridLayoutManager and 4 sample categories from his dictated list:

```kotlin
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerCategories)
        recyclerView.layoutManager = GridLayoutManager(this, 2) // شبكة من عمودين أو أكثر حسب الحاجة

        // قائمة التصنيفات المخصصة
        val categoryList = listOf(
            CategoryItem("1", "مزاج", R.drawable.ic_mood, "mood"),
            CategoryItem("2", "فانتازيا", R.drawable.ic_fantasy, "fantasy"),
            CategoryItem("3", "قصص شبابية للابد", R.drawable.ic_youth, "youth_stories"),
            CategoryItem("4", "خيال علمي", R.drawable.ic_scifi, "sci_fi")
        )

        val adapter = CategoryAdapter(categoryList) { selectedCategory ->
            // إجراء عند النقر على القسم (مثلاً فتح محتوى التصنيف)
            Toast.makeText(this, "تم اختيار: ${selectedCategory.title}", Toast.LENGTH_SHORT).show()
        }

        recyclerView.adapter = adapter
    }
}
```

Build notes:
- Sample icons referenced: `ic_mood`, `ic_fantasy`, `ic_youth`, `ic_scifi` — drawables still missing (full chip list in §7 has ~20 items, each needs an icon or a hosted URL per §12).
- The 2-column grid fits a dedicated categories/browse screen; the home's المزاج strip (§8 video) is a HORIZONTAL row — the build should use `LinearLayoutManager.HORIZONTAL` there and keep the grid for a browse screen if one is added.
- The Toast is a placeholder click action — the real action filters the home rows by the item's `tag`.
- Notflix is a Compose app (NuvioTV base), not Views + AppCompat — these pieces are the spec/reference for the restyle, to be adapted into Compose (LazyRow + focus handling) at build time, not dropped in as-is.

## 16. Custom catalogs via hosted JSON (user-sent 2026-11-14 ~23:03)

Idea for organizing sections inside Stremio or Kodi (custom-category addons): if the app relies on ready-made platforms, in Stremio one can create a Custom Catalog by hosting an external JSON file matching the mentioned categories and linking it to a Cinemeta or Cyberflix addon, so films get distributed under those exact names.

Notes:
- **Cyberflix is NOT usable for this** — retired Sept 2026, no working manifest (§11). The viable route is Cinemeta (already installed in Vevo) or **AIOLists** (ElfHosted's Cyberflix replacement: MDBList/Trakt lists → catalogs, free public instance) for list-driven custom catalogs.
- The category names in question are the §7 dictated list (مزاج، كوميديا، فانتازيا، قصص شبابية للأبد، خيال علمي، موسم الأعياد، كتبك المفضلة على الشاشة، رومانسي، واقعي، الأطفال والعائلة، رسوم متحركة، أنيمي، وثائقيات، علم التنجيم، WWE، ارتجالي، الجديد والجدير بالمشاهدة، جريمة، أكشن، دراما، أعمال مشوقة).
- A hosted-JSON catalog is a Stremio-side mechanism (addon manifest + catalog JSON); in Notflix it would surface as additional addon catalog rows, subject to the same per-row naming limits (no in-app rename — §10). Merged Netflix-style rows still need the custom build.
- Kodi side: custom categories there would be a separate mechanism (playlists / skin widgets) — not detailed by the user yet.

## 17. Genre catalogs as icons (user spec, 2026-11-14 ~23:58)

User (darija): «هذوا لي راك دير فيهم يجيو داخل ايقونات وكل أيقونة كي تحلها تلقا فيها المحتوي الخاص بلعنوان» — the catalogs being arranged (Vevo genre catalogs + TMDB rows) should appear as ICONS on the home; opening an icon shows that title's content.

- This is the CategoryItem model he sent (§12–§15): one icon card per category (id/title/icon/tag); tap → the category's content (a browse screen filtered by the tag), NOT a long row on the home.
- Same spirit as Netflix's المزاج chip bar (§8 video) but as icon cards, not text chips.
- Cannot be done from Notflix settings — needs the custom restyle build (held until he says «ديرها»). The in-app row reorder is the interim home arrangement.
- Icon set still missing: each of the ~20 categories needs an icon (drawable or hosted URL); bg_category_card drawable also missing (§14).

## 18. Dribbble design references (user-shared 2026-11-14 ~23:56)

He shared https://dribbble.com — «هذا مليح كشما تخدم بيه افكار». 10 streaming/TV UI shots collected in ~/workspace/design-references/dribbble-ideas.md (no account, no sign-in). Best for the restyle:
- Ian Rakeda, «NETFLIX TV Show Card Concept» (190k views) — the focus-expand model: focused card grows into a rich preview (synopsis/meta/actions) without leaving the row.
- Dinarys, «Netflix TV App Concept» (147k views) — closest whole-home reference (hero + stacked rows) for row order/hero proportions.
- Oleg Toptalov, «Detail view | STB» (ivi redesign) — closest analog to the details/streams panel on a box; full Behance case study.
- No Arabic RTL streaming-TV shot found on Dribbble; RTL rules recorded in the file instead. No mood-chip-bar shot either — the chip/icon bar follows Netflix's own pattern (§8).
- Also added to ~/workspace/design-references/2026-11-10-netflix-ui/README.md.

## 19. TMDB key entered + 00:23 burst findings (2026-11-15 ~00:50)

- **TMDB API key ENTERED** (user sent it in chat; typed via stdin into Settings › الخدمات المرتبطة › Notflix › مفتاح TMDB API; dialog has حفظ/مسح/إلغاء buttons — tap on حفظ and ENTER did NOT submit; the dialog closed + saved after a DOWN press; row shows masked ••••••3298). Gboard restore note: `ime set` fails while Gboard is disabled — run `ime enable` (full component) first, then `ime set`. Key value never recorded.
- **Enrichment VERIFIED working after app restart:** The Conjuring (2021) card shows an ARABIC synopsis + Arabic genres + a **TMDB** rating badge (7.3 TMDB). A new «Because you watched A Possession in Lincoln County» row appeared (TMDB recommendations — needs the key). Very new titles (Hunger Games 2026) still show English synopses — TMDB likely has no Arabic overview for them yet.
- **Hero dimming line CONFIRMED (his «صورة الهيرو الكبير فيها تعتيم كانو خط فاصل»):** the hero scrim does NOT cover the full width — the left ~480px strip stays bright while the rest is dimmed, with a hard vertical edge between them (seen on Les Misérables, Hunger Games, The Flash heroes). Restyle fix: full-width smooth gradient, no hard edge.
- **Icons = Netflix TEXT pills (his 00:23 photo of the Netflix mood bar):** rounded-rectangle chips carrying only the Arabic genre name (رسوم متحركة، أنيمي، وثائقيات، علم التنجيم…), no pictorial icon. The restyle's category strip follows this: text pills, horizontally scrollable — NOT pictorial icon cards. (Supersedes the §17 icon-card reading; the CategoryItem model still supplies the tap → filtered-content behavior.)
- **«IMDB بالانجليزي»:** the IMDb badge is the rating-source label (brand name, always Latin). Enriched cards now show a TMDB badge instead. Hiding ratings entirely is possible in-app (Layout → «التقييمات العامة» OFF) — offered, awaiting his word.
- **«TMBD لماذا»:** the «TMDB» in row titles is the AIOMetadata addon naming its own catalogs; Notflix displays them as-is. Clean Arabic row titles need the restyle build.
- **Trailer audio STILL broken (his «الاوديو مكانش / صورة فقط تتحرك», sent AFTER the DND fix):** zen_mode re-verified 0, STREAM_MUSIC volume 6, speaker icon unmuted, extractor + player code both carry audio (ADAPTIVE video+audio, MergingMediaSource, player.volume 1f when unmuted). Root cause unknown. Decisive question pending: does sound work in Netflix / live TV on the box? If yes → Notflix trailer-player issue (logcat next); if no → box/TV audio output.

## §20 — Live platform icons (2026-11-15 ~00:54)
- He sent a phone screenshot of the Matrix home (NuvioTV 1.2.0 base): a «Your Apps & Channels» row with animated/live platform tiles (blue 3D tile + Apple TV logo) and a «Studios» row (MAPPA, MADHOUSE, ufotable cards). His words: «ايقونات المنصات حية» — the platform icons are ALIVE (animated).
- Design signal: he likes icons that MOVE — connects to his 23:03 complaint «الايقونات تاع القوائم مش تتحرك». Open question for the Notflix restyle: should the genre chips become animated/live icons per genre (like these platform tiles) instead of static text pills — or should a live platforms row be added to the home? Asked him; awaiting his word.
- Also visible: «Continuer à regarder» holds an IPTV channel card («UK - BBC 1 FHD • Film») — IPTV resume items appear in Continue Watching on Matrix phone too.

- **DECIDED 2026-11-15 ~00:56:** the static text-pill mockup was REJECTED («مش لي بعثتلي عيانين»). Direction is now **animated genre icons in Notflix with his recorded names** («لي تتحرك في notflix بأسماء لي عندك»). Animated preview rendered: `~/workspace/mockups/chips-anim.mp4` (9.6s, 1280×720 — icon+name chips, focus travels with scale-up + white state + bounce, strip scrolls; Twemoji icons). Text-pill mockup (`chips-preview.png`) is DEAD — do not resurface it.
- **NEW ORDER 2026-11-15 ~00:57–00:58:** apply the same liveliness in **Matrix** («وهذي الحيوية طبقها في Matrix»). He says the live-platform tiles' source is already in my hands («منصات حية عندك المصدر تاعها») — i.e. the «Your Apps & Channels» implementation in the NuvioTV 1.2.0 base (matrix-3.0 branch). Investigation subagent dispatched (read-only): locate the implementation, explain what makes the tiles live, check whether the TV home has the row, assess the port + any conflict with the 3.1 branch. Build decision after the findings.

- **REJECTED 2026-11-15 ~01:01:** the emoji-icon animated version («هذا خدمة تاع أطفال» — kids' stuff). Emoji is OUT for the restyle. **v3 direction:** premium cinematic gradient tiles, no emoji — each genre a dark gradient tile with the Arabic name, focused tile brightens + white border + slow diagonal shine sweep. Preview: `~/workspace/mockups/chips-anim-v3.mp4` (9.6s, 1280×720). Sent 2026-11-15 ~01:16; awaiting his verdict.

- **Repo 2026-11-15 ~01:27 (his order «مستودع»):** MAMIMami19/notflix-addons (public) @ 768d776 — README (Arabic) + addons.json with the 8 Notflix Vevo addons in order + install guide. The 6 public manifests are in it; MediaFusion-PikPak + M3U Mux are listed by NAME only (credential-bearing URLs never published).
