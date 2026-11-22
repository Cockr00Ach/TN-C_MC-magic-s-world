package com.tnc.tnc.magic.free;

import net.minecraft.core.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.*;
import java.util.*;
import static com.tnc.tnc.magic.free.ElementalIntent.*;

final class EarthMagicEngine {
    static boolean supported(ServerPlayer p){BlockPos base=BlockPos.containing(p.getX(),p.getY()-.05,p.getZ());if(!p.level().hasChunkAt(base))return false;var shape=p.level().getBlockState(base).getCollisionShape(p.level(),base);return !shape.isEmpty()&&Math.abs(p.getY()-base.getY()-shape.max(Direction.Axis.Y))<.15;}
    static boolean construction(Ability a){return a==Ability.ROCK_RISE||a==Ability.ROCK_WALL||a==Ability.ROCK_BRIDGE||a==Ability.STONE_STEPS||a==Ability.STONE_ARCH;}
    static String requirement(ServerPlayer p,Intent intent,Vec3 at){
        if(intent.ability()==Ability.SHATTER_ROCK&&TemporaryRock.owned(p,at,5).isEmpty())return "准星附近没有属于你的临时岩构。";
        if(construction(intent.ability())){var plan=plan(p,intent,at);if(plan.isEmpty())return "缺少落脚支点、头顶空间或合法岩构位置。";if(plan.stream().anyMatch(pos->!TemporaryRock.placeable(p,pos)))return "岩构会覆盖现有方块，请调整位置。";}
        return "";
    }
    static List<BlockPos> plan(ServerPlayer p,Intent intent,Vec3 at){var result=new ArrayList<BlockPos>();var direction=Direction.fromYRot(p.getYRot());var side=direction.getClockWise();var origin=BlockPos.containing(at);
        switch(intent.ability()){
            case ROCK_RISE->{if(!supported(p)||p.level().getBlockState(p.blockPosition().below()).isAir())return List.of();origin=p.blockPosition();for(int y=0;y<(intent.has(GIANT)?12:intent.has(EMPOWER)?10:8);y++){if(!ElementalTargets.space(p,p.position().add(0,y+1,0))||!TemporaryRock.placeable(p,origin.above(y)))break;result.add(origin.above(y));}}
            case ROCK_WALL->{int width=intent.has(GIANT)?9:5;for(int x=-width/2;x<=width/2;x++)for(int y=0;y<(intent.has(EMPOWER)?5:3);y++)result.add(origin.relative(side,x).above(y));}
            case STONE_STEPS->{if(!supported(p))return List.of();for(int z=1;z<=(intent.has(EMPOWER)?7:5);z++)result.add(p.blockPosition().relative(direction,z).above(z-1));}
            case ROCK_BRIDGE->{if(!supported(p))return List.of();BlockPos base=p.blockPosition().below();int end=0;for(int i=3;i<=(intent.has(EMPOWER)?16:12);i++){var target=base.relative(direction,i);if(p.level().hasChunkAt(target)&&p.level().getBlockState(target).isFaceSturdy(p.level(),target,Direction.UP)){end=i;break;}}if(end==0)return List.of();for(int z=1;z<end;z++)for(int x=-1;x<=1;x++)result.add(base.relative(direction,z).relative(side,x));}
            case STONE_ARCH->{if(!supported(p))return List.of();for(int x=-2;x<=2;x++)for(int z=-1;z<=(intent.has(EMPOWER)?3:1);z++)result.add(p.blockPosition().relative(side,x).relative(direction,z).above(3));for(int y=0;y<3;y++)for(int x:new int[]{-2,2})result.add(p.blockPosition().relative(side,x).above(y));}
            default->{}
        }return result;
    }
    static boolean tick(ElementalEffects.Effect e){int age=e.age();double scale=e.intent.scale();
        if(construction(e.ability())){
            if(e.ability()==Ability.ROCK_RISE){if(age%5==1&&e.step<(e.intent.has(GIANT)?12:e.intent.has(EMPOWER)?10:8)){if(Math.abs(e.owner.getX()-e.origin.x)>.75||Math.abs(e.owner.getZ()-e.origin.z)>.75)return true;var pos=BlockPos.containing(e.origin).above(e.step);Vec3 next=e.owner.position().add(0,1,0);if(!ElementalTargets.space(e.owner,next)||!TemporaryRock.placeable(e.owner,pos))return true;
                    var nearby=e.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,new AABB(pos));if(nearby.stream().anyMatch(t->t!=e.owner))return true;
                    e.owner.connection.teleport(next.x,next.y,next.z,e.owner.getYRot(),e.owner.getXRot());if(!TemporaryRock.place(e.owner,pos,e.duration)){e.owner.connection.teleport(next.x,next.y-1,next.z,e.owner.getYRot(),e.owner.getXRot());return true;}e.step++;e.owner.fallDistance=0;}return e.step>=(e.intent.has(GIANT)?12:e.intent.has(EMPOWER)?10:8);}
            if(age==1)for(var pos:plan(e.owner,e.intent,e.origin))if(e.level.getEntities(null,new AABB(pos)).isEmpty())TemporaryRock.place(e.owner,pos,e.duration);return age>10;
        }
        switch(e.ability()){
            case ROCK_BALL,ROCK_BLADE->{return ElementalEffects.projectile(e);}
            case ROCK_CRUSH->{if(age==18&&LightningMagicEngine.valid(e)){e.damage(e.target);ElementalStates.mark(e,e.target,Ability.ROCK_GRASP,25,1);e.show(ElementalNetwork.IMPACT);}return age>30;}
            case ROCK_ARRAY,QUICKSAND->{if(age%10==1)for(var t:e.enemies(4*scale))if(t.onGround()){ElementalStates.mark(e,t,e.ability(),25,e.intent.power()*(e.ability()==Ability.QUICKSAND?Math.min(2,1+age/80.0):1));if(e.ability()==Ability.ROCK_ARRAY&&age%20==1)e.damage(t);}}
            case EARTH_SPIKE,FISSURE->{if(age%6==1){Vec3 desired=e.at.add(e.axis.multiply(1,0,1).normalize().scale(1.2));var next=ElementalTargets.groundNear(e.owner,desired);if(next.isEmpty()||!ElementalTargets.visible(e.owner,e.at.add(0,.5,0),next.get().add(0,.5,0)))return true;e.at=next.get();for(var t:e.enemies(1.8*scale))if(e.hits.add(t.getUUID())){e.damage(t);e.push(t,e.axis,.45,.6);}e.show(ElementalNetwork.IMPACT);}}
            case QUAKE->{double radius=age*.22*scale;for(var t:e.enemies(Math.min(10*scale,radius)))if(t.onGround()&&e.hits.add(t.getUUID())){e.damage(t);e.push(t,t.position().subtract(e.at),.6,.35);}}
            case STONE_ARMOR,ROOTED->{if(age==1)ElementalStates.mark(e,e.owner,e.ability(),e.duration,e.intent.power());return age>10;}
            case ROCK_GRASP->{if(age==1&&e.target!=null){e.damage(e.target);ElementalStates.mark(e,e.target,Ability.ROCK_GRASP,e.duration,e.intent.power());}return age>10;}
            case FALLING_ROCK->{if(age==1){e.at=e.origin.add(0,10,0);e.velocity=new Vec3(0,-.35,0);}if(age>18){e.velocity=e.velocity.add(0,-.05,0);var hit=ElementalTargets.trace(e.owner,e.at,e.at.add(e.velocity));e.at=hit.getLocation();if(hit.getType()!=HitResult.Type.MISS){for(var t:e.enemies(3*scale)){e.damage(t);e.push(t,t.position().subtract(e.at),.7,.4);}e.show(ElementalNetwork.IMPACT);return true;}}}
            case GRAVEL_FLOW->{if(age%(e.intent.has(FAST)?10:20)==1)for(var t:e.enemies(9))if(t.onGround()&&t.position().subtract(e.at).normalize().dot(e.axis)>.65){e.damage(t);e.push(t,e.axis,.3,.1);}}
            case EARTH_SENSE->{if(age%20==1)for(var t:e.enemies(12*scale))if(t.onGround()){t.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,25,0,false,false));e.arc(e.owner.position(),t.position());}}
            case SHATTER_ROCK->{if(age==1){var blocks=TemporaryRock.owned(e.owner,e.at,5);for(var pos:blocks)TemporaryRock.remove(e.level,pos);for(var t:e.enemies(5*scale)){e.damage(t);e.push(t,t.position().subtract(e.at),.8,.5);}e.show(ElementalNetwork.IMPACT);}return age>16;}
            default->throw new IllegalStateException("Unimplemented earth action "+e.ability());
        }return false;
    }
    private EarthMagicEngine(){}
}
