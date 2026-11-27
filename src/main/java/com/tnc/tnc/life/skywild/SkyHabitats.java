package com.tnc.tnc.life.skywild;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="tnc")
public final class SkyHabitats {
    public record Habitat(boolean woody, boolean shore, boolean highland, boolean open) {
        boolean supports(SkySpecies kind) {
            return switch (kind) {
                case SWALLOW -> shore;
                case STARLING -> woody;
                case EAGLE -> highland;
                case BAT -> woody || shore;
                case CRANE -> open || shore;
            };
        }
    }
    static int roof(ServerLevel level,int x,int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z);
    }
    static Habitat sample(ServerLevel level,int x,int z,int floor) {
        BlockPos ground=new BlockPos(x,floor-1,z);
        var biome=level.getBiome(ground);var surface=level.getBlockState(ground);
        boolean water=surface.getFluidState().is(FluidTags.WATER),nearWater=water,nearLand=!water;
        boolean leaves=surface.is(BlockTags.LEAVES),tree=leaves;
        int min=floor,max=floor;
        int[][] directions={{-14,0},{14,0},{0,-14},{0,14}};
        for (var d : directions) {
            int dx=x+d[0], dz=z+d[1];
            if (!level.hasChunkAt(new BlockPos(dx,floor,dz))) continue;
            int y=roof(level,dx,dz);
            if (y<=level.getMinBuildHeight()+2) continue;
            var cover=level.getBlockState(new BlockPos(dx,y-1,dz));
            boolean wet=cover.getFluidState().is(FluidTags.WATER);
            nearWater|=wet;nearLand|=!wet;tree|=cover.is(BlockTags.LEAVES);
            min=Math.min(min,y);max=Math.max(max,y);
        }
        boolean shore=nearWater && (nearLand || biome.is(BiomeTags.IS_RIVER));
        boolean wood=tree || biome.is(BiomeTags.IS_FOREST);
        boolean ridge=biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) || max-min>=13;
        boolean open=!wood && !water && !ridge && max-min<=6;
        return new Habitat(wood,shore,ridge,open);
    }
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase!=TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (level.dimension()!=Level.OVERWORLD && level.dimension()!=com.tnc.tnc.world.skyhaven.SkyhavenContent.KEY) return;
        long clock=level.getGameTime();
        if (clock%100!=29 || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || !level.getServer().isSpawningAnimals()) return;
        var players=level.players();
        if (players.isEmpty()) return;
        ServerPlayer player=players.get((int)Math.floorMod(clock/100,players.size()));
        if (!player.isAlive() || player.isSpectator()) return;
        var around=player.getBoundingBox().inflate(112,86,112);
        var population=level.getEntitiesOfClass(SkyBird.class,around);
        if (population.size()>=22) return;
        for (int attempt=0;attempt<5;attempt++) {
            var r=level.getRandom();double a=r.nextDouble()*Math.PI*2;int reach=29+r.nextInt(39);
            int x=player.getBlockX()+(int)(Math.cos(a)*reach), z=player.getBlockZ()+(int)(Math.sin(a)*reach);
            if (!level.hasChunkAt(new BlockPos(x,player.getBlockY(),z))) continue;
            int floor=roof(level,x,z);
            if (floor<=level.getMinBuildHeight()+2 || Math.abs(floor-player.getY())>50) continue;
            var habitat=sample(level,x,z,floor);
            var species=SkySpecies.values(); int start=r.nextInt(species.length);
            for (int j=0;j<species.length;j++) {
                var kind=species[(start+j)%species.length];
                if (!habitat.supports(kind) || !kind.active(level.getDayTime()) ||
                        population.stream().anyMatch(e->e.species==kind)) continue;
                if (place(level,kind,new BlockPos(x,floor,z),22-population.size())>0) return;
            }
        }
    }
    static int place(ServerLevel level,SkySpecies kind,BlockPos foot,int budget) {
        var random=level.getRandom();
        int count=Math.min(budget,kind.minGroup+random.nextInt(kind.maxGroup-kind.minGroup+1));
        if (count<kind.minGroup) return 0;
        long flock=random.nextLong()|1;double direction=random.nextDouble()*Math.PI*2;
        double sample=.22+random.nextDouble()*.56;
        var candidates=new ArrayList<SkyBird>(count);
        for (int slot=0;slot<count;slot++) {
            double side=slot==0?0:(slot%2==0?-1:1)*((slot+1)/2)*2.2;
            int x=foot.getX()+(int)Math.round(side*Math.cos(direction)-(slot/2)*Math.sin(direction)*2);
            int z=foot.getZ()+(int)Math.round(side*Math.sin(direction)+(slot/2)*Math.cos(direction)*2);
            if (!level.hasChunkAt(new BlockPos(x,foot.getY(),z))) continue;
            int floor=roof(level,x,z); if (floor<=level.getMinBuildHeight()+2) continue;
            int y=kind.altitude(floor,sample,level.getMaxBuildHeight());
            if(y==Integer.MIN_VALUE) continue;
            var point=new BlockPos(x,y,z);
            if (!level.getWorldBorder().isWithinBounds(point) || !level.canSeeSky(point) || !level.getFluidState(point).isEmpty()) continue;
            var box=AABB.ofSize(point.getCenter(),kind.width,kind.height,kind.width);
            if (!level.noCollision(box) || level.getNearestPlayer(x+.5,y+.5,z+.5,20,false)!=null) continue;
            var bird=SkyWildRegistry.TYPES.get(kind).create(level);
            if (bird==null) continue;
            bird.moveTo(x+.5,y+.15,z+.5,(float)(direction*180/Math.PI),0);
            if (!level.noCollision(bird,bird.getBoundingBox())) continue;
            candidates.add(bird);
        }
        if (candidates.size()<kind.minGroup) return 0;
        int joined=0;
        for (var bird : candidates) {
            bird.joinFlock(flock,joined,foot,direction);
            if (level.addFreshEntity(bird)) joined++;
        }
        return joined;
    }
    private SkyHabitats() {}
}

