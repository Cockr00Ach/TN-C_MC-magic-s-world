"""Extend the author's giant rig with five distinct, planted casting gestures."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'src/main/resources/assets/tnc/animations/entity/dark_giant.animation.json'


def clip(seconds, pose, release):
    bones = {}
    for name, rotation in pose.items():
        bones[name] = {'rotation': {
            '0.0': [0, 0, 0],
            str(round(seconds * .35, 3)): {'vector': [v * .65 for v in rotation], 'lerp_mode': 'catmullrom'},
            str(round(seconds - .15, 3)): {'vector': rotation, 'lerp_mode': 'catmullrom'},
            str(round(seconds + .12, 3)): {'vector': release.get(name, rotation), 'lerp_mode': 'catmullrom'},
            str(round(seconds + .6, 3)): [0, 0, 0],
        }}
    return {'loop': False, 'animation_length': seconds + .6, 'bones': bones}


def main():
    data = json.loads(TARGET.read_text(encoding='utf-8-sig'))
    poses = {
        # Draw the right arm back, then thrust the palm forward; left arm braces.
        'cast_hands': (2.2,
            {'body': [-7, -18, 0], 'head': [4, 18, 0], 'armright': [-72, -30, -28],
             'armleft': [-28, 12, 24], 'pauldronright': [-20, 0, -14], 'cape': [12, 0, 0]},
            {'body': [12, 12, 0], 'armright': [-112, 5, -8], 'armleft': [-44, 0, 18]}),
        # Both palms raised overhead, followed by a commanding downward sweep.
        'cast_sky_hands': (2.8,
            {'body': [-9, 0, 0], 'head': [-20, 0, 0], 'armright': [-154, -8, -22],
             'armleft': [-154, 8, 22], 'pauldronright': [-28, 0, -15], 'pauldronleft': [-28, 0, 15], 'cape': [18, 0, 0]},
            {'body': [17, 0, 0], 'head': [12, 0, 0], 'armright': [-65, 0, -12], 'armleft': [-65, 0, 12]}),
        # Gather the mantle inward, then spread both arms to release the domain.
        'cast_mist': (2.6,
            {'body': [10, 0, 0], 'head': [15, 0, 0], 'armright': [-70, 38, -12],
             'armleft': [-70, -38, 12], 'cape': [-12, 0, 0], 'tail': [15, 0, 0]},
            {'body': [-10, 0, 0], 'head': [-12, 0, 0], 'armright': [-45, -30, -72],
             'armleft': [-45, 30, 72], 'cape': [28, 0, 0]}),
        # Reach toward the victims and pull both hands toward the chest.
        'cast_siphon': (2.2,
            {'body': [8, 0, 0], 'head': [10, 0, 0], 'armright': [-105, -10, -18],
             'armleft': [-105, 10, 18], 'cape': [8, 0, 0]},
            {'body': [-8, 0, 0], 'armright': [-50, 32, -14], 'armleft': [-50, -32, 14], 'head': [-5, 0, 0]}),
        # Raise the arms sideways, then command the army with an asymmetric sweep.
        'command_army': (2.8,
            {'body': [-8, 0, 0], 'head': [-15, 0, 0], 'armright': [-95, -18, -60],
             'armleft': [-95, 18, 60], 'pauldronright': [-12, 0, -24], 'pauldronleft': [-12, 0, 24], 'cape': [20, 0, 0]},
            {'body': [5, -12, 0], 'armright': [-110, -12, -18], 'armleft': [-30, 10, 30]}),
    }
    for name, (seconds, pose, release) in poses.items():
        data['animations'][name] = clip(seconds, pose, release)
    TARGET.write_bytes((json.dumps(data, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))


if __name__ == '__main__':
    main()
