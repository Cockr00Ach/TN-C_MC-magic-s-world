package com.tnc.tnc.dungeon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.*;
public final class DungeonGateBlock extends Block {
    public final DungeonTheme theme;
    public DungeonGateBlock(DungeonTheme theme){super(BlockBehaviour.Properties.copy(Blocks.NETHER_PORTAL).noCollission().noOcclusion().lightLevel(s->11).strength(-1,3600000));this.theme=theme;}
    @Override public void entityInside(BlockState s,Level l,BlockPos at,Entity entity){if(entity instanceof ServerPlayer p&&!DungeonRealm.inside(l)&&!p.isPassenger()&&!p.isVehicle()){long now=l.getGameTime();if(p.getPersistentData().getLong("DungeonGateCooldown")>now)return;p.getPersistentData().putLong("DungeonGateCooldown",now+100);DungeonService.enter(p,theme);}}
    @Override public InteractionResult use(BlockState s,Level l,BlockPos at,Player player,InteractionHand hand,BlockHitResult hit){if(player instanceof ServerPlayer p){p.getPersistentData().putLong("DungeonGateCooldown",l.getGameTime()+100);DungeonService.enter(p,theme);}return InteractionResult.sidedSuccess(l.isClientSide);}
    @Override public void animateTick(BlockState s,Level l,BlockPos p,net.minecraft.util.RandomSource random){int c=theme.color;for(int i=0;i<2;i++)l.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f),1.1f),p.getX()+random.nextDouble(),p.getY()+random.nextDouble(),p.getZ()+random.nextDouble(),0,.02,0);}
}
