# GlassPlay

An Android media center with a glass-style interface and a plug-in system compatible with Stremio add-ons.

## Features

- **Accounts:** sign in with email and password or with Google (Firebase). Your profile and watch history are saved to your account.
- **Profile:** movies and TV shows watched and hours watched (today, yesterday, this week, month, year or all time), episodes watched, and your Watching / Finished / Dropped titles. Mark any movie, show or episode by hand.
- **Updates:** the app offers new releases when it opens, and downloads and installs them.

- **Home:** featured carousel, Continue Watching with progress, and rows from your add-ons' catalogs.
- **Discover:** Movies and TV Shows tabs, catalog and genre filters, infinite scrolling, plus built-in catalogs for streaming services (Netflix, Prime Video, JioHotstar, Disney+, HBO Max, Apple TV+, ZEE5, Sony LIV and more).
- **Franchises:** Marvel, DC, Harry Potter, Star Wars, Bond, Fast & Furious and 40+ other franchises, each with every movie in release order.
- **Search** across every installed add-on that supports it, with recent searches.
- **Details:** artwork, rating, cast, trailer, and a season and episode picker that marks watched episodes.
- **Downloads:** save torrent or direct-link streams to the device and watch them offline. Torrent downloads keep going in the background, and a torrent can be played while it's still downloading.
- **Player:** resumes exactly where you stopped. Fit, zoom or stretch the video with the size button or by pinching. A loading screen with the title's artwork shows peers and speed while a torrent starts. Supports direct video links, HLS, DASH and torrents, with subtitles from your add-ons and software decoding for AC3/DTS/TrueHD audio. Streams can also be opened in another player app.
- **Add-ons:** install any Stremio add-on by pasting its manifest link (including configured links and `stremio://` links), reorder by priority, or remove.

## Stremio add-on support

GlassPlay speaks the same add-on protocol as Stremio, so add-ons built for Stremio work here:

- **Catalogs, metadata and search** from any add-on, for every content type (movies, series, TV channels, anime…).
- **Streams:** direct links (`url`, with `proxyHeaders`), torrents (`infoHash` + `fileIdx` + `sources`, or magnet links), YouTube (`ytId`) and `externalUrl`.
- **Torrents** are streamed inside the app, like Stremio's streaming server: the pieces the player needs are downloaded first, so playback starts before the download finishes and seeking works. Downloaded data is kept in the app's cache only while you watch.
- **Subtitles** from subtitle add-ons and from streams that include their own. OpenSubtitles v3, which Stremio also installs by default, is added automatically. Subtitles are downloaded before playback and only working files are offered, in your phone's language, English (and Hindi in India) plus one per other language. Pick them with the CC button in the player.

## How watching is counted

- Time watched is real playing time, per day; skipping ahead adds nothing.
- A movie is finished within 15 minutes of its end, an episode within its last 10% (at least 2 minutes). Finishing counts the full length.
- Rewatching adds the time again but counts as one title.
- Not finished and nothing watched for 15 days: dropped. A show still airing with every released episode watched is "up to date".

## Getting the APK

Every push to `main` builds the app on GitHub:

1. Open the repo's **Releases** page and download the latest `GlassPlay-N.apk`, or download it from the **Actions** run artifacts.
2. On your phone, open the APK and allow installing from this source when Android asks.

Each build is signed with the same key, so new versions install over old ones and keep your watch history.

## Project layout

- `data/`: add-on protocol client, installed add-ons, watch history
- `torrent/`: torrent streaming engine (libtorrent) and the local server the player reads from
- `ui/`: Compose screens and the glass components
- `player/`: Media3 ExoPlayer screen and resume logic
