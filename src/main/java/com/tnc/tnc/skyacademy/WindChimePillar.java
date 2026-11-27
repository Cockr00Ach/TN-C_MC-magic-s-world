package com.tnc.tnc.skyacademy;

import com.tnc.tnc.TNMod;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Three architectural instruments. Each occupied cell has its own mesh and click target. */
public final class WindChimePillar extends Block {
    private static final IntegerProperty VOICE = IntegerProperty.create("voice", 0, 2);
    private static final IntegerProperty SECTION = IntegerProperty.create("section", 0, 2);
    private static final VoxelShape FOOT = Shapes.or(
            box(0, 0, 0, 16, 5, 16), box(2, 5, 2, 14, 10, 14), box(3, 10, 3, 13, 16, 13));
    private static final VoxelShape SHAFT = Shapes.or(
            box(3, 0, 3, 13, 13, 13), box(1, 13, 2, 15, 16, 14));
    private static final VoxelShape[] CROWNS = {
            box(1, 0, 4, 15, 16, 12),
            box(0, 0, 4, 16, 16, 12),
            box(1, 0, 3, 15, 16, 13)
    };

    public WindChimePillar() {
        super(Properties.of().mapColor(MapColor.COLOR_CYAN).sound(SoundType.COPPER)
                .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion()
                .lightLevel(state -> 5 + 3 * state.getValue(SECTION)));
        registerDefaultState(stateDefinition.any().setValue(VOICE, 0).setValue(SECTION, 0));
    }

    public static BlockState state(int runeIndex, int section) {
        return TNMod.WIND_CHIME_PILLAR.get().defaultBlockState().setValue(VOICE, runeIndex).setValue(SECTION, section);
    }

    public static BlockPos base(BlockPos hit, BlockState state) {
        return state.getBlock() instanceof WindChimePillar ? hit.below(state.getValue(SECTION)) : null;
    }

    public static int voice(BlockState state) {
        return state.getBlock() instanceof WindChimePillar ? state.getValue(VOICE) : -1;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        // Consume the client-side click too, even when a placeable block is held.
        // The server's AcademyLessons handler validates the target and advances the puzzle.
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(VOICE, SECTION);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(SECTION)) {
            case 0 -> FOOT;
            case 1 -> SHAFT;
            default -> CROWNS[state.getValue(VOICE)];
        };
    }
}
