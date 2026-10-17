# Dark overlord upper-body source

`materials.png` is the project-local 768px material source generated with the built-in imagegen tool on 2026-10-18. Quadrants: obsidian armor; joint leather/mail; runic gauntlets; layered shoulder steel. It supplies individual, aspect-correct new UV islands rather than replacing the entire atlas indiscriminately.

The four `base_` assets preserve the model, lower-body motion and body UV source immediately before the articulated-arm rebuild. They are the deterministic baseline for `python tools/rebuild_dark_giant_upperbody.py`. The output contains independent scapula, humerus, forearm, wrist, thumb and four two-joint fingers on each side.

Regenerate with the new builder, then run `python tools/audit_dark_giant_upperbody.py`. The historical giant generators predate this rig and should not be used to publish the articulated arms.
