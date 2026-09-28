package com.tnc.tnc.combat;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.network.CombatStatePacket;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

/** Server-authoritative downed/rescue lifecycle; never toggles the player's permanent invulnerable flag. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class DownedCombat {
    private static final String KEY="tncCombat";
    private static final UUID HEALTH=UUID.fromString("a1d6652e-09c8-4954-9949-df22b55b9a10");
    private static final UUID SLOW=UUID.fromString("a1d6652e-09c8-4954-9949-df22b55b9a11");
    private static final Map<UUID,Long> HELD=new HashMap<>();
    private static final Map<UUID,Rescue> RESCUES=new HashMap<>();
    private static final Set<UUID> WIPING=new HashSet<>();
    private record Rescue(UUID target,int ticks,long lastTick) {}
    private DownedCombat() {}
    static CompoundTag data(Player p) {
        var root=p.getPersistentData();
        if(!root.contains(Player.PERSISTED_NBT_TAG))root.put(Player.PERSISTED_NBT_TAG,new CompoundTag());
        var saved=root.getCompound(Player.PERSISTED_NBT_TAG);
        if(!saved.contains(KEY))saved.put(KEY,new CompoundTag());
        return saved.getCompound(KEY);
    }
    public static boolean isDowned(Player p) { return data(p).getBoolean("downed"); }
    private static long now(ServerPlayer p) { return p.server.overworld().getGameTime(); }
    public static boolean eligible(ServerPlayer p) { return !p.isCreative()&&!p.isSpectator(); }
    public static void ensureHealth(ServerPlayer p) {
        var attr=p.getAttribute(Attributes.MAX_HEALTH);
        if(attr!=null&&attr.getModifier(HEALTH)==null) {
            float old=p.getMaxHealth(),health=p.getHealth();
            attr.addPermanentModifier(new AttributeModifier(HEALTH,"TN-C base health",CombatRules.HEALTH_BONUS,AttributeModifier.Operation.ADDITION));
            // A new full-health player enters with a full larger bar; injured players do not get free healing.
            if(health>=old&&!isDowned(p))p.setHealth(p.getMaxHealth());
        }
    }
    /** Called after other legitimate death prevention (totem / fire revive) has had its chance. */
    public static boolean enter(ServerPlayer p) {
        if(!eligible(p)||WIPING.contains(p.getUUID()))return false;
        if(isDowned(p)) {p.setHealth(1);return true;}
        String group=CombatTeams.group(p);
        long present=p.server.getPlayerList().getPlayers().stream().filter(DownedCombat::eligible)
                .filter(q->(q==p||q.isAlive())&&CombatTeams.group(q).equals(group)).count();
        boolean solo=present<=1;
        var tag=data(p);long tick=now(p);
        if(solo&&!CombatRules.canSoloDown(tick,tag.getLong("soloUntil")))return false;
        tag.putBoolean("downed",true);tag.putBoolean("solo",solo);tag.putString("group",group);
        if(solo)tag.putLong("soloUntil",tick+CombatRules.SOLO_COOLDOWN);
        tag.putDouble("x",p.getX());tag.putDouble("y",p.getY());tag.putDouble("z",p.getZ());
        tag.putString("dimension",p.level().dimension().location().toString());
        p.setHealth(1);p.stopRiding();p.stopUsingItem();p.setDeltaMovement(Vec3.ZERO);
        freeze(p);com.tnc.tnc.magic.compat.CombatCasting.cancel(p);
        send(p,solo?1:2,0,solo?"按住救援键自救":"等待队友救援",p.getId());
        return true;
    }
    private static void freeze(ServerPlayer p) {
        p.setForcedPose(Pose.SWIMMING);
        var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null&&speed.getModifier(SLOW)==null)speed.addTransientModifier(new AttributeModifier(SLOW,"TN-C downed",-1,AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
    public static void revive(ServerPlayer p) {
        data(p).putBoolean("downed",false);release(p);p.setHealth(CombatRules.revivedHealth(p.getMaxHealth()));
        p.invulnerableTime=40;send(p,0,0,"已获救",p.getId());
        RESCUES.entrySet().removeIf(e->e.getKey().equals(p.getUUID())||e.getValue().target.equals(p.getUUID()));
    }
    private static void release(ServerPlayer p) {
        p.setForcedPose(null);var speed=p.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(SLOW);
        HELD.remove(p.getUUID());
    }
    public static void hold(ServerPlayer p,boolean held) {
        if(!held) {HELD.remove(p.getUUID());RESCUES.remove(p.getUUID());return;}
        if(!eligible(p)||!p.isAlive())return;
        HELD.put(p.getUUID(),now(p));
    }
    public static boolean canRescue(ServerPlayer helper,ServerPlayer target) {
        if(!eligible(helper)||!helper.isAlive()||!isDowned(target))return false;
        if(helper==target)return data(target).getBoolean("solo");
        if(isDowned(helper)||data(target).getBoolean("solo")||helper.level()!=target.level()
                || !CombatTeams.group(helper).equals(data(target).getString("group"))
                || helper.distanceToSqr(target)>CombatRules.RESCUE_DISTANCE*CombatRules.RESCUE_DISTANCE)return false;
        return com.tnc.tnc.magic.water.TNWaterFieldEntity.visible(helper.serverLevel(),helper.getEyePosition(),target.position().add(0,.4,0));
    }
    private static ServerPlayer target(ServerPlayer helper) {
        if(isDowned(helper))return canRescue(helper,helper)?helper:null;
        return helper.server.getPlayerList().getPlayers().stream().filter(p->canRescue(helper,p))
                .min(Comparator.comparingDouble(helper::distanceToSqr)).orElse(null);
    }
    public static void tickRescue(ServerPlayer helper) {
        long tick=now(helper);ServerPlayer target=target(helper);
        if(!HELD.containsKey(helper.getUUID())||tick-HELD.get(helper.getUUID())>6||target==null) {
            if(HELD.containsKey(helper.getUUID())&&tick-HELD.get(helper.getUUID())>6)HELD.remove(helper.getUUID());
            if(RESCUES.remove(helper.getUUID())!=null&&!isDowned(helper))send(helper,0,0,"",-1);
            return;
        }
        Rescue old=RESCUES.get(helper.getUUID());
        int progress=old!=null&&old.target.equals(target.getUUID())&&old.lastTick==tick-1?old.ticks+1:1;
        if(old!=null&&old.lastTick==tick)return; // Network spam never advances server time.
        RESCUES.put(helper.getUUID(),new Rescue(target.getUUID(),progress,tick));
        if(progress%5==0) {
            send(helper,helper==target?1:3,progress,"正在救援",target.getId());
            if(helper!=target)send(target,2,progress,helper.getName().getString()+"正在救援你",target.getId());
        }
        if(progress>=CombatRules.RESCUE_TICKS) {revive(target);if(helper!=target)send(helper,0,0,"救援完成",target.getId());}
    }
    private static void send(ServerPlayer p,int mode,int ticks,String text,int target) {
        MagicStoneNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new CombatStatePacket(mode,ticks,text,target));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void death(LivingDeathEvent e) {
        if(e.getEntity() instanceof ServerPlayer p&&!e.isCanceled()&&enter(p))e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent e) {
        if(e.getEntity() instanceof Player p&&isDowned(p)&&!WIPING.contains(p.getUUID()))e.setCanceled(true);
        else if(e.getSource().getEntity() instanceof Player p&&isDowned(p))e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void hurt(LivingHurtEvent e) {
        if(e.getEntity() instanceof Player p&&isDowned(p)&&!WIPING.contains(p.getUUID()))e.setCanceled(true);
    }
    @SubscribeEvent public static void heal(LivingHealEvent e) {if(e.getEntity() instanceof Player p&&isDowned(p))e.setCanceled(true);}
    @SubscribeEvent public static void interact(PlayerInteractEvent e) {if(isDowned(e.getEntity())&&e.isCancelable())e.setCanceled(true);}
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e) {if(isDowned(e.getPlayer()))e.setCanceled(true);}
    @SubscribeEvent public static void place(BlockEvent.EntityPlaceEvent e) {if(e.getEntity() instanceof Player p&&isDowned(p))e.setCanceled(true);}
    @SubscribeEvent public static void attackEntity(AttackEntityEvent e) {if(isDowned(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void use(LivingEntityUseItemEvent.Start e) {if(e.getEntity() instanceof Player p&&isDowned(p))e.setCanceled(true);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) {ensureHealth(p);if(isDowned(p))freeze(p);}
    }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) {
        var old=data(e.getOriginal()).copy();
        // Forge also clones when returning from the End. That must not bypass a pending rescue.
        if(e.isWasDeath())old.putBoolean("downed",false);
        var root=e.getEntity().getPersistentData();
        if(!root.contains(Player.PERSISTED_NBT_TAG))root.put(Player.PERSISTED_NBT_TAG,new CompoundTag());
        root.getCompound(Player.PERSISTED_NBT_TAG).put(KEY,old);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id=e.getEntity().getUUID();HELD.remove(id);RESCUES.remove(id);
        RESCUES.entrySet().removeIf(x->x.getValue().target.equals(id));
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) {HELD.clear();RESCUES.clear();WIPING.clear();CombatTeams.reset();}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e) {
        if(e.phase!=TickEvent.Phase.END)return;
        MinecraftServer server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return;
        for(ServerPlayer p:List.copyOf(server.getPlayerList().getPlayers())) {
            ensureHealth(p);
            if(isDowned(p)) {
                freeze(p);p.setHealth(1);p.setDeltaMovement(Vec3.ZERO);p.stopUsingItem();
                com.tnc.tnc.magic.compat.CombatCasting.cancel(p);
                var tag=data(p);Vec3 anchor=new Vec3(tag.getDouble("x"),tag.getDouble("y"),tag.getDouble("z"));
                var dimension=net.minecraft.resources.ResourceLocation.tryParse(tag.getString("dimension"));
                var original=dimension==null?null:server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,dimension));
                if(original!=null&&(p.level()!=original||p.position().distanceToSqr(anchor)>.01))
                    p.teleportTo(original,anchor.x,anchor.y,anchor.z,p.getYRot(),p.getXRot());
                if(now(p)%5==0) {
                    int progress=RESCUES.values().stream().filter(r->r.target.equals(p.getUUID())).mapToInt(Rescue::ticks).max().orElse(0);
                    send(p,tag.getBoolean("solo")?1:2,progress,tag.getBoolean("solo")?"按住救援键自救":"等待队友救援",p.getId());
                }
            } else if(!HELD.containsKey(p.getUUID())&&now(p)%10==0) {
                ServerPlayer nearby=target(p);
                send(p,nearby==null?0:4,0,nearby==null?"":nearby.getName().getString()+"倒地：按住救援键",nearby==null?-1:nearby.getId());
            }
            tickRescue(p);
        }
        for(ServerPlayer down:List.copyOf(server.getPlayerList().getPlayers())) {
            if(!isDowned(down)||data(down).getBoolean("solo"))continue;
            String group=data(down).getString("group");
            var members=server.getPlayerList().getPlayers().stream().filter(DownedCombat::eligible)
                    .filter(p->isDowned(p)?data(p).getString("group").equals(group):CombatTeams.group(p).equals(group)).toList();
            long standing=members.stream().filter(p->p.isAlive()&&!isDowned(p)).count();
            if(CombatRules.wipe(members.size(),(int)standing))wipe(members);
        }
    }
    public static void wipe(List<ServerPlayer> members) {
        for(var p:members)if(isDowned(p)) {
            data(p).putBoolean("downed",false);release(p);RESCUES.remove(p.getUUID());
            WIPING.add(p.getUUID());
            try {
                // Bypass secondary resurrection for the explicitly requested whole-party defeat.
                p.removeEffect(com.tnc.tnc.magic.TNFireMechanics.BLAZE_BURN.get());
                p.removeEffect(com.tnc.tnc.magic.TNFireMechanics.INFERNO_BURN.get());
                p.setHealth(0);p.die(p.damageSources().genericKill());
            } finally {WIPING.remove(p.getUUID());send(p,0,0,"全队倒地",p.getId());}
        }
    }
}
