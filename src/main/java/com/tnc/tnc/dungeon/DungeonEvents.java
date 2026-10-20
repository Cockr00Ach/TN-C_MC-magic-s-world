package com.tnc.tnc.dungeon;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

@Mod.EventBusSubscriber(modid="tnc")
public final class DungeonEvents {
    private static final UUID SPEED=UUID.fromString("ab0ab019-0339-4d52-873c-388f03c3f101");
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)DungeonService.tick(e.getServer());}
    @SubscribeEvent public static void playerTick(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%20!=0)return;var attr=p.getAttribute(Attributes.MOVEMENT_SPEED);if(attr==null)return;boolean buff=DungeonService.active(p)&&DungeonData.get(p.server).profile(p.getUUID()).getInt("Blessing")==3;if(buff&&attr.getModifier(SPEED)==null)attr.addTransientModifier(new AttributeModifier(SPEED,"Dungeon lightstep",.08,AttributeModifier.Operation.MULTIPLY_TOTAL));if(!buff&&attr.getModifier(SPEED)!=null)attr.removeModifier(SPEED);if(DungeonService.recovering(p)){com.tnc.tnc.magic.compat.CombatCasting.cancel(p);var t=DungeonData.get(p.server).profile(p.getUUID());var at=net.minecraft.core.BlockPos.of(t.getLong("RecoverPos"));if(!DungeonService.run(p).building&&DungeonService.run(p).state().built&&p.blockPosition().distSqr(at)>1)DungeonService.teleport(p,DungeonService.run(p),at);p.invulnerableTime=40;p.displayClientMessage(Component.literal("已安全重连 · 输入 /dungeon resume 确认继续"),true);}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void death(LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity() instanceof DungeonMob m)DungeonService.killed(m);}
    @SubscribeEvent public static void ward(LivingHurtEvent e){if(!(e.getEntity() instanceof ServerPlayer p)||!DungeonService.active(p))return;var r=DungeonService.run(p);var room=DungeonService.room(p);if(room==null)return;var t=DungeonData.get(p.server).profile(p.getUUID());String key="Ward_"+r.id+"_"+r.floor+"_"+room.id();if(t.getInt("Blessing")==2&&!t.getBoolean(key)){t.putBoolean(key,true);e.setAmount(e.getAmount()*.5f);DungeonData.get(p.server).setDirty();}}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(!(e.getEntity() instanceof ServerPlayer p))return;DungeonRewards.deliverMail(p);if(DungeonRealm.inside(p.level())&&DungeonService.run(p)==null){if(com.tnc.tnc.combat.DownedCombat.isDowned(p))com.tnc.tnc.combat.DownedCombat.revive(p);p.setHealth(p.getMaxHealth());DungeonService.leave(p);}else if(DungeonRealm.inside(p.level()))DungeonService.beginRecovery(p);}

    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void recoveryAttack(LivingAttackEvent e){if(e.getEntity() instanceof ServerPlayer p&&DungeonService.recovering(p)||e.getSource().getEntity() instanceof ServerPlayer q&&DungeonService.recovering(q))e.setCanceled(true);}
    @SubscribeEvent public static void recoveryInteract(PlayerInteractEvent e){if(e.getEntity() instanceof ServerPlayer p&&DungeonService.recovering(p)&&e.isCancelable())e.setCanceled(true);}
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent e){if(e.getPlayer()!=null&&DungeonRealm.inside(e.getPlayer().level()))e.setCanceled(true);}
    @SubscribeEvent public static void placing(BlockEvent.EntityPlaceEvent e){if(e.getEntity() instanceof Player p&&DungeonRealm.inside(p.level()))e.setCanceled(true);}
    @SubscribeEvent public static void bucket(FillBucketEvent e){if(DungeonRealm.inside(e.getEntity().level()))e.setCanceled(true);}
    @SubscribeEvent public static void explosion(net.minecraftforge.event.level.ExplosionEvent.Detonate e){if(DungeonRealm.inside(e.getLevel()))e.getAffectedBlocks().clear();}
    @SubscribeEvent public static void dimension(net.minecraftforge.event.entity.EntityTravelToDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p&&DungeonService.active(p)){var r=DungeonService.run(p);if(com.tnc.tnc.combat.DownedCombat.isDowned(p)||(r.state().started&~r.state().cleared)!=0){e.setCanceled(true);DungeonService.message(p,"清场并救起队友后才能离开地下城");}}}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){DungeonService.stop();DungeonNetwork.stop();}
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent e){var root=Commands.literal("dungeon")
      .then(Commands.literal("resume").executes(c->{DungeonService.resume(c.getSource().getPlayerOrException());return 1;}))
      .executes(c->{DungeonNetwork.open(c.getSource().getPlayerOrException(),"guide");return 1;})
      .then(Commands.literal("guide").executes(c->{DungeonNetwork.open(c.getSource().getPlayerOrException(),"guide");return 1;}))
      .then(Commands.literal("leave").executes(c->{var p=c.getSource().getPlayerOrException();if(DungeonRealm.inside(p.level()))DungeonService.leave(p);return 1;}))
      .then(Commands.literal("abandon").then(Commands.literal("confirm").executes(c->{DungeonService.abandon(c.getSource().getPlayerOrException());return 1;})).executes(c->{c.getSource().sendSuccess(()->Component.literal("放弃会重置当前五层段，损失本段遗币20%；确认请输入 /dungeon abandon confirm"),false);return 1;}))
      .then(Commands.literal("invite").then(Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).executes(c->{DungeonService.invite(c.getSource().getPlayerOrException(),net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player"));return 1;})))
      .then(Commands.literal("accept").executes(c->{DungeonService.accept(c.getSource().getPlayerOrException());return 1;}));
      var locate=Commands.literal("locate");var enter=Commands.literal("enter").requires(s->s.hasPermission(2));var build=Commands.literal("buildgate").requires(s->s.hasPermission(2));
      for(var t:DungeonTheme.values()){
        enter.then(Commands.literal(t.id).executes(c->{DungeonService.enter(c.getSource().getPlayerOrException(),t);return 1;}));
        locate.then(Commands.literal(t.id).executes(c->{var p=c.getSource().getPlayerOrException();if(p.serverLevel()!=p.server.overworld())return 0;var tag=net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.STRUCTURE,DungeonContent.id("dungeon/"+t.id));var at=p.serverLevel().findNearestMapStructure(tag,p.blockPosition(),64,false);c.getSource().sendSuccess(()->Component.literal(at==null?"附近尚未发现"+t.name+"大门，请探索新区域":t.name+"大门位于 "+at.toShortString()),false);return 1;}));
        build.then(Commands.literal(t.id).executes(c->{var p=c.getSource().getPlayerOrException();if(DungeonRealm.inside(p.level()))return 0;DungeonGateAssets.buildHere(p,t);return 1;}));
      }root.then(locate).then(enter).then(build);e.getDispatcher().register(root);
    }
}
