package com.tnc.tnc.arcana;
import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.life.routes.RouteNodeEntity;
import com.tnc.tnc.magic.SpellCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;
/** Call-scoped escrow. Only a successful release commits, and failed calls release their reservation. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="tnc")
public final class ArcanaBeacon {
    private static final class Cast {final ServerPlayer player;final ResourceLocation spell;final boolean release;RouteNodeEntity tower;int cost;boolean held,paid;Cast(ServerPlayer p,ResourceLocation s,boolean r){player=p;spell=s;release=r;}}
    private static final ThreadLocal<Deque<Cast>> CALLS=ThreadLocal.withInitial(ArrayDeque::new);
    public static long begun,committed,snapshots;
    private static boolean eligible(ServerPlayer p,SpellCatalog.Entry e){return e!=null&&e.tier()>=3&&p.getMainHandItem().getItem() instanceof ElementWands.Wand w&&w.design.tier()>=3&&w.design.element()==e.element();}
    public static int reserved(RouteNodeEntity tower){int total=0;for(var c:CALLS.get())if(c.tower==tower&&c.held)total+=c.cost;return total;}
    public static boolean affordable(ServerPlayer p,SpellCatalog.Entry e,int cost){var tower=eligible(p,e)?ArcanaRealms.beacon(p):null;return tower!=null&&tower.mana-reserved(tower)>=cost;}
    public static void enter(ServerPlayer p,ResourceLocation spell,boolean release){CALLS.get().push(new Cast(p,spell,release));}
    public static void provision(ServerPlayer p){var c=CALLS.get().peek();if(c==null||c.player!=p||!c.release||c.held||c.paid)return;var entry=SpellCatalog.byId(c.spell);if(!eligible(p,entry)||ArcanaPhysiology.data(p).getLong("MealSunUntil")>ArcanaPhysiology.now(p))return;var mana=com.tnc.tnc.magic.MagicStone.getOrNull(p);if(mana==null)return;int cost=com.tnc.tnc.equipment.MageGear.spellCost(p,entry.manaCostFor(mana.getMaxMana()));var tower=ArcanaRealms.beacon(p);if(tower==null||tower.mana-reserved(tower)<cost)return;c.tower=tower;c.cost=cost;c.held=true;begun++;}
    public static boolean pay(ServerPlayer p,SpellCatalog.Entry entry,int cost){var c=CALLS.get().peek();if(c==null||c.player!=p||!c.spell.equals(entry.id()))return false;if(c.paid)return true;if(!c.held||c.cost!=cost)return false;c.held=false;if(!c.tower.spend(cost))return false;c.paid=true;committed++;
        var start=c.tower.getLevel()==p.level()?net.minecraft.world.phys.Vec3.atCenterOf(c.tower.getBlockPos()):p.getEyePosition().add(0,5,0);com.tnc.tnc.mana.sight.ManaSightServer.record(p.serverLevel(),start,p.getEyePosition(),cost,0xffe4a0,4);return true;}
    public static void leave(ServerPlayer p){var calls=CALLS.get();if(!calls.isEmpty()&&calls.peek().player==p)calls.pop();if(calls.isEmpty())CALLS.remove();}
    public static float scale(LivingEntity caster){var c=CALLS.get().peek();return c!=null&&c.player==caster&&(c.held||c.paid)?1.5f:1;}
    @SubscribeEvent public static void spawned(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(e.getLevel().isClientSide)return;var c=CALLS.get().peek();if(c==null||scale(c.player)<=1||c.player.level()!=e.getLevel())return;var entity=e.getEntity();var id=net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());if(!id.getNamespace().equals("tnc")&&!id.getNamespace().equals("spell_engine")||entity instanceof LivingEntity)return;if(entity instanceof net.minecraft.world.entity.projectile.Projectile p&&p.getOwner()!=c.player)return;
        entity.getPersistentData().putFloat("BeaconScale",1.5f);snapshots++;if(entity instanceof com.tnc.tnc.magic.water.TNWaterBoltEntity water)water.setSpellScale(water.spellScale()*1.5f);if(com.tnc.tnc.magic.compat.SpellEngineBridge.enginePresent())Engine.expand(entity);
    }
    @SubscribeEvent public static void damage(net.minecraftforge.event.entity.living.LivingHurtEvent e){if(!(e.getSource().getEntity() instanceof ServerPlayer p))return;var direct=e.getSource().getDirectEntity();float factor=direct==null?1:direct.getPersistentData().getFloat("BeaconScale");if(factor<=1)factor=scale(p);if(factor>1)e.setAmount(e.getAmount()*1.5f);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void endTick(net.minecraftforge.event.TickEvent.ServerTickEvent e){if(e.phase==net.minecraftforge.event.TickEvent.Phase.END)CALLS.remove();}
    @SubscribeEvent public static void stop(net.minecraftforge.event.server.ServerStoppedEvent e){CALLS.remove();}
    private static final class Engine{static void expand(Entity p){if(p instanceof net.spell_engine.entity.SpellProjectile spell)spell.range*=1.5f;}}
    private ArcanaBeacon(){}
}
