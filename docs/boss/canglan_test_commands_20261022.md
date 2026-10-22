# Canglan attack rehearsal and movement limit

Operators can use `/tnc canglan` for help. Each command selects the closest living Canglan within 128 blocks; an optional final entity selector or UUID selects a specific boss. Finish the spawn/phase-shift animation before rehearsing an attack.

| Command | Result |
| --- | --- |
| `/tnc canglan attack thrust` | One thrust, then hold idle |
| `/tnc canglan loop sweep` | Repeat only the selected sweep |
| `/tnc canglan stop` | Cancel the attack, clear spells/anchors, hold idle |
| `/tnc canglan resume` | Restore the normal battle AI |

Skills: `thrust` (突刺), `sweep` (横扫), `tornado` (旋转龙卷), `gale_cut` (风刃), `arsenal_t4`, `arsenal_t5`. Rehearsal targets the issuing player, including creative players so animations can be inspected without taking damage. It disables unsolicited wind steps, spell dodges and normal approach movement. It does not change NoAI or persist test mode into saves.

Normal battle wind steps and dodges share a maximum of three consecutive moves (`CanglanWarGodRules.MAX_CONSECUTIVE_STEPS`). Movement unlocks only after a completed attack or recall attack. A wind step preserves the remaining attack cooldown instead of restarting it; the third step makes the next attack ready immediately. This fixes the previous loop where repeated steps reset the cooldown before any attack could happen.
