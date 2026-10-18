package com.tnc.tnc.casino.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * 赌场机器的公共方块 ✓ —— 老虎机与转盘桌都长这样：<b>2×2 格 × 1 格高</b>，
 * 一个方块渲染整台机器 ✓（老规矩沿用自 {@code prop} 里那批场景道具：模型从"被放下的那一格"
 * 向 <b>+x / +z</b> 展开 ✓）。
 *
 * <h2>⚠️ 模型为什么不走 {@code models/block/*.json}（这是本机的关键决定 ✗）</h2>
 * 原版方块模型有三把刀，这些机器一把都躲不过：
 * <ol>
 *   <li><b>顶点必须在 −16..32</b>（≈2 格）—— 2 格宽的机器会被加载器直接拒绝 ✗。
 *       实证：{@code block/gamble_table.json} 的 extents 是 {@code 0..64}，
 *       客户端日志是 {@code Failed to load model} ⇒ <b>那张赌博桌在游戏里根本没显示过</b> ✗；</li>
 *   <li>整合包里优先级更高的资源包会遮住 {@code tnc:models/**} ✗（同一条日志）；</li>
 *   <li>原版 element <b>只能绕单轴转固定角度</b> ✗ —— 滚轮/转盘要逐帧任意角度 ✓。</li>
 * </ol>
 * ⇒ 几何全部走<b>代码自绘</b> ✓（{@code casino/client/TNCasinoGeometry} ＋ 各自的渲染器 ✓），
 * 所以这里的 {@link RenderShape} 是 {@link RenderShape#INVISIBLE} ✓，也<b>没有</b> blockstate 模型文件 ✓。
 *
 * <p>⚠️ 代价：客户端<b>必须</b>注册渲染器 ✗，否则机器<b>完全隐形且一条错误都不报</b> ✗
 * —— 见 {@code casino/client/TNCasinoClient} ✓。
 *
 * @param <E> 该机器对应的方块实体类型 ✓
 */
public class TNCasinoMachineBlock<E extends TNMachineEntity> extends HorizontalDirectionalBlock
        implements EntityBlock {

    /**
     * 正面朝向（放置时朝向玩家 ✓）。
     *
     * <p>⚠️ 是 <b>public static final field</b> ✓，不是把 {@code HorizontalDirectionalBlock.FACING}
     * 静态导入进来的那个常量 ✗（本工程不用静态导入 ✓）。
     * 引用时一律写 {@code TNCasinoMachineBlock.FACING} ✓（子类继承它，但渲染器里直接写父类更清楚 ✓）。
     */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    /** 是否正在转（服务端权威 ✓；同时用来触发 {@code getUpdateTag} 同步 ✗）。 */
    public static final BooleanProperty SPINNING = BooleanProperty.create("spinning");

    /**
     * 机器占地与碰撞箱 <b>不再写死</b> ✗ —— 各机器不一样 ✓：
     * <ul>
     *   <li>老虎机：<b>1 × 2 × 1</b>（作者要求"高 2 宽 1 的那种经典老虎机" ✓）；</li>
     *   <li>转盘赌桌 / 赌博桌：<b>2 × 1 × 2</b> 与 <b>4 × 1 × 2</b>（台面类 ✓）。</li>
     * </ul>
     * 形状超出单个格子时<b>必须</b> {@code noOcclusion()} ✓（父类构造里已设 ✓），
     * 否则相邻面会被剔掉 ✗。
     */
    private final VoxelShape shape;
    private final VoxelShape turnedShape;

    private final BiFunction<BlockPos, BlockState, E> factory;

    /** 默认 2 × 1 × 2（台面类机器 ✓）。 */
    public TNCasinoMachineBlock(BiFunction<BlockPos, BlockState, E> factory) {
        this(factory, 2.0D, 1.0D, 2.0D);
    }

    /**
     * @param width  占地 X ✓
     * @param height 占地 Y ✓（碰撞箱高度，也是渲染时"整机多高"的依据 ✓）
     * @param depth  占地 Z ✓
     */
    public TNCasinoMachineBlock(BiFunction<BlockPos, BlockState, E> factory,
                                double width, double height, double depth) {
        super(Properties.of().strength(1.6F).sound(SoundType.WOOD).noOcclusion());
        this.factory = factory;
        this.shape = Shapes.box(0.0D, 0.0D, 0.0D, width, height, depth);
        this.turnedShape = Shapes.box((width-depth)/2, 0, (depth-width)/2,
                (width+depth)/2, height, (depth+width)/2);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SPINNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPINNING);
    }

    /** 放下时正面朝玩家 ✓（与原版箱子/熔炉同规矩）。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** 形状超出单个格子 ⇒ 不这样设会剔掉相邻面 ✗（与赌博桌同一个理由）。 */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? turnedShape : shape;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? turnedShape : shape;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /**
     * 取本方块位置的机器实体 ✓。
     * ⚠️ 先判"是不是我们家的方块实体"再转 ✗ —— 直接强转会在这个位置被别人放了
     * 别的方块实体时抛 {@code ClassCastException} ✓。
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public E machine(BlockGetter level, BlockPos pos) {
        BlockEntity entity = level.getBlockEntity(pos);
        if (!(entity instanceof TNMachineEntity)) return null;
        return (E) entity;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    /** 状态机活在服务端 ✓；客户端靠同步下来的字段自己插值算动画（每 tick 零网络包 ✓）。 */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        return level.isClientSide ? null
                : (world, pos, blockState, entity) -> {
                    if (entity instanceof TNMachineEntity machine) {
                        machine.serverTick((ServerLevel) world, pos, blockState);
                    }
                };
    }

    /** 放下时把主人记进方块实体 ✓（照牧场设施的做法 ✓）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                            ItemStack stack) {
        if (placer instanceof ServerPlayer player) {
            E machine = machine(level, pos);
            if (machine != null) machine.claim(player.getUUID());
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer server) {
            E machine = machine(level, pos);
            if (machine != null) {
                machine.interact(server);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            E machine = machine(level, pos);
            if (machine != null) machine.dropOwed(level, pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
