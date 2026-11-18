# Notflix Restyle 1 — build note

**Version:** 1.0.0-beta.4.1-restyle1 (versionCode 1077)
**Package:** `com.notflix.tv.custom` — installs ALONGSIDE upstream Notflix (`com.notflix.tv`)
**Launcher label:** NotFlix Custom
**Signing:** new key (`notflix-restyle`, created for this build — never upstream's)
**Base:** DaoeZ/notflix 1.0.0-beta.4.1 (commit 7495568), fork MAMIMami19/notflix, branch `notflix-restyle-1`, commit 034c9a1
**Built:** 2026-11-18, GitHub Actions (CI run 44415347268)

## What changed

1. **Genre strip on the home (row 0, under the hero)** — 21 tiles, Matrix
   Productions style: landscape 248×140, the genre name as the artwork on a
   dark per-genre gradient, animated on focus (scale + brighten + slow
   diagonal shine sweep). No emoji. The user's dictated list, in order:
   مزاج، كوميديا، فانتازيا، قصص شبابية للأبد، خيال علمي، موسم الأعياد،
   كتبك المفضلة على الشاشة، رومانسي، واقعي، الأطفال والعائلة، رسوم متحركة،
   أنيمي، وثائقيات، علم التنجيم، WWE، ارتجالي، الجديد والجدير بالمشاهدة،
   جريمة، أكشن، دراما، أعمال مشوقة.
   Tapping a tile filters the home rows in place (Netflix mood-bar
   behaviour): rows whose title is the genre stay whole; other rows keep
   only matching items. Tap the selected tile again, or press BACK, to
   clear the filter. The selected tile keeps a red border while active.
2. **Netflix-style home** — same-theme catalog rows from different addons
   (Trending, Popular, Top Rated, Featured, New) merge into one row, with
   items deduped; stock theme rows get Netflix-style Arabic titles
   (الأكثر رواجاً الآن، رائج الآن، الأعلى تقييماً، مختارات، جديد…).
   Custom titles still win. Movies and series never mix.
3. **Hero dimming fix** — the scrim now spans the full width (was a 75%
   band with a hard bright edge). Same gradient shape otherwise.
4. **Side-by-side install** — new package + new key + distinct label, so
   upstream Notflix stays untouched and the two can be compared.
5. **Kept:** Arabic RTL home, TMDB key entry, per-profile settings,
   the 8 Vevo addons (re-sync from the Nuvio account).
6. **Not touched:** live TV / IPTV (films-and-series-only, per the standing
   rule), the audio issue (separate diagnostic).

## Install steps (after he approves the preview)

1. Install `app-full-armeabi-v7a-release.apk` on the box — it installs
   ALONGSIDE upstream Notflix; do NOT uninstall anything.
2. Open **NotFlix Custom**, sign into the Nuvio account (Yahoo) — the 8
   Vevo addons re-sync from the cloud.
3. Re-enter the TMDB key (fresh app data — the key does not carry over).
4. Profiles are per-account; the 4 profiles (MIGA, MAGA2, KAMMO, ADEM)
   come back with the account.

## What to check on-device

- [ ] Strip visible under the hero, 21 tiles, Arabic names, no emoji.
- [ ] Focus on a tile: scale + brighten + shine sweep visible.
- [ ] LEFT/RIGHT move along the strip; DOWN from the strip reaches the
      first content row; UP from the strip returns to the hero.
- [ ] OK on أكشن (or دراما): rows filter to matching items; selected tile
      keeps a red border; BACK clears the filter.
- [ ] OK on a genre with no matches (e.g. علم التنجيم): the
      «لا توجد نتائج» message shows instead of an empty home.
- [ ] Merged rows: one Trending row (not one per addon); Arabic titles.
- [ ] Hero: no hard bright edge on the left; gradient smooth to the edge.
- [ ] Upstream Notflix still installed and working next to it.

## Known assumptions / gaps

- Genre filtering is **in-place on the home** (Netflix mood-bar), not a
  separate browse screen — decided from the §8 video reference; a
  dedicated browse screen can replace it if he prefers.
- Genres with no TMDB code match by title keywords instead: مزاج matches
  the Drama/Romance/Comedy/Music/Family cluster; قصص شبابية للأبد matches
  Family; موسم الأعياد / كتبك المفضلة على الشاشة / علم التنجيم / WWE /
  ارتجالي match title keywords; الجديد والجدير بالمشاهدة matches items
  released this year or last. Rows whose own title names the genre stay
  whole regardless.
- The "moving" feel is Compose animation (shine + scale + brighten), not
  a GIF overlay: the Productions model supports GIF-on-focus, but no
  genre GIF assets exist. Tile covers are per-genre gradients (v3
  colours), not the 16 hosted genre covers (portrait 600×900 with English
  names — wrong aspect and labels for landscape Arabic tiles).
- The merged row's "see all" opens the first member addon's catalog.
- The strip appears only once there is content to filter (no strip on an
  empty/loading home, so the loading/empty state still shows).

## Verification

<!-- filled in after the APK is downloaded and verified -->
