package com.tnc.tnc.sanctum;

import com.tnc.tnc.combat.DownedCombat;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "tnc")
public final class SanctumEvents {
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) UnlitSanctum.tick(event.getServer());
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { UnlitSanctum.stop(event.getServer()); }
    @SubscribeEvent public static void join(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) UnlitSanctum.login(p);
    }
    @SubscribeEvent public static void travel(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getFrom().equals(UnlitSanctum.LEVEL) && event.getEntity() instanceof ServerPlayer p)
            UnlitSanctum.leftRealm(p);
    }
    @SubscribeEvent public static void use(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() == InteractionHand.MAIN_HAND && event.getEntity() instanceof ServerPlayer p
                && UnlitSanctum.interact(p, event.getPos())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public static void hurt(LivingHurtEvent event) {
        UnlitSanctum.hurt(event);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void damage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        UnlitSanctum.damaged(event);
    }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer().level().dimension().equals(UnlitSanctum.LEVEL)) event.setCanceled(true);
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().dimension().equals(UnlitSanctum.LEVEL)) event.getAffectedBlocks().clear();
    }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        var test = Commands.literal("test").requires(source -> source.hasPermission(2))
                .executes(ctx -> UnlitSanctum.enter(ctx.getSource().getPlayerOrException()) ? 1 : 0)
                .then(Commands.argument("party", EntityArgument.players()).executes(ctx -> {
                    if (UnlitSanctum.enter(EntityArgument.getPlayers(ctx,"party"))) return 1;
                    ctx.getSource().sendFailure(Component.literal("请选择一至四名存活、尚未进入圣堂的玩家。"));
                    return 0;
                }));
        event.getDispatcher().register(Commands.literal("sanctum").then(test)
                .then(Commands.literal("boss").requires(source->source.hasPermission(2))
                        .executes(ctx->UnlitSanctum.bossRehearsal(ctx.getSource().getPlayerOrException())?1:0))
                .then(Commands.literal("phase").requires(source->source.hasPermission(2))
                        .then(Commands.literal("frenzy").executes(ctx->UnlitSanctum.frenzyRehearsal(ctx.getSource().getPlayerOrException())?1:0))
                        .then(Commands.literal("next").executes(ctx->UnlitSanctum.phaseRehearsal(ctx.getSource().getPlayerOrException(),0)?1:0))
                        .then(Commands.argument("number",com.mojang.brigadier.arguments.IntegerArgumentType.integer(1,3))
                                .executes(ctx->UnlitSanctum.phaseRehearsal(ctx.getSource().getPlayerOrException(),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx,"number"))?1:0)))
                .then(Commands.literal("leave").executes(ctx ->
                        UnlitSanctum.leave(ctx.getSource().getPlayerOrException()) ? 1 : 0))
                .then(Commands.literal("retry").executes(ctx -> {
                    var p = ctx.getSource().getPlayerOrException();
                    if (DownedCombat.isDowned(p) && UnlitSanctum.canRequestRetry(p) && UnlitSanctum.retry(p)) return 1;
                    ctx.getSource().sendFailure(Component.literal("队友仍可施救时，请等待救援。全队倒地后才能返回烛台。"));
                    return 0;
                })));
    }
}
