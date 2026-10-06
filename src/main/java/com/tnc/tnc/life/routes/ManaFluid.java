package com.tnc.tnc.life.routes;
import net.minecraft.core.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.*;
import net.minecraftforge.fluids.ForgeFlowingFluid;
/** Water-like flow without uprooting the crops it irrigates or replacing other fluids. */
public final class ManaFluid {
    private static boolean safe(BlockState state){return state.isAir()||state.getBlock()==RouteContent.MANA_LIQUID.get();}
    public static final class Source extends ForgeFlowingFluid.Source {
        public Source(Properties p){super(p);}
        @Override protected boolean canSpreadTo(BlockGetter l,BlockPos from,BlockState a,Direction d,BlockPos to,BlockState b,FluidState fluid,Fluid f){return safe(b)&&super.canSpreadTo(l,from,a,d,to,b,fluid,f);}
        @Override protected void spreadTo(LevelAccessor l,BlockPos p,BlockState s,Direction d,FluidState f){if(safe(s))super.spreadTo(l,p,s,d,f);}
    }
    public static final class Flowing extends ForgeFlowingFluid.Flowing {
        public Flowing(Properties p){super(p);}
        @Override protected boolean canSpreadTo(BlockGetter l,BlockPos from,BlockState a,Direction d,BlockPos to,BlockState b,FluidState fluid,Fluid f){return safe(b)&&super.canSpreadTo(l,from,a,d,to,b,fluid,f);}
        @Override protected void spreadTo(LevelAccessor l,BlockPos p,BlockState s,Direction d,FluidState f){if(safe(s))super.spreadTo(l,p,s,d,f);}
    }
}
