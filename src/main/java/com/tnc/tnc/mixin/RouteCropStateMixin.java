package com.tnc.tnc.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import com.tnc.tnc.life.routes.*;
@Mixin(value=BlockBehaviour.BlockStateBase.class,remap=false)
public abstract class RouteCropStateMixin {
    @Inject(method={"randomTick","m_222972_"},at=@At("HEAD"),cancellable=true,remap=false)
    private void tnc$season(ServerLevel level,BlockPos pos,RandomSource random,CallbackInfo ci){if(!RouteSeasons.allow((BlockState)(Object)this,level,pos,random))ci.cancel();}
    @Inject(method={"canSurvive","m_60710_"},at=@At("HEAD"),cancellable=true,remap=false)
    private void tnc$soil(LevelReader level,BlockPos pos,CallbackInfoReturnable<Boolean> ci){BlockState state=(BlockState)(Object)this;if(RoutePlanting.magic(state)&&!RoutePlanting.wild(state,level,pos))ci.setReturnValue(MagicSoilBlock.isSoil(level.getBlockState(RoutePlanting.soil(state,pos))));}
}
