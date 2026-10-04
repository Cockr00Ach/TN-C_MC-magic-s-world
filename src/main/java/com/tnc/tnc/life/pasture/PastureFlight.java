package com.tnc.tnc.life.pasture;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import java.util.Set;

/** Air destinations, bounded acceleration and obstacle retries, rather than ground wandering. */
public final class PastureFlight {
    private static final Set<String> FLYERS=Set.of("post_heron","mirrorwing_moth","papersail_ray","dewbound_whale");
    private PastureFlight(){}
    public static boolean flying(String id){return FLYERS.contains(id);}
    public static float scale(String id){return switch(id){
        case "dewbound_whale"->1.8F;case "bowlhorn_rhino"->1.4F;case "satchelback_runner"->1.25F;
        case "forgegill_tapir"->1.2F;case "papersail_ray"->1.15F;case "stonebarrow_boar"->1.15F;default->1F;
    };}
    public static final class Control extends MoveControl{
        private final PastureAnimal animal;
        public Control(PastureAnimal animal){super(animal);this.animal=animal;}
        @Override public void tick(){
            animal.setNoGravity(true);
            animal.setSpeed(0);animal.xxa=0;animal.yya=0;animal.zza=0;
            if(operation!=Operation.MOVE_TO){animal.setDeltaMovement(animal.getDeltaMovement().scale(.86));return;}
            Vec3 delta=new Vec3(wantedX-animal.getX(),wantedY-animal.getY(),wantedZ-animal.getZ());
            double length=delta.length();
            if(length<.35){operation=Operation.WAIT;animal.setDeltaMovement(animal.getDeltaMovement().scale(.7));return;}
            double top=switch(animal.speciesId()){case "dewbound_whale"->.13;case "mirrorwing_moth"->.20;case "post_heron"->.25;default->.18;};
            Vec3 desired=delta.scale(Math.min(top*Math.max(.25,speedModifier),length*.12)/length);
            if(animal.horizontalCollision){desired=desired.scale(-.3).add(0,.16,0);animal.getNavigation().stop();operation=Operation.WAIT;}
            if(animal.verticalCollision)desired=new Vec3(desired.x,animal.verticalCollisionBelow?.16:-.10,desired.z);
            animal.setDeltaMovement(animal.getDeltaMovement().lerp(desired,.16));
            float yaw=(float)(Mth.atan2(delta.z,delta.x)*180/Math.PI)-90;
            animal.setYRot(rotlerp(animal.getYRot(),yaw,10));animal.yBodyRot=animal.getYRot();
        }
    }
    public static final class Roam extends Goal{
        private final PastureAnimal animal;private Vec3 target;private int remaining;
        public Roam(PastureAnimal animal){this.animal=animal;setFlags(EnumSet.of(Flag.MOVE));}
        private boolean choose(){
            BlockPos anchor=animal.flightAnchor();var random=animal.getRandom();
            for(int i=0;i<16;i++){
                int x=anchor.getX()+random.nextInt(17)-8,z=anchor.getZ()+random.nextInt(17)-8;
                BlockPos column=new BlockPos(x,anchor.getY(),z);
                if(!animal.level().hasChunkAt(column))continue;
                int ground=animal.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                // Inside buildings use local height instead of flying through the roof.
                int y=animal.level().canSeeSky(animal.blockPosition())?ground+2+random.nextInt(animal.speciesId().equals("dewbound_whale")?5:4):anchor.getY()+random.nextInt(5)-1;
                y=Mth.clamp(y,animal.level().getMinBuildHeight()+1,animal.level().getMaxBuildHeight()-3);
                Vec3 candidate=new Vec3(x+.5,y,z+.5);
                var moved=animal.getBoundingBox().move(candidate.subtract(animal.position()));
                if(!animal.level().noCollision(animal,moved)||!animal.level().getFluidState(BlockPos.containing(candidate)).isEmpty())continue;
                if(animal.level().clip(new ClipContext(animal.getEyePosition(),candidate.add(0,animal.getEyeHeight(),0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,animal)).getType()!=HitResult.Type.MISS)continue;
                target=candidate;remaining=120;return true;
            }
            return false;
        }
        @Override public boolean canUse(){return !animal.flightBusy()&&choose();}
        @Override public boolean canContinueToUse(){return target!=null&&remaining>0&&!animal.flightBusy()&&animal.distanceToSqr(target)>.5&&!animal.horizontalCollision;}
        @Override public void start(){animal.getNavigation().stop();}
        @Override public void tick(){remaining--;animal.getMoveControl().setWantedPosition(target.x,target.y,target.z,1);}
        @Override public void stop(){target=null;animal.getMoveControl().setWantedPosition(animal.getX(),animal.getY(),animal.getZ(),0);}
    }
}
