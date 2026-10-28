"""Regression checks for authored channels against GeckoLib's mirrored engine coordinates."""
import json
import math
import unittest

from tortoise_transition_animation import GEO, ROOT, build_transition, retime_tick


def sample_channel(channel, tick, phase):
    def vector(values):
        result = []
        for value in values:
            if isinstance(value, str):
                query, values = value.split("?", 1)
                assert query.strip() == "query.tnc_phase_two"
                yes, no = values.split(":", 1)
                value = yes if phase == 2 else no
            result.append(float(value))
        return result
    keys = sorted((float(time) * 20, vector(values)) for time, values in channel.items())
    if tick <= keys[0][0]:
        return keys[0][1]
    for (a, av), (b, bv) in zip(keys, keys[1:]):
        if tick <= b:
            return [x + (y - x) * (tick - a) / (b - a) for x, y in zip(av, bv)]
    return keys[-1][1]


class TransitionAnimationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.clip = build_transition()

    def test_published_clip_is_current_and_crouches_vertically(self):
        resource = ROOT / "src/main/resources/assets/tnc/animations/entity/volcanic_tortoise.animation.json"
        self.assertEqual(json.loads(resource.read_text(encoding="utf-8"))["animations"]["transition"], self.clip)
        body = self.clip["bones"]["body"]["position"]
        self.assertEqual([0, -4.8, 0], sample_channel(body, retime_tick(24), 1))
        self.assertEqual([0, 6.4, 0], sample_channel(body, retime_tick(32), 2))

    def test_foot_soles_stay_on_their_original_plane_in_both_models(self):
        for phase in (1, 2):
            geometry = json.loads((GEO / f"volcanic_tortoise_phase{phase}.geo.json").read_text(encoding="utf-8"))
            legs = [b for b in geometry["minecraft:geometry"][0]["bones"] if b["name"].startswith("leg_")]
            for bone in legs:
                self.assertFalse(bone.get("rotation"))
                # These four bones are direct children of body and their cubes have no local rotation.
                self.assertEqual("body", bone["parent"])
                self.assertTrue(all(not cube.get("rotation") for cube in bone["cubes"]))
                corners = [(c["origin"][0] + ix * c["size"][0], c["origin"][1] + iy * c["size"][1])
                           for c in bone["cubes"] for ix in (0, 1) for iy in (0, 1)]
                base = min(y for x, y in corners)
                channels = self.clip["bones"][bone["name"]]
                for quarter in range(641):
                    tick = quarter / 4
                    body_y = sample_channel(self.clip["bones"]["body"]["position"], tick, phase)[1]
                    offset = sample_channel(channels["position"], tick, phase)[1]
                    angle = math.radians(sample_channel(channels["rotation"], tick, phase)[2])
                    px, py, _ = bone["pivot"]
                    # Engine-space X = -authored X; engine Z rotation = authored Z.
                    minimum = min(py + math.sin(angle) * (-x + px) + math.cos(angle) * (y - py)
                                  + body_y + offset for x, y in corners)
                    self.assertAlmostEqual(base, minimum, delta=0.004, msg=(phase, bone["name"], tick))
                angle = sample_channel(channels["rotation"], retime_tick(24), phase)[2]
                self.assertLess(angle, 0) if bone["name"].endswith("l") else self.assertGreater(angle, 0)

    def test_silence_freezes_every_channel_without_scaling_or_neck_translation(self):
        for bone in self.clip["bones"].values():
            self.assertNotIn("scale", bone)
            for channel in bone.values():
                for phase in (1, 2):
                    at_rest = sample_channel(channel, retime_tick(24), phase)
                    for tick in (24.5, 25, 26, 27, 28, 29, 30):
                        for expected, actual in zip(at_rest, sample_channel(channel, retime_tick(tick), phase)):
                            self.assertAlmostEqual(expected, actual, places=10)
        self.assertEqual({"rotation"}, set(self.clip["bones"]["neck"]))

    def test_last_pose_matches_idle_including_tail_axis_and_offset_plate_breathing(self):
        resource = ROOT / "src/main/resources/assets/tnc/animations/entity/volcanic_tortoise.animation.json"
        idle = json.loads(resource.read_text(encoding="utf-8"))["animations"]["idle_p2"]["bones"]
        for name, channels in idle.items():
            for channel, values in channels.items():
                if channel in self.clip["bones"].get(name, {}):
                    self.assertEqual(sample_channel(values, 0, 2),
                                     sample_channel(self.clip["bones"][name][channel], retime_tick(100), 2), (name, channel))

    def test_open_jaw_moves_its_tip_down_relative_to_the_head(self):
        geometry = json.loads((GEO / "volcanic_tortoise_phase2.geo.json").read_text(encoding="utf-8"))
        jaw = next(b for b in geometry["minecraft:geometry"][0]["bones"] if b["name"] == "jaw")
        tip = next(c for c in jaw["cubes"] if c["name"] == "lojaw_tip")
        y = tip["origin"][1] + tip["size"][1]
        z = tip["origin"][2]
        py, pz = jaw["pivot"][1:]
        for tick in (31, 80, 83, 86):
            authored_x = sample_channel(self.clip["bones"]["jaw"]["rotation"], retime_tick(tick), 2)[0]
            engine_x = math.radians(-authored_x)
            opened_y = py + math.cos(engine_x) * (y - py) - math.sin(engine_x) * (z - pz)
            self.assertLess(opened_y, y - 8, "Jaw should open below the head, never rotate into the skull")

    def test_shell_opening_remains_visible_after_the_model_swap_flash(self):
        self.assertEqual(8.0, self.clip["animation_length"])
        rotation = self.clip["bones"]["plate_l"]["rotation"]
        self.assertEqual(0, sample_channel(rotation, 53, 2)[2])
        self.assertEqual(-34, sample_channel(rotation, 70, 2)[2])
        self.assertGreaterEqual(70 - 55, 12)
        self.assertGreater(abs(sample_channel(rotation, 64, 2)[2]),
                           abs(sample_channel(rotation, 56, 2)[2]) + 10)


if __name__ == "__main__":
    unittest.main()
