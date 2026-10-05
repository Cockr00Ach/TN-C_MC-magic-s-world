# -*- coding: utf-8 -*-
"""
gen_dark_fog_spells.py -- rewrite the BLACK FOG chain (dark chain 4, "领域") for
author feedback 2026-10-09: "we are designing dark magic now, what do you think
the black fog should look like, I am not happy with the current one".

WHAT WAS WRONG (measured, not guessed)
    All five spells shipped one single particle batch:
        { particle_id: "smoke", shape: "SPHERE", origin: "CENTER", count: 35 }
    * `minecraft:smoke` is vanilla campfire smoke -- grey, thin, drifting UP. A
      black fog has to sit low, creep outward and be DARK.
    * a single SPHERE batch dies out with radius, so at 9 blocks the tier 5 field
      is a thin haze with no visible edge -- the player cannot tell whether they
      are inside it.
    * the five tiers differed ONLY in radius and damage.
    * the debuff was `tnc:gale_slow` -- a WIND effect on a dark spell.

WHAT IT WRITES NOW (per tier, all data-driven)
    cast / release / cloud particles : layered DARK particles, all verified to be
        real particle types (see the PARTICLE SAFETY block below -- the first
        version used `block_factorys_bosses:ink_fog` and crashed the client)
        body  minecraft:squid_ink + minecraft:smoke   (dark blob + volume)
        dark  fromtheshadows:shadow                    (packed dark vapour)
        soul  minecraft:soul / soul_fire_flame         (wisps)
        ember soulsweapons:black_flame
        wisp  block_factorys_bosses:soul_flip          (that mod's only safe id)
    release.target.cloud.entity_type_id : "tnc:fog"
        -> our DarkFogCloudEntity, which follows the caster from tier 3 up and
           hosts the boundary circle
    release.target.cloud.client_data.model : tnc:projectile/dark_fog
        -> the 3D dome from tools/gen_dark_fog_model.py, scale = 0.4 * fog radius.
           NOTE: this slot is engine-supported (SpellCloudRenderer feeds it to
           CustomModels.render) and was used by ZERO spells before this chain.
    impact[].action.status_effect : `tnc:dark_veil` (own debuff) replaces gale_slow
    tier table:
        t1 4.5  veil I    + damage 0.6
        t2 5.0  veil I    + own speed while standing in it + damage 0.9
        t3 6.0  veil I    + darkness 4s                    + damage 1.2
        t4 7.5  veil II   + darkness 5s + weakness         + damage 1.6
        t5 9.0  veil II   + darkness 6s + weakness + caster strength + damage 2.1

    Effects are also refreshed into `area_impact.particles` / `.sound`, which the
    engine plays at every impact tick (every 20 ticks) -- that is what gives the
    field a "pulse" instead of one static haze.

NOT TOUCHED: radius, time_to_live_seconds (15), impact_tick_interval (20),
delay_ticks (5), cooldown, range, learn.tier. Those are the author's numbers.

ASCII only. Usage: python tools/gen_dark_fog_spells.py
"""
import json
import os
import sys

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

PACK_NAME = "元素觉醒1.4.3-魔改版-20260915"
PACK_SPELL_DIR = os.path.join(REPO, "modpack", PACK_NAME, "kubejs", "data", "tnc", "spells")

FOG_ENTITY = "tnc:fog"
FOG_MODEL = "tnc:projectile/dark_fog"
VEIL = "tnc:dark_veil"

# The dome's scale relative to the fog radius.
#
# The model is 15 units (0.94 blocks) from its centre to its widest block, so at
# scale s its radius is s * 0.94 blocks. We want a dome roughly 0.375 * radius
# blocks across (its own radius), i.e. s = 0.375 * radius / 0.94 = 0.4 * radius --
# the DENSE CORE of the field, not the field itself. At s = radius (the first
# version) the cube faces covered the entire area and read as a solid stone dome
# instead of as fog, and it also swallowed the boundary circle.
DOME_SCALE = 0.4

# ---------------------------------------------------------------------------
#  particle vocabulary
# ---------------------------------------------------------------------------
INK = "minecraft:squid_ink"                    # the only VANILLA dark blob -- the fog's body
SMOKE = "minecraft:smoke"                      # volume / thickness (the old fog's only layer)
SHADOW = "fromtheshadows:shadow"               # packed smoke, reads as solid dark vapour
BLACK_FLAME = "soulsweapons:black_flame"       # black/white ember
SOUL_FIRE = "minecraft:soul_fire_flame"        # small blue flame licking the ground
SOUL = "minecraft:soul"                        # cyan wisp that drifts upward
SOUL_FLIP = "block_factorys_bosses:soul_flip"  # a second wisp shape (see the warning below)

# ---------------------------------------------------------------------------
#  ⚠⚠ PARTICLE SAFETY -- THIS IS THE "释放黑雾游戏闪退" CRASH (2026-10-09)
#
#  The first version of this chain used `block_factorys_bosses:ink_fog` / `:fog_1`.
#  Those particle ids EXIST as json files in that mod's assets, and they even have
#  textures -- but they are NOT Minecraft particle types: that mod registers them as
#  BEDROCK particles (`registerBedrockParticle(String)`), so looking the id up in
#  BuiltInRegistries.PARTICLE_TYPE returns that mod's own object instead of a
#  `ParticleOptions`. SpellEngine then does:
#      BuiltInRegistries.PARTICLE_TYPE.get(id)  -> (ParticleOptions) ...
#  => ClassCastException, 298 times in one log, and because it is thrown while the
#  CLIENT reads the `spell_engine:particle_effects` packet, the netty channel dies
#  and the game drops to the menu -- i.e. "释放黑雾游戏闪退".
#
#  ⇒ RULE: a particle id is only usable if the owning mod registers it as
#    `SimpleParticleType` (or another `ParticleType<... extends ParticleOptions>`).
#    Having a json under assets/<ns>/particles/ proves NOTHING.
#
#  Verified-safe ids used below (checked with javap against each mod's registry):
#    minecraft:*                       vanilla
#    soulsweapons:black_flame          RegistryObject<SimpleParticleType>
#    fromtheshadows:shadow             RegistryObject<SimpleParticleType>
#    block_factorys_bosses:soul_flip   RegistryObject<SimpleParticleType>
#      (that mod's OTHER particles -- ink_fog / fog_1 / mist_cloud -- are the broken
#       ones, so `soul_flip` is the only id from it we may touch)
#  The candidate sheet docs/previews/dark_fog_particle_candidates.png still lists the
#  forbidden ones; they are kept there only as a record of what went wrong.
# ---------------------------------------------------------------------------


def batch(pid, shape, origin, count, min_speed, max_speed, extent=0.0, **kw):
    """One ParticleBatch. Field names follow the engine's Gson mapping exactly --
    a typo here is dropped silently (that is the trap this project documents)."""
    out = {
        "particle_id": pid,
        "shape": shape,
        "origin": origin,
        "count": float(count),
        "min_speed": float(min_speed),
        "max_speed": float(max_speed),
    }
    if extent:
        out["extent"] = float(extent)
    out.update(kw)
    return out


def fog_batches(radius, tier):
    """The cloud's per-tick particles: dark pool + dark canopy + body + wisps + embers.

    `extent` on CIRCLE/SPHERE/CONE is a *distance multiplier* on the spawn offset
    (verified in the engine bytecode: offset = dirVector * (distance + radius)),
    so extent = radius spreads the batch across the whole field.

    ★ DARKNESS MIX (2026-10-09, author: "黑雾感觉不够黑啊")
      The first pass used `minecraft:smoke` as the bulk layer. Smoke is a LIGHT grey,
      semi-transparent puff that also RISES, so piling it on turned the field into
      pale haze -- exactly the complaint. The ratio is now inverted:
        * `minecraft:squid_ink` (the only genuinely dark vanilla particle) carries the
          ground pool, a canopy over the dome, and the rim wall;
        * `fromtheshadows:shadow` is the body vapour;
        * smoke only softens the dome's cube edges (a handful of puffs);
        * the wisps are few and the embers sit low and inside.
      Density knobs: the four big counts in this function. Nothing else needs changing.
    """
    b = []
    # 1) ground pool: dense, very slow, DARK -- the "the fog is a liquid" layer
    b.append(batch(INK, "CIRCLE", "FEET", int(66 + 12 * tier), 0.005, 0.04, extent=radius))
    b.append(batch(SHADOW, "CIRCLE", "FEET", int(30 + 6 * tier), 0.005, 0.04, extent=radius))
    b.append(batch(SMOKE, "CIRCLE", "FEET", int(10 + 2 * tier), 0.005, 0.04, extent=radius))
    # 2) a dark canopy above the dome, so you cannot see sky through the fog.
    #    ⚠ the batch origin is always the cloud itself (ParticleHelper.origin ignores
    #    any entity height for a cloud), so "canopy" here means "spread with an upward
    #    speed bias" -- NOT a placement offset. The engine drops unknown json fields
    #    silently, so an `offset_y` here would do nothing at all.
    b.append(batch(INK, "SPHERE", "CENTER", int(26 + 6 * tier), 0.02, 0.16,
                   extent=radius * 0.7))
    b.append(batch(SHADOW, "SPHERE", "CENTER", int(16 + 4 * tier), 0.02, 0.14,
                   extent=radius * 0.7))
    # 3) body vapour + a thin grey layer only to soften the dome's hard cube edges
    b.append(batch(SHADOW, "SPHERE", "CENTER", int(26 + 6 * tier), 0.01, 0.08, extent=radius * 0.85))
    b.append(batch(SMOKE, "SPHERE", "CENTER", int(6 + 2 * tier), 0.01, 0.08, extent=radius * 0.7))
    # 4) wisps rising out of it -- few, small, and the only bright thing up high
    b.append(batch(SOUL, "SPHERE", "CENTER", int(2 + tier), 0.01, 0.06, extent=radius * 0.55))
    if tier >= 3:
        b.append(batch(SOUL_FLIP, "SPHERE", "CENTER", int(1 + tier), 0.02, 0.08,
                       extent=radius * 0.7))
    # 5) a dark wall at the rim, twice over: the edge has to read as black
    b.append(batch(INK, "CIRCLE", "CENTER", int(20 + 5 * tier), 0.01, 0.05,
                   extent=radius * 1.05))
    b.append(batch(SHADOW, "CIRCLE", "FEET", int(14 + 4 * tier), 0.02, 0.08,
                   extent=radius * 1.0))
    # 6) embers: low, inside, few (accent, not lighting)
    if tier >= 2:
        b.append(batch(BLACK_FLAME, "CIRCLE", "FEET", int(2 + tier), 0.02, 0.08,
                       extent=radius * 0.6))
    if tier >= 4:
        b.append(batch(SOUL_FIRE, "CIRCLE", "FEET", int(2 + tier), 0.05, 0.15,
                       extent=radius * 0.4))
    return b


def cast_batches(radius, tier):
    """Cast-time flourish: the fog is PULLED together in front of the caster."""
    return [
        batch(SHADOW, "SPHERE", "CENTER", int(26 + 6 * tier), 0.15, 0.7, extent=radius * 0.5),
        batch(SMOKE, "SPHERE", "CENTER", int(10 + 3 * tier), 0.15, 0.7, extent=radius * 0.5),
        batch(INK, "SPHERE", "CENTER", int(22 + 6 * tier), 0.12, 0.6, extent=radius * 0.6),
        batch(BLACK_FLAME, "CIRCLE", "FEET", int(4 + tier), 0.05, 0.2, extent=radius * 0.4),
    ]


def spawn_batches(radius, tier):
    """The moment the field appears: it BLOOMS outward from the caster."""
    return [
        batch(SHADOW, "SPHERE", "CENTER", int(40 + 12 * tier), 0.4, 1.8, extent=radius),
        batch(INK, "SPHERE", "CENTER", int(34 + 10 * tier), 0.35, 1.5, extent=radius),
        batch(SMOKE, "SPHERE", "CENTER", int(18 + 6 * tier), 0.3, 1.2, extent=radius),
    ]


def pulse_batches(radius, tier):
    """Every impact tick (1 s): the field breathes -- a dark wave rolls outward."""
    out = [
        batch(INK, "CIRCLE", "FEET", int(22 + 5 * tier), 0.15, 0.6, extent=radius),
        batch(SHADOW, "CIRCLE", "FEET", int(14 + 4 * tier), 0.15, 0.6, extent=radius),
        batch(SMOKE, "CIRCLE", "FEET", int(4 + tier), 0.15, 0.6, extent=radius),
        batch(SOUL, "SPHERE", "CENTER", int(3 + tier), 0.1, 0.5, extent=radius * 0.7),
    ]
    if tier >= 3:
        out.append(batch(SOUL_FLIP, "SPHERE", "CENTER", int(2 + tier), 0.1, 0.5,
                         extent=radius * 0.8))
    return out


# ---------------------------------------------------------------------------
#  per tier knobs: radius comes from the EXISTING json (author's number)
# ---------------------------------------------------------------------------
TIERS = {
    "black_mist": dict(damage=0.6, veil_amp=0, veil_secs=4, darkness=0, weakness=False,
                       caster_speed=0),
    "night_grace": dict(damage=0.9, veil_amp=0, veil_secs=5, darkness=0, weakness=False,
                        caster_speed=5),
    "dark_city": dict(damage=1.2, veil_amp=0, veil_secs=6, darkness=4, weakness=False,
                      caster_speed=0),
    "where_light_cannot_reach": dict(damage=1.6, veil_amp=1, veil_secs=8, darkness=5,
                                     weakness=True, caster_speed=8),
    "devour_light": dict(damage=2.1, veil_amp=1, veil_secs=10, darkness=6, weakness=True,
                         caster_speed=0, caster_strength=True),
}


def status(effect_id, seconds, amplifier, apply_to_caster=False, show_particles=True,
           apply_mode="SET", hide_particles=False):
    entry = {
        "action": {
            "type": "STATUS_EFFECT",
            "apply_to_caster": apply_to_caster,
            "status_effect": {
                "effect_id": effect_id,
                "duration": int(seconds),
                "amplifier": int(amplifier),
                "apply_mode": apply_mode,
                "show_particles": (not hide_particles) if show_particles else False,
            },
        }
    }
    return entry


def damage_action(coefficient, knockback=0.0):
    return {"action": {"type": "DAMAGE",
                       "damage": {"spell_power_coefficient": coefficient, "knockback": knockback}}}


def rewrite(spell, name, cfg):
    radius = float(spell["release"]["target"]["cloud"]["volume"]["radius"])
    tier = int(spell["learn"]["tier"])

    # ---- cast ----
    spell["cast"]["particles"] = cast_batches(radius, tier)

    # ---- release: attach our cloud entity + the dome model ----
    cloud = spell["release"]["target"]["cloud"]
    cloud["entity_type_id"] = FOG_ENTITY
    cloud["client_data"] = {
        "particles": fog_batches(radius, tier),
        # ★ the dome. scale = radius in blocks (see tools/gen_dark_fog_model.py);
        #   rotate slowly so the fog churns, and do NOT set light_emission so it
        #   stays dark instead of glowing (the embers are in the texture).
        #
        #   ⚠ the model is ~2 blocks across at scale 1 (a 16-unit half-width), so
        #   scale = 0.5 * radius gives a dome radius of 0.5 * radius blocks --
        #   HALF the field. The dome is the DENSE core of the fog, not the field:
        #   at a full-radius scale the cube faces covered the whole area and read as
        #   a solid stone dome instead of as fog.
        "model": {
            "model_id": FOG_MODEL,
            "scale": round(radius * DOME_SCALE, 3),
            "rotate_degrees_per_tick": 0.25,
        },
    }
    cloud["spawn"] = {
        "particles": spawn_batches(radius, tier),
        "sound": {"id": "entity.wither.ambient", "volume": 1.0},
    }
    spell["release"]["sound"] = {"id": "entity.wither.ambient", "volume": 0.8}

    # ---- impact: what happens to everything caught in the field, every 20 ticks ----
    impacts = [damage_action(cfg["damage"])]
    impacts.append(status(VEIL, cfg["veil_secs"], cfg["veil_amp"]))
    if cfg["darkness"]:
        impacts.append(status("minecraft:darkness", cfg["darkness"], 0))
    if cfg["weakness"]:
        impacts.append(status("minecraft:weakness", cfg["veil_secs"], 0))
    if cfg["caster_speed"]:
        impacts.append(status("minecraft:speed", cfg["caster_speed"], 0,
                              apply_to_caster=True, show_particles=False))
    if cfg.get("caster_strength"):
        impacts.append(status("minecraft:strength", cfg["veil_secs"], 0,
                              apply_to_caster=True, show_particles=False))
    spell["impact"] = impacts

    # ---- the pulse ----
    # ⚠ some of the five JSONs have no area_impact block at all (the original author
    # only added it where he felt like it). Create it if missing -- the ENGINE gets
    # the field's area from volume.area, so this block is pure presentation, but a
    # missing block also means "no impact particles at all" for that tier.
    area = spell.get("area_impact")
    if not isinstance(area, dict):
        area = {}
    area.setdefault("radius", radius)
    area.setdefault("area", {"distance_dropoff": "SQUARED"})
    area["particles"] = pulse_batches(radius, tier)
    area["sound"] = {"id": "entity.wither.shoot", "volume": 0.6}
    spell["area_impact"] = area
    return spell


FORBIDDEN_PARTICLES = {
    # assets-only "particles" from block_factorys_bosses: registered as BEDROCK
    # particles, NOT as ParticleType => SpellEngine's cast crashes the client.
    # This is the 2026-10-09 "释放黑雾游戏闪退" bug -- never come back here.
    "block_factorys_bosses:ink_fog",
    "block_factorys_bosses:fog_1",
    "block_factorys_bosses:mist_cloud",
    "block_factorys_bosses:towering_mist_cloud",
    "block_factorys_bosses:soul_cloud",
    "block_factorys_bosses:soul_smoke",
}


def used_particles(spell):
    """Every particle_id the spell would hand to the engine."""
    out = set()
    batches = []
    for key in ("cast", "release"):
        node = spell.get(key) or {}
        batches += node.get("particles") or []
        batches += ((node.get("target") or {}).get("cloud") or {}).get(
            "client_data", {}).get("particles") or []
    cloud = spell["release"]["target"]["cloud"]
    batches += cloud["client_data"]["particles"]
    batches += (cloud.get("spawn") or {}).get("particles") or []
    batches += (spell.get("area_impact") or {}).get("particles") or []
    for imp in spell.get("impact") or []:
        batches += imp.get("particles") or []
    for b in batches:
        if isinstance(b, dict) and b.get("particle_id"):
            out.add(str(b["particle_id"]))
    return out


def main():
    out_dir = PACK_SPELL_DIR
    if not os.path.isdir(out_dir):
        print("ERROR: pack spell dir not found: %s" % out_dir)
        return 1

    problems = 0
    for name, cfg in TIERS.items():
        path = os.path.join(out_dir, name + ".json")
        if not os.path.isfile(path):
            print("MISSING %s" % path)
            problems += 1
            continue
        with open(path, "r", encoding="utf-8-sig") as fh:
            spell = json.load(fh)
        before = json.dumps(spell, sort_keys=True)
        spell = rewrite(spell, name, cfg)
        # sanity: the knobs we must not have broken
        cloud = spell["release"]["target"]["cloud"]
        assert cloud["entity_type_id"] == FOG_ENTITY
        assert cloud["client_data"]["model"]["model_id"] == FOG_MODEL
        assert abs(float(cloud["client_data"]["model"]["scale"])
                   - float(cloud["volume"]["radius"]) * DOME_SCALE) < 1e-6
        assert cloud["time_to_live_seconds"] == 15.0
        assert cloud["impact_tick_interval"] == 20
        assert spell["learn"]["tier"] == int(spell["learn"]["tier"])
        for impact in spell["impact"]:
            assert "action" in impact, "impact without action -> engine NPE on every cast"
        # ★ the crash guard: no assets-only particle may ever reach the engine
        bad = used_particles(spell) & FORBIDDEN_PARTICLES
        if bad:
            print("FORBIDDEN PARTICLE in %s: %s (would crash the client)" % (name, sorted(bad)))
            problems += 1
            continue

        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            json.dump(spell, fh, indent=2, ensure_ascii=False)
            fh.write("\n")
        changed = before != json.dumps(spell, sort_keys=True)
        print("%-26s tier=%d radius=%-5s batches=%d impacts=%d particles=%d %s"
              % (name, spell["learn"]["tier"], cloud["volume"]["radius"],
                 len(cloud["client_data"]["particles"]), len(spell["impact"]),
                 len(used_particles(spell)), "updated" if changed else "unchanged"))

    print("done, %d problem(s)" % problems)
    return 0 if problems == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
