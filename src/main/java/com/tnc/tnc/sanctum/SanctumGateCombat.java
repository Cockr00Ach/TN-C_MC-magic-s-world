package com.tnc.tnc.sanctum;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

final class SanctumGateCombat {
    private enum Move { LIGHT, SEEK, TRACK, STAMP, OPEN, BREATH }
    private final SanctumRoute route;
    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("双阙门神 · 两枚腕闩"),
            BossEvent.BossBarColor.YELLOW,BossEvent.BossBarOverlay.NOTCHED_10);
    private final Map<BlockPos,Block> palmTiles=new HashMap<>();
    private SanctumGateSentinel left,right,attacker;
    private Mob wrist;
    private Move move=Move.LIGHT;
    private int frames,clock,locks;
    private UUID target;
    private Vec3 impact;
    SanctumGateCombat(SanctumRoute route){this.route=route;}
    private Vec3 anchor(){return route.foot(attacker.side*8,SanctumArchitecture.ANCHOR_Z);}
    void reset(){
        clearPalm();
        if(left!=null){anchorLights(0);route.discard(left.body);route.discard(right.body);}
        route.discard(wrist);left=right=attacker=null;wrist=null;
        bar.removeAllPlayers();locks=clock=frames=0;target=null;move=Move.LIGHT;
    }
    private void stance(Move next,int ticks){move=next;frames=ticks;}
    private void anchorLights(int side){
        for(int sign:new int[]{-1,1}){
            Block block=sign==side?Blocks.OCHRE_FROGLIGHT:Blocks.CHISELED_POLISHED_BLACKSTONE;
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx==0||dz==0)
                route.level().setBlock(route.at(sign*8+dx,-1,SanctumArchitecture.ANCHOR_Z+dz),block.defaultBlockState(),2);
        }
    }
    private void clearPalm(){
        palmTiles.forEach((p,b)->route.level().setBlock(p,b.defaultBlockState(),2));palmTiles.clear();
    }
    private void palm(Vec3 pos,boolean locked){
        clearPalm();BlockPos center=BlockPos.containing(pos).below();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(dx*dx+dz*dz==5){
            var p=center.offset(dx,0,dz);palmTiles.put(p,route.level().getBlockState(p).getBlock());
            route.level().setBlock(p,(locked?Blocks.OCHRE_FROGLIGHT:Blocks.REDSTONE_BLOCK).defaultBlockState(),2);
        }
    }
    private void next(){
        route.discard(wrist);wrist=null;
        if(!left.unlocked && left.alive())attacker=left;
        else if(!right.unlocked && right.alive())attacker=right;
        else attacker=attacker==left&&right.alive()||!left.alive()?right:left;
        anchorLights(attacker.side);attacker.cue("gate_lamp",36);stance(Move.LIGHT,36);
    }
    boolean tick(List<ServerPlayer> players){
        Vec3 home=route.foot(0,110);
        var present=players.stream().filter(p->Math.abs(p.getX()-home.x)<38 && p.getZ()>home.z-35 && p.getZ()<home.z+19).toList();
        if(present.isEmpty()){bar.removeAllPlayers();return false;}
        if(left==null){
            double hp=68*(1+.6*(players.size()-1));left=new SanctumGateSentinel(route,-1,hp);right=new SanctumGateSentinel(route,1,hp);
            bar.setName(Component.literal("双阙门神 · 两枚腕闩"));
            route.tell("左右门神同时醒来。提灯的小手指向左锚，重臂正在蓄力。每尊各有一枚腕闩。");next();
        }
        if(left.body.isRemoved()&&!left.body.isDeadOrDying() || right.body.isRemoved()&&!right.body.isDeadOrDying()){
            reset();return false;
        }
        if(!left.alive()&&!right.alive()){reset();return true;}
        if(!attacker.alive())next();
        var others=attacker==left?right:left;
        var online=new HashSet<>(present);
        for(var p:List.copyOf(bar.getPlayers()))if(!online.contains(p))bar.removePlayer(p);
        present.forEach(bar::addPlayer);
        bar.setProgress(Math.max(0,Math.min(1,(left.body.getHealth()+right.body.getHealth())/(left.body.getMaxHealth()+right.body.getMaxHealth()))));
        clock++;others.defend(present,true);
        if(clock%14==0 && move!=Move.BREATH&&move!=Move.OPEN)route.ring(anchor(),2,false);
        switch(move){
            case LIGHT -> {
                attacker.walk(route.foot(attacker.side*12,107),.12);attacker.face(anchor());
                if(--frames<=0)stance(Move.SEEK,0);
            }
            case SEEK -> {
                var inRange=present.stream().filter(p->flat(p.position(),anchor())<=36)
                        .min(Comparator.comparingDouble(p->flat(p.position(),anchor())));
                if(inRange.isEmpty())attacker.defend(present,false);
                else{
                    target=inRange.get().getUUID();impact=inRange.get().position();
                    route.hint("押印重掌 · 踩亮锚引掌，金纹定住后立即横移");
                    attacker.lockSide(anchor());stance(Move.TRACK,50);attacker.cue("gate_stamp"+attacker.hand,78);
                }
            }
            case TRACK -> {
                var marked=present.stream().filter(s->s.getUUID().equals(target)).findFirst();
                if(marked.isEmpty()){clearPalm();next();break;}
                impact=attacker.trackPalm(new Vec3(marked.get().getX(),anchor().y,marked.get().getZ()));
                if(frames%7==0)palm(impact,false);
                if(--frames==0){stance(Move.STAMP,28);palm(impact,true);route.hint("掌印锁定 · 横移离开掌心！");route.sound(attacker.body.blockPosition(),SoundEvents.IRON_GOLEM_REPAIR,.5f);}
            }
            case STAMP -> {
                if(--frames<=0){
                    clearPalm();route.sound(BlockPos.containing(impact),SoundEvents.ANVIL_LAND,.58f);
                    for(var p:present)if(flat(p.position(),impact)<2.65*2.65 && Math.abs(p.getY()-impact.y)<2.4)
                        p.hurt(route.level().damageSources().mobAttack(attacker.body),7);
                    if(flat(impact,anchor())<=2.5*2.5){
                        wrist=route.spawn(EntityType.IRON_GOLEM,attacker.hand.equals("_R")?"wrist_right":"wrist","落地腕锁",impact,40);wrist.setGlowingTag(true);
                        attacker.cue("gate_pinned"+attacker.hand,120);stance(Move.OPEN,120);
                        route.hint("重腕被卡住了 · 击中地面腕锁即可断闩");
                    }else{
                        route.hint("掌印落在金砖之外，地锚重新亮起");attacker.cue("gate_recover"+attacker.hand,32);stance(Move.BREATH,32);
                    }
                }
            }
            case OPEN -> {
                if(frames%12==0)route.ring(wrist.position(),1.15,false);
                if(--frames<=0){
                    route.discard(wrist);wrist=null;attacker.cue("gate_recover"+attacker.hand,40);stance(Move.BREATH,40);
                }
            }
            case BREATH -> {
                attacker.walk(route.foot(attacker.side*13,108),.13);
                if(--frames<=0)next();
            }
        }
        return false;
    }
    private static double flat(Vec3 a,Vec3 b){double x=a.x-b.x,z=a.z-b.z;return x*x+z*z;}
    boolean hurt(LivingHurtEvent event){
        if(left==null)return false;
        if(event.getEntity()==wrist){
            if(!route.partySource(event.getSource())||move!=Move.OPEN||!attacker.alive()){
                event.setCanceled(true);return true;
            }
            if(!attacker.unlocked){
                attacker.unlocked=true;locks++;
                route.tell(locks==1?"左庭腕闩断了。留心另一尊门神的动作。":"第二枚腕闩崩断。两尊门神的石甲都已剥落！");
                route.sound(wrist.blockPosition(),SoundEvents.ANVIL_BREAK,.72f);
                bar.setName(Component.literal("双阙门神 · "+(locks==1?"余一闩":"石甲已碎")));
            }
            var linked=attacker.body;
            linked.setHealth(Math.max(0,linked.getHealth()-Math.max(1,event.getAmount()*1.5f)));
            if(linked.getHealth()<=0){attacker.cue("gate_fall",20);linked.die(event.getSource());}
            wrist.setHealth(wrist.getMaxHealth());event.setAmount(Math.min(1,Math.max(.01f,event.getAmount())));
            return true;
        }
        var sentinel=event.getEntity()==left.body?left:event.getEntity()==right.body?right:null;
        if(sentinel==null)return false;
        if(!sentinel.unlocked){
            float allow=Math.max(0,sentinel.body.getHealth()-.2f*sentinel.body.getMaxHealth());
            if(allow<=0)event.setCanceled(true);
            else event.setAmount(Math.min(allow,event.getAmount()*(sentinel.braced()?.48f:.82f)));
        }
        return true;
    }
}

