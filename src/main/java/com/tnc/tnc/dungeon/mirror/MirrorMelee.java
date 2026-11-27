package com.tnc.tnc.dungeon.mirror;

import net.minecraft.core.*;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.UUID;

/** One committed hand action at a time. Decisions and finite supplies belong to this particular
 * reflection, survive entity saves and advance only while the room has a valid opponent. */
@Mod.EventBusSubscriber(modid="tnc")
public final class MirrorMelee {
    private static final String BRAIN="TncEchoDuel";
    private static CompoundTag state(EchoDouble m){
        var t=m.getPersistentData();
        if(!t.contains(BRAIN)){var s=new CompoundTag();s.putInt("Apples",2);s.putInt("Arrows",16);
            s.putInt("Stone",16);s.putInt("Webs",2);s.putBoolean("Lava",true);t.put(BRAIN,s);}
        return t.getCompound(BRAIN);
    }
    public static void cleanup(ServerLevel level,UUID run){MirrorTerrain.cleanup(level,run);}
    private static void hand(EchoDouble m,Item item){
        if(!m.getMainHandItem().is(item))m.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(item));
    }
    private static void begin(EchoDouble m,CompoundTag s,String key,int ticks,Item item){
        m.stopUsingItem();m.setSprinting(false);m.getNavigation().stop();hand(m,item);
        s.putString("Move",key);s.putInt("Age",0);s.putInt("End",ticks);m.action(key,ticks);
    }
    private static void recover(EchoDouble m,CompoundTag s,int ticks){begin(m,s,"recover",ticks,Items.IRON_SWORD);}
    private static void point(CompoundTag s,String key,Vec3 pos){
        s.putDouble(key+"X",pos.x);s.putDouble(key+"Y",pos.y);s.putDouble(key+"Z",pos.z);
    }
    private static Vec3 point(CompoundTag s,String key){return new Vec3(s.getDouble(key+"X"),s.getDouble(key+"Y"),s.getDouble(key+"Z"));}
    private static void face(EchoDouble m,Vec3 at){
        m.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,at);
        m.yBodyRot=m.getYRot();m.yHeadRot=m.getYRot();
    }
    private static boolean ready(CompoundTag s,String what){return s.getInt("Clock")>=s.getInt("Ready"+what);}
    private static void waitFor(CompoundTag s,String what,int ticks){s.putInt("Ready"+what,s.getInt("Clock")+ticks);}
    public static void tick(EchoDouble m,ServerPlayer target) {
        if(target==null||!(m.level() instanceof ServerLevel level))return;
        var s=state(m);int now=s.getInt("Clock")+1;s.putInt("Clock",now);
        long frame=level.getGameTime();
        // The actor's visual clock continues during a disconnect. Re-telegraph upon return
        // instead of landing a saved axe swing after its client-side animation expired.
        if(s.contains("LastLiveTick")&&frame-s.getLong("LastLiveTick")>2)recover(m,s,8);
        s.putLong("LastLiveTick",frame);
        if(!s.hasUUID("Opponent")||!target.getUUID().equals(s.getUUID("Opponent"))){
            s.putUUID("Opponent",target.getUUID());s.remove("Move");s.putInt("ShieldTicks",0);
            m.stopUsingItem();m.getNavigation().stop();
        }
        s.putInt("ShieldTicks",target.isBlocking()?Math.min(20,s.getInt("ShieldTicks")+1):0);
        String move=s.getString("Move");
        if(!move.equals("water")&&ready(s,"Water")&&
            (m.isOnFire()||m.fallDistance>=4&&m.getDeltaMovement().y<-.35&&landing(m,Math.min(5,1-m.getDeltaMovement().y*4))!=null)){
            begin(m,s,"water",16,Items.WATER_BUCKET);s.remove("Wet");
            BlockPos dest=m.isOnFire()?m.blockPosition():landing(m,6);
            if(dest!=null)s.putLong("WaterPos",dest.asLong());
            waitFor(s,"Water",35);return;
        }
        if(!move.isEmpty()){
            int age=s.getInt("Age")+1;s.putInt("Age",age);
            act(level,m,target,s,move,age);
            if(s.getString("Move").equals(move)&&age>=s.getInt("End")){
                s.remove("Move");m.stopUsingItem();m.setSprinting(false);hand(m,Items.IRON_SWORD);
            }
            return;
        }
        select(level,m,target,s,now);
    }
    private static void select(ServerLevel level,EchoDouble m,ServerPlayer p,CompoundTag s,int now){
        double range=m.distanceTo(p);boolean sight=m.hasLineOfSight(p);
        boolean pressure=s.getInt("Pressure")>=2&&now-s.getInt("HitAt")<=40;
        if(m.getHealth()<m.getMaxHealth()*.4f&&s.getInt("Apples")>0&&ready(s,"Apple")){
            waitFor(s,"Apple",120);startRetreat(m,p,s,"apple");return;
        }
        if(s.getInt("ShieldTicks")>=10&&range<4&&sight&&ready(s,"Axe")){
            begin(m,s,"axe",26,Items.IRON_AXE);point(s,"Aim",p.getEyePosition());waitFor(s,"Axe",65);
            m.playSound(SoundEvents.ARMOR_EQUIP_IRON,.8f,.75f);return;
        }
        if(pressure&&range<4&&ready(s,"Trap")&&(s.getBoolean("Lava")||s.getInt("Webs")>0)){
            var trap=p.blockPosition();
            if(sight && !p.isInWater() && s.getBoolean("Lava") && s.getInt("Webs")>0
                    && level.isEmptyBlock(trap) && level.isEmptyBlock(trap.above())){
                begin(m,s,"web_lava",29,Items.COBWEB);
                s.putLong("Snare",trap.asLong());s.putBoolean("Snared",false);
                waitFor(s,"Trap",140);s.putInt("Pressure",0);return;
            }
            boolean useWeb=s.getInt("Webs")>0&&(m.isInWater()||p.isInWater()||!s.getBoolean("Lava")||m.getRandom().nextBoolean());
            begin(m,s,useWeb?"web":"lava",16,useWeb?Items.COBWEB:Items.LAVA_BUCKET);
            Vec3 away=flat(m.position().subtract(p.position()));
            point(s,"Trap",m.position().subtract(away.scale(.8)));point(s,"Away",away);
            waitFor(s,"Trap",140);s.putInt("Pressure",0);return;
        }
        boolean corner=m.position().distanceToSqr(Vec3.atCenterOf(m.center()))>15*15&&pressure;
        if(sight && ready(s,"Pearl") && s.getInt("PearlsUsed")<2 && m.onGround()
                && range<20 && (range>=12 || p.getY()-m.getY()>2.4 && range>=5)){
            var toward=flat(p.position().subtract(m.position()));
            var landing=p.position().subtract(toward.scale(2.6));
            var probe=level.clip(new ClipContext(landing.add(0,1,0),landing.add(0,-4,0),
                    ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,m));
            if(probe.getType()==HitResult.Type.BLOCK && probe.getDirection()==Direction.UP){
                begin(m,s,"pearl",21,Items.ENDER_PEARL);
                point(s,"Aim",Vec3.atBottomCenterOf(probe.getBlockPos().above()));
                waitFor(s,"Pearl",180);return;
            }
        }
        if(s.getInt("Stone")>=4&&ready(s,"Pillar")&&m.onGround()&&!m.isInWater()
                &&(p.getY()-m.getY()>2.4||corner||(!sight&&now-s.getInt("SeenAt")>38&&range<15))){
            begin(m,s,"pillar",65,Items.COBBLESTONE);var foot=m.blockPosition();
            s.putLong("Foot",foot.asLong());s.putInt("Built",0);s.putInt("Goal",s.getInt("Stone")>=5&&m.getRandom().nextBoolean()?5:4);
            waitFor(s,"Pillar",240);return;
        }
        if(sight)s.putInt("SeenAt",now);
        if((m.getY()>p.getY()+2.3||range>11)&&range<22&&sight&&s.getInt("Arrows")>0&&ready(s,"Bow")){
            begin(m,s,"bow",24,Items.BOW);m.startUsingItem(InteractionHand.MAIN_HAND);waitFor(s,"Bow",50);return;
        }
        if(range>=5&&range<=11&&sight&&ready(s,"Rod")){
            begin(m,s,"rod",26,Items.FISHING_ROD);point(s,"Aim",p.getEyePosition().add(p.getDeltaMovement().scale(2)).add(0,-.48,0));
            waitFor(s,"Rod",65);return;
        }
        if(range<3.15&&sight&&ready(s,"Sword")){
            if(p.isUsingItem()&&p.getUseItem().getItem() instanceof ProjectileWeaponItem&&ready(s,"Block")){
                begin(m,s,"block",19,Items.IRON_SWORD);m.startUsingItem(InteractionHand.OFF_HAND);waitFor(s,"Block",65);return;
            }
            if(m.onGround()&&!m.isInWater()&&(s.getBoolean("JumpNext")||s.getInt("Combos")%3==1)){
                leap(m,p,s);return;
            }
            begin(m,s,"sword",15,Items.IRON_SWORD);point(s,"Aim",p.getEyePosition());
            s.putInt("Combos",s.getInt("Combos")+1);waitFor(s,"Sword",17);return;
        }
        if((pressure||p.isSprinting()&&range<3.6)&&ready(s,"Guard")){
            if(m.getRandom().nextBoolean()){
                begin(m,s,"block",16,Items.IRON_SWORD);m.startUsingItem(InteractionHand.OFF_HAND);
            }else startRetreat(m,p,s,"turn");
            waitFor(s,"Guard",55);return;
        }
        if(range>=3.1&&range<14&&sight&&p.isUsingItem()&&p.getUseItem().getItem() instanceof ProjectileWeaponItem&&ready(s,"Block")){
            begin(m,s,"block",22,Items.IRON_SWORD);m.startUsingItem(InteractionHand.OFF_HAND);waitFor(s,"Block",65);return;
        }
        if(now%5==0||m.getNavigation().isDone()){
            hand(m,Items.IRON_SWORD);m.setSprinting(range>3.8);m.getNavigation().moveTo(p,range>3.8?1.28:1.02);
        }
        m.getLookControl().setLookAt(p,35,28);
    }
    private static void act(ServerLevel level,EchoDouble m,ServerPlayer p,CompoundTag s,String move,int age){
        switch(move){
            case "sword"->{
                face(m,point(s,"Aim"));if(age==5)hit(m,p,6,3.15,.35f);
            }
            case "axe"->{
                if(age<=5)point(s,"Aim",p.getEyePosition());
                face(m,point(s,"Aim"));
                if(age<=7&&m.distanceTo(p)>2.4)m.getNavigation().moveTo(p,1.2);else m.getNavigation().stop();
                if(age==14){
                    m.swing(InteractionHand.MAIN_HAND);
                    if(inArc(m,p,3.35,.6)&&p.isBlocking()&&shieldFaces(p,m)){
                        var shield=p.getUseItem().getItem();p.stopUsingItem();p.getCooldowns().addCooldown(shield,100);
                        level.broadcastEntityEvent(p,(byte)30);
                    }else hit(m,p,9,3.35,.6f);
                }
            }
            case "rod"->{face(m,point(s,"Aim"));if(age==8){m.swing(InteractionHand.MAIN_HAND);MirrorHook.cast(m,point(s,"Aim"));}}
            case "leap"->{
                if(age<=4){point(s,"Aim",p.getEyePosition());face(m,point(s,"Aim"));m.setSprinting(true);}
                if(age==4&&m.onGround()){
                    var v=flat(point(s,"Aim").subtract(m.position()));
                    m.getJumpControl().jump();m.push(v.x*.32,0,v.z*.32);
                }
                if(age>4&&!s.getBoolean("Swung")&&!m.onGround()&&m.getDeltaMovement().y<-.04){
                    m.setSprinting(false);face(m,point(s,"Aim"));s.putBoolean("Swung",true);
                    if(hit(m,p,9,3.3,.25f)){
                        level.sendParticles(ParticleTypes.CRIT,p.getX(),p.getY()+1,p.getZ(),9,.25,.28,.25,.12);
                        m.playSound(SoundEvents.PLAYER_ATTACK_CRIT,.85f,1f);
                    }
                }
            }
            case "chase"->{face(m,p.getEyePosition());m.setSprinting(true);if(age%3==1)m.getNavigation().moveTo(p,1.48);
                if(m.distanceTo(p)<3.05&&m.onGround())leap(m,p,s);}
            case "block"->{face(m,p.getEyePosition());if(age==1)m.startUsingItem(InteractionHand.OFF_HAND);}
            case "web_lava"->{
                var feet=BlockPos.of(s.getLong("Snare"));
                var terrain=MirrorTerrain.get(level);
                face(m,Vec3.atCenterOf(age<10?feet:feet.above()));
                if(age==7 && p.getBoundingBox().intersects(new AABB(feet)) && m.distanceTo(p)<4 && m.hasLineOfSight(p)
                        && terrain.place(level,m.runId(),feet,"web",feet.asLong())){
                    s.putBoolean("Snared",true);s.putInt("Webs",s.getInt("Webs")-1);
                    m.swing(InteractionHand.MAIN_HAND);m.playSound(SoundEvents.WOOL_PLACE,.8f,1f);
                }
                if(age==10 && s.getBoolean("Snared")){
                    hand(m,Items.LAVA_BUCKET);m.action("lava_overhead",19);
                }
                if(age==19 && s.getBoolean("Snared") && level.getBlockState(feet).is(Blocks.COBWEB)
                        && p.getBoundingBox().intersects(new AABB(feet)) && m.distanceTo(p)<4 && m.hasLineOfSight(p)
                        && terrain.place(level,m.runId(),feet.above(),"lava",feet.asLong())){
                    s.putBoolean("Lava",false);
                    m.swing(InteractionHand.MAIN_HAND);m.playSound(SoundEvents.BUCKET_EMPTY_LAVA,.8f,1f);
                }
                if(age==22)startRetreat(m,p,s,"turn");
            }
            case "web","lava"->{
                face(m,point(s,"Trap"));if(age==2)m.push(point(s,"Away").x*.22,.04,point(s,"Away").z*.22);
                if(age==7){
                    var pos=BlockPos.containing(point(s,"Trap"));
                    if(MirrorTerrain.get(level).place(level,m.runId(),pos,move,pos.asLong())){
                        if(move.equals("web"))s.putInt("Webs",s.getInt("Webs")-1);else s.putBoolean("Lava",false);
                        m.swing(InteractionHand.MAIN_HAND);m.playSound(move.equals("web")?SoundEvents.WOOL_PLACE:SoundEvents.BUCKET_EMPTY_LAVA,.8f,1f);
                    }
                }
                if(age==12)startRetreat(m,p,s,"turn");
            }
            case "flee"->{
                Vec3 destination=point(s,"Route");m.setSprinting(true);
                if(age%6==1)m.getNavigation().moveTo(destination.x,destination.y,destination.z,1.46);
                if(age==1&&s.getString("After").equals("apple")&&m.hasLineOfSight(p))cover(level,m,p,s);
                if(age>=8&&(age>=s.getInt("End")||m.position().distanceToSqr(destination)<1.8||s.getString("After").equals("apple")&&!m.hasLineOfSight(p))){
                    if(s.getString("After").equals("apple")){
                        begin(m,s,"apple",32,Items.GOLDEN_APPLE);s.putBoolean("Interrupted",false);
                    }else {face(m,p.getEyePosition());recover(m,s,4);}
                }
            }
            case "apple"->{
                if(s.getBoolean("Interrupted")){waitFor(s,"Apple",120);recover(m,s,7);return;}
                if(age%5==1)m.playSound(SoundEvents.GENERIC_EAT,.85f,.9f+m.getRandom().nextFloat()*.2f);
                if(age==32){
                    new ItemStack(Items.GOLDEN_APPLE).finishUsingItem(level,m);
                    s.putInt("Apples",s.getInt("Apples")-1);m.playSound(SoundEvents.PLAYER_BURP,.65f,1.1f);
                    waitFor(s,"Apple",120);
                }
            }
            case "pillar"->pillar(level,m,p,s,age);
            case "bow"->{
                if(age<=15)point(s,"Aim",p.getEyePosition().add(p.getDeltaMovement().scale(3)));
                face(m,point(s,"Aim"));
                if(age==19&&m.hasLineOfSight(p)){
                    var a=new Arrow(level,m);var dir=point(s,"Aim").subtract(a.position());
                    a.shoot(dir.x,dir.y+dir.horizontalDistance()*.11,dir.z,1.6f,1.6f);
                    a.pickup=AbstractArrow.Pickup.DISALLOWED;a.setBaseDamage(2);a.getPersistentData().putUUID("EchoArrow",m.runId());
                    level.addFreshEntity(a);s.putInt("Arrows",s.getInt("Arrows")-1);m.playSound(SoundEvents.ARROW_SHOOT,.9f,1f);
                }
            }
            case "pearl"->{
                var aim=point(s,"Aim");
                face(m,aim.add(0,1,0));
                if(age==8){
                    var pearl=new ThrownEnderpearl(level,m);
                    var delta=aim.subtract(pearl.position());
                    pearl.shoot(delta.x,delta.y+.008*delta.horizontalDistanceSqr(),delta.z,1.5f,0);
                    pearl.getPersistentData().putBoolean("EchoPearl",true);
                    if(level.addFreshEntity(pearl)){
                        s.putInt("PearlsUsed",s.getInt("PearlsUsed")+1);
                        m.swing(InteractionHand.MAIN_HAND);
                        m.playSound(SoundEvents.ENDER_PEARL_THROW,.85f,1f);
                    }
                }
            }
            case "water"->{
                var at=BlockPos.of(s.getLong("WaterPos"));face(m,Vec3.atCenterOf(at));
                var terrain=MirrorTerrain.get(level);
                if(age<=6&&!s.getBoolean("Wet")&&m.position().distanceToSqr(Vec3.atBottomCenterOf(at))<=6.25
                        &&(terrain.cool(level,m.runId(),at)||terrain.place(level,m.runId(),at,"water",at.asLong()))){
                    s.putBoolean("Wet",true);hand(m,Items.BUCKET);m.swing(InteractionHand.MAIN_HAND);
                    m.playSound(SoundEvents.BUCKET_EMPTY,1f,1f);
                    if(m.getBoundingBox().intersects(new AABB(at))){m.clearFire();m.fallDistance=0;}
                }
                if(age==15&&s.getBoolean("Wet")&&m.position().distanceToSqr(Vec3.atCenterOf(at))<12&&MirrorTerrain.get(level).reclaimWater(level,m.runId(),at))
                    m.playSound(SoundEvents.BUCKET_FILL,.85f,1.05f);
            }
            default->{}
        }
    }
    private static void leap(EchoDouble m,ServerPlayer p,CompoundTag s){
        begin(m,s,"leap",23,Items.IRON_SWORD);s.putBoolean("JumpNext",false);s.putBoolean("Swung",false);
        s.putInt("Combos",s.getInt("Combos")+1);point(s,"Aim",p.getEyePosition());waitFor(s,"Sword",23);
    }
    private static void pillar(ServerLevel level,EchoDouble m,ServerPlayer p,CompoundTag s,int age){
        int built=s.getInt("Built");var base=BlockPos.of(s.getLong("Foot"));var at=base.above(built);
        if(built>=s.getInt("Goal")){
            if(m.onGround()){if(s.getInt("Arrows")>0){begin(m,s,"bow",24,Items.BOW);m.startUsingItem(InteractionHand.MAIN_HAND);}
                else recover(m,s,6);}return;
        }
        if(Math.abs(m.getX()-base.getX()-.5)>.86||Math.abs(m.getZ()-base.getZ()-.5)>.86){recover(m,s,9);return;}
        face(m,Vec3.atCenterOf(at).subtract(0,.5,0));
        if(!level.getBlockState(at).isAir()||!level.getBlockState(at.above(3)).isAir()){recover(m,s,8);return;}
        if(m.onGround()&&age>=4)m.getJumpControl().jump();
        if(m.getY()>=at.getY()+1&&m.getY()<at.getY()+1.55&&m.getDeltaMovement().y>0
                &&MirrorTerrain.get(level).place(level,m.runId(),at,built==0?"foot":"stone",base.asLong())){
            s.putInt("Built",built+1);s.putInt("Stone",s.getInt("Stone")-1);
            m.swing(InteractionHand.MAIN_HAND);m.playSound(SoundEvents.STONE_PLACE,.65f,1.15f);
        }
    }
    private static BlockPos landing(EchoDouble m,double drop){
        Vec3 end=m.position().add(m.getDeltaMovement().x*2,-drop,m.getDeltaMovement().z*2);
        var hit=m.level().clip(new ClipContext(m.position(),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,m));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getDirection()==Direction.UP?hit.getBlockPos().above():null;
    }
    private static Vec3 flat(Vec3 v){return new Vec3(v.x,0,v.z).normalize();}
    private static boolean inArc(EchoDouble m,ServerPlayer p,double reach,double dot){
        var v=flat(p.position().subtract(m.position()));
        return m.distanceTo(p)<=reach&&m.hasLineOfSight(p)&&flat(m.getLookAngle()).dot(v)>=dot;
    }
    static boolean shieldFaces(ServerPlayer p,EchoDouble m){return flat(p.getLookAngle()).dot(flat(m.position().subtract(p.position())))>.05;}
    private static boolean hit(EchoDouble m,ServerPlayer p,float damage,double reach,float arc){
        m.swing(InteractionHand.MAIN_HAND);
        return inArc(m,p,reach,arc)&&p.hurt(m.damageSources().mobAttack(m),damage);
    }
    static void hooked(EchoDouble m,ServerPlayer p){
        if(!"rod".equals(state(m).getString("Move")))return;
        if(p.isBlocking()&&shieldFaces(p,m)){
            p.playSound(SoundEvents.SHIELD_BLOCK,.85f,1.2f);recover(m,state(m),9);return;
        }
        var data=p.getPersistentData();long now=m.level().getGameTime();
        if(data.hasUUID("EchoHookRun")&&data.getUUID("EchoHookRun").equals(m.runId())&&data.getLong("EchoHookUntil")>now)return;
        data.putUUID("EchoHookRun",m.runId());data.putLong("EchoHookUntil",now+30);
        p.knockback(.34,m.getX()-p.getX(),m.getZ()-p.getZ());p.setSprinting(false);p.hurtMarked=true;
        m.playSound(SoundEvents.FISHING_BOBBER_RETRIEVE,.85f,.93f);
        var s=state(m);begin(m,s,"chase",24,Items.IRON_SWORD);s.putUUID("Opponent",p.getUUID());s.putBoolean("JumpNext",true);
    }
    private static void startRetreat(EchoDouble m,ServerPlayer p,CompoundTag s,String after){
        Vec3 away=flat(m.position().subtract(p.position()));if(away.lengthSqr()<.2)away=flat(m.getLookAngle()).scale(-1);
        Vec3 best=m.position();double bestScore=-Double.MAX_VALUE;
        for(double degrees:new double[]{0,38,-38,76,-76,115,-115}){
            var dir=away.yRot((float)Math.toRadians(degrees));var end=m.position().add(dir.scale(6));
            if(Math.abs(end.x-m.center().getX()-.5)>20||Math.abs(end.z-m.center().getZ()-.5)>20)continue;
            var probe=m.level().clip(new ClipContext(end.add(0,2,0),end.add(0,-4,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,m));
            if(probe.getType()!=HitResult.Type.BLOCK||probe.getDirection()!=Direction.UP)continue;
            var at=probe.getBlockPos().above();
            if(!m.level().getBlockState(at).isAir()||!m.level().getBlockState(at.above()).isAir())continue;
            var path=m.getNavigation().createPath(at,0);if(path==null||!path.canReach())continue;
            var v=Vec3.atBottomCenterOf(at);boolean covered=m.level().clip(new ClipContext(p.getEyePosition(),v.add(0,1.45,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,m)).getType()==HitResult.Type.BLOCK;
            double score=v.distanceTo(p.position())+(covered?7:0);
            if(score>bestScore){best=v;bestScore=score;}
        }
        begin(m,s,"flee",26,Items.IRON_SWORD);point(s,"Route",best);s.putString("After",after);
    }
    private static void cover(ServerLevel l,EchoDouble m,ServerPlayer p,CompoundTag s){
        if(s.getInt("Stone")<2)return;
        var pos=BlockPos.containing(m.position().add(flat(p.position().subtract(m.position())).scale(1.5)));
        if(!l.getBlockState(pos.below()).isSolid())return;
        hand(m,Items.COBBLESTONE);
        for(int h=0;h<2;h++)if(MirrorTerrain.get(l).place(l,m.runId(),pos.above(h),h==0?"foot":"stone",pos.asLong())){
            m.swing(InteractionHand.MAIN_HAND);m.playSound(SoundEvents.STONE_PLACE,.6f,1.1f);
            s.putInt("Stone",s.getInt("Stone")-1);
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void damaged(LivingDamageEvent e){
        if(!(e.getEntity() instanceof EchoDouble m)||!m.melee()||e.getAmount()<=0)return;
        var s=state(m);s.putBoolean("Interrupted",true);
        if(e.getSource().getEntity() instanceof ServerPlayer p&&m.distanceToSqr(p)<16){
            int now=s.getInt("Clock");s.putInt("Pressure",now-s.getInt("HitAt")<=40?s.getInt("Pressure")+1:1);s.putInt("HitAt",now);
        }
    }
    @SubscribeEvent public static void echoPearl(ProjectileImpactEvent event){
        if(!(event.getProjectile() instanceof ThrownEnderpearl pearl)
                || !pearl.getPersistentData().getBoolean("EchoPearl")
                || !(pearl.level() instanceof ServerLevel level)) return;
        if(event.getRayTraceResult() instanceof EntityHitResult){
            event.setImpactResult(ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
            return;
        }
        event.setImpactResult(ProjectileImpactEvent.ImpactResult.STOP_AT_CURRENT_NO_DAMAGE);
        if(pearl.getOwner() instanceof EchoDouble mirror && mirror.isAlive() && mirror.melee()
                && event.getRayTraceResult() instanceof BlockHitResult hit && hit.getDirection()==Direction.UP){
            var at=hit.getBlockPos().above();
            var center=mirror.center();
            var landing=Vec3.atBottomCenterOf(at);
            if(Math.abs(at.getX()-center.getX())<=20 && Math.abs(at.getZ()-center.getZ())<=20
                    && at.getY()>center.getY() && at.getY()<=center.getY()+18 && level.hasChunkAt(at)
                    && level.getBlockState(at.below()).isFaceSturdy(level,at.below(),Direction.UP)
                    && level.noCollision(mirror,mirror.getBoundingBox().move(landing.subtract(mirror.position())))){
                mirror.playSound(SoundEvents.ENDERMAN_TELEPORT,.8f,1.2f);
                mirror.teleportTo(landing.x,landing.y,landing.z);
                mirror.fallDistance=0;
                mirror.playSound(SoundEvents.ENDERMAN_TELEPORT,.8f,1.2f);
            }
        }
        pearl.discard();
    }
    @SubscribeEvent public static void echoArrow(net.minecraftforge.event.entity.ProjectileImpactEvent e){
        if(!(e.getProjectile() instanceof Arrow arrow)||!arrow.getPersistentData().hasUUID("EchoArrow"))return;
        if(e.getRayTraceResult() instanceof EntityHitResult hit&&(!(hit.getEntity() instanceof ServerPlayer p)
                ||!MirrorFight.silent(p)||!arrow.getPersistentData().getUUID("EchoArrow").equals(MirrorFight.activeRun(p))
                ||com.tnc.tnc.combat.DownedCombat.isDowned(p)||com.tnc.tnc.dungeon.DungeonService.recovering(p))){
            e.setImpactResult(net.minecraftforge.event.entity.ProjectileImpactEvent.ImpactResult.SKIP_ENTITY);
        }
    }
    private MirrorMelee(){}
}
