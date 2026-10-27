package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.combat.DownedCombat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Real blocks and counters; field safety is handled by the dedicated training ground. */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public final class TemperingTraining {
    private static final Map<UUID, GuardDrill> ACTIVE = new HashMap<>();
    private TemperingTraining() {}
    private static boolean eligible(ServerPlayer p) {
        return p.isAlive() && !p.isCreative() && !p.isSpectator() && !DownedCombat.isDowned(p)
            && TemperingData.get(p.server).enrolled(p.getUUID());
    }
    public static boolean start(ServerPlayer p) {
        if (!eligible(p)) return false;
        if(ACTIVE.containsKey(p.getUUID())) {TemperingEvents.sync(p);return true;}
        int mode=TrainingGround.inside(p)?TrainingGroundData.get(p.server).record(p.getUUID()).mode():0;
        ACTIVE.put(p.getUUID(), new GuardDrill(mode));
        if(TrainingGround.inside(p))TrainingGround.resetRhythm(p);
        var data=TemperingData.get(p.server);
        int reward=Math.min(data.cap(p.getUUID())-data.points(p.getUUID()),WarriorTalentRules.gain(GuardDrill.REWARD,data.talent(p.getUUID())));
        p.sendSystemMessage(Component.literal("守势反击 · "+PracticeDifficulty.name(mode)+"：持盾格挡三次，再于"+(PracticeDifficulty.window(mode)/20)+"秒内持剑或斧反击最后一个敌人。超时可再次格挡。"
            +(TrainingGround.inside(p)?"场内保护生命；武器攻击仍消耗耐久。":"野外实战照常受伤。")
            +(data.guardGraduate(p.getUUID())?"首次训练已完成，本次为练习。":"本次首次完成预计获得 "+reward+" 磨砺。")));
        TemperingEvents.sync(p);
        return true;
    }
    public static void cancel(ServerPlayer p) { if(ACTIVE.remove(p.getUUID())!=null)TemperingEvents.sync(p); }
    public static boolean active(ServerPlayer p) {return ACTIVE.containsKey(p.getUUID());}
    static int mode(ServerPlayer p){var d=ACTIVE.get(p.getUUID());return d==null?TrainingGroundData.get(p.server).record(p.getUUID()).mode():d.mode();}
    static void miss(ServerPlayer p){var d=ACTIVE.get(p.getUUID());if(d!=null)d.miss();}
    public static int remaining(ServerPlayer p,long now) {
        var drill=ACTIVE.get(p.getUUID());return drill==null?0:drill.remaining(now);
    }
    static int blockCount(ServerPlayer p) {
        var drill = ACTIVE.get(p.getUUID()); return drill == null ? 0 : drill.blocks();
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void block(ShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !eligible(p) || event.getBlockedDamage() <= 0
            || !(event.getDamageSource().getEntity() instanceof Enemy)
            || p.isAlliedTo(event.getDamageSource().getEntity())) return;
        var drill = ACTIVE.get(p.getUUID());
        if (drill != null && drill.block(event.getDamageSource().getEntity().getUUID(), p.server.overworld().getGameTime())) {
            TemperingEvents.sync(p);
            p.displayClientMessage(Component.literal("守势反击 · 格挡 " + drill.blocks() + "/3"
                + (drill.blocks() == 3 ? " · "+(PracticeDifficulty.window(drill.mode())/20)+"秒内反击刚才的敌人" : "")), true);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void counter(LivingDamageEvent event) {
        if (event.getAmount() <= 0 || !(event.getEntity() instanceof Enemy)
            || !(event.getSource().getEntity() instanceof ServerPlayer p) || !eligible(p)
            || event.getSource().getDirectEntity() != p || !event.getSource().is(DamageTypes.PLAYER_ATTACK)
            || !TemperingEvents.weapon(p) || p.isAlliedTo(event.getEntity())) return;
        var drill = ACTIVE.get(p.getUUID());
        if (drill == null || !drill.counter(event.getEntity().getUUID(), p.server.overworld().getGameTime())) return;
        ACTIVE.remove(p.getUUID());
        if(TrainingGround.inside(p)){
            int reaction=drill.reaction(p.server.overworld().getGameTime()),score=PracticeDifficulty.score(drill.misses(),reaction);
            TrainingGroundData.get(p.server).complete(p.getUUID(),drill.mode(),score,reaction);
            p.sendSystemMessage(Component.literal("陪练结算 · "+PracticeDifficulty.name(drill.mode())+" · "+PracticeDifficulty.grade(score)+" "+score+"分 · 漏挡 "+drill.misses()+"次 · 反击 "+String.format(java.util.Locale.ROOT,"%.2f",reaction/20.0)+"秒。按修习快捷键可查看本档纪录。"));
            p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER,p.getX(),p.getY()+1,p.getZ(),12,.5,.7,.5,0);
            p.serverLevel().playSound(null,p.blockPosition(),net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP,net.minecraft.sounds.SoundSource.PLAYERS,.5f,1.3f);
        }
        var data = TemperingData.get(p.server);
        if (data.completeGuardDrill(p.getUUID())) {
            int gained = TemperingEvents.award(p, GuardDrill.REWARD);
            p.sendSystemMessage(Component.literal("守势反击 · 首次完成，磨砺 +" + gained + "。可再次训练，但不重复领取首次奖励。"));
        } else p.sendSystemMessage(Component.literal("守势反击 · 训练完成。首次奖励已领取，本次不重复加点。"));
        TemperingEvents.sync(p);
    }
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e) {
        if(e.phase==TickEvent.Phase.END && e.player instanceof ServerPlayer p && active(p) && !eligible(p)) {
            cancel(p);p.sendSystemMessage(Component.literal("本次守势反击训练已中断，可在恢复后重新开始。"));
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { ACTIVE.remove(e.getEntity().getUUID()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e) { if(e.getEntity() instanceof ServerPlayer p) cancel(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if(e.getEntity() instanceof ServerPlayer p) cancel(p); }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { ACTIVE.clear(); }
}
