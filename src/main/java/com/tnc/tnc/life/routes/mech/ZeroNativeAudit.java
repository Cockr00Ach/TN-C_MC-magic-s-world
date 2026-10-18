package com.tnc.tnc.life.routes.mech;

import com.tnc.tnc.life.routes.*;
import com.tnc.tnc.magic.MagicStone;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Full isolated PCL runtime audit, including the installed SpellEngine and original spell assets. */
@Mod.EventBusSubscriber(modid="tnc",value=Dist.CLIENT)
public final class ZeroNativeAudit {
    private static final List<String> failures=new ArrayList<>();
    private static final List<Map<String,Object>> results=new ArrayList<>();
    private static volatile boolean ready,saving,checked;
    private static boolean started,captured;
    private static int stage,ticks,screenshots,startupTicks;
    private static volatile ItemStack suit;
    private static volatile net.minecraft.world.entity.monster.Zombie target;
    private static volatile int personalBefore;
    private static volatile long stageStart;
    private static volatile float targetHealth,attributedDamage;
    private static Path folder(){return Path.of(System.getProperty("tnc.zeroAuditOutput")).toAbsolutePath();}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event)throws Exception{
        if(!Boolean.getBoolean("tnc.zeroAudit")||event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(mc.options.enableVsync().get()){mc.options.enableVsync().set(false);mc.getWindow().updateVsync(false);}
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if(mc.level==null||mc.player==null||mc.getOverlay()!=null)return;
        // MCA's mandatory first-login screen suppresses both rendering and held-item input.
        if(mc.screen!=null&&mc.screen.getClass().getName().contains(".mca.")){
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button&&button.active
                    &&(button.getMessage().getString().contains("接受")||button.getMessage().getString().contains("完成")||button.getMessage().getString().equals("某处")||button.getMessage().getString().equals("下一步")||button.getMessage().getString().equalsIgnoreCase("Next")||button.getMessage().getString().equalsIgnoreCase("Done")||button.getMessage().getString().equalsIgnoreCase("Accept"))){button.onPress();break;}
            startupTicks=0;return;
        }
        if(!started&&++startupTicks<200)return;
        if(!started){
            started=true;Files.createDirectories(folder());mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(2);
            mc.options.renderDistance().set(6);mc.options.entityDistanceScaling().set(1.0);mc.options.hideGui=false;
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.resizeDisplay();
            if(!com.tnc.tnc.magic.compat.SpellEngineBridge.enginePresent())throw new IllegalStateException("Full PCL SpellEngine is required for five-form verification");
            if(!(net.minecraftforge.client.extensions.common.IClientItemExtensions.of(new ItemStack(RouteContent.item("zero_spellgun"))).getCustomRenderer() instanceof ZeroGunRenderer))failures.add("Zero custom renderer missing");
            for(String part:ZeroGunRenderer.PARTS)if(mc.getModelManager().getModel(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("tnc","item/zero_spellgun_"+part)).getQuads(null,null,net.minecraft.util.RandomSource.create(1)).isEmpty())failures.add("Missing model part "+part);
            prepare(mc,true);return;
        }
        if(!ready||saving)return;
        mc.setScreen(null);ticks++;
        // Server inventory selection is not automatically a client carried-slot update.
        // A stale selected slot makes useItem send an empty hand back to the server.
        mc.player.getInventory().selected=0;
        if(ticks==5)mc.gui.getChat().clearMessages(false);
        if(stage>=1&&ticks==10){mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,InteractionHand.MAIN_HAND);}
        if(stage>=1&&stage<=5&&ticks==55)grab(mc,stage+"-"+ZeroRules.Form.values()[stage-1].spell+"-firing.png");
        int duration=stage==0?60:stage==6?70:360;
        // Heavy first-world generation can slow the integrated server while client
        // ticks remain at 20 TPS. Endurance must wait for real simulation ticks.
        if(ticks<duration||mc.getSingleplayerServer().overworld().getGameTime()-stageStart<duration)return;
        if(!captured){grab(mc,stage==0?"0-zero-thirdperson.png":stage==6?"6-zero-thirdperson-firing.png":stage+"-endurance.png");captured=true;return;}
        mc.options.keyUse.setDown(false);if(mc.player.isUsingItem())mc.gameMode.releaseUsingItem(mc.player);
        if(stage>=1&&stage<=5&&!checked){
            checked=true;ready=false;int mode=stage-1;
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);int mana=RouteChargeLedger.get(p.serverLevel()).amount(suit),shots=suit.getOrCreateTag().getInt("ZeroShots");
                float damage=attributedDamage;float healthDelta=target==null?0:targetHealth-target.getHealth();int personal=MagicStone.getOrNull(p).getMana();
                if(shots!=166||mana!=48)failures.add("Cadence/endurance "+mode+": "+shots+" shots, "+mana+"M");
                if(damage<=0||attributedDamage<=0)failures.add("No actual target damage: "+ZeroRules.Form.values()[mode].spell);
                if(personal<personalBefore)failures.add("Personal mana was charged: "+mode);
                results.add(Map.of("spell",ZeroRules.Form.values()[mode].spell,"shots",shots,"remaining_mana",mana,"target_damage",damage,"target_health_delta",healthDelta,"attributed_spell_damage",attributedDamage,"personal_before",personalBefore,"personal_after",personal,"seconds",16.6));ready=true;
            });return;
        }
        if(++stage==7){
            Files.writeString(folder().resolve("report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("failures",failures,"forms",results,"screenshots",screenshots,"full_spell_engine",true,"scope","Isolated complete PCL pack; real held-right-click and real target damage")));
            mc.stop();return;
        }
        ticks=0;captured=false;checked=false;prepare(mc,false);
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void damage(net.minecraftforge.event.entity.living.LivingDamageEvent event){
        if(!Boolean.getBoolean("tnc.zeroAudit")||event.getEntity()!=target||event.isCanceled())return;
        var source=event.getSource().getDirectEntity();
        var caster=event.getSource().getEntity();
        boolean engineShot=caster instanceof net.minecraft.server.level.ServerPlayer p
                &&p.getMainHandItem().getItem() instanceof ZeroGunItem
                &&(ZeroGunItem.form(p.getMainHandItem())==ZeroRules.Form.EARTH||ZeroGunItem.form(p.getMainHandItem())==ZeroRules.Form.LIGHTNING);
        if(source!=null&&source.getPersistentData().hasUUID("ZeroCaster")||engineShot)attributedDamage+=event.getAmount();
    }
    private static void prepare(Minecraft mc,boolean terrain){
        ready=false;mc.options.setCameraType(stage==0||stage==6?CameraType.THIRD_PERSON_FRONT:CameraType.FIRST_PERSON);
        mc.getSingleplayerServer().execute(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);var level=p.serverLevel();
            p.stopUsingItem();
            if(terrain){
                level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,p.server);
                for(var pos:BlockPos.betweenClosed(1000,219,996,1060,234,1040))level.setBlock(pos,pos.getY()==219?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState():pos.getY()==220?net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),2);
            }
            for(var entity:level.getEntitiesOfClass(Entity.class,new net.minecraft.world.phys.AABB(996,215,992,1064,240,1044),e->!(e instanceof net.minecraft.world.entity.player.Player)))entity.discard();
            level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            p.getAbilities().flying=true;p.onUpdateAbilities();p.connection.teleport(1032.5,221,1030.5,180,0);
            p.getInventory().clearContent();p.getInventory().selected=0;
            suit=new ItemStack(RouteContent.item("mana_mech"));p.setItemSlot(EquipmentSlot.CHEST,suit);RouteChargeLedger.get(level).deposit(suit,12000,12000);
            var gun=new ItemStack(RouteContent.item("zero_spellgun"));gun.getOrCreateTag().putInt("ZeroForm",stage>=1&&stage<=5?stage-1:0);p.setItemInHand(InteractionHand.MAIN_HAND,gun);
            var magic=MagicStone.getOrNull(p);magic.addMana(magic.getMaxMana());personalBefore=magic.getMana();
            target=net.minecraft.world.entity.EntityType.ZOMBIE.create(level);attributedDamage=0;target.setItemSlot(EquipmentSlot.HEAD,new ItemStack(net.minecraft.world.item.Items.NETHERITE_HELMET));target.setNoAi(true);target.setNoGravity(true);target.setInvulnerable(false);
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);targetHealth=target.getHealth();target.setPos(1032.5,221,1016.5);level.addFreshEntity(target);stageStart=p.server.overworld().getGameTime();ready=true;
        });
    }
    private static void grab(Minecraft mc,String name){
        saving=true;screenshots++;
        Screenshot.grab(mc.gameDirectory,"zero-"+name,mc.getMainRenderTarget(),message->{try{Files.copy(mc.gameDirectory.toPath().resolve("screenshots/zero-"+name),folder().resolve(name),StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){failures.add(e.toString());}finally{saving=false;}});
    }
}
