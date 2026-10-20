"""Deploy only the Stone Lancer geometry and albedo; preserve installed gameplay fixes."""
import argparse
from pathlib import Path
from prepare_water_leviathan_update import main


ENTRIES = {
    'assets/tnc/geo/entity/stone_lancer.geo.json',
    'assets/tnc/textures/entity/stone_lancer.png',
}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('base', 'built', 'output', 'report'):
        parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args()
    main(args.base, args.built, args.output, args.report, entries=ENTRIES, translation_keys=())
