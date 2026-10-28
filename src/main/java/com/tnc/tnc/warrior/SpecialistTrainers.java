package com.tnc.tnc.warrior;

import com.tnc.tnc.TNMod;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Owned instructors use real shield, melee cooldown and wall-clipped wind-cut events. */
@Mod.EventBusSubscriber(modid=TNMod.MODID)
public final class SpecialistTrainers {
    static final String ROLE="TncSpecialist",OWNER="TncPracticeOwner";
    private static final Map<UUID,List<Husk>> NPCS=new HashMap<>();
    private static final Map<UUID,Lesson> LESSONS=new HashMap<>();
    private static final class Lesson{final String role;final UUID target;final long until;int progress;long next,click=Long.MIN_VALUE,lastHit=Long.MIN_VALUE;boolean charged;Lesson(String r,Husk t,long now){role=r;target=t.getUUID();until=now+900;next=now+50;}}
    private SpecialistTrainers(){}
    static String name(String role){return switch(role){case "guard"->"盾卫教官 · 洛克";case "blade"->"剑术教官 · 莉娅";default->"风刃教官 · 岚";};}
    static int goal(String role){return role.equals("wind")?3:5;}
    static boolean isWindTarget(Entity e){return e!=null&&"wind".equals(e.getPersistentData().getString(ROLE));}
    static List<Husk> trainers(ServerPlayer p){return NPCS.getOrDefault(p.getUUID(),List.of());}
    static void spawn(ServerPlayer p,int plot){
        remove(p);var result=new ArrayList<Husk>();NPCS.put(p.getUUID(),result);
        String[] roles={"guard","blade","wind"};int[] xs={-6,6,-6},zs={-2,-4,7};
        for(int i=0;i<roles.length;i++){
            var t=EntityType.HUSK.create(p.serverLevel());if(t==null)continue;String role=roles[i];
            t.getPersistentData().putUUID(OWNER,p.getUUID());t.getPersistentData().putString(ROLE,role);
            t.setNoAi(true);t.setPersistenceRequired();t.setCanPickUpLoot(false);t.setCustomNameVisible(true);t.setCustomName(Component.literal(name(role)+" · 右键训练"));
            t.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);t.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);t.setHealth(200);
            t.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.CARVED_PUMPKIN));
            var armor=new ItemStack(Items.LEATHER_CHESTPLATE);((DyeableLeatherItem)armor.getItem()).setColor(armor,i==0?0x6396c5:i==1?0xcf9b58:0x65c6a2);t.setItemSlot(EquipmentSlot.CHEST,armor);
            t.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(i==0?Items.WOODEN_SWORD:i==1?Items.IRON_SWORD:Items.FEATHER));if(i==0)t.setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.SHIELD));
            for(var slot:EquipmentSlot.values())t.setDropChance(slot,0);
            t.moveTo(TrainingGroundRules.centerX(plot)+xs[i]+.5,2,TrainingGroundRules.centerZ(plot)+zs[i]+.5,180,0);
            t.getPersistentData().putDouble("LessonX",t.getX());t.getPersistentData().putDouble("LessonZ",t.getZ());
            if(p.serverLevel().addFreshEntity(t))result.add(t);
        }
    }
    static void remove(ServerPlayer p){cancel(p,false);var list=NPCS.remove(p.getUUID());if(list!=null)list.forEach(Entity::discard);}
    static void cancel(ServerPlayer p,boolean notify){if(LESSONS.remove(p.getUUID())!=null&&notify)p.sendSystemMessage(Component.literal("专项训练已结束，可右键训练师重新开始。"));}
    static int progress(ServerPlayer p){var l=LESSONS.get(p.getUUID());return l==null?0:l.progress;}
    static String diagnostic(ServerPlayer p){var l=LESSONS.get(p.getUUID());return l==null?"no lesson":l.role+" progress="+l.progress+" charged="+l.charged+" hit="+l.lastHit+" until="+l.until;}
    static boolean begin(ServerPlayer p,Husk teacher){
        if(!TrainingGround.inside(p)||!TrainingGround.qualified(p)||!teacher.isAlive()||!teacher.getPersistentData().hasUUID(OWNER)||!teacher.getPersistentData().getUUID(OWNER).equals(p.getUUID())||p.distanceToSqr(teacher)>16)return false;
        String role=teacher.getPersistentData().getString(ROLE);if(!Set.of("guard","blade","wind").contains(role))return false;
        if(role.equals("guard")&&!(p.getOffhandItem().getItem() instanceof ShieldItem)){p.sendSystemMessage(Component.literal("洛克：请在副手装备盾牌，再右键开始。"));return false;}
        if(role.equals("blade")&&!TemperingEvents.weapon(p)){p.sendSystemMessage(Component.literal("莉娅：请在主手持剑或斧，再右键开始。"));return false;}
        if(role.equals("wind")&&(!WindInfusion.isWind(p.getMainHandItem())||TemperingData.get(p.server).points(p.getUUID())<200)){p.sendSystemMessage(Component.literal("岚：需要风灌注剑斧和 200 磨砺，再来练习风刃。"));return false;}
        TemperingTraining.cancel(p);LESSONS.put(p.getUUID(),new Lesson(role,teacher,p.server.overworld().getGameTime()));
        p.sendSystemMessage(Component.literal(name(role)+"："+switch(role){case "guard"->"面向我，完成五次有效盾挡。观察出招前摇；击退后向前补位。";case "blade"->"等攻击指示器蓄满，再持剑或斧命中我五次；急挥不计进度。";default->"退到至少三格外，面向我，持风灌注武器潜行对空右键，三次风刃命中。冷却仍为八秒。";}+" 限时45秒，潜行右键可取消。已完成 "+SpecialistTrainingData.get(p.server).count(p.getUUID(),role)+" 次。"));return true;
    }
    private static void advance(ServerPlayer p,Lesson lesson){
        lesson.progress++;p.displayClientMessage(Component.literal(name(lesson.role)+" · "+lesson.progress+"/"+goal(lesson.role)),true);
        if(lesson.progress<goal(lesson.role))return;LESSONS.remove(p.getUUID());SpecialistTrainingData.get(p.server).complete(p.getUUID(),lesson.role);
        p.sendSystemMessage(Component.literal(name(lesson.role)+"：本次专项训练完成！完成纪录已保存；专项练习不额外发放磨砺奖励。"));p.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,p.getX(),p.getY()+1,p.getZ(),12,.4,.6,.4,0);
    }
    @SubscribeEvent public static void interact(PlayerInteractEvent.EntityInteract e){
        if(!(e.getEntity() instanceof ServerPlayer p)||!(e.getTarget() instanceof Husk t)||!t.getPersistentData().contains(ROLE))return;
        e.setCanceled(true);e.setCancellationResult(InteractionResult.SUCCESS);if(e.getHand()!=net.minecraft.world.InteractionHand.MAIN_HAND)return;
        if(p.isShiftKeyDown()){if(t.getPersistentData().hasUUID(OWNER)&&t.getPersistentData().getUUID(OWNER).equals(p.getUUID()))cancel(p,true);return;}
        var old=LESSONS.get(p.getUUID());long now=p.server.overworld().getGameTime();if(old!=null&&old.click!=Long.MIN_VALUE&&now-old.click<10)return;
        if(begin(p,t))LESSONS.get(p.getUUID()).click=now;
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void block(ShieldBlockEvent e){if(e.getEntity() instanceof ServerPlayer p){var l=LESSONS.get(p.getUUID());if(l!=null&&l.role.equals("guard")&&e.getBlockedDamage()>0&&e.getDamageSource().getEntity()!=null&&l.target.equals(e.getDamageSource().getEntity().getUUID())){long now=p.server.overworld().getGameTime();if(l.lastHit==Long.MIN_VALUE||now-l.lastHit>=10){l.lastHit=now;advance(p,l);}}}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void swing(AttackEntityEvent e){if(e.getEntity() instanceof ServerPlayer p){var l=LESSONS.get(p.getUUID());if(l!=null&&l.role.equals("blade")&&l.target.equals(e.getTarget().getUUID())){l.charged=p.getAttackStrengthScale(.5f)>=.85f&&TemperingEvents.weapon(p);l.lastHit=p.server.overworld().getGameTime();}}}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void hit(LivingDamageEvent e){if(e.getAmount()>0&&e.getSource().getEntity() instanceof ServerPlayer p&&e.getSource().getDirectEntity()==p&&e.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)){var l=LESSONS.get(p.getUUID());if(l!=null&&l.role.equals("blade")&&l.target.equals(e.getEntity().getUUID())){if(l.charged&&l.lastHit==p.server.overworld().getGameTime()){l.charged=false;advance(p,l);}else p.displayClientMessage(Component.literal("莉娅：这次太急了，等攻击指示器蓄满。"),true);}}}
    static void windHit(ServerPlayer p,LivingEntity target){var l=LESSONS.get(p.getUUID());if(l!=null&&l.role.equals("wind")&&l.target.equals(target.getUUID())){if(p.distanceToSqr(target)>=9)advance(p,l);else p.displayClientMessage(Component.literal("岚：再退远一些，至少三格外命中才计数。"),true);}}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!(e.player instanceof ServerPlayer p)||!TrainingGround.inside(p))return;
        var l=LESSONS.get(p.getUUID());long now=p.server.overworld().getGameTime();if(l!=null&&(!TrainingGround.qualified(p)||TemperingTraining.active(p)||now>=l.until)){cancel(p,true);l=null;}
        for(var t:trainers(p)){if(t.isRemoved())continue;t.setPos(t.getPersistentData().getDouble("LessonX"),2,t.getPersistentData().getDouble("LessonZ"));t.setHealth(t.getMaxHealth());t.clearFire();var delta=p.position().subtract(t.position());float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));t.setYRot(yaw);t.yBodyRot=yaw;t.setYHeadRot(yaw);
            String role=t.getPersistentData().getString(ROLE);boolean active=l!=null&&l.target.equals(t.getUUID());t.setCustomName(Component.literal(name(role)+" · "+(active?l.progress+"/"+goal(role)+" · 剩余"+Math.max(0,(l.until-now)/20)+"秒":"右键训练")));
            if(!active||!role.equals("guard"))continue;if(p.distanceToSqr(t)>12.25){l.next=now+50;if(now%40==0)p.displayClientMessage(Component.literal("洛克：靠近我三格以内，正面举盾。"),true);continue;}
            if(now==l.next-15)p.serverLevel().sendParticles(ParticleTypes.CRIT,t.getX(),t.getY()+1.8,t.getZ(),5,.15,.15,.15,0);
            if(now>=l.next){l.next=now+50;t.swing(net.minecraft.world.InteractionHand.MAIN_HAND);p.hurt(p.damageSources().mobAttack(t),4);}
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){NPCS.clear();LESSONS.clear();}
}
