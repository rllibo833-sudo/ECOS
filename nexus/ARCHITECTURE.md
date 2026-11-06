# Nexus Architecture

## Product model

Nexus is a private, offline-first Android knowledge library. ECOS is one collection within a larger archive of personal creations and files.

## Platform and interface

- Kotlin Android application.
- Android Views and custom Canvas components; no Jetpack Compose or AndroidX Material Components dependency.
- Custom launcher/adaptive icon and a custom dark visual system.
- Some text entry and reading surfaces still use native EditText/TextView widgets and remain scheduled for refinement.
- No network dependency for core library operations.

## Storage model

- Originals: app-private files/library/.
- Asset catalog: app-private files/nexus-catalog.json.
- Knowledge graph: app-private files/nexus-knowledge.json.
- Backup: ZIP containing catalog, graph, and original asset files.
- Restore stages and validates the backup before replacing the current library; paths are checked against traversal.

Each asset stores a stable ID, display name, MIME type, detected category, private relative path, byte size, import timestamp, and SHA-256 fingerprint.

## Access and permissions

- Internal app-private storage is accessible to Nexus without a runtime storage permission and is not exposed to other apps.
- Documents and arbitrary files are imported through Android Storage Access Framework.
- Direct shared-media access requests Android 13+ image/video/audio permissions, Android 14 selected-visual-media permission, or legacy read-storage permission on older Android versions.
- Shared files are exported through FileProvider content URIs with temporary read grants.

## Knowledge model

A KnowledgeNode may represent an asset, article, project, person, place, concept, or collection.

### Canonical edges

Explicit, typed, directed relationships with provenance. Examples: belongs_to, authored_by, references, part_of. Canonical edges are user-confirmed or imported from an authoritative source.

### Semantic edges

Candidate relationships inferred locally from titles and bounded extracted text. Each candidate carries method, score, and status (suggested/accepted/rejected). Candidates never become canonical facts automatically. Current similarity is lexical token overlap, not embeddings.

## Rendering and performance

- PDF: Android PdfRenderer; pages are rasterized when visible and a two-page bitmap cache bounds memory.
- Images: bounds-first decode and downsampling to a maximum preview dimension.
- Video: Android VideoView plays from the app-private file. PDF renderer, video playback, and preview bitmap resources are released on navigation and view detachment.
- Article table-of-contents entries resolve to rendered heading offsets and scroll the containing article view to the selected section.
- Text previews and semantic extraction are bounded.
- Graph indexing is capped to a bounded candidate set; the graph view draws a bounded number of nodes.
- Physical Vivo Y18 performance validation remains outstanding.

## Safety

- Imported source code and APKs are stored but never executed.
- ZIP inventory is read-only; no extraction is performed.
- Backup restore rejects absolute paths and traversal segments and caps total uncompressed input.
