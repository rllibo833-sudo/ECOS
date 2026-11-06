# Nexus — Personal Knowledge Library

Nexus is the private, offline-first Android library inside ECOS. It collects personal files and authored knowledge locally.

## Current implementation
- Kotlin Android Views/Canvas UI; no Jetpack Compose or AndroidX Material Components.
- Custom adaptive launcher icon.
- SAF multi-file import, app-private originals, SHA-256 duplicate detection, and cancellable import progress.
- Runtime permissions for shared images, video, and audio; document picker access does not require broad storage permission.
- In-app PDF reader, bounded image preview, and in-app video playback.
- ZIP inventory without extraction; source/APK files are never executed.
- Persistent canonical knowledge graph and separately reviewed local similarity suggestions.
- Articles with summaries, generated contents list, and internal [[title]] links.
- File details, safe deletion, sharing, and offline ZIP backup/restore.
- CI verification on pushes. APK packaging is manual-only, so routine code changes do not generate repeated debug/release APK artifacts.

## Status

This is an active implementation, not a claim that every roadmap item is complete. See ROADMAP.md for exact implemented and outstanding acceptance work, and ARCHITECTURE.md for storage and permission behavior.
