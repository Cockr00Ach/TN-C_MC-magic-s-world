# Curved physical tornado — 2026-10-22

Shared player and Canglan model `tnc:projectile/wind_tornado` now uses a baked Forge OBJ mesh: two nested layers of three spiral ribbons each and a ground intake spiral. The funnel narrows at ground contact and flares around a gently bent axis. Feathered translucent texture softens the edges. Outer wind ribbons are three times the width of the first curved preview. The inner layer has 66% of the outer radius, 65% of its ribbon width, and a shifted spiral phase to reveal both layers.

600 double-sided baked quads; no added entities or per-frame geometry generation. Existing spell scale, damage and spin speed remain intact. A single-source block atlas declaration makes the texture available independently of optional client mods.

Validation: isolated native Minecraft client loads the OBJ, asserts 600 quads and no missing sprites, and captures four rotation views. This is not a full-pack FPS benchmark.

Regenerate with `python tools/gen_wind_tornado_model.py`. The scoped installer preparation overlays only four model/texture assets and merges the atlas entry, preserving all other JAR entries byte for byte. Installation uses baseline hashes, backups, validation and rollback.
