# Nexus Roadmap

Nexus is a private, offline-first library for all personal work and files: documents, photos, videos, ZIP archives, Markdown, source code, datasets, APKs, and articles. ECOS is one collection inside the wider archive.

## Implemented on `nexus/complete-library-v1`
- [x] Kotlin Android application with custom Android Views and Canvas; no Compose or Material UI dependency.
- [x] Multi-file import through Android Storage Access Framework.
- [x] App-private copy of imported originals and local JSON catalog.
- [x] File categories for images, videos, ZIP, Markdown, source, datasets, PDF, office documents, APK, and other files.
- [x] Persistent local knowledge nodes and typed directed canonical edges with provenance.
- [x] Separate semantic-suggestion records with score, method, and suggested/accepted/rejected status.
- [x] Actual graph edges are drawn from persisted relationships; no generated decorative ring.
- [x] Article creation and local persistence.
- [x] Markdown headings, lists, emphasis, and internal `[[Article title]]` links.
- [x] Read-only ZIP inventory (no extraction).
- [x] Text/source preview with a bounded 512 KiB preview limit.
- [x] Office, image, video, and package handoff to Android viewers.\n- [x] Initial in-app PDF reader using Android PdfRenderer, with visible-page rendering and a two-page bitmap cache.\n- [x] Canvas-rendered brand header, action controls, library cards, and graph viewport; article/editor text surfaces still need custom treatment.
- [x] File import and local indexing moved off the UI thread.
- [x] CI workflow for unit tests, lint, debug APK, and installable release APK.

## Still required before a final APK
- [ ] Verify the build and resolve all compiler/lint failures in CI.
- [ ] Add automated behavior tests for import/catalog integrity, graph persistence, and archive safety.
- [ ] Add import progress, cancellation, duplicate detection, and recovery after interrupted copies.
- [ ] Add file details, safe delete, share/export, and backup/restore.
- [ ] Add memory-bounded image/video thumbnails and a proper in-app media viewer.
- [ ] Validate PDF rendering with varied, large, malformed, and password-protected documents; add page navigation/search/zoom.\n- [ ] Finish custom Canvas/View treatment for article reading, editor dialogs, search, and all remaining screens.\n- [ ] Improve document support and Markdown rendering (tables, code blocks, links, images).
- [ ] Add article infoboxes, table of contents, citations, and asset references.
- [ ] Add graph node selection, focus/neighborhood expansion, zoom, pan, and clustering.
- [ ] Improve semantic discovery using extracted content; current suggestions use explainable title-token overlap and are not embedding-based semantic understanding.
- [ ] Add low-memory performance checks and test on a physical Vivo Y18.
- [ ] Verify offline behavior after force-stop/restart and device reboot.
- [ ] Verify release APK signature, installation, upgrade path, and uninstall/reinstall behavior.

## Acceptance gates
The current Canvas UI and PDF reader changes are implemented but remain subject to the CI and device validation below. A green compile is necessary but not sufficient. A release is final only after all supported import types are handled correctly, graph links persist and navigate, articles are readable and internally linked, offline storage survives restarts, CI checks pass, and physical-device testing is documented.

## Device constraints
Target includes 4 GB RAM Android devices. Use metadata-first loading, bounded previews, incremental graph neighborhoods, and avoid loading full-resolution media into graph views.

## Privacy and safety
No account or network dependency for core library features. Imported source code and APKs are never executed. ZIP contents are listed read-only; any future extraction must reject absolute paths and traversal segments.
