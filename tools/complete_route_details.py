from pathlib import Path
import json,re,zipfile
R=Path(__file__).resolve().parents[1]; J=R/'src/main/java/com/tnc/tnc'; S=R/'src/main/resources'
def change(path,a,b):
 p=J/path;s=p.read_text(encoding='utf-8');assert a in s,(path,a);p.write_text(s.replace(a,b),encoding='utf-8')
# The old FE bridge is retired; bottles deposit real mana into the same balance as the network.
change('life/pasture/ManaBottleItem.java','instanceof EnergyBlockEntity node&&node.mayUse(player)){accepted=node.addMana(offered);','instanceof com.tnc.tnc.life.routes.RouteNodeEntity node&&node.mayUse(player)){accepted=node.receive(offered);')
change('life/pasture/ManaBottleItem.java','instanceof EnergyBlockEntity node&&owner.equals(node.owner()))accepted=node.addMana(remaining);','instanceof com.tnc.tnc.life.routes.RouteNodeEntity node&&owner.equals(node.owner))accepted=node.receive(remaining);')
change('life/pasture/ManaBottleItem.java','炼金炉口/发电座','魔力炉口/魔导装置')
# Bind Bellwool sheep to the same tools and boundaries as the other original animals.
change('life/fauna/BellwoolSheepEntity.java','super.aiStep();\n        if (!level().isClientSide','super.aiStep();\n        if (!level().isClientSide) com.tnc.tnc.life.routes.RouteBellCare.tick(this);\n        if (!level().isClientSide')
change('life/fauna/BellwoolSheepEntity.java','ItemStack stack = player.getItemInHand(hand);','ItemStack stack = player.getItemInHand(hand);\n        if(player instanceof ServerPlayer p&&stack.getItem() instanceof com.tnc.tnc.life.pasture.PastureStaffItem&&(caretaker==null||caretaker.equals(p.getUUID()))){stack.getOrCreateTag().putUUID("Animal",getUUID());stack.getOrCreateTag().putString("Dimension",level().dimension().location().toString());return InteractionResult.SUCCESS;}')
change('life/fauna/BellwoolSheepEntity.java','nextHarvestTick = now + HARVEST_INTERVAL;','nextHarvestTick = now + HARVEST_INTERVAL;\n        com.tnc.tnc.life.routes.RouteProgress.award(player,"harvest/bellwool_sheep");')
change('life/pasture/PastureStaffItem.java','if(!(player.serverLevel().getEntity(tag.getUUID("Animal")) instanceof PastureAnimal animal)','if(player.serverLevel().getEntity(tag.getUUID("Animal")) instanceof com.tnc.tnc.life.fauna.BellwoolSheepEntity sheep)return com.tnc.tnc.life.routes.RouteBellCare.setHome(player,sheep,context.getClickedPos());\n        if(!(player.serverLevel().getEntity(tag.getUUID("Animal")) instanceof PastureAnimal animal)')
# No teleport or falling loop counts as powered movement.
change('life/pasture/PastureAnimal.java','double moved=Math.min(3,position().distanceTo(before));','double distance=position().distanceTo(before);double moved=distance<=3?distance:0;')
change('life/pasture/PastureAnimal.java','case "springhoof_strider"->Math.min(2,moved);','case "springhoof_strider"->onGround()?Math.min(2,moved):0;')
change('life/pasture/PastureAnimal.java','double route=t.getDouble("RollDistance")+moved;','double route=t.getDouble("RollDistance")+(onGround()?moved:0);')
# Four legacy plants can occur as native wild specimens; player placements remain cultivated.
wild='com.tnc.tnc.life.routes.RoutePlantBlock.WILD'
for path,a,b in [
 ('life/HomewardFlower.java','builder.add(BLOOMING);','builder.add(BLOOMING,'+wild+');'),
 ('life/ManaRoot.java','builder.add(AGE, FED);','builder.add(AGE, FED,'+wild+');'),
 ('life/WarningMoss.java','builder.add(FACING, LIT, AGE);','builder.add(FACING, LIT, AGE,'+wild+');'),
 ('life/wonders/SkyVineRootBlock.java','b.add(AGE);','b.add(AGE,'+wild+');')]:change(path,a,b)
for path,a,b in [
 ('life/HomewardFlower.java','setValue(BLOOMING, false)','setValue(BLOOMING, false).setValue('+wild+',false)'),
 ('life/ManaRoot.java','setValue(FED, 0)','setValue(FED, 0).setValue('+wild+',false)'),
 ('life/WarningMoss.java','setValue(AGE, 0)','setValue(AGE, 0).setValue('+wild+',false)'),
 ('life/wonders/SkyVineRootBlock.java','setValue(AGE,0)','setValue(AGE,0).setValue('+wild+',false)')]:change(path,a,b)
# Keep the native early access recipe: three stone, two copper, one glass.
def dump(p,x):p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
dump(S/'data/tnc/recipes/mana_infuser.json',{'type':'minecraft:crafting_shaped','pattern':[' G ','C C','SSS'],'key':{'G':{'item':'minecraft:glass'},'C':{'item':'minecraft:copper_ingot'},'S':{'item':'minecraft:stone'}},'result':{'item':'tnc:mana_infuser'}})
for id,biomes in [('prismatic_antelope',['minecraft:meadow','minecraft:savanna','minecraft:plains']),('drumbelly_otter',['minecraft:desert','minecraft:savanna','minecraft:badlands'])]:
 dump(S/f'data/tnc/tags/worldgen/biome/pasture_{id}.json',{'replace':False,'values':biomes})
 dump(S/f'data/tnc/forge/biome_modifier/pasture_{id}.json',{'type':'forge:add_spawns','biomes':'#tnc:pasture_'+id,'spawners':{'type':'tnc:'+id,'weight':1,'minCount':1,'maxCount':2}})
dump(S/'data/tnc/recipes/prism_horn_target.json',{'type':'minecraft:crafting_shapeless','ingredients':[{'item':'tnc:prism_horn_shard'},{'item':'tnc:spirit_iron'},{'item':'minecraft:stone_bricks'}],'result':{'item':'tnc:color_target'}})
dump(S/'data/tnc/recipes/sand_otter_cloth.json',{'type':'minecraft:crafting_shaped','pattern':['FF','FF'],'key':{'F':{'item':'tnc:sand_otter_fiber'}},'result':{'item':'tnc:mist_cloth'}})
