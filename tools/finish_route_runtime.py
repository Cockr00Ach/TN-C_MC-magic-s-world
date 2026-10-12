"""Small guarded migrations for the approved route runtime."""
from pathlib import Path
R=Path(__file__).resolve().parents[1]; J=R/'src/main/java/com/tnc/tnc'
def change(path,a,b):
 p=J/path;s=p.read_text(encoding='utf-8');assert a in s,(path,a);p.write_text(s.replace(a,b),encoding='utf-8')
change('life/routes/RouteNodeEntity.java','t.putInt("TargetColor",targetColor);','t.putInt("TargetColor",targetColor);t.putBoolean("AnimalCharging",animalCharging);')
change('life/routes/RouteNodeEntity.java','targetColor=Math.floorMod(t.getInt("TargetColor"),16);','targetColor=Math.floorMod(t.getInt("TargetColor"),16);animalCharging=t.getBoolean("AnimalCharging");')
change('life/routes/RouteNetwork.java','if(node.kind().supply()&&node.mana>0)','if(node.kind().supply()&&!(node.kind()==RouteKind.ANIMAL_COLLECTOR&&node.animalCharging)&&node.mana>0)')
change('life/routes/RouteNetwork.java','&&dest.kind()!=RouteKind.INFUSER)continue;','&&dest.kind()!=RouteKind.INFUSER&&!(dest.kind()==RouteKind.ANIMAL_COLLECTOR&&dest.animalCharging))continue;')
change('life/routes/RouteNetwork.java','if(source.priority==2)Collections.rotate(targets,targets.isEmpty()?0:(int)(l.getGameTime()/20%targets.size()));','if(source.priority==2){Collections.rotate(targets,targets.isEmpty()?0:(int)(l.getGameTime()/20%targets.size()));targets.sort(Comparator.comparingInt(p->rank(l,p,2)));}')
change('life/routes/RouteKind.java','ANIMAL_COLLECTOR("breath_collector","集息器",200,8)','ANIMAL_COLLECTOR("breath_collector","集息器",200,32)')
change('life/pasture/PastureProductItem.java','instanceof EnergyBlockEntity node','instanceof com.tnc.tnc.life.routes.RouteNodeEntity node')
change('life/pasture/PastureProductItem.java','int accepted=node.addEnergy(reserve);','int accepted=node.receive(reserve);')
change('life/pasture/PastureProductItem.java','最多50FE','最多50点纯魔力')
change('life/pasture/PastureProductItem.java','剩余电量随晶体保存','剩余魔力随晶体保存')
change('life/pasture/PastureProductItem.java','+accepted+" FE。','+accepted+" 魔力。')
change('life/pasture/ManaBottleItem.java','animal.speciesId().equals("pillowlight_marten")','animal.speciesId().equals("pillowlight_marten")||player.isShiftKeyDown()&&(animal.speciesId().equals("dewbound_whale")||animal.speciesId().equals("wirecall_lizard"))')
change('life/pasture/ManaBottleItem.java','"给枕光貂注入 "+received','"给"+animal.species().name()+"注入 "+received')
