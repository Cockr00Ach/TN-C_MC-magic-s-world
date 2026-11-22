package com.tnc.tnc.magic.free;

import com.tnc.tnc.adventure.ElementWands;
import com.tnc.tnc.magic.*;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Charging and debit authority. Each elemental engine implements its own actual actions. */
@Mod.EventBusSubscriber(modid="tnc")
public final class ElementalRuntime {
    private static final Map<UUID,Charge> CHARGES=new HashMap<>();
    private static long sequence=1_000_000;
    static long nextId(){return ++sequence;}
    record Placement(Vec3 at,LivingEntity target,long visualId){}
    static final class Charge {
        final ServerPlayer owner;final ServerLevel level;final ItemStack wand;final ElementalIntent.Intent intent;
        final long session,id=nextId();final Set<UUID> viewers=new HashSet<>();
        final LivingEntity target;final Vec3 at,look;long started;int wave;float boost=1;
        final List<Placement> placements;
        Charge(ServerPlayer owner,long session,ElementalIntent.Intent intent,List<Placement> placements){
            this.owner=owner;level=owner.serverLevel();wand=owner.getMainHandItem();this.session=session;this.intent=intent;
            this.placements=List.copyOf(placements);this.at=placements.get(0).at();this.target=placements.get(0).target();look=owner.getLookAngle();started=level.getGameTime();
        }
        int count(){return placements.size();}
        void show(int phase){for(var p:placements)ElementalNetwork.show(owner,viewers,new ElementalNetwork.Visual(level.dimension().location(),owner.getUUID(),p.visualId(),phase,intent.ability().ordinal(),intent.flags(),intent.chantPower(),p.at(),look,started,wave==0?intent.charge():intent.interval(),p.target()==null?-1:p.target().getId()));}
    }
    public static Element context(Player player){return player.getMainHandItem().getItem() instanceof ElementWands.Wand wand?wand.design.element():Element.WATER;}
    static int tier(ServerPlayer player,Element element){
        var data=MagicStone.getOrNull(player);if(data==null||!FreeMagicServer.canAct(player))return 0;
        var item=player.getMainHandItem().getItem();
        int wand=item instanceof ElementWands.Wand w?w.design.tier():item==net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(com.tnc.tnc.magic.compat.SpellEngineBridge.WAND)?5:0;
        return Math.min(wand,data.maxTierFor(element));
    }
    static boolean busy(ServerPlayer player){return CHARGES.containsKey(player.getUUID());}
    static String prepare(ServerPlayer owner,long session,ElementalIntent.Intent intent){
        if(tier(owner,intent.ability().element())<intent.tier())return "需要 "+intent.tier()+" 阶"+(intent.ability().element()==Element.DARK?"暗":intent.ability().element().cn())+"系亲和与法杖。";
        if(busy(owner)||FireMagicEngine.count(owner)+ElementalEffects.count(owner)+intent.count()>12)return "现有自由魔法过多，请先等待或停止。";
        LivingEntity target=null;Vec3 at;
        switch(intent.ability().target){
            case ENEMY,CHANNEL_ENEMY -> {target=ElementalTargets.aimed(owner,24);if(target==null)return "准星前没有可作用的敌人。";at=target.getBoundingBox().getCenter();}
            case GROUND -> {var ground=ElementalTargets.ground(owner);if(ground.isEmpty())return "准星前没有可用地面。";at=ground.get();}
            case FRIEND -> {target=ElementalTargets.aimFriend(owner,24);at=target.getBoundingBox().getCenter();}
            case AIR -> at=ElementalTargets.trace(owner,owner.getEyePosition(),owner.getEyePosition().add(owner.getLookAngle().scale(12))).getLocation();
            case SELF,CHANNEL_SELF -> at=owner.position().add(0,.1,0);
            default -> at=ElementalTargets.origin(owner,intent.scale());
        }
        if(intent.ability()==ElementalIntent.Ability.DETONATE_BURN&&!FireMagicEngine.burning(owner,target))return "目标尚未被你的火焰灼烧。";
        String requirement=ElementalEffects.requirement(owner,intent,target,at);if(!requirement.isEmpty())return requirement;
        var placements=new ArrayList<Placement>();
        if(target!=null){
            placements.add(new Placement(at,target,nextId()));
            if(intent.count()>1)for(var other:ElementalTargets.area(owner,at,8))if(other!=target&&owner.hasLineOfSight(other)){placements.add(new Placement(other.getBoundingBox().getCenter(),other,nextId()));if(placements.size()>=intent.count())break;}
        }else for(int index=0;index<intent.count();index++){
            Vec3 next=at.add(WaterCastGeometry.right(owner.getLookAngle()).scale((index-(intent.count()-1)/2.0)*1.6*intent.scale()));
            if(intent.ability().target==ElementalIntent.Target.GROUND){var grounded=ElementalTargets.groundNear(owner,next);if(grounded.isEmpty())return "齐发落点缺少可靠地面。";next=grounded.get();}
            if(intent.ability().target!=ElementalIntent.Target.SELF&&intent.ability().target!=ElementalIntent.Target.CHANNEL_SELF&&(!ElementalTargets.visible(owner,owner.getEyePosition(),next)||intent.ability().target==ElementalIntent.Target.PROJECTILE&&!ElementalTargets.open(owner,next,.35*intent.scale())))return "阵列空间被遮挡，请调整准星。";
            placements.add(new Placement(next,null,nextId()));
        }
        var data=MagicStone.getOrNull(owner);if(data.getMana()<intent.cost()*placements.size())return "魔力不足，未开始凝聚。";
        var charge=new Charge(owner,session,intent,placements);CHARGES.put(owner.getUUID(),charge);charge.show(ElementalNetwork.FORM);return "";
    }
    static boolean pay(ServerPlayer owner,int cost){
        var data=MagicStone.getOrNull(owner);if(data==null||data.getMana()<cost)return false;
        data.setMana(data.getMana()-cost);MagicStone.markCast(owner);MagicStoneNetwork.syncTo(owner);return true;
    }
    static void cancel(ServerPlayer owner,long session){var charge=CHARGES.get(owner.getUUID());if(charge!=null&&charge.session==session){CHARGES.remove(owner.getUUID());charge.show(ElementalNetwork.END);}}
    static void stop(ServerPlayer owner){var charge=CHARGES.remove(owner.getUUID());if(charge!=null)charge.show(ElementalNetwork.END);FireMagicEngine.stopChannels(owner);ElementalEffects.stopChannels(owner);}
    static void beginSpeech(ServerPlayer owner){FireMagicEngine.stopChannels(owner);ElementalEffects.stopChannels(owner);}
    static void message(ServerPlayer owner,String text){
        FreeMagicNetwork.send(owner,new FreeMagicNetwork.Event(owner.level().dimension().location(),FreeMagicNetwork.ERROR,owner.getUUID(),0,text,0,0,0,owner.position(),Vec3.ZERO,owner.level().getGameTime(),0,-1));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        for(var iterator=CHARGES.values().iterator();iterator.hasNext();){
            var c=iterator.next();var owner=c.owner;long now=c.level.getGameTime();
            boolean valid=FreeMagicServer.canAct(owner)&&owner.level()==c.level&&owner.getMainHandItem()==c.wand&&tier(owner,c.intent.ability().element())>=c.intent.tier();
            if(!valid){c.show(ElementalNetwork.END);iterator.remove();continue;}
            int duration=c.wave==0?c.intent.charge():c.intent.interval();
            if(now-c.started<duration){if((now-c.started)%5==0)c.show(ElementalNetwork.FORM);continue;}
            boolean placementsValid=c.placements.stream().allMatch(p->p.target()!=null?(c.intent.ability().target==ElementalIntent.Target.FRIEND?ElementalTargets.friend(owner,p.target()):ElementalTargets.enemy(owner,p.target()))&&owner.hasLineOfSight(p.target())&&owner.distanceToSqr(p.target())<=28*28
                    :c.intent.ability().target==ElementalIntent.Target.SELF||c.intent.ability().target==ElementalIntent.Target.CHANNEL_SELF||ElementalTargets.visible(owner,owner.getEyePosition(),p.at())
                    &&(c.intent.ability().target!=ElementalIntent.Target.PROJECTILE||ElementalTargets.open(owner,p.at(),.35*c.intent.scale()))
                    &&(c.intent.ability().target!=ElementalIntent.Target.GROUND||ElementalTargets.groundNear(owner,p.at()).filter(g->g.distanceToSqr(p.at())<.05).isPresent()));
            String requirement=ElementalEffects.requirement(owner,c.intent,c.target,c.at);
            if(EarthMagicEngine.construction(c.intent.ability())&&c.intent.ability().target==ElementalIntent.Target.SELF&&(Math.abs(owner.getX()-c.at.x)>.2||Math.abs(owner.getZ()-c.at.z)>.2||Math.abs(owner.getY()-(c.at.y-.1))>.25))requirement="岩构凝聚时位置发生变化，请重新施法。";
            if(!requirement.isEmpty()){c.show(ElementalNetwork.END);iterator.remove();message(owner,requirement);continue;}
            if(!placementsValid||c.intent.ability()==ElementalIntent.Ability.DETONATE_BURN&&!FireMagicEngine.burning(owner,c.target)){
                if(Boolean.getBoolean("tnc.freeMagicAudit"))for(var p:c.placements){var t=p.target();System.out.println("TN-C elemental audit rejected "+c.intent.ability()+" owner="+owner.position()+" anchor="+p.at()+" target="+(t==null?"none":t.getType()+" pos="+t.position()+" alive="+t.isAlive()+" enemy="+ElementalTargets.enemy(owner,t)+" sight="+owner.hasLineOfSight(t)+" centerSight="+ElementalTargets.visible(owner,owner.getEyePosition(),t.getBoundingBox().getCenter())+" eye="+t.getEyePosition()+" distance="+owner.distanceToSqr(t)));}
                c.show(ElementalNetwork.END);iterator.remove();message(owner,"落点或目标已被遮挡，这一轮未扣魔力。");continue;
            }
            if(FireMagicEngine.count(owner)+ElementalEffects.count(owner)+c.count()>12||!pay(owner,c.intent.cost()*c.count())){
                c.show(ElementalNetwork.END);iterator.remove();message(owner,"容量或魔力不足，后续施法已停止。");continue;
            }
            c.show(ElementalNetwork.END);
            if(c.wave==0&&c.intent.ability()!=ElementalIntent.Ability.STORE_CHARGE&&c.intent.ability()!=ElementalIntent.Ability.SACRIFICE&&c.intent.ability()!=ElementalIntent.Ability.BLOOD_SACRIFICE)c.boost=ElementalStates.attackPower(owner,c.intent.ability().element());
            for(int i=0;i<c.count();i++){if(c.intent.ability().element()==Element.FIRE)FireMagicEngine.launch(c,i);else ElementalEffects.launch(c,i);}
            if(++c.wave>=c.intent.waves())iterator.remove();
            else {c.started=now;c.show(ElementalNetwork.FORM);}
        }
        FireMagicEngine.tick();ElementalEffects.tick();
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){remove(event.getEntity().getUUID());}
    private static void remove(UUID id){var charge=CHARGES.remove(id);if(charge!=null)charge.show(ElementalNetwork.END);FireMagicEngine.remove(id);ElementalEffects.remove(id);}
    @SubscribeEvent public static void shutdown(ServerStoppedEvent event){CHARGES.clear();FireMagicEngine.clear();ElementalEffects.clear();}
    public static void auditClear(ServerPlayer player){if(Boolean.getBoolean("tnc.freeMagicAudit")){remove(player.getUUID());for(var pos:TemporaryRock.owned(player,player.position(),64))TemporaryRock.remove(player.serverLevel(),pos);}}
    private ElementalRuntime(){}
}
