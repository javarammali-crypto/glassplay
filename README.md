# GlassPlay

An Android media center with a glass-style interface and a plug-in system compatible with Stremio add-ons.

## Features

- **Home:** featured carousel, Continue Watching with progress, and rows from your add-ons' catalogs.
- **Discover:** Movies and TV Shows tabs, catalog and genre filters, infinite scrolling.
- **Search** across every installed add-on that supports it, with recent searches.
- **Details:** artwork, rating, cast, trailer, and a season and episode picker that marks watched episodes.
- **Player:** resumes exactly where you stopped. Supports direct video links and HLS. Streams can also be opened in another player app.
- **Add-ons:** install any add-on by pasting its manifest link, reorder by priority, or remove.

GlassPlay plays direct HTTP(S) video links and opens YouTube links in YouTube. It doesn't play torrent links.

## Getting the APK

Every push to `main` builds the app on GitHub:

1. Open the repo's **Releases** page and download the latest `GlassPlay-N.apk`, or download it from the **Actions** run artifacts.
2. On your phone, open the APK and allow installing from this source when Android asks.

Each build is signed with the same key, so new versions install over old ones and keep your watch history.

## Project layout

- `data/`: add-on protocol client, installed add-ons, watch history
- `ui/`: Compose screens and the glass components
- `player/`: Media3 ExoPlayer screen and resume logic
