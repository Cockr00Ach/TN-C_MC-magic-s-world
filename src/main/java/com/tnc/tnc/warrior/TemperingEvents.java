package com.tnc.tnc.warrior;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.DownedCombat;
import java.util.Locale;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.SwordItem;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.network.PacketDistributor;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicitly enrolled prototype; no client-controlled experience or damage values. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TemperingEvents {
    private TemperingEvents() {}
    public static boolean weapon(ServerPlayer player) {
        var item = player.getMainHandItem().getItem();
        return item instanceof SwordItem || item instanceof AxeItem;
    }
    public static void sync(ServerPlayer player) {
        sync(player,false);
    }
    public static TemperingPacket snapshot(ServerPlayer player,boolean open) {
        var data = TemperingData.get(player.server);
        long now=player.server.overworld().getGameTime();
        var record=TrainingGroundData.get(player.server).record(player.getUUID());
        return new TemperingPacket(data.enrolled(player.getUUID()), data.points(player.getUUID()), data.talent(player.getUUID()),
            data.guardGraduate(player.getUUID()),TemperingTraining.active(player),TemperingTraining.blockCount(player),
            TemperingTraining.remaining(player,now),data.windRemaining(player.getUUID(),now),open,TrainingGround.inside(player),record.mode(),record.completions(),record.score(),record.reaction());
    }
    public static void sync(ServerPlayer player,boolean open) {
        MagicStoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            snapshot(player,open));
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) { if(e.getEntity() instanceof ServerPlayer p) sync(p); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if(e.getEntity() instanceof ServerPlayer p) sync(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if(e.getEntity() instanceof ServerPlayer p) sync(p); }
    public static int award(ServerPlayer player, int gain) {
        var data = TemperingData.get(player.server);
        if (!data.enrolled(player.getUUID())) return 0;
        int old = data.points(player.getUUID()),gained=data.award(player.getUUID(),gain),next=data.points(player.getUUID());
        if(gained==0)return 0;
        sync(player);
        player.displayClientMessage(Component.literal("磨砺 +" + gained + " · " + summary(next,data.cap(player.getUUID()))), true);
        if(TemperingRules.rank(next)>TemperingRules.rank(old))
            player.sendSystemMessage(Component.literal("修习精进 · "+TemperingRules.rankName(next)+"：额外减伤 "+String.format(Locale.ROOT,"%.1f%%",TemperingRules.reduction(next)*100)));
        if(next==data.cap(player.getUUID()))player.sendSystemMessage(Component.literal("已达到当前战士天赋的磨砺上限。"));
        return gained;
    }
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void damage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()
                || DownedCombat.isDowned(player) || event.getAmount() <= 0
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == player
                || player.isAlliedTo(attacker)
                || event.getSource().is(DamageTypeTags.BYPASSES_ARMOR)
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        var data = TemperingData.get(player.server);
        if (!data.enrolled(player.getUUID())) return;
        // Forge LivingDamage is after armor, resistance and absorption. Never add armor percentages.
        event.setAmount((float)(event.getAmount() * (1 - TemperingRules.reduction(data.points(player.getUUID())))));
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void defeated(LivingDeathEvent event) {
        if(TrainingGround.trainer(event.getEntity()))return;
        if (!(event.getEntity() instanceof Enemy) || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getSource().getDirectEntity() != player || !event.getSource().is(DamageTypes.PLAYER_ATTACK)
                || player.isCreative() || player.isSpectator() || DownedCombat.isDowned(player)
                || player.isAlliedTo(event.getEntity())) return;
        if (!weapon(player)) return;
        var data = TemperingData.get(player.server);
        if (!data.enrolled(player.getUUID())) return;
        var tag = event.getEntity().getPersistentData();
        if (tag.getBoolean("tncTemperingRewarded")) return;
        tag.putBoolean("tncTemperingRewarded", true);
        award(player, TemperingRules.killReward(event.getEntity().getMaxHealth()));
    }
    public static String summary(int points) {
        return summary(points,TemperingRules.MAX_POINTS);
    }
    public static String summary(int points,int cap) {
        return TemperingRules.rankName(points) + " · " + points + "/"+cap+" · 额外减伤 "
                + String.format(Locale.ROOT, "%.1f%%", TemperingRules.reduction(points) * 100);
    }
    private static String talentSummary(ServerPlayer p) {
        var data=TemperingData.get(p.server); int talent=data.talent(p.getUUID());
        return "战士天赋 " + talent + "/6 · 成长倍率 " + WarriorTalentRules.multiplier(talent)
            + " · 磨砺 " + data.points(p.getUUID()) + "/" + data.cap(p.getUUID())
            + (talent==0 ? " · 无法修习战士能力" : " · 额外减伤 " + String.format(Locale.ROOT,"%.1f%%",TemperingRules.reduction(data.points(p.getUUID()))*100));
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tempering")
            .executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException(); var data = TemperingData.get(p.server);
                ctx.getSource().sendSuccess(() -> Component.literal(talentSummary(p)), false); return 1;
            })
            .then(Commands.literal("join").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                if (!TemperingData.get(p.server).enroll(p.getUUID())) {
                    ctx.getSource().sendFailure(Component.literal("战士天赋为 0，无法修习。天赋由角色设定或剧情赋予，加入命令不能生成天赋。")); return 0;
                }
                sync(p);
                ctx.getSource().sendSuccess(() -> Component.literal("已启用磨砺：持剑或斧近战击败敌对生物可成长；磨砺永久保留。"), false); return 1;
            }))
            .then(Commands.literal("panel").executes(ctx->{sync(ctx.getSource().getPlayerOrException(),true);return 1;}))
            .then(Commands.literal("ground").executes(ctx->{return TrainingGround.enter(ctx.getSource().getPlayerOrException())?1:0;})
                .then(Commands.literal("leave").executes(ctx->{return TrainingGround.leave(ctx.getSource().getPlayerOrException())?1:0;})))
            .then(Commands.literal("talent")
                .executes(ctx -> { var p=ctx.getSource().getPlayerOrException();ctx.getSource().sendSuccess(()->Component.literal(talentSummary(p)),false);return 1; })
                .then(Commands.literal("set").requires(s->s.hasPermission(2))
                    .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.players())
                        .then(Commands.argument("value", IntegerArgumentType.integer(0,6)).executes(ctx->{
                            int value=IntegerArgumentType.getInteger(ctx,"value");
                            var targets=net.minecraft.commands.arguments.EntityArgument.getPlayers(ctx,"targets");
                            for(var p:targets){TemperingData.get(p.server).setTalent(p.getUUID(),value);TemperingTraining.cancel(p);sync(p);p.sendSystemMessage(Component.literal(talentSummary(p)));}
                            ctx.getSource().sendSuccess(()->Component.literal("已设置 "+targets.size()+" 名角色的战士天赋为 "+value),true);return targets.size();
                        })))))
            .then(Commands.literal("train").executes(ctx -> {
                var p = ctx.getSource().getPlayerOrException();
                if (!TemperingTraining.start(p)) {
                    ctx.getSource().sendFailure(Component.literal("请先加入磨砺，并在生存模式、未倒地时开始训练。")); return 0;
                }
                return 1;
            }))
            .then(Commands.literal("cancel").executes(ctx -> {
                TemperingTraining.cancel(ctx.getSource().getPlayerOrException());
                ctx.getSource().sendSuccess(() -> Component.literal("本次训练已结束。"), false); return 1;
            }))
            .then(Commands.literal("set").requires(source -> source.hasPermission(2))
                .then(Commands.argument("points", IntegerArgumentType.integer(0, TemperingRules.MAX_POINTS)).executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrException(); int value = IntegerArgumentType.getInteger(ctx, "points");
                    var data=TemperingData.get(p.server);
                    if (!data.enrolled(p.getUUID()) || value > data.cap(p.getUUID())) {
                        ctx.getSource().sendFailure(Component.literal("磨砺不能超过当前战士天赋上限："+data.cap(p.getUUID())));return 0;
                    }
                    data.set(p.getUUID(), value); sync(p);
                    ctx.getSource().sendSuccess(() -> Component.literal(summary(value,data.cap(p.getUUID()))), false); return 1;
                }))));
    }
}
