package com.tnc.tnc.life.seagarden;

import com.tnc.tnc.home.TownProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

final class TidekelpBlock extends SeaPlantBlock {
    enum Part implements StringRepresentable {
        STEM("stem"), TIP("tip");
        private final String id;
        Part(String id) {this.id=id;}
        @Override public String getSerializedName() {return id;}
    }
    static final EnumProperty<Part> PART=EnumProperty.create("part",Part.class);

    TidekelpBlock() {
        super(SeaGarden.Kind.KELP);
        registerDefaultState(defaultBlockState().setValue(PART,Part.TIP));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        var below=level.getBlockState(pos.below());
        return seaBed(below) || below.is(this) && below.getValue(PART)==Part.STEM;
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state,level,pos,old,moving);
        if (!level.isClientSide && !old.is(this) && state.getValue(PART)==Part.TIP && state.getValue(STAGE)<2)
            level.scheduleTick(pos,this,500+level.random.nextInt(160));
    }
    @Override public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos other) {
        if (direction==Direction.UP && state.getValue(PART)==Part.STEM && !neighbor.is(this)
                && state.canSurvive(level,pos)) {
            level.scheduleTick(pos,this,520);
            return defaultBlockState();
        }
        return super.updateShape(state,direction,neighbor,level,pos,other);
    }
    @Override public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART)!=Part.TIP || state.getValue(STAGE)>=2) return;
        int stemCount=0;
        while (stemCount<4 && level.getBlockState(pos.below(stemCount+1)).is(this)) stemCount++;
        // Growth and harvest keep a single tip. Leave one water cell above the canopy.
        if (state.getValue(STAGE)==0 && stemCount<4 && source(level.getBlockState(pos.above())) && source(level.getBlockState(pos.above(2)))
                && !TownProtection.hazard(level,pos.above())) {
            level.setBlock(pos,defaultBlockState().setValue(PART,Part.STEM).setValue(STAGE,1),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(pos.above(),defaultBlockState(),Block.UPDATE_CLIENTS|Block.UPDATE_KNOWN_SHAPE);
        } else {
            int stage=state.getValue(STAGE)+1;
            level.setBlock(pos,state.setValue(STAGE,stage),Block.UPDATE_CLIENTS);
            if(stage==1) level.scheduleTick(pos,this,520);
        }
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).is(Items.SHEARS) || state.getValue(PART)!=Part.TIP) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp) || TownProtection.denied(sp,pos)) return InteractionResult.FAIL;
        if (state.getValue(STAGE)<2) {
            sp.displayClientMessage(Component.translatable("seagarden.tnc.kelp_growing"),true);
            return InteractionResult.CONSUME;
        }
        level.setBlock(pos,state.setValue(STAGE,0),Block.UPDATE_CLIENTS);
        level.scheduleTick(pos,this,620);
        if (!sp.isCreative()) {
            var cutting=new ItemStack(this);
            if (!sp.getInventory().add(cutting)) sp.drop(cutting,false);
            sp.getItemInHand(hand).hurtAndBreak(1,sp,p->p.broadcastBreakEvent(hand));
        }
        particles(sp.serverLevel(),pos,net.minecraft.core.particles.ParticleTypes.BUBBLE,8);
        cue(sp.serverLevel(),pos,1.3F);
        sp.displayClientMessage(Component.translatable("seagarden.tnc.kelp_harvest"),true);
        return InteractionResult.CONSUME;
    }
}
