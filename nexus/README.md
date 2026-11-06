# Nexus — Personal Knowledge Universe

Nexus is the Android personal library module inside ECOS. It is designed to collect documents, photos, videos, ZIP archives, Markdown, source code, datasets, APKs, and other files.

## Foundation status
- Kotlin Android application.
- Custom Android Views and Canvas; no Jetpack Compose or Material UI dependency.
- Offline-first local catalog and app-private file storage.
- Multi-file import through Android Storage Access Framework.
- Initial article and graph foundations.
- GitHub Actions debug APK build.

This is a foundation, not a claim that all planned features are complete. Semantic similarity, canonical graph editing, ZIP browsing, rich document rendering, and physical-device testing remain planned work.

## Product principles
1. Library browsing and locally imported assets work offline.
2. Canonical relationships and semantic suggestions remain separate.
3. Custom-designed UI, not generic templates.
4. Memory-aware rendering for 4 GB Android devices.
5. Imported originals are preserved.

See ARCHITECTURE.md and ROADMAP.md.
