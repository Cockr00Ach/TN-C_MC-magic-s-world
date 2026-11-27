package com.tnc.tnc.magic.free;

import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.arcana.ArcanaMealEffects;
import com.tnc.tnc.boss.fire.TortoiseStun;
import com.tnc.tnc.combat.DownedCombat;
import com.tnc.tnc.dungeon.DungeonService;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.tnc.tnc.magic.free.FreeMagicNetwork.*;

@Mod.EventBusSubscriber(modid = "tnc")
public final class FreeMagicServer {
    private static final int MAX_ACTIVE = 12;
    private static final int SESSION_TIMEOUT = 800;
    private static final int FINAL_SUBTITLE_DURATION = 80;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();

    private FreeMagicServer() {}

    private static final class Session {
        final long id, started;
        final ServerPlayer caster;
        final ServerLevel level;
        final ItemStack wand;
        final Set<UUID> viewers = new HashSet<>();
        long lastText;
        Charge charge;
        List<Optional<Vec3>> stationaryTargets = List.of();

        Session(long id, long now, ServerPlayer caster) {
            this.id = id;
            this.started = now;
            this.lastText = now - 4;
            this.caster = caster;
            this.level = caster.serverLevel();
            this.wand = caster.getMainHandItem();
        }
    }

    private record Charge(FreeMagicIntent.Intent intent, int tier, int remainingWaves, long started,
                          int duration, String text) {
        int count() { return intent.formationCount(tier); }
        int cost() { return intent.costPerWave(tier); }

        Charge nextWave(long now) {
            return new Charge(intent, tier, remainingWaves - 1, now, intent.waveInterval(), text);
        }
    }

    static boolean canAct(ServerPlayer player) {return canRemain(player)&&!ElementalStates.silenced(player)&&!com.tnc.tnc.magic.water.ChaosSilence.silenced(player);}
    static boolean canRemain(ServerPlayer player) {
        return !com.tnc.tnc.dungeon.mirror.MirrorSilence.active(player) && player.isAlive() && !player.isSpectator() && !player.hasDisconnected()
                && !DownedCombat.isDowned(player) && !DungeonService.recovering(player)
                && !TortoiseStun.isStunned(player) && !player.hasEffect(ArcanaMealEffects.NO_MAGIC);
    }

    private static int wandTier(ServerPlayer player) {
        if (!canAct(player)) return 0;
        var item=player.getMainHandItem().getItem();
        if(item instanceof ElementWands.Wand wand)return wand.design.tier();
        return item==net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(com.tnc.tnc.magic.compat.SpellEngineBridge.WAND)?5:0;
    }

    private static int tier(ServerPlayer player) {
        if (!canAct(player)) return 0;
        if (wandTier(player)==0) return 0;
        var data = MagicStone.getOrNull(player);
        return data == null ? 0 : Math.min(wandTier(player), data.maxTierFor(Element.WATER));
    }

    private static boolean stillEligible(Session session, int requiredTier) {
        return session.caster.level() == session.level && session.caster.getMainHandItem() == session.wand
                && (session.charge==null?wandTier(session.caster)>0:tier(session.caster)>=requiredTier);
    }

    public static void input(ServerPlayer player, Input input) {
        long now = player.serverLevel().getGameTime();
        long serverTick = player.getServer().getTickCount();
        UUID id = player.getUUID();
        if(input.action()==STOP){ElementalRuntime.stop(player);return;}
        if(input.action()==CANCEL)ElementalRuntime.cancel(player,input.session());
        if (input.action() == BEGIN) {
            ElementalRuntime.beginSpeech(player);
            if(ElementalRuntime.busy(player)){error(player,"元素魔法正在凝聚，请稍候或使用停止键。");return;}
            if (SESSIONS.containsKey(id) || COOLDOWNS.getOrDefault(id, 0L) > serverTick) return;
            COOLDOWNS.put(id, serverTick + 10);
            if (wandTier(player) == 0) {
                error(player, "请在主手持有法杖，再吟唱已装配技能或自由魔法。");
                return;
            }
            if (WaterAttackEngine.activeCount(player) >= MAX_ACTIVE) {
                error(player, "上一轮水形尚未散去。");
                return;
            }
            SESSIONS.put(id, new Session(input.session(), now, player));
            return;
        }

        Session session = SESSIONS.get(id);
        if (session == null || session.id != input.session() || session.caster != player) return;
        if (input.action() == CANCEL || !stillEligible(session, requiredTier(session))
                || now - session.started > SESSION_TIMEOUT && session.charge == null) {
            SESSIONS.remove(id);
            end(session, CLEAR);
            return;
        }
        if (session.charge != null) return;

        if (input.action() == PARTIAL && now - session.lastText >= 4) {
            session.lastText = now;
            String text = FreeMagicIntent.normalize(input.text());
            chant(session, text, FreeMagicIntent.parse(text), 0);
        } else if (input.action() == FINAL) {
            prepare(session, now, FreeMagicIntent.clean(input.text()));
        }
    }

    private static int requiredTier(Session session) {
        return session.charge == null ? 1 : session.charge.intent().requiredTier();
    }

    private static void prepare(Session session, long now, String text) {
        ServerPlayer player = session.caster;
        var equipped=EquippedSpellVoice.match(text,MagicStone.getOrNull(player));
        if(equipped.isPresent()){
            var match=equipped.get();
            chant(session,text,Optional.empty(),FINAL_SUBTITLE_DURATION);
            if(wandTier(player)<match.spell().tier()) {reject(session,"法杖阶级不足，无法承载这个技能。");return;}
            if(!com.tnc.tnc.magic.compat.SpellEngineBridge.enginePresent()){reject(session,"当前实例缺少原有技能引擎。");return;}
            var data=MagicStone.getOrNull(player);
            data.setLoadoutPage(match.slot()/com.tnc.tnc.magic.MagicStoneData.SLOTS_PER_PAGE);
            com.tnc.tnc.magic.compat.SpellEngineBridge.ensureWand(player,data.learnedView());
            MagicStoneNetwork.syncTo(player);
            SESSIONS.remove(player.getUUID());end(session,CAST_END);
            COOLDOWNS.put(player.getUUID(),(long)player.getServer().getTickCount()+12);
            send(player,new Event(session.level.dimension().location(),EQUIPPED_CAST,player.getUUID(),session.id,
                    match.spell().id().toString(),0,0,1,player.position(),Vec3.ZERO,now,0,-1));
            return;
        }
        var elemental=ElementalIntent.parse(text,ElementalRuntime.context(player));
        if(elemental.isPresent()){
            String normalized=ElementalSpeech.normalize(text,ElementalRuntime.context(player));
            chant(session,normalized,Optional.empty(),FINAL_SUBTITLE_DURATION);
            String failure=ElementalRuntime.prepare(player,session.id,elemental.get());
            SESSIONS.remove(player.getUUID());end(session,CAST_END);
            if(!failure.isEmpty())error(player,failure);
            return;
        }
        Optional<FreeMagicIntent.Intent> parsed = FreeMagicIntent.parse(text);
        chant(session, text, parsed, FINAL_SUBTITLE_DURATION);
        if (parsed.isEmpty()) {
            reject(session, "没有辨认出当前元素能力或已学习并装配的技能名称。");
            return;
        }

        var intent = parsed.get();
        int tier = tier(player);
        if (tier < intent.requiredTier()) {
            reject(session, "这次塑形需要 " + intent.requiredTier() + " 阶水系亲和与法杖。");
            return;
        }
        var charge = new Charge(intent, tier, intent.waves(tier), now, intent.chargeTicks(), FreeMagicIntent.clean(text));
        int count = charge.count();
        if (WaterAttackEngine.activeCount(player) + count > MAX_ACTIVE) {
            reject(session, "已有水形过多，请等它们散去。");
            return;
        }
        var data = MagicStone.getOrNull(player);
        if (data == null || data.getMana() < charge.cost()) {
            reject(session, "魔力不足，水形未能凝聚。");
            return;
        }
        session.charge = charge;
        showCharge(session);
    }

    private static void reject(Session session, String message) {
        SESSIONS.remove(session.caster.getUUID());
        COOLDOWNS.put(session.caster.getUUID(), (long) session.level.getServer().getTickCount() + 12);
        end(session, CAST_END);
        error(session.caster, message);
    }

    private static void chant(Session session, String text, Optional<FreeMagicIntent.Intent> intent, int duration) {
        int shape = intent.map(value -> value.shape().ordinal() + 1).orElse(0);
        int mode = intent.map(value -> value.mode().ordinal()).orElse(0);
        int count = intent.map(value -> value.formationCount(tier(session.caster))).orElse(1);
        int modifiers = intent.map(FreeMagicIntent.Intent::modifiers).orElse(0);
        int chantPower = intent.map(FreeMagicIntent.Intent::chantPower).orElse(0);
        show(session, CHANT, FreeMagicIntent.clean(text), shape, mode, count, session.level.getGameTime(), duration,
                List.of(), modifiers, chantPower);
    }

    private static void showCharge(Session session) {
        Charge charge = session.charge;
        var intent = charge.intent();
        int count = charge.count();
        List<Vec3> previews = List.of();
        if (session.stationaryTargets.isEmpty()) {
            var targets = new ArrayList<Optional<Vec3>>(count);
            for (int index = 0; index < count; index++) {
                targets.add(intent.shape().stationary()?WaterAttackEngine.stationaryTarget(session.caster,intent,index,count)
                        :WaterAttackEngine.formationOrigin(session.caster,intent,index,count));
            }
            session.stationaryTargets = List.copyOf(targets);
        }
        Vec3 fallback=session.caster.getEyePosition();
        previews=session.stationaryTargets.stream().map(target->target.orElse(fallback)).toList();
        show(session, CHARGE, charge.text(), intent.shape().ordinal() + 1, intent.mode().ordinal(), count,
                charge.started(), charge.duration(), previews, intent.modifiers(), intent.chantPower());
    }

    private static void show(Session session, int kind, String text, int shape, int mode, int count,
                             long started, int duration, List<Vec3> targets, int modifiers, int chantPower) {
        ServerPlayer player = session.caster;
        Vec3 eye = player.getEyePosition();
        Vec3 at = targets.isEmpty() ? eye : targets.get(targets.size() / 2);
        var packet = new Event(session.level.dimension().location(), kind, player.getUUID(), session.id, text,
                shape, mode, count, at, player.getLookAngle(), started, duration, -1, targets, modifiers, chantPower);
        showNearby(session.level, session.viewers, packet, eye);
    }

    private static void end(Session session, int kind) {
        var packet = new Event(session.level.dimension().location(), kind, session.caster.getUUID(), session.id, "",
                0, 0, 1, session.caster.position(), Vec3.ZERO, session.level.getGameTime(), 0, -1);
        finishForViewers(session.level, session.viewers, packet);
    }

    private static void error(ServerPlayer player, String message) {
        send(player, new Event(player.level().dimension().location(), ERROR, player.getUUID(), 0, message,
                0, 0, 1, player.position(), Vec3.ZERO, player.serverLevel().getGameTime(), 0, -1));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        COOLDOWNS.entrySet().removeIf(entry -> entry.getValue() <= event.getServer().getTickCount());
        for (Iterator<Session> iterator = SESSIONS.values().iterator(); iterator.hasNext();) {
            Session session = iterator.next();
            long now = session.level.getGameTime();
            if (!stillEligible(session, requiredTier(session))
                    || session.charge == null && now - session.started > SESSION_TIMEOUT) {
                iterator.remove();
                end(session, CLEAR);
                continue;
            }
            if (session.charge == null) continue;

            long elapsed = now - session.charge.started();
            if (elapsed >= session.charge.duration()) {
                if (!release(session, now)) iterator.remove();
            } else if (elapsed > 0 && elapsed % 5 == 0) {
                showCharge(session);
            }
        }

        WaterAttackEngine.tick();
    }

    private static boolean release(Session session, long now) {
        ServerPlayer player = session.caster;
        Charge charge = session.charge;
        var intent = charge.intent();
        COOLDOWNS.put(player.getUUID(), (long) player.getServer().getTickCount() + 12);
        end(session, CAST_END);
        if (WaterAttackEngine.activeCount(player) + charge.count() > MAX_ACTIVE) {
            error(player, "已有水形过多，请等它们散去。");
            return false;
        }
        if (session.stationaryTargets.stream().anyMatch(target -> target.isEmpty()
                || !WaterAttackEngine.validPlacement(player, intent, target.get()))) {
            error(player, "落点已被遮挡，水形未能凝聚。");
            return false;
        }
        var data = MagicStone.getOrNull(player);
        if (data == null || !data.spendMana(charge.cost())) {
            error(player, "魔力不足，水形未能凝聚。");
            return false;
        }

        MagicStoneNetwork.syncTo(player);
        for (int index = 0; index < charge.count(); index++) {
            if (intent.shape().stationary()) {
                // Use the last displayed targets, so the field emerges where the circle was drawn.
                WaterAttackEngine.launchStationary(player, intent, session.stationaryTargets.get(index).orElseThrow());
            } else {
                WaterAttackEngine.launchFrom(player,intent,session.stationaryTargets.get(index).orElseThrow());
            }
        }
        if (charge.remainingWaves() <= 1) return false;
        if (data.getMana() < charge.cost()) {
            error(player, "魔力不足，后续水形未能凝聚。");
            return false;
        }
        session.charge = charge.nextWave(now);
        session.stationaryTargets=List.of();
        showCharge(session);
        return true;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) interruptSpells(p);
    }

    public static void interruptSpells(ServerPlayer player) {
        UUID id = player.getUUID();
        Session session = SESSIONS.remove(id);
        if (session != null) end(session, CLEAR);
        COOLDOWNS.remove(id);
        WaterAttackEngine.removeOwner(id);
        ElementalRuntime.stop(player);
        ElementalEffects.remove(id);
        FireMagicEngine.remove(id);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        SESSIONS.clear();
        COOLDOWNS.clear();
        WaterAttackEngine.clear();
    }
}
