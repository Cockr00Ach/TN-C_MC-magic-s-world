package com.tnc.tnc.world.wind;

import com.tnc.tnc.combat.DownedCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Finite execution damage and a narrow guard for non-finite player health; never revives finite zero HP. */
@Mod.EventBusSubscriber(modid="tnc")
public final class CanglanExecutionHealth {
    private CanglanExecutionHealth(){}
    public static float damage(float health,float absorption,float maximum){
        double hp=Float.isFinite(health)?Math.max(0,health):0;
        double shield=Float.isFinite(absorption)?Math.max(0,absorption):0;
        double cap=Float.isFinite(maximum)&&maximum>0?maximum:20;
        return (float)Math.max(64,Math.min(1_000_000,cap*8+hp+shield+16));
    }
    public static float restoredHealth(float maximum,boolean downed){
        return downed?1:Float.isFinite(maximum)&&maximum>0?maximum:20;
    }
    public static boolean invalid(float health,float absorption){return !Float.isFinite(health)||!Float.isFinite(absorption);}
    public static boolean repair(LivingEntity victim){
        boolean badHealth=!Float.isFinite(victim.getHealth()),badShield=!Float.isFinite(victim.getAbsorptionAmount());
        if(!badHealth&&!badShield)return false;
        if(badShield)victim.setAbsorptionAmount(0);
        if(badHealth)victim.setHealth(restoredHealth(victim.getMaxHealth(),victim instanceof ServerPlayer p&&DownedCombat.isDowned(p)));
        if(victim instanceof ServerPlayer p&&!(p instanceof net.minecraftforge.common.util.FakePlayer))p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(p.getHealth(),p.getFoodData().getFoodLevel(),p.getFoodData().getSaturationLevel()));
        return true;
    }
    public static void finish(CanglanWarlordEntity boss,LivingEntity victim){
        repair(victim);victim.invulnerableTime=0;
        victim.hurt(boss.level().damageSources().mobAttack(boss),damage(victim.getHealth(),victim.getAbsorptionAmount(),victim.getMaxHealth()));
        // A third-party hurt handler may itself produce NaN; do not let that become a saved ghost player.
        if(repair(victim)&&victim instanceof ServerPlayer player&&!DownedCombat.isDowned(player)){
            if(!DownedCombat.enter(player)){player.setHealth(0);player.die(boss.level().damageSources().mobAttack(boss));}
            repair(player);
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p)repair(p);}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase==TickEvent.Phase.END&&e.player instanceof ServerPlayer p)repair(p);}
}
