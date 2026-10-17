package com.tnc.tnc.life.routes;
import java.util.*;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import com.mojang.math.Axis;
import com.mojang.blaze3d.platform.Lighting;
/** Opt-in native rendering in the isolated development world, never in player worlds. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class RouteVisualAudit {
 private static int page,ticks;private static boolean initialized;private static volatile boolean saved,saving;
 private static final List<String> failures=new ArrayList<>();
 private static List<Item> items;
 private record Plant(String name,net.minecraft.world.level.block.Block block){}
 private static final List<Plant> plants=new ArrayList<>();
 private static final int PAGES=17;
 private static volatile boolean worldReady;
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{if(!Boolean.getBoolean("tnc.lifeRoutesVisualAudit")||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;if(!initialized){initialized=true;mc.options.guiScale().set(1);mc.options.pauseOnLostFocus=false;mc.resizeDisplay();items=RouteContent.ITEMS.getEntries().stream().map(net.minecraftforge.registries.RegistryObject::get).toList();try(var reader=new java.io.InputStreamReader(RouteVisualAudit.class.getResourceAsStream("/data/tnc/life_routes/catalogue.json"),java.nio.charset.StandardCharsets.UTF_8)){for(var raw:com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("magic")){var entry=raw.getAsJsonObject();var b=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc",entry.get("id").getAsString()));plants.add(new Plant(entry.get("name").getAsString(),b));}}audit(mc);mc.setScreen(new Gallery(0));}if(++ticks<(page>=14?100:25)||saving||page>=14&&!worldReady)return;if(!saved){saving=true;Screenshot.grab(mc.gameDirectory,"life-routes-"+page+".png",mc.getMainRenderTarget(),message->{saved=true;saving=false;});return;}if(++page<PAGES){ticks=0;saved=false;if(page==13){var state=RouteContent.NODES.get("accessory_charger").get().defaultBlockState();var be=new RouteNodeEntity(BlockPos.ZERO,state);be.setLevel(mc.level);be.mana=650;be.inventory.setItem(0,new ItemStack(RouteContent.item("dew_ward_charm")));mc.setScreen(new RouteClient.Screen(new RouteMenu(0,mc.player.getInventory(),be),mc.player.getInventory(),be.getDisplayName()));}else if(page==14){mc.setScreen(null);setupWorld(mc);}else if(page==15){com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(true);}else if(page==16){com.tnc.tnc.mana.sight.client.ManaSightClient.setEnabled(false);mc.setScreen(new Gallery(page));}else mc.setScreen(new Gallery(page));return;}var report=new LinkedHashMap<String,Object>();report.put("new_items",items.size());report.put("plants",plants.size());report.put("network_blocks",RouteContent.NODES.size());report.put("animal_types",com.tnc.tnc.life.pasture.PastureRegistry.TYPES.size());report.put("screenshots",PAGES);report.put("failures",failures);report.put("scope","Actual native client models and GUI, isolated development world; full PCL playthrough remains user testing.");Files.writeString(Path.of("../work/life-routes-client-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();}
 private static void setupWorld(Minecraft mc){
  mc.getSingleplayerServer().execute(()->{
   var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=player.serverLevel();
   var origin=new BlockPos(1024,220,1024);
   for(var old:level.getEntitiesOfClass(com.tnc.tnc.tavern.TavernGuestEntity.class,new net.minecraft.world.phys.AABB(origin).inflate(20)))old.discard();
   for(var p:BlockPos.betweenClosed(origin.offset(-2,-1,-1),origin.offset(10,4,15))){level.getChunkAt(p);level.setBlock(p,p.getY()==219?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():p.getY()==220?RouteContent.SOIL.get().defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);}
   for(int z=0;z<=10;z++)level.setBlock(origin.offset(-1,0,z),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
   level.setBlock(origin.offset(-1,0,4),RouteContent.MANA_LIQUID.get().defaultBlockState(),3);
   String[] kinds={"homeward_flower","dawn_disk","dream_compost","thermal_foldleaf","dance_bell","stone_fern","echo_bean","mirror_lotus","prism_crown","turnpage_fern","hearth_pepper","star_dew"};
   for(int i=0;i<kinds.length;i++){
    var block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc",kinds[i]));var state=block.defaultBlockState();
    for(var prop:state.getProperties())if(prop.getName().equals("age")&&prop instanceof net.minecraft.world.level.block.state.properties.IntegerProperty age)state=state.setValue(age,age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(3));
    if(state.hasProperty(com.tnc.tnc.life.HomewardFlower.FlowerBlock.BLOOMING))state=state.setValue(com.tnc.tnc.life.HomewardFlower.FlowerBlock.BLOOMING,true);
    var p=origin.offset(1+i%6,1,2+i/6*3);level.setBlock(p,state,3);
    if(level.getBlockEntity(p) instanceof com.tnc.tnc.life.botanical.BotanicalPlantEntity be){be.owner=player.getUUID();be.growth=be.species().firstTicks;be.ready=true;be.changed();}
    if(level.getBlockEntity(p) instanceof RoutePlantEntity be){be.owner=player.getUUID();be.growth=18000;be.setChanged();}
   }
   for(int i=0;i<3;i++){
    var p=origin.offset(2+i*2,1,10);level.setBlock(p,net.minecraft.world.level.block.Blocks.OAK_SLAB.defaultBlockState(),3);
    var guest=com.tnc.tnc.npc.TNNpcs.TAVERN_GUEST.get().create(level);guest.configure(new com.tnc.tnc.tavern.TavernAtmosphere.Seat("audit-"+i,BlockPos.ZERO,"minecraft:oak_slab",.5,0,2+i,"歇脚的镇民",List.of("今天天气不错。")),p);level.addFreshEntity(guest);
   }
   var stock=new RouteNodeEntity[4];var sites=List.of(origin.offset(0,1,12),origin.offset(1,1,12),origin.offset(1,2,12),origin.offset(2,2,12));var types=List.of(RouteKind.POOL,RouteKind.WIRE,RouteKind.GOLD_WIRE,RouteKind.CHARGER);
   for(int i=0;i<4;i++){var at=sites.get(i);level.setBlock(at,RouteContent.NODES.get(types.get(i).id).get().defaultBlockState(),3);stock[i]=(RouteNodeEntity)level.getBlockEntity(at);stock[i].owner=player.getUUID();RouteNetwork.add(level,at);}
   stock[0].mana=500;for(var at:sites)if(level.getBlockState(at).getBlock() instanceof RouteWireBlock wire)wire.refresh(level,at);
   level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);player.connection.teleport(1028.5,222,1038.5,180,18);worldReady=true;
  });
 }
 private static void audit(Minecraft mc){var missing=mc.getModelManager().getMissingModel();for(var item:items){var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);if(mc.getModelManager().getModel(new ModelResourceLocation(id,"inventory"))==missing)failures.add("Missing item: "+id);}for(var block:RouteContent.BLOCKS.getEntries())if(block.get()!=RouteContent.MANA_LIQUID.get())for(var state:block.get().getStateDefinition().getPossibleStates())if(mc.getBlockRenderer().getBlockModel(state)==missing)failures.add("Missing block: "+state);for(var plant:plants)for(var state:plant.block.getStateDefinition().getPossibleStates())if(mc.getBlockRenderer().getBlockModel(state)==missing)failures.add("Missing plant: "+state);for(var type:com.tnc.tnc.life.pasture.PastureRegistry.TYPES.entrySet())if(mc.getEntityRenderDispatcher().getRenderer(type.getValue().create(mc.level))==null)failures.add("Missing renderer: "+type.getKey());mc.getEntityModels().bakeLayer(RouteMechModel.LAYER);}
 private static final class Gallery extends Screen {
  private final int page;Gallery(int p){super(Component.literal("生活与魔导视觉验收"));page=p;}@Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff17383c);g.drawString(font,"生活与魔导 · 蓝绿色土壤 / 独立模型 · 实际客户端渲染 "+page,24,20,0xc8eedc,false);
   if(page<2){for(int i=0;i<48&&page*48+i<items.size();i++){var item=items.get(page*48+i);int x=30+i%8*154,y=65+i/8*100;g.fill(x,y,x+124,y+74,0xff84998f);g.pose().pushPose();g.pose().translate(x+32,y+10,0);g.pose().scale(3.3f,3.3f,1);g.renderItem(new ItemStack(item),0,0);g.pose().popPose();g.drawString(font,font.plainSubstrByWidth(new ItemStack(item).getHoverName().getString(),142),x,y+79,0xc8eedc,false);}}
   else if(page<10){for(int row=0;row<5&&(page-2)*5+row<plants.size();row++){var plant=plants.get((page-2)*5+row);int y=105+row*117;g.drawString(font,plant.name,20,y,0xc8eedc,false);for(int stage=0;stage<4;stage++)block(g,stage(plant.block.defaultBlockState(),stage),310+stage*230,y+30,65,true);}}
   else if(page<12){var kinds=RouteKind.values();for(int i=0;i<16&&(page-10)*16+i<kinds.length;i++){var kind=kinds[(page-10)*16+i];int x=140+i%4*305,y=110+i/4*148;block(g,RouteContent.NODES.get(kind.id).get().defaultBlockState(),x,y+55,76,false);g.drawString(font,kind.name,x-70,y+90,0xc8eedc,false);}}
   else if(page==16){int i=0;for(var kind:List.of(RouteKind.WIRE,RouteKind.GOLD_WIRE,RouteKind.CRYSTAL_WIRE)){int x=200+i++*390;var state=RouteContent.NODES.get(kind.id).get().defaultBlockState();block(g,state.setValue(RouteWireBlock.ARMS.get(net.minecraft.core.Direction.WEST),true).setValue(RouteWireBlock.ARMS.get(net.minecraft.core.Direction.EAST),true),x,200,125,false);block(g,state.setValue(RouteWireBlock.ARMS.get(net.minecraft.core.Direction.DOWN),true).setValue(RouteWireBlock.ARMS.get(net.minecraft.core.Direction.SOUTH),true),x,390,125,false);g.drawString(font,kind.name,x-70,460,0xc8eedc,false);}g.drawString(font,"每段最多两个接口 · 直连 / 转角 / 竖接",400,600,0xc8eedc,false);}
   else{block(g,RouteContent.SOIL.get().defaultBlockState(),200,260,130,false);block(g,RouteContent.SOIL_SOURCE.get().defaultBlockState(),500,260,130,false);g.drawString(font,"魔法土：细纹",130,315,0xc8eedc,false);g.drawString(font,"魔法土源：更强流光",420,315,0xc8eedc,false);beast(g,"prismatic_antelope",880,330,110);beast(g,"drumbelly_otter",1090,575,120);g.drawString(font,"曳彩角羚",800,375,0xc8eedc,false);g.drawString(font,"鼓腹砂獭",1010,610,0xc8eedc,false);}
  }
  @SuppressWarnings({"rawtypes","unchecked"}) private BlockState stage(BlockState state,int stage){for(var prop:state.getProperties()){if(prop.getName().equals("age")&&prop instanceof net.minecraft.world.level.block.state.properties.IntegerProperty age){int max=age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(3);state=state.setValue(age,stage*max/3);}else if(prop.getName().equals("blooming"))state=state.setValue((net.minecraft.world.level.block.state.properties.Property)prop,stage>=2);}return state;}
  private void block(GuiGraphics g,BlockState s,int x,int y,float scale,boolean soil){g.flush();Lighting.setupFor3DItems();var pose=g.pose();pose.pushPose();pose.translate(x,y,160);pose.scale(scale,-scale,scale);pose.mulPose(Axis.XP.rotationDegrees(25));pose.mulPose(Axis.YP.rotationDegrees(225));pose.translate(-.5,0,-.5);var buffer=minecraft.renderBuffers().bufferSource();if(soil){pose.pushPose();if(s.getBlock() instanceof com.tnc.tnc.life.WarningMoss.MossBlock)pose.translate(0,0,1);else pose.translate(0,-1,0);minecraft.getBlockRenderer().renderSingleBlock(RouteContent.SOIL.get().defaultBlockState(),pose,buffer,15728880,OverlayTexture.NO_OVERLAY);pose.popPose();}if(s.getRenderShape()==net.minecraft.world.level.block.RenderShape.ENTITYBLOCK_ANIMATED){var be=((net.minecraft.world.level.block.EntityBlock)s.getBlock()).newBlockEntity(BlockPos.ZERO,s);if(be!=null){be.setLevel(minecraft.level);minecraft.getBlockEntityRenderDispatcher().renderItem(be,pose,buffer,15728880,OverlayTexture.NO_OVERLAY);}}else minecraft.getBlockRenderer().renderSingleBlock(s,pose,buffer,15728880,OverlayTexture.NO_OVERLAY);buffer.endBatch();pose.popPose();Lighting.setupForFlatItems();}
  private void beast(GuiGraphics g,String id,int x,int y,float scale){var animal=com.tnc.tnc.life.pasture.PastureRegistry.TYPES.get(id).create(minecraft.level);animal.setYRot(135);g.flush();Lighting.setupFor3DItems();var pose=g.pose();pose.pushPose();pose.translate(x,y,150);pose.scale(scale,-scale,scale);pose.mulPose(Axis.XP.rotationDegrees(15));pose.mulPose(Axis.YP.rotationDegrees(35));var dispatcher=minecraft.getEntityRenderDispatcher();dispatcher.setRenderShadow(false);var buffer=minecraft.renderBuffers().bufferSource();dispatcher.render(animal,0,0,0,0,0,pose,buffer,15728880);buffer.endBatch();dispatcher.setRenderShadow(true);pose.popPose();Lighting.setupForFlatItems();}
 }
}
