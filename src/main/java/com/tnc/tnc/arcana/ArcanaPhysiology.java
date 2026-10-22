package com.tnc.tnc.arcana;
import com.tnc.tnc.journey.JourneyData;
import com.tnc.tnc.magic.MagicStone;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
/** Persistent physiology. World ticks advance only while the server is running. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class ArcanaPhysiology {
    public static CompoundTag data(ServerPlayer p){var root=JourneyData.get(p.server).player(p.getUUID());if(!root.contains("DeepArcana",10))root.put("DeepArcana",new CompoundTag());return root.getCompound("DeepArcana");}
    public static int toxicity(ServerPlayer p){return RitualRules.clamp(data(p).getInt("mana_toxicity"),100);}
    public static void toxic(ServerPlayer p,int amount){var t=data(p);t.putInt("mana_toxicity",RitualRules.clamp(toxicity(p)+amount,100));JourneyData.get(p.server).setDirty();}
    public static long now(ServerPlayer p){return p.server.overworld().getGameTime();}
    public static boolean clearChannels(ServerPlayer p){return data(p).getLong("ClearUntil")>now(p);}
    public static boolean permitDrink(ServerPlayer p){if(toxicity(p)<=80)return true;
        if(now(p)-data(p).getLong("LastVomit")>=20){data(p).putLong("LastVomit",now(p));p.hurt(p.damageSources().magic(),1);p.addEffect(new MobEffectInstance(MobEffects.CONFUSION,100,0));}
        p.displayClientMessage(net.minecraft.network.chat.Component.literal("魔力淤积过高，先去魔导温泉排毒。"),true);return false;
    }
    public static double potency(ServerPlayer p,RitualRules.Quality q){return q.potency*(toxicity(p)>50?.5:1);}
    public static void drink(ServerPlayer p,RitualRules.Quality q){toxic(p,p.hasEffect(ArcanaMealEffects.HARMONY)?(q.toxicity+1)/2:q.toxicity);if(q==RitualRules.Quality.MASTER)data(p).putLong("MasterAuraUntil",now(p)+1200);}
    public static void detox(ServerPlayer p,int amount){int old=toxicity(p);if(old==0)return;toxic(p,-amount);if(toxicity(p)==0){data(p).putLong("ClearUntil",now(p)+24000);p.displayClientMessage(net.minecraft.network.chat.Component.literal("经脉通畅 · 一日内回魔加快50%，魔力视精神免耗"),true);}}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||p.tickCount%20!=0||!p.isAlive()||p.isSpectator())return;
        var t=data(p);long now=now(p);boolean sight=com.tnc.tnc.mana.sight.ManaSightServer.enabled(p);
        int seconds=sight?t.getInt("VisionSeconds")+1:0;t.putInt("VisionSeconds",seconds);
        double sanity=Math.max(0,Math.min(100,t.getDouble("vision_sanity")));
        if(sight&&seconds>180&&!clearChannels(p))sanity=Math.min(100,sanity+.28);else if(!sight)sanity=Math.max(0,sanity-.5);
        t.putDouble("vision_sanity",sanity);
        if(toxicity(p)>50){p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,30,0,false,false));if(p.tickCount%100==0)p.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,p.getX(),p.getEyeY()-.15,p.getZ(),5,.12,.1,.12,.02);}
        if(sight&&!clearChannels(p)){var look=p.getLookAngle();boolean glaring=luminousGaze(p,look);if(glaring){sanity=Math.min(100,sanity+.45);t.putDouble("vision_sanity",sanity);}}
        if(t.getLong("MealSunUntil")>now){var m=MagicStone.getOrNull(p);if(m!=null){m.addMana(m.getMaxMana());ArcanaMeals.radiate(p);com.tnc.tnc.network.MagicStoneNetwork.syncTo(p);}}
        if(p.isInWater())ArcanaDevices.bathe(p);
        ArcanaMealEffects.tick(p);LeylineDiscoveries.guide(p);ArcanaEquipment.tick(p);ArcanaSpirits.tick(p,sight,sanity);
        var packet=new CompoundTag();packet.putString("Type","physiology");packet.putBoolean("DeadMana",ArcanaRealms.barren(p.level())&&!ArcanaRealms.powered(p.serverLevel(),p.blockPosition()));packet.putLongArray("Oases",ArcanaRealms.nearby(p));packet.putInt("Toxicity",toxicity(p));packet.putDouble("Sanity",sanity);packet.putBoolean("Clear",clearChannels(p));packet.putBoolean("Bathing",t.getLong("BathingUntil")>now);packet.putBoolean("Sun",t.getLong("MealSunUntil")>now);packet.putBoolean("MasterAura",t.getLong("MasterAuraUntil")>now);packet.putBoolean("Aurora",ArcanaSky.aurora(p.serverLevel()));ArcanaNetwork.sync(p,packet);JourneyData.get(p.server).setDirty();
    }
    private static boolean luminousGaze(ServerPlayer p,net.minecraft.world.phys.Vec3 look){
        if(p.serverLevel().isDay()&&p.serverLevel().canSeeSky(p.blockPosition())&&look.y>.92)return true;
        var hit=p.pick(32,0,false);return hit instanceof net.minecraft.world.phys.BlockHitResult b&&p.serverLevel().getBlockEntity(b.getBlockPos()) instanceof com.tnc.tnc.life.routes.RouteNodeEntity n&&(n.kind()==com.tnc.tnc.life.routes.RouteKind.SOLAR||n.kind()==com.tnc.tnc.life.routes.RouteKind.ABYSS_TOWER&&n.getPersistentData().getDouble("overload_level")>40);
    }
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent e){var C=net.minecraft.commands.Commands.literal("arcana");
        C.then(net.minecraft.commands.Commands.literal("status").executes(c->{var p=c.getSource().getPlayerOrException();p.sendSystemMessage(net.minecraft.network.chat.Component.literal("淤积 "+toxicity(p)+" / 精神过载 "+(int)data(p).getDouble("vision_sanity")+" / "+ArcanaSky.forecast(p.serverLevel())));return 1;}));
        C.then(net.minecraft.commands.Commands.literal("debug").requires(s->s.hasPermission(2))
            .then(net.minecraft.commands.Commands.literal("toxicity").then(net.minecraft.commands.Commands.argument("value",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,100)).executes(c->{var p=c.getSource().getPlayerOrException();data(p).putInt("mana_toxicity",com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"value"));JourneyData.get(p.server).setDirty();return 1;})))
            .then(net.minecraft.commands.Commands.literal("sanity").then(net.minecraft.commands.Commands.argument("value",com.mojang.brigadier.arguments.IntegerArgumentType.integer(0,100)).executes(c->{var p=c.getSource().getPlayerOrException();data(p).putDouble("vision_sanity",com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c,"value"));JourneyData.get(p.server).setDirty();return 1;})))
            .then(net.minecraft.commands.Commands.literal("aurora").executes(c->{ArcanaSky.forceTonight(c.getSource().getLevel());return 1;})));
        e.getDispatcher().register(C);
    }
}
