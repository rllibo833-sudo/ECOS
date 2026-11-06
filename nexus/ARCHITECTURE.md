# Nexus Architecture

## Product model
Nexus is a personal, offline-first knowledge library, not an ECOS-only encyclopedia. ECOS is one collection inside the larger archive.

## Asset model
Each asset has a stable ID, display name, MIME type, detected category, private relative path, byte size, and import timestamp. Categories: document, image, video, archive, markdown, source, dataset, package, other.

## Knowledge model
A KnowledgeNode may represent an asset, article, project, person, place, concept, or collection.

### Canonical edges
Explicit, typed, directed relationships with provenance. Examples: belongs_to, authored_by, references, part_of. Canonical edges are user-confirmed or imported from an authoritative source.

### Semantic edges
Candidate relationships inferred from metadata, extracted text, shared terms, or embeddings. Each candidate carries method, score, and status (suggested/accepted/rejected). Never silently promote semantic candidates to canonical facts.

## UI
Kotlin Activity and custom Views; Canvas graph viewport; custom article/library screens; custom dark cinematic design tokens. No Compose or Material UI dependency.

## Storage
App-private files plus a JSON catalog for the first milestone. Originals live under files/library/. Catalog lives at files/nexus-catalog.json. Room may be considered after validating the initial model.

## Device constraints
Target includes 4 GB RAM devices. Use metadata-first loading, bounded thumbnail decoding, incremental graph neighborhoods, and avoid loading full-resolution media into graph views.

## Privacy
No account or network dependency in the initial app. Never execute imported source code or APKs. ZIP extraction, when added, must prevent path traversal.