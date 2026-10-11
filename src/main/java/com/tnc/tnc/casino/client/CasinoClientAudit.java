package com.tnc.tnc.casino.client;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.math.Axis;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.casino.machine.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.nio.file.*;
import java.util.*;

/** Opt-in development gallery. Never changes placed machines or runs during normal play. */
@Mod.EventBusSubscriber(modid=TNMod.MODID,value=Dist.CLIENT)
public final class CasinoClientAudit {
    @Mod.EventBusSubscriber(modid=TNMod.MODID,bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void setup(net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent event) {
            if (Boolean.getBoolean("tnc.casinoClientAudit")) event.enqueueWork(() -> {
                // This isolated dev gallery has no SpellEngine runtime. Its unrelated
                // player aim listener otherwise links SpellProjectile on the first tick.
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkAimMechanics.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.magic.TNDarkHandCord.class);
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(com.tnc.tnc.client.TNSpellClientVisuals.class);
                System.out.println("CASINO AUDIT: SpellEngine-dependent tick listeners detached in isolated gallery");
            });
        }
    }
    private static final String[] IDS={"slot_machine","roulette_table","gamble_table","lucky_wheel","card_table"};
    private static final List<String> failures=new ArrayList<>();
    private static int frames,page;private static boolean started;private static volatile boolean saving,saved;
    private static int particlesBefore,particlesDuring;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) throws Exception {
        if(!Boolean.getBoolean("tnc.casinoClientAudit")||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        if(!started) {
            started=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(1);mc.resizeDisplay();
            for(String name:IDS) {
                var id=ResourceLocation.fromNamespaceAndPath("tnc",name);
                if(mc.getModelManager().getModel(new ModelResourceLocation(id,"inventory"))==mc.getModelManager().getMissingModel())failures.add("Missing inventory model "+name);
            }
            mc.setScreen(new Gallery());
        }
        if(page==3) {
            ++frames;
            if(frames==1) {
                mc.options.particles().set(net.minecraft.client.ParticleStatus.ALL);
                mc.setScreen(null);mc.player.setYRot(0);mc.player.setXRot(-10);
                particlesBefore=Integer.parseInt(mc.particleEngine.countParticles());
                var at=mc.player.getEyePosition().add(mc.player.getLookAngle().scale(3.5));
                CasinoEffects.burst(new CasinoPackets.Celebration(at.x,at.y,at.z));
            }
            if(frames==12) {
                particlesDuring=Integer.parseInt(mc.particleEngine.countParticles());
                if(particlesDuring<=particlesBefore) failures.add("Firework particles were not emitted");
                saving=true;Screenshot.grab(mc.gameDirectory,"casino-fireworks.png",mc.getMainRenderTarget(),msg->{saved=true;saving=false;});
            }
            if(frames>=30&&saved&&!saving) {
                Files.writeString(Path.of(System.getProperty("tnc.casinoAuditReport")),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("machines",5,"states_rendered",15,"screenshots",4,"firework_particles_before",particlesBefore,"firework_particles_during",particlesDuring,"failures",failures,"scope","Actual dev client gallery and visual fireworks; not full modpack gameplay")));
                System.out.println("CASINO CLIENT AUDIT "+(failures.isEmpty()?"PASS":"FAIL")+" "+failures);mc.stop();
            }
            return;
        }
        if(++frames<35||saving)return;
        if(!saved) {
            saving=true;Screenshot.grab(mc.gameDirectory,"casino-audit-"+page+".png",mc.getMainRenderTarget(),msg->{saved=true;saving=false;});return;
        }
        if(++page<3){frames=0;saved=false;mc.setScreen(new Gallery());return;}
        frames=0;saved=false;mc.setScreen(null);
    }
    private static final class Gallery extends Screen {
        Gallery(){super(Component.literal("Casino gallery"));}
        @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial) {
            if(minecraft.level==null||page>=3)return;
            g.fill(0,0,width,height,0xFF152126);
            g.drawString(font,"TN-C CASINO / ACTUAL CLIENT / "+new String[]{"IDLE","SPINNING","RESULT"}[page],24,20,0xF0D59C,false);
            for(int i=0;i<IDS.length;i++) {
                int x=25+i%3*410,y=65+i/3*305;
                g.fill(x,y,x+390,y+275,0xFF35474E);
                var id=ResourceLocation.fromNamespaceAndPath("tnc",IDS[i]);var block=ForgeRegistries.BLOCKS.getValue(id);
                var state=block.defaultBlockState();var be=(TNMachineEntity)((net.minecraft.world.level.block.EntityBlock)block).newBlockEntity(BlockPos.ZERO,state);be.setLevel(minecraft.level);
                CompoundTag tag=new CompoundTag();tag.putString("Phase",new String[]{"IDLE","SPINNING","RESULT"}[page]);
                tag.putInt("Spins",page==0?0:1);tag.putInt("LastPayout",page==2?2:0);
                tag.putIntArray("Result",i==3?new int[]{7,0,0}:i==4?new int[]{12,0,12}:new int[]{0,2,4});
                tag.putLong("StartedAt",minecraft.level.getGameTime()-(page==1?40:150));be.load(tag);
                try {
                    g.flush();Lighting.setupFor3DItems();var pose=g.pose();pose.pushPose();pose.translate(x+195,y+225,150);
                    float scale=i==0?105:i==3?44:34;pose.scale(scale,-scale,scale);
                    pose.mulPose(Axis.XP.rotationDegrees(25));pose.mulPose(Axis.YP.rotationDegrees(210));
                    float cx=i==1||i==2||i==4?4:i==0?.5F:i==3?2:1;float cz=i==0?.5F:i==3?.933333F:2;pose.translate(-cx,0,-cz);
                    var buffer=minecraft.renderBuffers().bufferSource();var renderer=minecraft.getBlockEntityRenderDispatcher().getRenderer(be);
                    if(renderer==null)failures.add("Missing renderer "+IDS[i]);else renderer.render(be,partial,pose,buffer,15728880,OverlayTexture.NO_OVERLAY);
                    buffer.endBatch();pose.popPose();Lighting.setupForFlatItems();
                }catch(Throwable failure){throw new IllegalStateException("Casino gallery render "+IDS[i],failure);}
                g.drawString(font,Component.translatable(block.getDescriptionId()),x+12,y+12,0xFFF4DC,false);
                g.renderItem(new ItemStack(block.asItem()),x+355,y+10);
            }
        }
    }
}
