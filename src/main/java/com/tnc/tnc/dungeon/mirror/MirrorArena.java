package com.tnc.tnc.dungeon.mirror;

import com.tnc.tnc.dungeon.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.util.List;

/** Local +Z is the door, irrespective of which leaf the floor generator picked. */
public final class MirrorArena {
    private static final String PAVED="MirrorCourtBuilt";
    private static final String OLD_POOL="EchoPoolBuilt";
    private static final double INLAY_RADIUS=8.5;
    private MirrorArena() {}

    public static boolean site(DungeonData.Run run, DungeonGraph.Room room) {
        return run.theme == DungeonTheme.ASTRAL && run.floor == 30 && room.kind() == DungeonGraph.Kind.GUARDIAN;
    }

    public static boolean built(DungeonData.Run run, DungeonGraph.Room room) {
        return site(run, room) && (run.state().events.getBoolean(PAVED) || run.state().events.getBoolean(OLD_POOL));
    }

    public static Direction entrance(DungeonData.Run run, DungeonGraph.Room room) {
        var next = run.graph().rooms.get(room.links().get(0)).cell();
        return Direction.fromDelta(next.x() - room.cell().x(), 0, next.z() - room.cell().z());
    }

    private static BlockPos local(BlockPos origin, Direction front, int x, int y, int z) {
        return origin.relative(front, z).relative(front.getCounterClockWise(), x).above(y);
    }

    public static BlockPos exit(DungeonData.Run run, DungeonGraph.Room room) {
        return local(DungeonRealm.center(run, room), entrance(run, room), 0, 1, -18);
    }

    static boolean onSeal(ServerPlayer player,ServerLevel level,BlockPos center){
        double x=player.getX()-center.getX()-.5,z=player.getZ()-center.getZ()-.5;
        double height=player.getY()-center.getY()-1;
        var floor=BlockPos.containing(player.getX(),center.getY(),player.getZ());
        return player.level()==level && x*x+z*z<=INLAY_RADIUS*INLAY_RADIUS && height>=-.01 && height<=.22
                && level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP);
    }

    /** Eight silver facets frame the dark glass in the centre; the walkable surface stays level. */
    private static Block inlay(int x,int z){
        int a=Math.abs(x),b=Math.abs(z);
        int radiusSquared=x*x+z*z;
        if(radiusSquared==0) return Blocks.CHISELED_POLISHED_BLACKSTONE;
        if(a+b<=2) return a+b==2?Blocks.AMETHYST_BLOCK:Blocks.BLACK_GLAZED_TERRACOTTA;
        if(a+b==5 || radiusSquared>=55 && radiusSquared<=61) return Blocks.SMOOTH_BASALT;
        if((a==b && a>=3 && a<=4) || ((a==0 || b==0) && a+b>=5 && a+b<=7))
            return a+b==5 || a+b==6 && a==b ? Blocks.SEA_LANTERN : Blocks.CALCITE;
        return (a>b?x:z)<0 ? Blocks.POLISHED_BLACKSTONE : Blocks.POLISHED_BLACKSTONE_BRICKS;
    }

    static boolean ensureFloor(ServerLevel level,DungeonData.Run run,DungeonGraph.Room room,List<ServerPlayer> party){
        var events=run.state().events;
        if(events.getBoolean(PAVED) || !events.getBoolean(OLD_POOL))return true;
        var center=DungeonRealm.center(run,room);
        if(!level.hasChunksAt(center.offset(-8,0,-8),center.offset(8,0,8)))return false;
        var front=entrance(run,room);
        var ledger=MirrorTerrain.get(level);
        DungeonRealm.write(()->{
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
                if(x*x+z*z>INLAY_RADIUS*INLAY_RADIUS)continue;
                var pos=local(center,front,x,0,z);
                var old=level.getBlockState(pos);
                if(old.isAir() || old.is(Blocks.WATER) || ledger.coversOldWater(level,run.id,pos))
                    level.setBlock(pos,inlay(x,z).defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            }
        });
        // Keep the legacy marker so actors saved below the new floor can be lifted when they next load.
        events.putBoolean(PAVED,true);
        var area=new AABB(center).inflate(10,2,10);
        for(var e:level.getEntitiesOfClass(LivingEntity.class,area))liftFromOldFloor(level,run,center,e);
        for(var p:party)liftFromOldFloor(level,run,center,p);
        DungeonData.get(level.getServer()).setDirty();
        return true;
    }

    static void liftFromOldFloor(ServerLevel level,DungeonData.Run run,BlockPos center,LivingEntity actor){
        var events=run.state().events;
        if(actor.level()!=level || !events.getBoolean(OLD_POOL) || !events.getBoolean(PAVED)
                || actor.getY()>=center.getY()+1 || actor.getY()<center.getY()-.5)return;
        var bounds=actor.getBoundingBox();
        double dx=Math.max(0,Math.abs(actor.getX()-center.getX()-.5)-actor.getBbWidth()/2-.5);
        double dz=Math.max(0,Math.abs(actor.getZ()-center.getZ()-.5)-actor.getBbWidth()/2-.5);
        if(dx*dx+dz*dz>INLAY_RADIUS*INLAY_RADIUS || level.noCollision(actor,bounds))return;
        for(int ring=0;ring<=12;ring++)for(int x=-ring;x<=ring;x++)for(int z=-ring;z<=ring;z++){
            if(Math.max(Math.abs(x),Math.abs(z))!=ring)continue;
            double px=actor.getX()+x,pz=actor.getZ()+z,py=center.getY()+1;
            var foot=BlockPos.containing(px,py,pz);
            if(!level.getBlockState(foot.below()).isFaceSturdy(level,foot.below(),Direction.UP)
                    || !level.getFluidState(foot).isEmpty() || !level.noCollision(actor,bounds.move(x,py-actor.getY(),z)))continue;
            var previous=actor.blockPosition();
            actor.teleportTo(px,py,pz);
            actor.fallDistance=0;
            if(actor instanceof ServerPlayer p && DungeonService.run(p)==run){
                com.tnc.tnc.combat.DownedCombat.relocateAnchor(p,level,foot);
                var profile=DungeonData.get(p.server).profile(p.getUUID());
                profile.putLong("ResumePos",foot.asLong());
                if(profile.getBoolean("Recovering") && profile.contains("RecoverPos")
                        && previous.distSqr(BlockPos.of(profile.getLong("RecoverPos")))<=1)
                    profile.putLong("RecoverPos",foot.asLong());
            }
            return;
        }
    }

    public static void build(List<DungeonRealm.Cell> cells, DungeonData.Run run, DungeonGraph.Room room) {
        var center = DungeonRealm.center(run, room);
        var front = entrance(run, room);
        for (int x = -22; x <= 22; x++) {
            for (int z = -22; z <= 22; z++) {
                double r = Math.hypot(x, z);
                if (r > 22) continue;
                var p = local(center, front, x, 0, z);
                if (r <= INLAY_RADIUS) {
                    add(cells,p,inlay(x,z));
                } else {
                    Block paving = (r < 9.4 || r > 19.9 && r < 20.8) ? Blocks.CALCITE
                            : r < 10.2 || Math.abs(x) <= 1 && Math.abs(z) >= 10 ? Blocks.POLISHED_BASALT
                            : (Math.floorMod(x * 31 + z * 53, 13) < 2 ? Blocks.CRACKED_DEEPSLATE_TILES : Blocks.DEEPSLATE_TILES);
                    add(cells,p,paving);
                }
                if (r > 20.7 && r < 21.7 && Math.abs(x) > 2) {
                    int height = (Math.abs(x) + Math.abs(z)) % 7 == 0 ? 3 : 2;
                    for (int y = 1; y <= height; y++) add(cells,p.above(y),y == height ? Blocks.CHISELED_POLISHED_BLACKSTONE : Blocks.DEEPSLATE_BRICKS);
                }
            }
        }
        int[][] pillars = {{-11,4,4},{10,10,3},{14,-4,5},{-11,-12,6}};
        for (var column : pillars) {
            var p = local(center, front, column[0], 0, column[1]);
            for (int y=1; y<=column[2]; y++) {
                add(cells,p.above(y),y==1 || y==column[2] ? Blocks.CHISELED_DEEPSLATE : Blocks.POLISHED_BASALT);
                if (y < column[2]-1) add(cells,p.relative(front).above(y),Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS);
            }
            add(cells,p.above(column[2]+1),Blocks.SEA_LANTERN);
        }
        // A dim oculus and radial ribs frame the hovering shell without an outdoor skylight leak.
        for (int x=-19; x<=19; x++) for (int z=-19; z<=19; z++) {
            double r=Math.hypot(x,z);
            if (r<3 || r>4.4 && r<5.3 || r>6.6 && r<7.6 || r<19 && (x==0 || z==0 || Math.abs(x)==Math.abs(z))) {
                var p=local(center,front,x,19,z);
                add(cells,p,r<3 ? Blocks.PEARLESCENT_FROGLIGHT : r<7.6 ? Blocks.TINTED_GLASS : Blocks.POLISHED_BASALT);
            }
        }
        add(cells,exit(run,room),DungeonContent.WAYSTONE);
        run.state().events.putBoolean(PAVED,true);
    }

    private static void add(List<DungeonRealm.Cell> cells, BlockPos p, Block block) {
        cells.add(new DungeonRealm.Cell(p,block.defaultBlockState()));
    }
}
