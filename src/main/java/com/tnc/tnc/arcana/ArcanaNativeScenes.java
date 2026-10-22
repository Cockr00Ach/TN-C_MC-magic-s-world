package com.tnc.tnc.arcana;
import com.tnc.tnc.life.routes.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
/** Explicit opt-in, disposable full-pack world only. Never runs during normal play. */
public final class ArcanaNativeScenes {
 public static void prepare(ServerPlayer p,int scene,BlockPos base){
  if(scene>=23){feedback(p,scene,base.offset(0,0,30));return;}
  var l=p.serverLevel();p.teleportTo(p.server.overworld(),base.getX()+.5,base.getY()+4.5,base.getZ()+12.5,180,22);l=p.serverLevel();l.setDayTime(scene==15||scene==16||scene>=19?6000:18000);
  if(scene==19){for(var spirit:l.getEntitiesOfClass(DimensionalSpirit.class,p.getBoundingBox().inflate(48)))spirit.discard();ArcanaPhysiology.data(p).putDouble("vision_sanity",0);}
  for(int x=-9;x<=9;x++)for(int z=-6;z<=14;z++)l.setBlock(base.offset(x,-1,z),Blocks.DEEPSLATE_TILES.defaultBlockState(),3);
  if(scene==14){var inv=new net.minecraft.world.SimpleContainer(54);int i=0;for(String id:ArcanaContent.GOODS)inv.setItem(i++,new ItemStack(ArcanaContent.item(id)));for(var k:RouteKind.values())if(k.ordinal()>=RouteKind.ABYSS_TOWER.ordinal()){if(i<54)inv.setItem(i++,new ItemStack(RouteContent.item(k.id)));};for(String id:new String[]{"thorn_tyrant_vine","emerald_lifemoss","predator_bud","tailwind_boots"}){if(i<54)inv.setItem(i++,new ItemStack(ArcanaContent.item(id)));}p.openMenu(new net.minecraft.world.SimpleMenuProvider((id,pi,pl)->net.minecraft.world.inventory.ChestMenu.sixRows(id,pi,inv),net.minecraft.network.chat.Component.literal("深层魔法 · 遗物与工程")));return;}
  if(scene==15){int i=0;for(var kind:new RouteKind[]{RouteKind.ABYSS_TOWER,RouteKind.SOLAR,RouteKind.LUNAR,RouteKind.NURTURE,RouteKind.MEAL_PLATE,RouteKind.GEOTHERMAL,RouteKind.RESTRAINT,RouteKind.SPRAYER,RouteKind.OBSERVATORY,RouteKind.SKY_LAUNCHER,RouteKind.SKY_RELAY,RouteKind.SINGULARITY}){var pos=base.offset(-6+(i%4)*4,0,-3+(i/4)*4);l.setBlock(pos,RouteContent.NODES.get(kind.id).get().defaultBlockState(),3);var n=(RouteNodeEntity)l.getBlockEntity(pos);n.owner=p.getUUID();n.mana=500; if(kind==RouteKind.ABYSS_TOWER){for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<3;y++)if(x!=0||z!=0)l.setBlock(pos.offset(x,y,z),ArcanaContent.TOWER_BRICK.defaultBlockState(),3);n.getPersistentData().putDouble("overload_level",58);}
    if(kind==RouteKind.NURTURE){n.inventory.setItem(0,new ItemStack(ArcanaContent.item("fire_wand_3")));n.inventory.getItem(0).getOrCreateTag().putString("SpiritElement","fire");}
    if(kind==RouteKind.MEAL_PLATE){Item[] foods={ArcanaContent.item("hearth_pepper_fruit"),ArcanaContent.item("homeward_flower_petal"),ArcanaContent.item("rainletter_berry"),ArcanaContent.item("dance_petal"),ArcanaContent.item("stone_pattern_leaf"),ArcanaContent.item("star_dew_fruit"),ArcanaContent.item("honey_powder"),ArcanaContent.item("prism_crown_petal"),ArcanaContent.item("farlight_fruit")};for(int j=0;j<9;j++)n.inventory.setItem(j,new ItemStack(foods[j]));}n.setChanged();i++;}}
  com.tnc.tnc.mana.sight.ManaSightServer.subscribe(p,scene>=16);
  if(scene==17){ArcanaSky.forceTonight(l);p.connection.teleport(p.getX(),p.getY()+4,p.getZ(),175,-38);}
  if(scene==18){var spirit=ArcanaContent.SPIRIT.create(l);spirit.owner=p.getUUID();spirit.setPos(p.getX(),p.getY(),p.getZ()-3);l.addFreshEntity(spirit);ArcanaPhysiology.data(p).putDouble("vision_sanity",90);var packet=new net.minecraft.nbt.CompoundTag();packet.putString("Type","contract");packet.putInt("Entity",spirit.getId());ArcanaNetwork.sync(p,packet);}
  if(scene==19){p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new ItemStack(ArcanaContent.item("thorn_tyrant_vine")));}
  if(scene==20){var n=(RouteNodeEntity)l.getBlockEntity(base.offset(-6,0,-3));n.getPersistentData().putDouble("overload_level",90);n.setChanged();var b=ArcanaContent.DEVOURER.create(l);b.owner=p.getUUID();b.setPos(base.getX()+1,base.getY()+1,base.getZ()-1);b.setNoAi(true);l.addFreshEntity(b);}
  if(scene==21){ArcanaPhysiology.data(p).putDouble("vision_sanity",100);ArcanaPhysiology.data(p).putLong("NextSpirit",Long.MAX_VALUE);
   for(int i=0;i<3;i++){var a=(i==1?net.minecraft.world.entity.EntityType.SHEEP:net.minecraft.world.entity.EntityType.COW).create(l);a.setNoAi(true);a.setInvulnerable(true);a.setPos(base.getX()-2+i*2,base.getY(),base.getZ()+5);l.addFreshEntity(a);}
  }
 }
 private static RouteNodeEntity put(ServerPlayer p,BlockPos at,RouteKind kind){p.serverLevel().setBlock(at,RouteContent.NODES.get(kind.id).get().defaultBlockState(),3);var n=(RouteNodeEntity)p.serverLevel().getBlockEntity(at);n.owner=p.getUUID();return n;}
 private static void feedback(ServerPlayer p,int scene,BlockPos base){
  var l=p.server.overworld();p.teleportTo(l,base.getX()+.5,base.getY()+2.4,base.getZ()+5.5,180,20);p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,ItemStack.EMPTY);l.setDayTime(18000);for(var spirit:l.getEntitiesOfClass(DimensionalSpirit.class,p.getBoundingBox().inflate(60)))spirit.discard();ArcanaPhysiology.data(p).putDouble("vision_sanity",0);ArcanaPhysiology.data(p).putLong("NextSpirit",Long.MAX_VALUE);
  for(int x=-4;x<=12;x++)for(int z=-10;z<=8;z++){for(int y=0;y<=5;y++)l.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);l.setBlock(base.offset(x,-1,z),Blocks.DEEPSLATE_TILES.defaultBlockState(),3);}
  for(int x:new int[]{-3,3,10})l.setBlock(base.offset(x,-1,3),Blocks.SEA_LANTERN.defaultBlockState(),3);com.tnc.tnc.mana.sight.ManaSightServer.subscribe(p,true);
  if(scene==23){var spirit=ArcanaContent.SPIRIT.create(l);spirit.owner=p.getUUID();spirit.setPos(base.getX()+.5,base.getY(),base.getZ()+.5);l.addFreshEntity(spirit);ArcanaPhysiology.data(p).putDouble("vision_sanity",90);}
  if(scene==24){var a=put(p,base,RouteKind.SKY_LAUNCHER);var b=put(p,base.offset(8,0,0),RouteKind.SKY_RELAY);var c=put(p,base.offset(8,0,-8),RouteKind.SKY_RELAY);a.getPersistentData().putLong("SkyTarget",b.getBlockPos().asLong());b.getPersistentData().putLong("SkyTarget",c.getBlockPos().asLong());a.mana=5000;p.connection.teleport(base.getX()+3,base.getY()+3.2,base.getZ()+8,180,24);ArcanaHighway.tick(l,a);}
  if(scene==25){var n=put(p,base,RouteKind.MEAL_PLATE);String[] foods={"hearth_pepper_fruit","dance_petal","rainletter_berry","stone_pattern_leaf","homeward_flower_petal","rainletter_berry","star_dew_fruit","mana_root_core","steam_lotus_heart"};for(int i=0;i<9;i++)n.inventory.setItem(i,new ItemStack(ArcanaContent.item(foods[i])));n.setChanged();l.sendBlockUpdated(base,n.getBlockState(),n.getBlockState(),3);p.connection.teleport(base.getX()+.5,base.getY()+2.3,base.getZ()+2.6,180,43);}
  if(scene==26){var n=put(p,base,RouteKind.ABYSS_TOWER);for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)for(int y=0;y<3;y++)if(x!=0||z!=0)l.setBlock(base.offset(x,y,z),ArcanaContent.TOWER_BRICK.defaultBlockState(),3);var at=base.offset(3,0,0);l.setBlock(at.below(),RouteContent.SOIL.get().defaultBlockState(),3);l.setBlock(at.offset(0,-1,1),RouteContent.MANA_LIQUID.get().defaultBlockState(),3);l.setBlock(at,RouteContent.PLANTS.get(NewPlantKind.STEAM.id).get().defaultBlockState().setValue(RoutePlantBlock.AGE,3),3);var plant=(RoutePlantEntity)l.getBlockEntity(at);plant.owner=p.getUUID();plant.growth=18000;plant.mana=500;p.connection.teleport(base.getX()+3,base.getY()+3.5,base.getZ()+8,160,20);}
 }

}
