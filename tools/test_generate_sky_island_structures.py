from __future__ import annotations

import math
import sys
import unittest
from pathlib import Path


TOOLS = Path(__file__).resolve().parent
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

import generate_sky_island_structures as sky


class SkyIslandGeometryTests(unittest.TestCase):
    def test_existing_nbt_palette_can_be_loaded(self) -> None:
        repo = TOOLS.parent
        town_dir = (
            repo
            / "modpack"
            / "元素觉醒1.4.3-魔改版-20260915"
            / "kubejs"
            / "data"
            / "tnc"
            / "structures"
            / "medieval_town"
        )
        piece = next(town_dir.glob("piece_*.nbt"))
        size, blocks, data_version = sky.load_structure(piece)
        self.assertTrue(blocks)
        self.assertTrue(all(1 <= value <= 48 for value in size))
        self.assertEqual(data_version, 3105)

    def test_boundary_fits_canvas_and_is_not_perfect_circle(self) -> None:
        radii = [sky.boundary_radius(index * math.tau / 360) for index in range(360)]
        self.assertGreaterEqual(min(radii), 252.0)
        self.assertLessEqual(max(radii), 259.0)
        self.assertGreater(max(radii) - min(radii), 3.0)

    def test_town_corners_have_natural_buffer(self) -> None:
        cx, cz = sky.ISLAND_CENTER
        x0, x1, z0, z1 = sky.TOWN_BOUNDS
        margins = []
        for x, z in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
            angle = math.atan2(z - cz, x - cx)
            distance = math.hypot(x - cx, z - cz)
            margins.append(sky.boundary_radius(angle) - distance)
        self.assertGreaterEqual(min(margins), 32.0)

    def test_town_surface_is_flat_support_only(self) -> None:
        x0, x1, z0, z1 = sky.TOWN_BOUNDS
        for x, z in ((x0, z0), (x1, z1), ((x0 + x1) // 2, (z0 + z1) // 2)):
            self.assertEqual(sky.surface_height(x, z), sky.SURFACE_Y)
            bottom = sky.shell_bottom(x, z, sky.SURFACE_Y)
            self.assertLess(bottom, sky.SURFACE_Y)
            self.assertGreaterEqual(bottom, 55)

    def test_outside_canvas_corner_is_air(self) -> None:
        self.assertIsNone(sky.surface_height(0, 0))
        self.assertIsNone(sky.surface_height(519, 519))

    def test_anchor_coordinates_are_stable(self) -> None:
        center = [
            sky.TOWN_OFFSET[0] + sky.TOWN_CENTER_LOCAL[0],
            sky.TOWN_OFFSET[1] + sky.TOWN_CENTER_LOCAL[1],
            sky.TOWN_OFFSET[2] + sky.TOWN_CENTER_LOCAL[2],
        ]
        arrival = [
            sky.TOWN_OFFSET[0] + sky.TOWN_ARRIVAL_LOCAL[0],
            sky.TOWN_OFFSET[1] + sky.TOWN_ARRIVAL_LOCAL[1],
            sky.TOWN_OFFSET[2] + sky.TOWN_ARRIVAL_LOCAL[2],
        ]
        self.assertEqual(center, [240, 90, 260])
        self.assertEqual(arrival, [209, 90, 424])
        self.assertEqual(sky.ISLAND_WORLD_Y + arrival[1], 180)

    def test_root_layout_has_four_large_and_six_secondary_roots(self) -> None:
        specs = sky.root_specs()
        self.assertEqual(len(specs), 10)
        self.assertEqual(sum(spec.start_radius >= 9.5 for spec in specs), 4)
        for spec in specs:
            self.assertGreater(spec.start_y, spec.end_y)
            self.assertGreater(spec.start_radius, spec.end_radius)

    def test_tree_layout_is_deterministic_and_avoids_town(self) -> None:
        first = sky.tree_positions()
        second = sky.tree_positions()
        self.assertEqual(first, second)
        self.assertEqual(len(first), 115)
        self.assertTrue(all(not sky.in_town_buffer(x, z, 10) for x, _, z, _ in first))

    def test_portal_has_safe_center_and_lit_ring(self) -> None:
        blocks = sky.portal_blocks()
        positions = {(x, y, z): state for x, y, z, state, _ in blocks}
        self.assertEqual(positions[(7, 1, 7)], sky.SEA_LANTERN)
        self.assertGreaterEqual(sum(state == sky.SEA_LANTERN for state in positions.values()), 9)
        self.assertTrue(all(0 <= x < 15 and 0 <= y < 4 and 0 <= z < 15 for x, y, z in positions))


if __name__ == "__main__":
    unittest.main()
