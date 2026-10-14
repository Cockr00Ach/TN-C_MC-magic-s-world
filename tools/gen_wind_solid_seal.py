"""Wind material variant of the registered solid multi-ring water/earth seal."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]/'src/main/resources/assets/tnc/models'
seal=json.loads((root/'water/seal.json').read_text())
seal['credit']='TN-C solid wind seal: pearl rings and mint-blue wind runes'
seal['textures'].update(deep='minecraft:block/cyan_concrete',water='minecraft:block/light_blue_concrete',light='minecraft:block/white_concrete',pearl='minecraft:block/sea_lantern',particle='minecraft:block/light_blue_concrete')
(root/'wind/seal.json').write_text(json.dumps(seal,indent=2)+'\n',encoding='utf8')
