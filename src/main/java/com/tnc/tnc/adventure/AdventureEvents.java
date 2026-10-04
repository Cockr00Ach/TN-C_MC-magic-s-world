package com.tnc.tnc.adventure;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.MagicStone;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.*;
import net.minecraft.world.*;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class AdventureEvents {
    private record Hit(java.util.UUID player,long tick){}
    private static final java.util.Map<java.util.UUID,java.util.Map<java.util.UUID,Long>> CONTRIBUTIONS=new java.util.HashMap<>();
    @SubscribeEvent public static void hurt(net.minecraftforge.event.entity.living.LivingHurtEvent e){
        if(e.getAmount()<=0||!(e.getSource().getEntity() instanceof ServerPlayer p)||p.isCreative()||p.isSpectator())return;
        CONTRIBUTIONS.computeIfAbsent(e.getEntity().getUUID(),id->new java.util.HashMap<>()).put(p.getUUID(),p.server.overworld().getGameTime());
    }
    public static class HandbookItem extends Item {
        public HandbookItem(){super(new Properties().stacksTo(1));}
        @Override public InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player p,InteractionHand hand) {
            if(p instanceof ServerPlayer player)AdventureService.sync(player,true,"");
            return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand),level.isClientSide());
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(!(e.getEntity() instanceof ServerPlayer p))return;
        AdventureService.profile(p);AdventureService.refreshGrowth(p);
        var inv=p.getInventory();boolean found=false;for(int i=0;i<inv.getContainerSize();i++)if(inv.getItem(i).is(TNMod.HANDBOOK.get()))found=true;
        if(!found){var tx=new InventoryTransaction(inv);if(tx.add(new ItemStack(TNMod.HANDBOOK.get())))tx.commit();}
        for(String id:java.util.List.copyOf(AdventureService.profile(p).milestones))AdventureService.milestone(p,id);
    }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if(e.getEntity() instanceof ServerPlayer p)AdventureService.refreshGrowth(p);
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract e) {
        // Self keeps his existing story interaction; public services have separate NPCs.
    }
    @SubscribeEvent public static void death(LivingDeathEvent e) {
        if(!(e.getEntity().level() instanceof net.minecraft.server.level.ServerLevel level))return;
        var hits=CONTRIBUTIONS.remove(e.getEntity().getUUID());if(hits==null)hits=new java.util.HashMap<>();
        String mob=ForgeRegistries.ENTITY_TYPES.getKey(e.getEntity().getType()).toString();
        boolean boss=com.tnc.tnc.life.BossCuisine.BOSSES.contains(mob)||BossCommissions.isTarget(mob);if(!(e.getEntity() instanceof Enemy)&&!boss)return;
        long now=level.getServer().overworld().getGameTime();if(e.getSource().getEntity() instanceof ServerPlayer p)hits.put(p.getUUID(),now);
        for(var hit:hits.entrySet()) {
            var p=level.getServer().getPlayerList().getPlayer(hit.getKey());if(p==null||p.serverLevel()!=level||!p.isAlive()||p.isCreative()||p.isSpectator()||now-hit.getValue()>1200||p.distanceToSqr(e.getEntity())>4096)continue;
            var profile=AdventureService.profile(p);profile.addXp(boss?500:6);
            profile.adventureKills=Math.min(1_000_000,profile.adventureKills+1);if(boss)profile.bossKills=Math.min(1_000_000,profile.bossKills+1);
            for(int n:new int[]{25,100})if(profile.adventureKills>=n)AdventureService.milestone(p,"kills_"+n);
            for(int n:new int[]{1,8})if(profile.bossKills>=n)AdventureService.milestone(p,"bosses_"+n);
            for(var progress:profile.contracts.values()){var c=ContractCatalog.find(progress.id);if(c.enemy().equals(mob))progress.kills=Math.min(c.kills(),progress.kills+1);}
            BossCommissions.recordParticipant(p,mob);
            AdventureSavedData.get(p.server).setDirty();AdventureService.refreshGrowth(p);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        if(server.getPlayerList().getPlayers().stream().anyMatch(p->!p.isSpectator()&&!p.isCreative())) {
            var data=AdventureSavedData.get(server);data.activeTicks++;data.setDirty();
        }
        if(server.getTickCount()%40==0)for(var p:server.getPlayerList().getPlayers()) {
            if(p.isCreative()||p.isSpectator())continue;
            NativeBountiful.refreshCount(p);
            if(AdventureService.profile(p).registered())for(var stack:p.getInventory().items)if(NativeBountiful.isTownPaper(stack)){AdventureService.milestone(p,"accepted");break;}
            if(p.level().dimension()==net.minecraft.world.level.Level.NETHER)AdventureService.milestone(p,"entered_nether");
            if(p.level().dimension()==net.minecraft.world.level.Level.END)AdventureService.milestone(p,"entered_end");
            for(int n:new int[]{5,10,20,30,50,70,85,100})if(AdventureService.profile(p).level()>=n)AdventureService.milestone(p,"level_"+n);
            var arrival=com.tnc.tnc.npc.SkyIslandAnchors.resolve(server.overworld(),com.tnc.tnc.npc.SkyIslandAnchors.Anchor.ARRIVAL);
            if(p.level()==server.overworld()&&arrival!=null&&arrival.closerToCenterThan(p.position(),16))AdventureService.milestone(p,"arrived");
            var origin=com.tnc.tnc.npc.SkyIslandAnchors.resolve(server.overworld(),com.tnc.tnc.npc.SkyIslandAnchors.Anchor.ORIGIN);
            if(p.level()==server.overworld()&&origin!=null&&origin.offset(210,95,312).closerToCenterThan(p.position(),12))AdventureService.milestone(p,"visited_entertainment");
            var magic=MagicStone.getOrNull(p);
            if(magic!=null&&!magic.getLearned().isEmpty())AdventureService.milestone(p,"learned");
            if(magic!=null)for(var entry:com.tnc.tnc.magic.SpellCatalog.all())if(entry.element()!=null&&magic.getLearned().contains(entry.id()))AdventureService.milestone(p,"learn_"+entry.element().id()+"_"+entry.tier());
        }
        if(server.getTickCount()%200==0){long now=server.overworld().getGameTime();CONTRIBUTIONS.values().forEach(h->h.values().removeIf(t->now-t>1200));CONTRIBUTIONS.values().removeIf(java.util.Map::isEmpty);}
    }
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){CONTRIBUTIONS.clear();}
    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(literal("adventure").executes(ctx->{AdventureService.sync(ctx.getSource().getPlayerOrException(),true,"");return 1;})
            .then(literal("trust").then(net.minecraft.commands.Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx->{var p=ctx.getSource().getPlayerOrException();p.sendSystemMessage(Component.literal(com.tnc.tnc.home.HousingService.guest(p,net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,"player"),true)));return 1;})))
            .then(literal("untrust").then(net.minecraft.commands.Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).executes(ctx->{var p=ctx.getSource().getPlayerOrException();p.sendSystemMessage(Component.literal(com.tnc.tnc.home.HousingService.guest(p,net.minecraft.commands.arguments.EntityArgument.getPlayer(ctx,"player"),false)));return 1;})))
            .then(literal("handbook").executes(ctx->{var p=ctx.getSource().getPlayerOrException();
                boolean has=p.getInventory().contains(new ItemStack(TNMod.HANDBOOK.get()));
                var tx=new InventoryTransaction(p.getInventory());if(!has&&tx.add(new ItemStack(TNMod.HANDBOOK.get())))tx.commit();
                p.sendSystemMessage(Component.literal(has?"你已有冒险手册。":"请查看背包；背包满时先腾出位置。"));return 1;})));
    }
}
