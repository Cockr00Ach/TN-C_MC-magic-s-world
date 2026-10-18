package com.tnc.tnc.life.routes;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import net.minecraftforge.fluids.ForgeFlowingFluid;
/** Water-like flow without uprooting the crops it irrigates or replacing other fluids. */
public final class ManaFluid {
    public static boolean allowedInOwnedGarden(BlockGetter world,BlockPos target,Direction flow,FluidState fluid){
        if(!(world instanceof net.minecraft.server.level.ServerLevel level)||fluid.getFluidType()!=RouteContent.MANA_TYPE.get())return false;
        var to=com.tnc.tnc.home.HousingService.ownedAt(level,target);
        var from=com.tnc.tnc.home.HousingService.ownedAt(level,target.relative(flow.getOpposite()));
        if(to==null||from==null)return false;
        var a=com.tnc.tnc.home.HousingService.home(level.getServer(),to.id());
        var b=com.tnc.tnc.home.HousingService.home(level.getServer(),from.id());
        return !a.getBoolean("Preparing")&&!b.getBoolean("Preparing")&&a.hasUUID("Owner")&&b.hasUUID("Owner")&&a.getUUID("Owner").equals(b.getUUID("Owner"));
    }
    private static boolean withinBoundary(BlockGetter world,BlockPos from,BlockPos to){
        if(!(world instanceof net.minecraft.server.level.ServerLevel level))return true;
        var a=com.tnc.tnc.home.HousingService.ownedAt(level,from);
        var b=com.tnc.tnc.home.HousingService.ownedAt(level,to);
        if(a==null&&b==null)return true;
        if(a==null||b==null)return false;
        var ar=com.tnc.tnc.home.HousingService.home(level.getServer(),a.id());
        var br=com.tnc.tnc.home.HousingService.home(level.getServer(),b.id());
        return !ar.getBoolean("Preparing")&&!br.getBoolean("Preparing")&&ar.hasUUID("Owner")&&br.hasUUID("Owner")&&ar.getUUID("Owner").equals(br.getUUID("Owner"));
    }
    private static boolean safe(BlockState state){return state.isAir()||state.getBlock()==RouteContent.MANA_LIQUID.get();}
    public static final class Source extends ForgeFlowingFluid.Source {
        public Source(Properties p){super(p);}
        @Override protected java.util.Map<Direction,FluidState> getSpread(Level l,BlockPos p,BlockState state){
            var preferred=super.getSpread(l,p,state);
            preferred.entrySet().removeIf(e->!safe(l.getBlockState(p.relative(e.getKey())))||!withinBoundary(l,p,p.relative(e.getKey())));
            // A protected garden must fill its legal level floor even if vanilla selects
            // a nearer downhill route. Never let the protected edge starve irrigation.
            if(l instanceof net.minecraft.server.level.ServerLevel server&&com.tnc.tnc.home.HousingService.ownedAt(server,p)!=null){
                for(var d:Direction.Plane.HORIZONTAL){var q=p.relative(d);var target=l.getBlockState(q);var next=getNewLiquid(l,q,target);
                    if(!next.isEmpty()&&canSpreadTo(l,p,state,d,q,target,target.getFluidState(),next.getType()))preferred.put(d,next);
                }
            }return preferred;
        }
        @Override protected boolean canSpreadTo(BlockGetter l,BlockPos from,BlockState a,Direction d,BlockPos to,BlockState b,FluidState fluid,Fluid f){return safe(b)&&withinBoundary(l,from,to)&&super.canSpreadTo(l,from,a,d,to,b,fluid,f);}
        @Override protected void spreadTo(LevelAccessor l,BlockPos p,BlockState s,Direction d,FluidState f){if(safe(s))super.spreadTo(l,p,s,d,f);}
    }
    public static final class Flowing extends ForgeFlowingFluid.Flowing {
        public Flowing(Properties p){super(p);}
        @Override protected java.util.Map<Direction,FluidState> getSpread(Level l,BlockPos p,BlockState state){
            var preferred=super.getSpread(l,p,state);
            preferred.entrySet().removeIf(e->!safe(l.getBlockState(p.relative(e.getKey())))||!withinBoundary(l,p,p.relative(e.getKey())));
            // A protected garden must fill its legal level floor even if vanilla selects
            // a nearer downhill route. Never let the protected edge starve irrigation.
            if(l instanceof net.minecraft.server.level.ServerLevel server&&com.tnc.tnc.home.HousingService.ownedAt(server,p)!=null){
                for(var d:Direction.Plane.HORIZONTAL){var q=p.relative(d);var target=l.getBlockState(q);var next=getNewLiquid(l,q,target);
                    if(!next.isEmpty()&&canSpreadTo(l,p,state,d,q,target,target.getFluidState(),next.getType()))preferred.put(d,next);
                }
            }return preferred;
        }
        @Override protected boolean canSpreadTo(BlockGetter l,BlockPos from,BlockState a,Direction d,BlockPos to,BlockState b,FluidState fluid,Fluid f){return safe(b)&&withinBoundary(l,from,to)&&super.canSpreadTo(l,from,a,d,to,b,fluid,f);}
        @Override protected void spreadTo(LevelAccessor l,BlockPos p,BlockState s,Direction d,FluidState f){if(safe(s))super.spreadTo(l,p,s,d,f);}
    }
}
