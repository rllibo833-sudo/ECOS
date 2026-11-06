# Nexus Roadmap

Nexus is a private, offline-first Android library for personal files and knowledge: documents, photos, videos, audio, ZIP archives, Markdown, source code, datasets, APKs, and authored articles. ECOS is one collection inside the archive. The app is Kotlin + Android Views/Canvas; it does not use Jetpack Compose or the AndroidX Material Components library.

## Implemented

### App foundation and device access
- [x] Kotlin Android application with a custom dark interface, Canvas-rendered brand header, action tiles, library cards, and graph surface.
- [x] Launcher icon and round adaptive launcher icon.
- [x] Runtime media permission request flow for images, video, and audio on Android 13/14+, plus legacy external-storage read permission on Android 12 and below.
- [x] Android Storage Access Framework import for multiple files and document types without requesting broad all-files access.
- [x] App-private original-file storage and a persistent local JSON catalog.
- [x] Explicit privacy model: no network/account dependency for core features; internal app storage remains private and needs no Android storage permission.

### Import and file handling
- [x] Type classification for images, video, audio, PDF, office documents, ZIP, Markdown, source, datasets, APKs, and other files.
- [x] SHA-256 duplicate detection.
- [x] Import work moved off the UI thread, with progress feedback and cancellation between files.
- [x] Interrupted partial-import cleanup on next app start.
- [x] File details, safe deletion of the Nexus copy, and cleanup of the associated graph node/edges.
- [x] Share original files through scoped FileProvider URIs.
- [x] Bounded image decoding for in-app image preview.
- [x] In-app video playback using Android VideoView; audio can be handed to an installed player.
- [x] Release PDF renderer, video playback, and preview bitmap resources when navigating away or when the screen detaches.
- [x] Read-only ZIP inventory. ZIP entries are not extracted or executed.
- [x] Bounded text/source prefix extraction and text preview.
- [x] Offline PDF reader using Android PdfRenderer, visible-page rendering, a two-page bitmap cache, and previous/next page controls.
- [x] Offline backup export and restore for originals, catalog, and knowledge graph, with staging, validation, traversal rejection, and rollback.

### Knowledge graph and articles
- [x] Persistent local knowledge nodes and typed, directed canonical relationships with provenance.
- [x] Semantic candidates stored separately from canonical facts; acceptance/rejection is explicit.
- [x] Explainable local content-token suggestions use bounded extracted text for up to 200 assets. They are lexical similarity, not embeddings or human-level semantic understanding.
- [x] Graph draws persisted edges, supports pan/zoom, and opens tapped nodes.
- [x] Article creation and local persistence, summary field, generated table-of-contents list with section-jump navigation, and related-knowledge links.
- [x] Internal [[title]] links can navigate to articles and imported assets.

### Automated checks
- [x] Instrumentation coverage for graph persistence, semantic/canonical separation, read-only ZIP inventory, PDF rendering, backup round-trip, and ZIP traversal rejection.
- [x] CI runs unit tests, Android lint, and emulator instrumentation tests on pushes to the Nexus branch or main.
- [x] APK packaging is manual-only through workflow dispatch; routine verification pushes do not repeatedly build or upload APKs.

## Remaining work before product acceptance

### Import, catalog, and file formats
- [ ] Add end-to-end import tests using ContentResolver/SAF URIs, including catalog consistency, duplicate behavior, zero-byte files, and interrupted imports.
- [ ] Add in-library thumbnail tiles for image/video assets; current previews are bounded but library cards remain text-first.
- [ ] Add broader office-document rendering; currently these files are handed to compatible Android apps.
- [ ] Add additional archive formats only if safe, with path traversal and decompression-bomb protections.

### PDF and media
- [ ] Validate PDFs across large, malformed, encrypted, rotated, and mixed-size documents on emulator and device.
- [ ] Add PDF text search and zoom. PdfRenderer itself does not expose text extraction; this requires a separately evaluated offline parser.
- [ ] Improve media controls, rotation, and playback lifecycle across device vendors.

### Articles and UI
- [ ] Finish custom Canvas/View styling for article reading, editor fields/dialogs, search, and every remaining screen; several text-entry and article surfaces still use native widgets.
- [ ] Render Markdown tables, fenced code blocks, images, richer links, and inline emphasis as styled spans.
- [ ] Add editable articles, explicit infobox fields, citation records, and embedded asset references.

### Graph and discovery
- [ ] Add explicit node selection, focus mode, neighborhood expansion, and clustering for large graphs.
- [ ] Improve graph layout and edge labels while keeping rendering incremental and bounded.
- [ ] Improve discovery beyond token overlap if a fully offline, low-memory model is practical; never silently convert suggestions into canonical facts.

### Validation
- [ ] Add performance/memory checks with large libraries and media files.
- [ ] Verify offline behavior after force-stop, restart, and device reboot.
- [ ] Install and exercise the app on the target Vivo Y18 (4 GB RAM). Emulator CI cannot substitute for physical-device validation.
- [ ] Verify install/upgrade behavior and the generated APK signature when an APK is explicitly requested.

## Acceptance criteria

Nexus is accepted only when supported imports preserve originals and catalog integrity; PDF and media handling work without memory spikes; graph relationships persist and navigate; articles are readable and interlinked; backup/restore is recoverable; offline data survives restart/reboot; CI passes; and target-device validation is documented.

## Device constraints

Target includes 4 GB RAM devices. Use metadata-first loading, bounded image/text decoding, a small PDF bitmap cache, incremental graph neighborhoods, and avoid loading full-resolution media into graph views.

## Privacy and safety

Core library features work offline. Imported source code and APKs are never executed. Documents selected through SAF do not require broad storage permissions. App-private internal storage does not require a permission. Media permissions are requested only for direct access to shared image/video/audio collections. ZIP contents are read-only; any future extraction must reject absolute paths, traversal segments, and decompression bombs.
