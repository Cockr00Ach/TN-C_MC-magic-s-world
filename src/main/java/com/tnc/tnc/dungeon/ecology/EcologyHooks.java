package com.tnc.tnc.dungeon.ecology;

import com.tnc.tnc.arcana.ArcanaItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Item-independent hooks used by direct application, waste flasks and room reagents. */
@Mod.EventBusSubscriber(modid="tnc")
public final class EcologyHooks {
    private EcologyHooks(){}
    private static String path(String id){return id==null?"":id.substring(id.indexOf(':')+1);}
    public static boolean wastePotion(String id){return path(id).equals("strange_waste");}
    public static boolean capturePotion(String id){return path(id).equals("capture_shrink_elixir")||path(id).equals("capture_shrink");}
    public static boolean firePotion(String id){return switch(path(id)){case "flame_twist_elixir","fire","fire_elixir","ecology_fire_flask"->true;default->false;};}
    public static boolean applyPotion(ServerPlayer player,LivingEntity target,String id){return target instanceof EcologyMob mob&&mob.applyPotion(player,id);}
    public static int splash(ServerPlayer player,Vec3 at,String id){
        int affected=0;for(var mob:player.serverLevel().getEntitiesOfClass(EcologyMob.class,new AABB(at,at).inflate(2.5)))if(mob.distanceToSqr(at)<=6.25&&mob.applyPotion(player,id))affected++;return affected;
    }
    /** Existing agricultural potions now have a physical splash path; block-use still applies them to plants. */
    @SubscribeEvent(priority=EventPriority.HIGH) public static void throwPotion(PlayerInteractEvent.RightClickItem event){
        if(!(event.getItemStack().getItem() instanceof ArcanaItem item)||!capturePotion(item.id)&&!firePotion(item.id))return;
        if(event.getEntity() instanceof ServerPlayer player){
            if(!player.isAlive()||player.isSpectator()||com.tnc.tnc.combat.DownedCombat.isDowned(player)||com.tnc.tnc.dungeon.DungeonService.recovering(player)||player.getCooldowns().isOnCooldown(item))return;
            var flask=EcologyContent.FLASK.create(player.serverLevel());if(flask==null)return;flask.setOwner(player);flask.setItem(event.getItemStack().copyWithCount(1));flask.setPos(player.getX(),player.getEyeY()-.1,player.getZ());flask.shootFromRotation(player,player.getXRot(),player.getYRot(),-10,.9f,.4f);
            if(player.serverLevel().addFreshEntity(flask)){ArcanaItem.consume(player,event.getItemStack());player.getCooldowns().addCooldown(item,10);player.playSound(net.minecraft.sounds.SoundEvents.SPLASH_POTION_THROW,.7f,.9f);}
        }
        event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
