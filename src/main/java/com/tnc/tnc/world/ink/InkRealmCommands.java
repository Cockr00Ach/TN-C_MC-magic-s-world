package com.tnc.tnc.world.ink;

import com.tnc.tnc.TNMod;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Operator-facing bridge used to verify the dimension before a real portal exists. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class InkRealmCommands {
    private InkRealmCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tnc")
                .then(Commands.literal("ink")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("trial").executes(context->{
                            boolean started=InkEncounter.start(context.getSource().getPlayerOrException());
                            if(!started)context.getSource().sendFailure(Component.literal("请在水墨维度启动试炼；同一时间只能进行一场三相战斗。"));
                            return started?1:0;
                        }))
                        .then(Commands.literal("trial_reset").executes(context->{var p=context.getSource().getPlayerOrException();InkEncounter.get(p.serverLevel()).reset(p.serverLevel());return 1;}))
                        .then(Commands.literal("portal").executes(context->InkPortalEntity.place(context.getSource().getPlayerOrException())?1:0))
                        .then(Commands.literal("dragon").executes(context->{
                            var player=context.getSource().getPlayerOrException();var level=player.serverLevel();
                            Vec3 forward=player.getLookAngle().multiply(1,0,1).normalize();if(forward.lengthSqr()<.01)forward=new Vec3(0,0,1);
                            Vec3 at=player.position().add(forward.scale(InkDragonFlightRules.SUMMON_DISTANCE)).add(0,InkDragonFlightRules.SUMMON_RISE,0);
                            if(at.y+40>=level.getMaxBuildHeight()||!level.getWorldBorder().isWithinBounds(InkDragonFlightRules.bounds(at,player.getYRot()+180))){
                                context.getSource().sendFailure(Component.literal("墨龙体型较大，请在世界边界内、上方开阔处召唤。"));return 0;}
                            var dragon=InkBossRegistration.INK_CELESTIAL_DRAGON.create(level);if(dragon==null)return 0;
                            dragon.moveTo(at.x,at.y,at.z,player.getYRot()+180,0);
                            dragon.setGroundedForObservation(false);at=dragon.position();
                            if(!level.hasChunkAt(net.minecraft.core.BlockPos.containing(at))||!dragon.hasFlightRoom(InkDragonFlightRules.bounds(at,player.getYRot()+180))){
                                context.getSource().sendFailure(Component.literal("前方空间不足，请到开阔处召唤墨龙。"));return 0;}
                            level.addFreshEntity(dragon);
                            Vec3 placed=dragon.position();context.getSource().sendSuccess(()->Component.literal(String.format("玄墨天龙已现身：%.0f / %.0f / %.0f，已恢复飞行与战斗。",placed.x,placed.y,placed.z)),true);return 1;
                        }))
                        .then(Commands.literal("dragon_find").executes(context->{
                            var player=context.getSource().getPlayerOrException();
                            var dragon=player.serverLevel().getEntitiesOfClass(InkCelestialDragonEntity.class,player.getBoundingBox().inflate(512)).stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                            if(dragon==null){context.getSource().sendFailure(Component.literal("附近已加载区域内没有墨龙。"));return 0;}
                            context.getSource().sendSuccess(()->Component.literal(String.format("最近墨龙：%.0f / %.0f / %.0f，距离 %.0f 格。",dragon.getX(),dragon.getY(),dragon.getZ(),player.distanceTo(dragon))),false);return 1;
                        }))
                        .then(Commands.literal("dragon_attack").then(Commands.argument("move",IntegerArgumentType.integer(1,5)).executes(context->{
                            var player=context.getSource().getPlayerOrException();
                            var dragon=player.serverLevel().getEntitiesOfClass(InkCelestialDragonEntity.class,player.getBoundingBox().inflate(128)).stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                            if(dragon==null||!dragon.startInkAttack(IntegerArgumentType.getInteger(context,"move"),player)){
                                context.getSource().sendFailure(Component.literal(dragon!=null&&dragon.isGroundedForObservation()?"墨龙当前固定在地面观察，已暂停攻击。":"附近没有可出招的墨龙，或它正在施法。"));return 0;}
                            return 1;
                        })))
                        .then(Commands.literal("tiger").executes(context->{
                            var player=context.getSource().getPlayerOrException();var level=player.serverLevel();
                            var tiger=InkBossRegistration.INK_WINGED_TIGER.create(level);if(tiger==null)return 0;
                            Vec3 forward=player.getLookAngle().multiply(1,0,1).normalize().scale(10);
                            if(forward.lengthSqr()<1)forward=new Vec3(0,0,10);
                            tiger.moveTo(player.getX()+forward.x,player.getY()+1,player.getZ()+forward.z,player.getYRot()+180,0);
                            level.addFreshEntity(tiger);context.getSource().sendSuccess(()->Component.literal("玄墨翼虎已踏墨现身。"),true);return 1;
                        }))
                        .then(Commands.literal("tiger_attack").then(Commands.argument("move",IntegerArgumentType.integer(1,4)).executes(context->{
                            var player=context.getSource().getPlayerOrException();var tiger=player.serverLevel().getEntitiesOfClass(
                                    InkWingedTigerEntity.class,player.getBoundingBox().inflate(96),LivingEntity->LivingEntity.isAlive())
                                    .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                            if(tiger==null){context.getSource().sendFailure(Component.literal("附近没有翼虎，请先用 /tnc ink tiger 召唤。"));return 0;}
                            if(!tiger.startInkAttack(IntegerArgumentType.getInteger(context,"move"),player)){
                                context.getSource().sendFailure(Component.literal("翼虎正在出招，请稍后重试。"));return 0;}
                            return 1;
                        })))
                        .then(Commands.literal("enter").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (!InkRealmTravel.enter(player)) {
                                context.getSource().sendFailure(Component.literal("水墨维度未加载，请检查数据包。"));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal("已进入水墨维度。"), false);
                            return 1;
                        }))
                        .then(Commands.literal("leave").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            if (!InkRealmTravel.leave(player)) {
                                context.getSource().sendFailure(Component.literal("无法返回主世界。"));
                                return 0;
                            }
                            context.getSource().sendSuccess(() -> Component.literal("已返回主世界出生点。"), false);
                            return 1;
                        }))
                        .then(Commands.literal("boss").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ServerLevel level = player.serverLevel();
                            InkAbyssDrakeEntity boss = InkBossRegistration.INK_ABYSS_DRAKE.create(level);
                            if (boss == null) {
                                context.getSource().sendFailure(Component.literal("墨渊螭实体创建失败。"));
                                return 0;
                            }
                            Vec3 forward = player.getLookAngle().multiply(8.0, 0.0, 8.0);
                            boss.moveTo(player.getX() + forward.x, player.getY() + 1.0,
                                    player.getZ() + forward.z, player.getYRot() + 180.0F, 0.0F);
                            level.addFreshEntity(boss);
                            context.getSource().sendSuccess(() -> Component.literal("墨渊螭已在前方苏醒。"), true);
                            return 1;
                        }))
                        .then(Commands.literal("crane_attack")
                                .then(Commands.argument("move", IntegerArgumentType.integer(1, 4)).executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    InkCraneBossEntity boss = player.serverLevel().getEntitiesOfClass(
                                            InkCraneBossEntity.class, player.getBoundingBox().inflate(96), candidate -> candidate.isAlive())
                                            .stream().min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
                                    if (boss == null) {
                                        context.getSource().sendFailure(Component.literal("附近没有玄翎墨鹤，请先用 /tnc ink crane 召唤。"));
                                        return 0;
                                    }
                                    int move = IntegerArgumentType.getInteger(context, "move");
                                    if (!boss.startInkAttack(move, player)) {
                                        context.getSource().sendFailure(Component.literal("墨鹤正在施放技能，稍后再试。"));
                                        return 0;
                                    }
                                    context.getSource().sendSuccess(() -> Component.literal("墨鹤技能 " + move + " 已开始。"), false);
                                    return 1;
                                })))
                        .then(Commands.literal("crane").executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            ServerLevel level = player.serverLevel();
                            InkCraneBossEntity boss = InkBossRegistration.INK_CRANE_BOSS.create(level);
                            if (boss == null) {
                                context.getSource().sendFailure(Component.literal("玄翎墨鹤实体创建失败。"));
                                return 0;
                            }
                            Vec3 forward = player.getLookAngle().multiply(8.0, 0.0, 8.0);
                            boss.moveTo(player.getX() + forward.x, player.getY() + 1.0,
                                    player.getZ() + forward.z, player.getYRot() + 180.0F, 0.0F);
                            level.addFreshEntity(boss);
                            context.getSource().sendSuccess(() -> Component.literal("玄翎墨鹤已在前方落羽。"), true);
                            return 1;
                        }))));
    }
}
