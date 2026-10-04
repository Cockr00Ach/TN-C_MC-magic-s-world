package com.tnc.tnc.life.pasture.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.life.botanical.*;
import com.tnc.tnc.life.pasture.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.*;
import java.util.*;

/** Explicit dev-only actual client rendering audit, always in a separate world. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class EcologyVisualAudit {
    private static int frames,page;
    private static volatile boolean saving,saved;
    private static final List<String> failures=new ArrayList<>();
    private static List<String> items;
    private static int checkedStates;
    private static final List<String> plants=List.of("dawn_disk","hearth_pepper","mist_cotton","stone_fern","mirror_lotus","wish_puff","echo_bean","ladder_vine","frost_chime","salt_ink","wind_sail","sleep_clock","shadow_cut","paper_tree","flight_pod","honey_cluster","star_dew","dance_bell","star_rest","homeward_flower","warning_moss","road_bell_crop","night_gourd_crop","tide_reed_crop","hushcap_mushroom","rainletter_bush","mana_root","verdant_vein","sky_vine");
    private static final List<String> machines=List.of("mana_generator","mana_battery","mana_work_lamp","mana_paper_press","mana_cable","mana_bottle_base","magic_forge","forge_core","forge_exhaust","forge_firebrick","pasture_trough","pasture_tray","egg_rack","dew_rack","habitat_marker","charging_perch");

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception {
        if(!Boolean.getBoolean("tnc.ecologyVisualAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(items==null){
            mc.options.guiScale().set(1);mc.resizeDisplay();
            var ids=JsonParser.parseString(Files.readString(Path.of(System.getProperty("tnc.visualAuditManifest")))).getAsJsonObject().getAsJsonArray("ids");
            items=new ArrayList<>();ids.forEach(id->items.add(id.getAsString()));audit(mc);mc.setScreen(new Gallery(0));frames=0;
        }
        if(++frames<24||saving)return;
        if(!saved){saving=true;Screenshot.grab(mc.gameDirectory,String.format("ecology-art-%02d.png",page+1),mc.getMainRenderTarget(),m->{System.out.println("TN-C VISUAL AUDIT: "+m.getString());saved=true;saving=false;});return;}
        if(++page<14){saved=false;frames=0;mc.setScreen(new Gallery(page));return;}
        var report=new LinkedHashMap<String,Object>();report.put("items",items.size());report.put("plant_species",plants.size());report.put("baked_plant_states",checkedStates);report.put("screenshots",14);report.put("machine_inventory_models",machines.size());report.put("feed_models",7);report.put("failures",failures);report.put("scope","Actual Minecraft inventory, block and block-entity rendering in a dev gallery; not a full modpack playthrough");
        Files.writeString(Path.of(System.getProperty("tnc.visualAuditReport")),new GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("TN-C VISUAL AUDIT "+(failures.isEmpty()?"PASS":"FAIL")+": "+report);mc.stop();
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("tnc",path);}
    private static Block block(String name){return ForgeRegistries.BLOCKS.getValue(id(name));}
    private static void audit(Minecraft mc){
        var models=mc.getModelManager();var missing=models.getMissingModel();
        for(String name:items){Item item=ForgeRegistries.ITEMS.getValue(id(name));if(item==null||item==Items.AIR||models.getModel(new ModelResourceLocation(id(name),"inventory"))==missing)failures.add("Item missing: "+name);}
        for(String name:plants){Block b=block(name);if(b==null||b==Blocks.AIR){failures.add("Plant missing: "+name);continue;}for(var s:b.getStateDefinition().getPossibleStates()){checkedStates++;if(mc.getBlockRenderer().getBlockModel(s)==missing)failures.add("Plant state missing: "+s);}}
        for(String name:machines){Block b=block(name);if(b==null||b==Blocks.AIR||mc.getBlockRenderer().getBlockModel(b.defaultBlockState())==missing)failures.add("Machine missing: "+name);if(models.getModel(new ModelResourceLocation(id(name),"inventory"))==missing)failures.add("Machine inventory missing: "+name);}
        for(String name:List.of("hay","grain","roots","fruit","fish","fungus","pellets"))if(models.getModel(id("block/feed_"+name))==missing)failures.add("Feed model missing: "+name);
    }
    @SuppressWarnings({"rawtypes","unchecked"}) private static BlockState stage(Block b,int stage){
        var state=b.defaultBlockState();var age=b.getStateDefinition().getProperty("age");
        if(age instanceof IntegerProperty integer){var values=new ArrayList<>(integer.getPossibleValues());Collections.sort(values);state=state.setValue(integer,values.get(Math.round((values.size()-1)*stage/3F)));}
        var bloom=b.getStateDefinition().getProperty("blooming");if(bloom instanceof BooleanProperty bp)state=state.setValue(bp,stage>=2);
        var fed=b.getStateDefinition().getProperty("fed");if(fed instanceof IntegerProperty ip)state=state.setValue(ip,stage==3?Collections.max(ip.getPossibleValues()):stage);
        return state;
    }
    private static final class Gallery extends Screen {
        private final int p;
        Gallery(int page){super(Component.literal("Ecology asset gallery"));p=page;}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
            g.fill(0,0,width,height,0xFF202327);g.fill(0,0,width,48,0xFF15181C);
            String title=p<3?"原创物品 / 实际客户端模型与贴图 / "+(p+1):p<9?"种下后的植物 / 四个成长阶段 / "+(p-2):p==9?"魔导工坊与牧场设施 / 原生方块模型":"食槽实存饲料 / 材料与数量决定外观";
            g.drawString(font,title,20,18,0xE5D6B8,false);
            if(p<3)renderItems(g);
            else if(p<9)renderPlants(g,partial);
            else if(p==9)renderMachines(g);
            else if(p==10)renderFeed(g,partial);
            else if(p==11)renderFeedAmounts(g,partial);
            else if(p==12)renderLowMixedFeed(g,partial);
            else renderMachineItems(g);
            g.drawString(font,"TN-C · 实际游戏渲染检查 · 32px物品 / 16px材料",20,height-16,0x9CAAAC,false);
        }
        private void renderItems(GuiGraphics g){
            int start=p*54;
            for(int i=0;i<54&&start+i<items.size();i++){
                String name=items.get(start+i);var item=ForgeRegistries.ITEMS.getValue(id(name));if(item==null)continue;
                int x=20+i%9*138,y=62+i/9*101;g.fill(x,y,x+120,y+82,0xFF8B8B8B);
                g.pose().pushPose();g.pose().translate(x+32,y+12,0);g.pose().scale(3.5F,3.5F,1);g.renderItem(new ItemStack(item),0,0);g.pose().popPose();
                g.drawString(font,font.plainSubstrByWidth(new ItemStack(item).getHoverName().getString(),120),x,y+86,0xDECDAA,false);
            }
        }
        private void renderPlants(GuiGraphics g,float partial){
            int start=(p-3)*5;
            for(int row=0;row<5&&start+row<plants.size();row++){
                String name=plants.get(start+row);Block b=block(name);if(b==null||b==Blocks.AIR)continue;
                int y=75+row*112;g.drawString(font,Component.translatable(b.getDescriptionId()),22,y+42,0xDECDAA,false);
                for(int age=0;age<4;age++){
                    int x=310+age*232;g.fill(x-58,y-8,x+75,y+87,0xFF596368);
                    drawBlock(g,stage(b,age),x,y+57,58,true,partial);
                    g.drawString(font,"阶段 "+age,x-16,y+89,0xB6C6BF,false);
                }
            }
        }
        private void renderMachines(GuiGraphics g){
            for(int i=0;i<machines.size();i++){
                Block b=block(machines.get(i));if(b==null)continue;int x=130+i%4*304,y=112+i/4*143;
                g.fill(x-75,y-28,x+95,y+85,0xFF596368);drawBlock(g,b.defaultBlockState(),x,y+45,72,false,0);
                g.drawString(font,Component.translatable(b.getDescriptionId()),x-74,y+92,0xDECDAA,false);
            }
        }
        private void renderFeed(GuiGraphics g,float partial){
            Item[][] supplies={{TNMod.BELLWOOL_FODDER.get()},{TNMod.BELLWOOL_FODDER.get()},{Items.WHEAT_SEEDS},{Items.CARROT},{Items.COD},{Items.SWEET_BERRIES},{Items.BROWN_MUSHROOM},{TNMod.BELLWOOL_FODDER.get(),Items.WHEAT_SEEDS,Items.CARROT,Items.COD}};
            String[] labels={"空食槽","1份饲捆","16份谷粒","16份根蔬","16份鱼料","16份果料","16份菌料","四槽混合饲料"};
            for(int i=0;i<8;i++){
                int x=140+i%4*302,y=170+i/4*258;g.fill(x-99,y-63,x+113,y+110,0xFF596368);
                var state=block("pasture_trough").defaultBlockState();var be=new PastureFacilityEntity(BlockPos.ZERO,state);be.setLevel(minecraft.level);
                if(i>0)for(int j=0;j<supplies[i].length;j++)be.setItem(j,new ItemStack(supplies[i][j],i==1?1:16));
                drawBlock(g,state,x,y+48,122,false,partial,be);g.drawString(font,labels[i],x-75,y+119,0xDECDAA,false);
            }
        }
        private void renderFeedAmounts(GuiGraphics g,float partial){
            int[] counts={0,1,4,8,16,16,16,1};
            String[] labels={"空食槽","1份草料","4份草料","8份草料","16份草料","16份颗粒料","两格同种草料","单份放在第四格"};
            for(int i=0;i<8;i++){
                int x=140+i%4*302,y=170+i/4*258;g.fill(x-99,y-63,x+113,y+110,0xFF596368);
                var state=block("pasture_trough").defaultBlockState();var be=new PastureFacilityEntity(BlockPos.ZERO,state);be.setLevel(minecraft.level);
                if(i>0)be.setItem(i==7?3:0,new ItemStack(i==5?Items.BREAD:TNMod.BELLWOOL_FODDER.get(),counts[i]));
                if(i==6)be.setItem(1,new ItemStack(TNMod.BELLWOOL_FODDER.get(),16));
                drawBlock(g,state,x,y+48,122,false,partial,be);g.drawString(font,labels[i],x-75,y+119,0xDECDAA,false);
            }
        }
        private void renderLowMixedFeed(GuiGraphics g,float partial){
            Item[] low={Items.COD,Items.CARROT,Items.BROWN_MUSHROOM,Items.SWEET_BERRIES,Items.BREAD,TNMod.BELLWOOL_FODDER.get(),Items.COD,Items.COD};
            String[] labels={"谷粒16 + 鱼1","谷粒16 + 根蔬1","谷粒16 + 菌料1","谷粒16 + 果料1","谷粒16 + 颗粒1","谷粒16 + 草料1","谷粒16 + 鱼2","谷粒16 + 鱼16"};
            for(int i=0;i<8;i++){
                int x=140+i%4*302,y=170+i/4*258;g.fill(x-99,y-63,x+113,y+110,0xFF596368);
                var state=block("pasture_trough").defaultBlockState();var be=new PastureFacilityEntity(BlockPos.ZERO,state);be.setLevel(minecraft.level);
                be.setItem(0,new ItemStack(Items.WHEAT_SEEDS,16));be.setItem(1,new ItemStack(low[i],i==7?16:i==6?2:1));
                drawBlock(g,state,x,y+48,122,false,partial,be);g.drawString(font,labels[i],x-75,y+119,0xDECDAA,false);
            }
        }
        private void renderMachineItems(GuiGraphics g){
            for(int i=0;i<machines.size();i++){
                var item=new ItemStack(block(machines.get(i)).asItem());
                int x=66+i%4*304,y=90+i/4*143;g.fill(x,y,x+145,y+111,0xFF8B8B8B);
                g.pose().pushPose();g.pose().translate(x+22,y+6,0);g.pose().scale(6F,6F,1);g.renderItem(item,0,0);g.pose().popPose();
                g.drawString(font,font.plainSubstrByWidth(item.getHoverName().getString(),214),x,y+118,0xDECDAA,false);
            }
        }
        private void drawBlock(GuiGraphics g,BlockState state,int x,int y,float scale,boolean soil,float partial){drawBlock(g,state,x,y,scale,soil,partial,null);}
        private void drawBlock(GuiGraphics g,BlockState state,int x,int y,float scale,boolean soil,float partial,PastureFacilityEntity facility){
            var mc=minecraft;var pose=g.pose();g.flush();Lighting.setupFor3DItems();pose.pushPose();pose.translate(x,y,150);pose.scale(scale,-scale,scale);pose.mulPose(Axis.XP.rotationDegrees(25));pose.mulPose(Axis.YP.rotationDegrees(225));pose.translate(-.5,0,-.5);
            var buffer=mc.renderBuffers().bufferSource();
            if(soil){pose.pushPose();pose.translate(0,-.13,0);pose.scale(1,.13F,1);mc.getBlockRenderer().renderSingleBlock(Blocks.DIRT.defaultBlockState(),pose,buffer,15728880,OverlayTexture.NO_OVERLAY);pose.popPose();}
            if(state.getBlock()==block("warning_moss")){pose.pushPose();pose.translate(0,0,1);pose.scale(1,1,.12F);mc.getBlockRenderer().renderSingleBlock(Blocks.STONE_BRICKS.defaultBlockState(),pose,buffer,15728880,OverlayTexture.NO_OVERLAY);pose.popPose();}
            mc.getBlockRenderer().renderSingleBlock(state,pose,buffer,15728880,OverlayTexture.NO_OVERLAY);
            if(facility!=null){var renderer=mc.getBlockEntityRenderDispatcher().getRenderer(facility);if(renderer!=null)renderer.render(facility,partial,pose,buffer,15728880,OverlayTexture.NO_OVERLAY);}
            else if(state.getBlock() instanceof BotanicalBlock){var be=new BotanicalPlantEntity(BlockPos.ZERO,state);be.setLevel(mc.level);var renderer=mc.getBlockEntityRenderDispatcher().getRenderer(be);if(renderer!=null)renderer.render(be,partial,pose,buffer,15728880,OverlayTexture.NO_OVERLAY);}
            buffer.endBatch();pose.popPose();Lighting.setupForFlatItems();
        }
    }
}
