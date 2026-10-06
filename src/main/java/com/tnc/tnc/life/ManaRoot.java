package com.tnc.tnc.life;

import com.tnc.tnc.TNMod;
import com.tnc.tnc.home.TownProtection;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Player-tended root: forty real mana grows a core worth thirty forge mana. */
public final class ManaRoot {
    public static final int MANA_PER_FEED = 10;
    public static final int FEEDS_TO_GROW = 4;
    public static final int CORE_FORGE_MANA = 30;

    private ManaRoot() {}

    public static final class RootBlock extends BushBlock {
        public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 3);
        public static final IntegerProperty FED = IntegerProperty.create("fed", 0, FEEDS_TO_GROW);
        private static final VoxelShape[] SHAPES = {
                Block.box(6, 0, 6, 10, 4, 10), Block.box(5, 0, 5, 11, 7, 11),
                Block.box(4, 0, 4, 12, 11, 12), Block.box(3, 0, 3, 13, 14, 13)
        };

        public RootBlock() {
            super(BlockBehaviour.Properties.copy(Blocks.WHEAT).noCollission().randomTicks()
                    .lightLevel(state -> state.getValue(FED) == 0 ? 0 : 3 + state.getValue(FED)));
            registerDefaultState(stateDefinition.any().setValue(AGE, 0).setValue(FED, 0).setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,false));
        }

        @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AGE, FED,com.tnc.tnc.life.routes.RoutePlantBlock.WILD);
        }

        @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES[state.getValue(AGE)];
        }

        @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).is(BlockTags.DIRT)
                    || level.getBlockState(pos.below()).is(Blocks.FARMLAND);
        }

        @Override public boolean isRandomlyTicking(BlockState state) {
            return state.getValue(AGE) < 3 && state.getValue(FED) > state.getValue(AGE);
        }

        @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (isRandomlyTicking(state) && random.nextInt(3) == 0)
                level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), 2);
        }

        @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                                InteractionHand hand, BlockHitResult hit) {
            if (!player.getItemInHand(hand).isEmpty()&&!player.getItemInHand(hand).is(net.minecraft.world.item.Items.SHEARS)) return InteractionResult.PASS;
            if (level.isClientSide) return InteractionResult.SUCCESS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            if (TownProtection.denied(serverPlayer, pos)) return InteractionResult.FAIL;
            int age = state.getValue(AGE);
            int fed = state.getValue(FED);
            if (age == 3 && fed == FEEDS_TO_GROW) {
                Block.popResource(level, pos, player.getItemInHand(hand).is(net.minecraft.world.item.Items.SHEARS)?new ItemStack(TNMod.MANA_ROOT_SEED.get()):new ItemStack(TNMod.MANA_ROOT_CORE.get()));
                com.tnc.tnc.life.routes.RouteProgress.award(serverPlayer,"harvest/mana_root");
                level.setBlock(pos, state.setValue(AGE, 0).setValue(FED, 0).setValue(com.tnc.tnc.life.routes.RoutePlantBlock.WILD,false), 3);
                serverPlayer.displayClientMessage(Component.literal("取下一枚蓄魔根芯。根还活着，可以重新注魔培育。"), true);
                return InteractionResult.CONSUME;
            }
            if (fed == FEEDS_TO_GROW) {
                serverPlayer.displayClientMessage(Component.literal("根已吸满魔力，还需一点生长时间。"), true);
                return InteractionResult.CONSUME;
            }
            var magic = MagicStone.getOrNull(serverPlayer);
            if (magic == null || !magic.spendMana(MANA_PER_FEED)) {
                serverPlayer.displayClientMessage(Component.literal("需要自己的魔法石中有 10 魔力，才能喂养蓄魔根。"), true);
                return InteractionResult.CONSUME;
            }
            level.setBlock(pos, state.setValue(FED, fed + 1), 3);
            MagicStoneNetwork.syncTo(serverPlayer);
            serverPlayer.displayClientMessage(Component.literal("向蓄魔根注入 10 魔力（"
                    + (fed + 1) + "/" + FEEDS_TO_GROW + "）。"), true);
            return InteractionResult.CONSUME;
        }
    }

    public static final class CoreItem extends Item {
        public CoreItem() { super(new Properties()); }
        @Override public void appendHoverText(ItemStack stack, Level level, List<Component> text, TooltipFlag flag) {
            text.add(Component.literal("由活根吸收 40 点玩家魔力长成；投入锻造炉返还 30 点。"));
            text.add(Component.literal("只能储存你亲手注入的魔力，不能凭空生电。"));
        }
    }
}
