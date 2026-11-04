# ECOS WORLD — Procedural Cinematic Trailer

A code-generated 45-second world-exploration teaser for ECOS. The first production pass uses procedural 2.5D artwork (layered landscapes, architecture, transport, atmosphere, titles and an original synthesized score) so it can render in GitHub Actions without a 3D workstation or paid API.

## Story structure

| Time | Sequence |
|---|---|
| 00–07 | Sunrise approach, distant ECOS skyline and passing train |
| 07–15 | Active city, moving traffic, monumental civic district and Cuk code easter egg |
| 15–25 | Port, airport, rail/industry, settlement, snow and desert |
| 25–35 | The 1,000-hectare mosque complex and a contrasting region |
| 35–45 | Traveler, horizon-wide world reveal, title and tagline |

## Render from GitHub (phone-friendly)

1. Open the **Actions** tab in this repository.
2. Select **Render ECOS World Trailer**.
3. Tap **Run workflow** and confirm.
4. When the run finishes, download the `ecos-world-trailer` artifact. It contains the MP4.

The workflow runs on GitHub-hosted Ubuntu. No local PC, API key, Blender installation or paid service is required.

## Render locally

Requires Python 3.11+, FFmpeg, Pillow and NumPy.

```bash
pip install -r requirements.txt
python trailer.py --output ecos-world-trailer.mp4 --fps 24 --width 1280 --height 720
```

For a quick preview, use `--fps 12 --width 640 --height 360`.

## Art direction and current scope

This is the first visual production pass, not a capture of the playable ECOS game. All scenes are generated from code and intentionally use a cohesive cinematic illustration style. The Cuk screen is a visual easter egg; replace its sample syntax with the canonical language syntax when ready. Real in-game locations, characters, logos and architectural references can be integrated as source assets in a later pass.

## Project layout

- `trailer.py` — scene composition, camera motion, transitions, typography and audio synthesis.
- `.github/workflows/render.yml` — one-tap cloud render and MP4 artifact publishing.
