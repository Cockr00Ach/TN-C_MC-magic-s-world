package com.tnc.tnc.life.routes;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;
import java.util.UUID;

/** One explicit reciprocal pair, arbitrary direction, loaded chunks and an unobstructed optical path. */
public final class RouteMirrorLinks {
    public static boolean mirror(RouteNodeEntity n){return n.kind()==RouteKind.MIRROR||n.kind()==RouteKind.FAR_MIRROR||n.kind()==RouteKind.DIMENSION_PRISM;}
    public static int range(RouteNodeEntity n){return n.kind()==RouteKind.DIMENSION_PRISM?30000000:n.kind()==RouteKind.FAR_MIRROR?24:12;}
    public static String link(ServerLevel l,BlockPos a,BlockPos b,UUID owner){
        if(a.equals(b))return "请选另一面折光镜";
        if(!l.hasChunkAt(a)||!l.hasChunkAt(b))return "两端都需要处于加载范围";
        if(!(l.getBlockEntity(a) instanceof RouteNodeEntity x)||!(l.getBlockEntity(b) instanceof RouteNodeEntity y)||!mirror(x)||!mirror(y))return "需要两面折光镜";
        if(!owner.equals(x.owner)||!owner.equals(y.owner)||!RouteAccess.allowed(l,a,owner)||!RouteAccess.allowed(l,b,owner))return "只能配对自己有权限的镜子";
        if(a.distSqr(b)>Math.pow(Math.min(range(x),range(y)),2))return "超出两端的共同距离：普通12格 / 远折24格";
        if(!(x.kind()==RouteKind.DIMENSION_PRISM&&y.kind()==RouteKind.DIMENSION_PRISM)&&!clear(l,a,b))return "镜面之间被方块遮挡";
        unlink(l,x);unlink(l,y);x.mirrorTarget=b.immutable();y.mirrorTarget=a.immutable();sync(l,x);sync(l,y);return "";
    }
    public static void unlink(ServerLevel l,RouteNodeEntity n){
        var old=n.mirrorTarget;n.mirrorTarget=null;sync(l,n);
        if(old!=null&&l.hasChunkAt(old)&&l.getBlockEntity(old) instanceof RouteNodeEntity other&&n.getBlockPos().equals(other.mirrorTarget)){other.mirrorTarget=null;sync(l,other);}
    }
    private static void sync(ServerLevel l,RouteNodeEntity n){n.setChanged();l.sendBlockUpdated(n.getBlockPos(),n.getBlockState(),n.getBlockState(),3);}
    public static BlockPos remote(ServerLevel l,RouteNodeEntity n){
        var q=n.mirrorTarget;if(q==null||!l.hasChunkAt(q)||!(l.getBlockEntity(q) instanceof RouteNodeEntity peer)||!mirror(peer)||n.owner==null||!n.owner.equals(peer.owner)||!n.getBlockPos().equals(peer.mirrorTarget))return null;
        return n.getBlockPos().distSqr(q)<=Math.pow(Math.min(range(n),range(peer)),2)&&RouteAccess.allowed(l,q,n.owner)&&(n.kind()==RouteKind.DIMENSION_PRISM&&peer.kind()==RouteKind.DIMENSION_PRISM||clear(l,n.getBlockPos(),q))?q:null;
    }
    public static boolean clear(ServerLevel l,BlockPos a,BlockPos b){
        for(int x=Math.min(a.getX(),b.getX())>>4;x<=Math.max(a.getX(),b.getX())>>4;x++)for(int z=Math.min(a.getZ(),b.getZ())>>4;z<=Math.max(a.getZ(),b.getZ())>>4;z++)if(!l.hasChunk(x,z))return false;
        BlockGetter view=new BlockGetter(){
            public BlockState getBlockState(BlockPos p){return p.equals(a)||p.equals(b)?Blocks.AIR.defaultBlockState():l.getBlockState(p);}
            public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
            public BlockEntity getBlockEntity(BlockPos p){return l.getBlockEntity(p);}
            public int getHeight(){return l.getHeight();}public int getMinBuildHeight(){return l.getMinBuildHeight();}
        };
        return view.clip(new ClipContext(Vec3.atCenterOf(a).add(0,.25,0),Vec3.atCenterOf(b).add(0,.25,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null)).getType()==HitResult.Type.MISS;
    }
}
