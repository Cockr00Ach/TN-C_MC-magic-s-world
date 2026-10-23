from pathlib import Path
import json
r=Path(__file__).resolve().parents[1]
assets=r/"src/main/resources/assets/tnc"
def save(p,obj): p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(obj,ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
def cube(a,b,tex):
 return {"from":a,"to":b,"faces":{f:{"texture":"#"+tex} for f in ("up","down","north","south","east","west")}}
models={
 "source":{"textures":{"base":"minecraft:block/polished_deepslate","crystal":"minecraft:block/amethyst_block","particle":"minecraft:block/amethyst_block"},"elements":[cube([1,0,1],[15,3,15],"base"),cube([3,3,3],[13,5,13],"base"),cube([5,5,5],[11,15,11],"crystal"),cube([2,4,6],[5,10,10],"crystal"),cube([11,4,6],[14,12,10],"crystal")]},
 "nest":{"textures":{"shell":"minecraft:block/crimson_hyphae","core":"minecraft:block/crying_obsidian","particle":"minecraft:block/nether_wart_block"},"elements":[cube([2,0,2],[14,3,14],"shell"),cube([1,3,3],[15,12,13],"shell"),cube([3,11,4],[13,15,12],"core"),cube([5,3,0],[11,10,4],"core")]},
 "node":{"textures":{"stone":"minecraft:block/chiseled_deepslate","metal":"minecraft:block/gilded_blackstone","particle":"minecraft:block/chiseled_deepslate"},"elements":[cube([1,0,1],[15,4,15],"stone"),cube([3,4,3],[13,12,13],"stone"),cube([2,12,2],[14,14,14],"metal")]}
}
for suffix,model in models.items():
 name="leyline_"+suffix;save(assets/"models/block"/(name+".json"),model);save(assets/"blockstates"/(name+".json"),{"variants":{"":{"model":"tnc:block/"+name}}})
for suffix in ("veil","phase_material","phase_aether"):
 name="leyline_"+suffix;save(assets/"models/block"/(name+".json"),{"textures":{"particle":"minecraft:block/stone"},"elements":[]});save(assets/"blockstates"/(name+".json"),{"variants":{"":{"model":"tnc:block/"+name}}})
# Apply source operation only inside intended placement scope.
p=r/"src/main/java/com/tnc/tnc/dungeon/DungeonEvents.java";s=p.read_text(encoding="utf-8").replace('p&&DungeonRealm.inside(p.level()))e.setCanceled(true);','p&&DungeonRealm.locked(p.level()))e.setCanceled(true);');p.write_text(s,encoding="utf-8")
# Puzzles replace the old treasure spawns entirely; avoid a free parallel hidden-cache reward.
p=r/"src/main/java/com/tnc/tnc/dungeon/DungeonRealm.java";s=p.read_text(encoding="utf-8").replace('q->q.kind()==DungeonGraph.Kind.TREASURE','q->q.kind()==DungeonGraph.Kind.TREASURE&&!com.tnc.tnc.dungeon.leyline.LeylineService.puzzle(r,q)');p.write_text(s,encoding="utf-8")

