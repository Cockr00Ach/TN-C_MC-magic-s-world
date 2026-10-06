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
 @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e)throws Exception{if(!Boolean.getBoolean("tnc.lifeRoutesVisualAudit")||e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;if(!initialized){initialized=true;mc.options.guiScale().set(1);mc.resizeDisplay();items=RouteContent.ITEMS.getEntries().stream().map(net.minecraftforge.registries.RegistryObject::get).toList();audit(mc);mc.setScreen(new Gallery(0));}if(++ticks<25||saving)return;if(!saved){saving=true;Screenshot.grab(mc.gameDirectory,"life-routes-"+page+".png",mc.getMainRenderTarget(),message->{saved=true;saving=false;});return;}if(++page<8){ticks=0;saved=false;if(page==7){var state=RouteContent.NODES.get("accessory_charger").get().defaultBlockState();var be=new RouteNodeEntity(BlockPos.ZERO,state);be.setLevel(mc.level);be.mana=650;be.inventory.setItem(0,new ItemStack(RouteContent.item("dew_ward_charm")));mc.setScreen(new RouteClient.Screen(new RouteMenu(0,mc.player.getInventory(),be),mc.player.getInventory(),be.getDisplayName()));}else mc.setScreen(new Gallery(page));return;}var report=new LinkedHashMap<String,Object>();report.put("new_items",items.size());report.put("plants",9);report.put("network_blocks",RouteContent.NODES.size());report.put("animal_types",com.tnc.tnc.life.pasture.PastureRegistry.TYPES.size());report.put("screenshots",8);report.put("failures",failures);report.put("scope","Actual native client models and GUI, isolated development world; full PCL playthrough remains user testing.");Files.writeString(Path.of("../work/life-routes-client-audit.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));mc.stop();}
 private static void audit(Minecraft mc){var missing=mc.getModelManager().getMissingModel();for(var item:items){var id=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item);if(mc.getModelManager().getModel(new ModelResourceLocation(id,"inventory"))==missing)failures.add("Missing item: "+id);}for(var block:RouteContent.BLOCKS.getEntries())if(block.get()!=RouteContent.MANA_LIQUID.get())for(var state:block.get().getStateDefinition().getPossibleStates())if(mc.getBlockRenderer().getBlockModel(state)==missing)failures.add("Missing block: "+state);for(var type:com.tnc.tnc.life.pasture.PastureRegistry.TYPES.entrySet())if(mc.getEntityRenderDispatcher().getRenderer(type.getValue().create(mc.level))==null)failures.add("Missing renderer: "+type.getKey());mc.getEntityModels().bakeLayer(RouteMechModel.LAYER);}
 private static final class Gallery extends Screen {
  private final int page;Gallery(int p){super(Component.literal("生活与魔导视觉验收"));page=p;}@Override public boolean isPauseScreen(){return false;}
  @Override public void render(GuiGraphics g,int mx,int my,float partial){g.fill(0,0,width,height,0xff17383c);g.drawString(font,"生活与魔导 · 蓝绿色土壤 / 独立模型 · 实际客户端渲染 "+page,24,20,0xc8eedc,false);
   if(page<2){for(int i=0;i<48&&page*48+i<items.size();i++){var item=items.get(page*48+i);int x=30+i%8*154,y=65+i/8*100;g.fill(x,y,x+124,y+74,0xff84998f);g.pose().pushPose();g.pose().translate(x+32,y+10,0);g.pose().scale(3.3f,3.3f,1);g.renderItem(new ItemStack(item),0,0);g.pose().popPose();g.drawString(font,font.plainSubstrByWidth(new ItemStack(item).getHoverName().getString(),142),x,y+79,0xc8eedc,false);}}
   else if(page<4){var kinds=NewPlantKind.values();for(int row=0;row<5&&(page-2)*5+row<kinds.length;row++){var kind=kinds[(page-2)*5+row];int y=100+row*117;g.drawString(font,kind.name,20,y,0xc8eedc,false);for(int stage=0;stage<4;stage++)block(g,RouteContent.PLANTS.get(kind.id).get().defaultBlockState().setValue(RoutePlantBlock.AGE,stage),310+stage*230,y+70,75,true);}}
   else if(page<6){var kinds=RouteKind.values();for(int i=0;i<16&&(page-4)*16+i<kinds.length;i++){var kind=kinds[(page-4)*16+i];int x=140+i%4*305,y=110+i/4*148;block(g,RouteContent.NODES.get(kind.id).get().defaultBlockState(),x,y+55,76,false);g.drawString(font,kind.name,x-70,y+90,0xc8eedc,false);}}
   else{block(g,RouteContent.SOIL.get().defaultBlockState(),200,260,130,false);block(g,RouteContent.SOIL_SOURCE.get().defaultBlockState(),500,260,130,false);g.drawString(font,"魔法土：细纹",130,315,0xc8eedc,false);g.drawString(font,"魔法土源：更强流光",420,315,0xc8eedc,false);beast(g,"prismatic_antelope",880,330,110);beast(g,"drumbelly_otter",1090,575,120);g.drawString(font,"曳彩角羚",800,375,0xc8eedc,false);g.drawString(font,"鼓腹砂獭",1010,610,0xc8eedc,false);}
  }
  private void block(GuiGraphics g,BlockState s,int x,int y,float scale,boolean soil){g.flush();Lighting.setupFor3DItems();var pose=g.pose();pose.pushPose();pose.translate(x,y,160);pose.scale(scale,-scale,scale);pose.mulPose(Axis.XP.rotationDegrees(25));pose.mulPose(Axis.YP.rotationDegrees(225));pose.translate(-.5,0,-.5);var buffer=minecraft.renderBuffers().bufferSource();if(soil){pose.pushPose();pose.translate(0,-.15,0);pose.scale(1,.15f,1);minecraft.getBlockRenderer().renderSingleBlock(RouteContent.SOIL.get().defaultBlockState(),pose,buffer,15728880,OverlayTexture.NO_OVERLAY);pose.popPose();}minecraft.getBlockRenderer().renderSingleBlock(s,pose,buffer,15728880,OverlayTexture.NO_OVERLAY);buffer.endBatch();pose.popPose();Lighting.setupForFlatItems();}
  private void beast(GuiGraphics g,String id,int x,int y,float scale){var animal=com.tnc.tnc.life.pasture.PastureRegistry.TYPES.get(id).create(minecraft.level);animal.setYRot(135);g.flush();Lighting.setupFor3DItems();var pose=g.pose();pose.pushPose();pose.translate(x,y,150);pose.scale(scale,-scale,scale);pose.mulPose(Axis.XP.rotationDegrees(15));pose.mulPose(Axis.YP.rotationDegrees(35));var dispatcher=minecraft.getEntityRenderDispatcher();dispatcher.setRenderShadow(false);var buffer=minecraft.renderBuffers().bufferSource();dispatcher.render(animal,0,0,0,0,0,pose,buffer,15728880);buffer.endBatch();dispatcher.setRenderShadow(true);pose.popPose();Lighting.setupForFlatItems();}
 }
}
