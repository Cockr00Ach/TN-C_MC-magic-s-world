package com.tnc.tnc.sanctum;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Local coordinates use the same +Z forward convention as the sculpt. Each part is a
 * living, network-tracked target. The actual damage always enters the body's Forge hooks. */
final class FalseGodHitboxes {
    private record Region(String role,double x,double y,double z,double rx,double ry,double rz){}
    private static final Region[] STATUE={
        new Region("base",0,2.5,1,11,2.5,10),
        new Region("skirt",0,11,0,8.5,6,5.9),
        new Region("skirt",0,23,0,6.5,6,4.8),
        new Region("chest",0,36,0,5.9,8,4.1),
        new Region("chest",0,46,0,4.3,3.6,3),
        new Region("head",0,52.8,1.2,3.3,4.3,3.2),
        new Region("hand_l",-12.3,32.2,4.5,2.3,2.1,2.1),
        new Region("hand_r",6.7,30.1,3,2.3,3.2,2.2),
        new Region("forearm_l",-8.5,39,0,3.7,5.2,3),
        new Region("forearm_r",6.8,38,0,3.3,6.1,2.8),
        new Region("wing_l",-9,44,-4,5.8,7.2,3),
        new Region("wing_l",-20,50,-6,5.5,6.5,2.5),
        new Region("wing_l",-27,54,-7,4.1,4.8,2),
        new Region("wing_r",9,44,-4,5.5,7,3),
        new Region("wing_r",19.5,50,-6.5,5.5,6.5,2.7),
        new Region("wing_r",26,54,-8.1,3.6,4.7,2)
    };
    private static final class Group {
        final List<FalseGodPart> parts=new ArrayList<>();
        int used;
    }
    private final FalseGodFight fight;
    private final Map<String,Group> groups=new HashMap<>();
    private Vec3 lastFeet;
    private float lastYaw;
    private int lastPhase;
    FalseGodHitboxes(FalseGodFight fight){this.fight=fight;}
    void clear(){
        for(var group:groups.values())for(var part:group.parts)part.discard();
        groups.clear();lastFeet=null;
    }
    void update(){
        Vec3 feet=fight.feet;
        float yaw=fight.body.getYRot();
        if(fight.phase<3 && feet.equals(lastFeet) && yaw==lastYaw && fight.phase==lastPhase)return;
        lastFeet=feet;lastYaw=yaw;lastPhase=fight.phase;
        for(var group:groups.values())group.used=0;
        double angle=Math.toRadians(yaw),c=Math.cos(angle),s=Math.sin(angle);
        if(fight.phase==3){
            String action=fight.move==null?"idle":fight.skills.contactAction(fight.move);
            float[][] pose=FalseGodContacts.pose(action,fight.age);
            for(int i=0;i<pose.length;i++){
                float[] b=pose[i];
                addLocal(FalseGodContacts.ROLES[i],b[0],b[1],b[2],b[3],b[4],b[5],c,s);
            }
        } else for(Region r:STATUE) {
            addLocal(r.role,r.x,r.y,r.z,r.rx,r.ry,r.rz,c,s);
        }
        for(var group:groups.values())for(int i=group.used;i<group.parts.size();i++)group.parts.get(i).hide();
    }
    private void addLocal(String role,double x,double y,double z,double rx,double ry,double rz,double c,double s) {
        if(rx<=0 || ry<=0 || rz<=0)return;
        Vec3 feet=fight.feet;
        double wx=feet.x+x*c-z*s,wz=feet.z+x*s+z*c,wy=feet.y+y;
        double dx=Math.abs(c)*rx+Math.abs(s)*rz,dz=Math.abs(s)*rx+Math.abs(c)*rz;
        split(role,groups.computeIfAbsent(role,unused->new Group()),new AABB(wx-dx,wy-ry,wz-dz,wx+dx,wy+ry,wz+dz),0);
    }
    // EntitySectionStorage searches a small margin around a ray. A large box whose anchor is
    // in a different section can otherwise be absent from a perfectly valid short attack.
    private void split(String role,Group group,AABB box,int axis) {
        if(axis==3){
            boolean spawn=group.used==group.parts.size();
            var part=spawn?new FalseGodPart(FalseGodPart.TYPE,fight.level):group.parts.get(group.used);
            if(spawn){part.bind(fight,role);group.parts.add(part);}
            part.fit(box);group.used++;
            if(spawn)fight.level.addFreshEntity(part);
            return;
        }
        double lo=axis==0?box.minX:axis==1?box.minY:box.minZ,hi=axis==0?box.maxX:axis==1?box.maxY:box.maxZ;
        double anchor=axis==1?lo:(lo+hi)*.5;
        double start=Math.floor(anchor/16)*16;
        double cut=lo<start-1.85?start:hi>start+16+(axis==1?3.85:1.85)?start+16:Double.NaN;
        if(!(cut>lo+.01 && cut<hi-.01)){split(role,group,box,axis+1);return;}
        AABB lower=axis==0?box.setMaxX(cut):axis==1?box.setMaxY(cut):box.setMaxZ(cut);
        AABB upper=axis==0?box.setMinX(cut):axis==1?box.setMinY(cut):box.setMinZ(cut);
        split(role,group,lower,axis);split(role,group,upper,axis);
    }
    int activeCount(){return groups.values().stream().mapToInt(group->group.used).sum();}
}

