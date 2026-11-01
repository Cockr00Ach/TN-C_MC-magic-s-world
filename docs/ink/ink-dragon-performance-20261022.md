# Dragon performance patch — 2026-10-22

User reports severe lag when the dragon appears with Complementary enabled; no non-shader comparison available. This is a targeted rendering cost reduction, not a measured FPS root-cause profile.

- Previous close-range submission: 152,306 triangles every pass, including translucent duplicates.
- New closest-head submission: 80,710 triangles. Head/jaw retain full detail inside 40 blocks; body uses its continuous silhouette LOD. Other appendages use medium detail nearby. Head/jaw become medium at 40 blocks and silhouette at 112 blocks.
- Phantom submission: 36,630 triangles regardless of distance; editable source model and mesh asset are unchanged.
- Restore frustum rejection using the existing full animated dragon bounds, not the tiny vanilla entity bounds. Remote visibility snapshots and range are unchanged.
- Decode wave indices once; reuse transformation vectors; transform one normal per triangle; reuse the duplicated fourth corner; use the standard full-vertex submission API. No custom GL pipeline reintroduced.

Verification: 408 JUnit tests passed. Isolated Forge 47.4.22 client with Oculus 1.8.0, Embeddium 0.3.31 and ComplementaryUnbound_r5.6.1 completed the encounter audit (7 screenshots, all three stages, OpenGL error 0). Dragon intro screenshot visually checked. Log: `work/ink-dragon-performance-client.log`; report: `work/ink-encounter-audit.json`. No controlled before/after FPS measurement or full-modpack performance profile was performed.

Installed only four renderer/budget class entries; 16,927 other installed entries verified byte-identical. SHA256: `14B6762AF8C36DFE7D2C643D4A0DA04D0D90AF23B7365820108BE17B015D9677`.

Backup: `D:/ModTest/work/install-backups/ink-dragon-performance-20261022-0055/tnc-1.0.0.jar`.

Merged artifact: `build/libs/tnc-ink-dragon-performance-merged.jar`. Restart the game to load it. Formal saves, shader settings and source models were not modified. Development-only SpellCloud fixture removed from build output after native testing.
