package com.tnc.tnc.magic.free;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;

/** Loaded-world geometry and shared allegiance rules for the elemental actions. */
final class ElementalTargets {
    static boolean friend(ServerPlayer owner,LivingEntity target){return target.isAlive()&&!target.isSpectator()&&target.level()==owner.level()&&(target==owner||owner.isAlliedTo(target)||target.isAlliedTo(owner)||target instanceof net.minecraft.world.entity.TamableAnimal tame&&tame.isOwnedBy(owner));}
    static List<LivingEntity> friends(ServerPlayer owner,Vec3 at,double radius){return owner.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(radius),t->friend(owner,t)&&t.distanceToSqr(at)<radius*radius&&visible(owner,at,t.getBoundingBox().getCenter()));}
    static LivingEntity aimFriend(ServerPlayer owner,double range){Vec3 eye=owner.getEyePosition(),end=trace(owner,eye,eye.add(owner.getLookAngle().scale(range))).getLocation();return friends(owner,eye,range).stream().filter(t->t!=owner&&t.getBoundingBox().inflate(.3).clip(eye,end).isPresent()).min(Comparator.comparingDouble(t->t.distanceToSqr(owner))).orElse(owner);}
    static boolean space(ServerPlayer p,Vec3 feet){var l=p.serverLevel();var box=p.getBoundingBox().move(feet.subtract(p.position()));return l.hasChunkAt(BlockPos.containing(feet))&&!l.isOutsideBuildHeight(BlockPos.containing(feet))&&l.getWorldBorder().isWithinBounds(box)&&l.noCollision(p,box)&&!l.containsAnyLiquid(box);}
    static boolean enemy(ServerPlayer owner,LivingEntity target){
        return target!=owner&&target.isAlive()&&!target.isSpectator()&&target.level()==owner.level()
                &&!owner.isAlliedTo(target)&&!target.isAlliedTo(owner)
                &&(!(target instanceof Player player)||owner.getServer().isPvpAllowed()&&owner.canHarmPlayer(player));
    }
    static BlockHitResult trace(ServerPlayer owner,Vec3 start,Vec3 end){
        var level=owner.serverLevel();var offset=end.subtract(start);int steps=(int)Math.ceil(offset.length()*2);
        for(int step=0;step<=steps;step++){
            Vec3 at=start.add(offset.scale(steps==0?0:(double)step/steps));var pos=BlockPos.containing(at);
            if(!level.hasChunkAt(pos)||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos))
                return new BlockHitResult(at,Direction.UP,pos,false);
        }
        return level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner));
    }
    static boolean visible(ServerPlayer owner,Vec3 from,Vec3 to){return trace(owner,from,to).getType()==HitResult.Type.MISS;}
    static List<LivingEntity> area(ServerPlayer owner,Vec3 at,double radius){
        return owner.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(radius),e->enemy(owner,e))
                .stream().filter(e->e.getBoundingBox().getCenter().distanceToSqr(at)<=radius*radius&&visible(owner,at,e.getBoundingBox().getCenter()))
                .sorted(Comparator.comparingDouble(e->e.distanceToSqr(at))).limit(12).toList();
    }
    static LivingEntity aimed(ServerPlayer owner,double range){
        Vec3 eye=owner.getEyePosition(),end=trace(owner,eye,eye.add(owner.getLookAngle().scale(range))).getLocation();
        return segment(owner,eye,end,.25,Set.of()).stream().findFirst().orElse(null);
    }
    static List<LivingEntity> segment(ServerPlayer owner,Vec3 from,Vec3 to,double radius,Set<Integer> struck){
        var positions=new HashMap<LivingEntity,Vec3>();
        for(var e:owner.serverLevel().getEntitiesOfClass(LivingEntity.class,new AABB(from,to).inflate(radius+2),e->enemy(owner,e)&&!struck.contains(e.getId()))){
            var box=e.getBoundingBox().inflate(radius);Vec3 hit=box.contains(from)?from:box.clip(from,to).orElse(null);
            if(hit!=null&&visible(owner,from,hit)&&visible(owner,hit,e.getBoundingBox().getCenter()))positions.put(e,hit);
        }
        return positions.keySet().stream().sorted(Comparator.comparingDouble(e->positions.get(e).distanceToSqr(from))).limit(12).toList();
    }
    static Optional<Vec3> ground(ServerPlayer owner){
        Vec3 eye=owner.getEyePosition();var hit=trace(owner,eye,eye.add(owner.getLookAngle().scale(24)));
        if(solidTop(owner,hit))return Optional.of(hit.getLocation().add(0,.08,0));
        Vec3 end=hit.getLocation();var down=trace(owner,end.add(0,1,0),end.add(0,-12,0));
        return solidTop(owner,down)
                ?Optional.of(down.getLocation().add(0,.08,0)):Optional.empty();
    }
    static Optional<Vec3> groundNear(ServerPlayer owner,Vec3 desired){var hit=trace(owner,desired.add(0,1,0),desired.add(0,-2,0));return solidTop(owner,hit)?Optional.of(hit.getLocation().add(0,.08,0)):Optional.empty();}
    private static boolean solidTop(ServerPlayer owner,BlockHitResult hit){return hit.getType()!=HitResult.Type.MISS&&hit.getDirection()==Direction.UP&&owner.level().hasChunkAt(hit.getBlockPos())&&!owner.level().getBlockState(hit.getBlockPos()).getCollisionShape(owner.level(),hit.getBlockPos()).isEmpty();}
    static boolean open(ServerPlayer owner,Vec3 at,double radius){return owner.level().hasChunkAt(BlockPos.containing(at))&&owner.level().getWorldBorder().isWithinBounds(BlockPos.containing(at))&&owner.level().noCollision(new AABB(at,at).inflate(radius));}
    static Vec3 origin(ServerPlayer owner,double scale){
        Vec3 eye=owner.getEyePosition();var hit=trace(owner,eye,eye.add(owner.getLookAngle().scale(scale>1?5:2.8)));
        Vec3 end=hit.getLocation();var targets=segment(owner,eye,end,.35*scale,Set.of());
        if(!targets.isEmpty()){var box=targets.get(0).getBoundingBox().inflate(.35*scale);end=box.contains(eye)?eye:box.clip(eye,end).orElse(end);}
        Vec3 offset=end.subtract(eye);return eye.add(offset.normalize().scale(Math.max(0,offset.length()-.15)));
    }
    private ElementalTargets(){}
}
