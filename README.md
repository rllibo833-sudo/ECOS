# ECOS WORLD — 3D Cinematic Trailer

A 45-second Blender-rendered 3D world fly-through, built and rendered in GitHub Actions. The project is designed to be operated from a phone: start the workflow in the repository's Actions tab and download the finished artifact.

## Creative direction

The trailer is a continuous cinematic journey rather than a slideshow:

| Time | Sequence |
|---|---|
| 00–07 | Rural Juailin at morning, fields, livestock and a connected rail journey |
| 07–15 | Arunika / Nawasena: dense urban skyline, industry, elevated rail and everyday digital infrastructure |
| 15–25 | A fast regional journey: Wanasera research campus, Tirta Nusa coast, Giriwana forests and highlands |
| 25–35 | The mosque district: central mosque within a broad campus, roads, homes, services and the eight named kampung |
| 35–45 | Camera pulls back from the lived-in world toward a wide horizon; ECOS WORLD title and tagline |

## ECOS canon guardrails

Visual distinctions follow the current Notion references:

- **Arunika:** densely populated, industrial and nationally connected; Nawasena is the national capital and is located here.
- **Wanasera:** technology, education, science, research and Knowledge Graph Living Shared (KGLS).
- **Tirta Nusa:** scenic landscapes and tourism.
- **Giriwana:** frontier, forest and interior regions, with natural hazards part of its lived reality.
- **Juailin:** a rural community with agricultural and livestock work, connected to the wider digital society.
- **Mosque district:** a 1,000-hectare area, not one building of that size. It includes the mosque, settlements, homes, land, connecting routes and everyday activity. The eight kampung named in the source are Juailin, Meravin, Talven, Ordel, Senvara, Kalven, Darsen and Elvara.

The national scale is 1,700,000 km² and 513,759,331 people. The trailer communicates scale through varied environments and infrastructure rather than inventing a precise map.

**Canon rule:** this is an art-direction interpretation, not a claim that every building design or exact geographic placement is already fixed in lore. No invented flag, national emblem, province names, or undocumented signature architecture is presented as established canon.

## Render from GitHub on a phone

1. Open the **Actions** tab.
2. Select **Render ECOS World Trailer**.
3. Tap **Run workflow**.
4. Wait for the job to finish, then download **ecos-world-trailer** from Artifacts.

The artifact contains:
- `ecos-world-trailer.mp4` — the rendered 45-second trailer with original synthesized score.
- `ecos-world.blend` — the Blender scene for future revisions.

No local PC, paid API, or video-generation subscription is required. Rendering runs on GitHub-hosted Ubuntu.

## Project files

- `blender_scene.py` — 3D scene, materials, terrain, architecture, camera animation and title cards.
- `trailer.py` — original procedural score generator and the earlier 2.5D prototype.
- `.github/workflows/render.yml` — cloud render and artifact publishing.

The current build is a procedural 3D interpretation. When canonical in-game assets, screenshots, maps or architecture references are available, they can replace the provisional art direction without changing the overall trailer structure.
