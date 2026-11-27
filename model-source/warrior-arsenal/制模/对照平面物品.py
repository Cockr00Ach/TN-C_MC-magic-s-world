"""Reconstruct the opaque extruded sprite surfaces for a vanilla generated item."""
import numpy as np
from roster import texture, tex_ref


def sprite_geometry(ident, data):
    refs = data.get("textures", {})
    if "layer0" not in refs:
        raise ValueError(f"{ident} has no generated item texture")
    atlas = texture(tex_ref(refs["layer0"], refs), False).convert("RGBA")
    alpha = np.asarray(atlas)[:, :, 3] > 0
    height, width = alpha.shape
    dx, dy = 16 / width, 16 / height
    vertices, texcoords = [], []

    def quad(points, uv):
        for indices in ((0, 1, 2), (0, 2, 3)):
            vertices.append([points[i] for i in indices])
            texcoords.append([uv[i] for i in indices])

    for row, col in np.argwhere(alpha):
        left, right = col * dx, (col + 1) * dx
        top, bottom = 16 - row * dy, 16 - (row + 1) * dy
        outline = [(left, bottom), (right, bottom), (right, top), (left, top)]
        uv = [(col, row + 1), (col + 1, row + 1), (col + 1, row), (col, row)]
        front = [(x, y, 8.5) for x, y in outline]
        back = [(x, y, 7.5) for x, y in outline]
        quad(front, uv)
        quad(back[::-1], uv[::-1])
        # Only the outer and cutout edge pixels contribute walls. Each wall
        # samples its own opaque texel just as the generated sprite does.
        side_uv = [(col + .5, row + .5)] * 4
        for edge, (dr, dc) in enumerate(((1, 0), (0, 1), (-1, 0), (0, -1))):
            rr, cc = row + dr, col + dc
            if 0 <= rr < height and 0 <= cc < width and alpha[rr, cc]:
                continue
            following = (edge + 1) % 4
            quad([back[edge], back[following], front[following], front[edge]], side_uv)
    return np.asarray(vertices, dtype=float), np.asarray(texcoords, dtype=float), atlas
