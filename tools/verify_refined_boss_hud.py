"""Check the actual native GUI captures, including the user's unchanged tortoise exception."""
import json
from pathlib import Path
from PIL import Image, ImageChops

root = Path(__file__).resolve().parents[1]
baseline = root / 'work/epic-boss-hud-client-20261025/screenshots'
run = root / 'work/epic-boss-hud-refined-client-20261026'
report = json.loads((run / 'report.json').read_text(encoding='utf-8'))
assert report['failures'] == [], report
assert report['variantsRendered'] == 13 and report['foreignBarPreserved'] and report['cacheCleared']
old = Image.open(baseline / 'epic-bars-elements.png').convert('RGBA')
new = Image.open(run / 'screenshots/epic-bars-elements.png').convert('RGBA')
assert old.size == new.size == (1400, 1000)
area = (290, 572, 1100, 686)
diff = ImageChops.difference(old.crop(area).convert('RGB'), new.crop(area).convert('RGB'))
assert diff.getbbox() is None, ('Volcanic tortoise artwork changed', diff.getbbox())
for name in ('epic-bars-elements.png', 'epic-bars-wind-and-ink.png'):
    assert ImageChops.difference(Image.open(baseline / name).convert('RGB'), Image.open(run / 'screenshots' / name).convert('RGB')).getbbox()
report['volcanicTortoisePixelIdentical'] = True
report['tortoiseComparedRect'] = area
(run / 'verified.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, indent=2))
