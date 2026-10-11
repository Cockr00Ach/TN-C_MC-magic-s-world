package com.tnc.tnc.casino.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 赌场机器的<b>公共状态机</b> —— 老虎机与转盘桌共用 ✓。
 *
 * <h2>★ 职责边界（这是整个赌场最要紧的一条规矩 ✗）</h2>
 * <b>结算在服务端，动画在客户端</b> ✓：
 * <ul>
 *   <li>服务端只抽一次随机数、算出结果、决定赔多少 ✓；</li>
 *   <li>同步给客户端的只有 {@link Phase} ＋ 结果下标 ＋ 开局 tick 这几个<b>整数</b> ✓；</li>
 *   <li>客户端<b>自己插值</b>算角度（{@code gameTime + partialTick} ✓）⇒
 *       每 tick <b>零网络包</b> ✓，动画还是亚 tick 丝滑的 ✓，
 *       而且客户端<b>连随机数都碰不到</b> ✓ —— 这是这类功能唯一安全的做法 ✓。</li>
 * </ul>
 *
 * <h2>为什么不自己写网络包</h2>
 * 机器只在<b>状态跳变</b>的少数几个 tick 才需要发包（开局 / 出结果 / 复位 ✓），
 * 用 blockstate 属性 {@code spinning} 的变化触发 {@code getUpdateTag} 就够了 ✓
 * —— 本工程已有 {@code network/MagicStoneNetwork}，但这里没必要动它 ✗。
 */
public abstract class TNMachineEntity extends BlockEntity {

    /** 三只滚轮 / 三个骰子之类：本工程两台机器都是 3 个结果位 ✓。 */
    public static final int RESULT_SLOTS = 3;

    public enum Phase {
        /** 待机 ✓。 */
        IDLE,
        /** 转动中：服务端只记开局 tick ✓，客户端自己插值 ✓。 */
        SPINNING,
        /** 出结果：停稳 ＋ 结果灯亮 ✓。 */
        RESULT
    }

    protected Phase phase = Phase.IDLE;
    protected final int[] result = new int[RESULT_SLOTS];
    protected long startedAt;
    protected long nextSpinAllowedAt;
    /** 欠着的赔付（服务端私有 ✗，不进同步包 ✓）。 */
    protected int owed;
    /** 已投入的币（服务端私有 ✗）。 */
    protected int stake;
    protected int spins;
    protected java.util.UUID owner;
    protected java.util.UUID bettor;
    protected int selectedBet;
    protected int lastPayout;
    protected long settledAt;

    protected TNMachineEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * 玩家右键了这台机器 ✓ —— <b>每台机器的玩法入口</b> ✗（不是抽象状态机的一部分 ✗）。
     * 由 {@link TNCasinoMachineBlock#use} 转发过来 ✓，只在服务端被调用 ✓。
     */
    public abstract void interact(net.minecraft.server.level.ServerPlayer player);

    /**
     * 方块每次服务端 tick 调这里 ✓（由 {@link TNCasinoMachineBlock} 的 ticker 转发 ✓）。
     * 默认就是"推进状态机"✓，子类要加自己的 tick 逻辑就覆写并调 {@code super} ✓。
     */
    public void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        advance(level, pos, state);
    }

    /**
     * 机器被拆时把<b>投入的币与欠的赔付</b>掉出来 ✓ ——
     * 不让玩家的钱凭空消失 ✗（这一条必须留着，否则拆机器就等于没收 ✓）。
     */
    public void dropOwed(net.minecraft.world.level.Level level, BlockPos pos) {
        if (level.isClientSide) return;
        // An interrupted round resolves once instead of refunding a known losing result.
        int total = owed + (phase == Phase.SPINNING ? Math.max(0, payoutFor(result, stake)) : stake);
        if (total > 0) {
            net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D,
                    pos.getZ() + 0.5D, new net.minecraft.world.item.ItemStack(
                            com.tnc.tnc.TNMod.COPPER_COIN.get(), total));
        }
        owed = 0;
        stake = 0;
        setChanged();
    }

    // ------------------------------------------------------------------
    //  子类必须提供的旋钮
    // ------------------------------------------------------------------

    /** 服务端占用多少 tick ✓。 */
    public abstract int spinTicks();

    /**
     * 客户端动画总时长（服务端按两者较大值等待完整停轮 ✓）。
     *
     * <p>⚠️ <b>必须 public</b> ✗ —— 渲染器在 {@code casino/client} 里，
     * 与实体<b>不是同一个包</b> ✓，改成 private / package-private 会直接编译不过 ✓。
     */
    public abstract int clientSpinTicks();

    /** 出结果后可读状态维持多久 ✓。 */
    public abstract int resultTicks();

    /** 本次结果该赔多少币（0 = 没赢 ✓）。<b>赔率表就写在这一个方法里</b> ✓。 */
    protected abstract int payoutFor(int[] result, int staked);

    // ------------------------------------------------------------------
    //  服务端 tick
    // ------------------------------------------------------------------

    protected void advance(ServerLevel level, BlockPos pos, BlockState state) {
        long now = level.getGameTime();
        switch (phase) {
            case SPINNING -> {
                if (now - startedAt >= Math.max(spinTicks(), clientSpinTicks())) settle(level, pos, state);
            }
            case RESULT -> {
                if (now - settledAt >= resultTicks()) {
                    // 回到待机，但结果下标留着 ⇒ 灯灭了、滚轮还停在结果上 ✓
                    phase = Phase.IDLE;
                    setChanged();
                    sync(level, pos, state, false);
                }
            }
            case IDLE -> { }
        }
    }

    /** 开局 ✓：子类先掷好 {@link #result}，再调这个 ✓。 */
    protected void beginSpin(ServerLevel level, BlockPos pos, BlockState state) {
        phase = Phase.SPINNING;
        startedAt = level.getGameTime();
        nextSpinAllowedAt = startedAt + Math.max(spinTicks(), clientSpinTicks()) + 20L;
        spins++;
        setChanged();
        sync(level, pos, state, true);
    }

    /** 停轮结算 ✓。 */
    protected void settle(ServerLevel level, BlockPos pos, BlockState state) {
        phase = Phase.RESULT;
        settledAt = level.getGameTime();
        int payout = Math.max(0, payoutFor(result, stake));
        boolean profit=payout>stake&&stake>0;
        lastPayout = payout;
        if (payout > 0) {
            owed += payout;
        }
        stake = 0;
        setChanged();
        sync(level, pos, state, false);
        onSettled(level, pos, payout);
        if(profit) CasinoPackets.celebrate(level,pos,state);
        if (bettor != null) {
            var player = level.getServer().getPlayerList().getPlayer(bettor);
            if (player != null) tell(player, "本局已结算：返回 " + payout + " 枚铜币（含本金）。" +
                    (payout > 0 ? "右键设备领取。" : "本局未中奖。"));
        }
    }

    /** 子类可以加自己的音效/表现 ✓（默认什么都不做 ✓）。 */
    protected void onSettled(ServerLevel level, BlockPos pos, int payout) {
    }

    /** 还在转吗 ✓。 */
    public boolean spinning() {
        return phase == Phase.SPINNING;
    }

    /** 冷却好了吗 ✓。 */
    protected boolean ready(ServerLevel level) {
        return level.getGameTime() >= nextSpinAllowedAt;
    }

    // ------------------------------------------------------------------
    //  同步与存档
    // ------------------------------------------------------------------

    /**
     * 状态跳变时才调 ✓（开局 / 出结果 / 复位各一次 ✗ —— 不是每 tick ✗）。
     *
     * <p>两条路都要走通 ✗：
     * <ul>
     *   <li>{@code spinning} 真的变了 ⇒ {@code setBlock} 会顺手把新状态广播出去 ✓，
     *       但<b>方块实体数据不会跟着发</b> ✗ ⇒ 必须再补一次
     *       {@link ServerLevel#sendBlockUpdated} ✓，否则客户端只拿到"转/不转"、拿不到结果下标 ✓；</li>
     *   <li>{@code spinning} 没变（例如"出结果 → 复位"这两步都是 false ✓）⇒
     *       客户端不重读 {@code getUpdateTag()}，结果灯就会一直亮着不灭 ✗
     *       —— 这也是为什么这一支也必须显式发一次 ✓。</li>
     * </ul>
     */
    protected void sync(ServerLevel level, BlockPos pos, BlockState state, boolean spinning) {
        BlockState target = state;
        if (state.hasProperty(TNCasinoMachineBlock.SPINNING)
                && state.getValue(TNCasinoMachineBlock.SPINNING) != spinning) {
            target = state.setValue(TNCasinoMachineBlock.SPINNING, spinning);
            level.setBlock(pos, target, Block.UPDATE_ALL);
        }
        // ★ 无论走哪一支都要补这一发：方块实体数据（Phase / Result / StartedAt）靠它过河 ✗
        level.sendBlockUpdated(pos, target, target, Block.UPDATE_CLIENTS);
    }

    /**
     * 开局：告诉客户端"从哪一 tick 开始转"✓。
     *
     * <p>只发这三个字段就够 ✓：{@code startedAt} 是<b>服务端</b>的游戏时间 ✗，
     * 而客户端<b>自己也有</b>游戏时间（{@code ClientLevel.getGameTime()} 由原版的
     * {@code ClientboundSetTimePacket} 持续同步 ✓），两者同一条时间轴 ✓
     * ⇒ 客户端算 {@code 客户端游戏时间 − startedAt} 就能得到"已经转了多久" ✓。
     * 所以<b>不需要</b>每 tick 发包 ✗，也不需要在这条 tag 里额外塞时间基准 ✓。
     */
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Phase", phase.name());
        tag.putInt("Spins", spins);
        tag.putIntArray("Result", result);
        tag.putLong("StartedAt", startedAt);
        tag.putLong("SettledAt", settledAt);
        tag.putInt("SelectedBet", selectedBet);
        tag.putInt("LastPayout", lastPayout);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet) {
        // 1.20.1 的签名是 (Connection, packet) 两参 ✓；NBT 可能就是 null，要挡住 ✗。
        CompoundTag tag = packet.getTag();
        if (tag != null) load(tag);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Phase", phase.name());
        tag.putInt("Spins", spins);
        tag.putIntArray("Result", result);
        tag.putLong("StartedAt", startedAt);
        tag.putLong("SettledAt", settledAt);
        tag.putInt("SelectedBet", selectedBet);
        tag.putInt("LastPayout", lastPayout);
        tag.putLong("NextSpinAllowedAt", nextSpinAllowedAt);
        tag.putInt("Owed", owed);
        tag.putInt("Stake", stake);
        if (owner != null) tag.putUUID("Owner", owner);
        if (bettor != null) tag.putUUID("Bettor", bettor);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        phase = readPhase(tag.getString("Phase"));
        int[] saved = tag.getIntArray("Result");
        for (int slot = 0; slot < RESULT_SLOTS; slot++) {
            result[slot] = sanitizeResult(slot, slot < saved.length ? saved[slot] : 0);
        }
        startedAt = tag.getLong("StartedAt");
        settledAt = tag.contains("SettledAt") ? tag.getLong("SettledAt") : startedAt;
        selectedBet = Math.floorMod(tag.getInt("SelectedBet"), betNames().length);
        lastPayout = Math.max(0, tag.getInt("LastPayout"));
        nextSpinAllowedAt = tag.getLong("NextSpinAllowedAt");
        owed = Math.max(0, tag.getInt("Owed"));
        stake = Math.max(0, Math.min(maxStake(), tag.getInt("Stake")));
        spins = Math.max(0, tag.getInt("Spins"));
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        bettor = tag.hasUUID("Bettor") ? tag.getUUID("Bettor") : null;
    }

    /**
     * 把某个结果位绕回该位自己的合法范围 ✓。
     *
     * <p>⚠️ 必须<b>逐位</b>处理 ✗ —— 不同位的取值范围不一样
     * （转盘桌：第 0 位是 12 个袋，第 1/2 位是 6 个骰子面 ✓）。
     * 存档里可能是别的版本写的数，也可能是负数 ✓，所以每一台机器都要自己实现 ✓。
     *
     * @param slot 结果位下标（0..{@link #RESULT_SLOTS}−1）
     * @param raw  存档里读出来的原始值
     */
    protected abstract int sanitizeResult(int slot, int raw);

    /** 单次最多攒多少币 ✓。 */
    protected abstract int maxStake();

    /** 存档里可能存着别的版本写的名字 ⇒ 认不出就当待机 ✓（别抛异常把区块读崩 ✗）。 */
    private static Phase readPhase(String name) {
        for (Phase candidate : Phase.values()) {
            if (candidate.name().equals(name)) return candidate;
        }
        return Phase.IDLE;
    }

    protected String[] betNames() { return new String[]{"三轮连线"}; }

    public String betName() { return betNames()[selectedBet]; }
    public int selectedBet() { return selectedBet; }
    public int lastPayout() { return lastPayout; }

    /** Shared physical-coin interaction. A payout belongs to the player who paid. */
    protected boolean prepareRound(net.minecraft.server.level.ServerPlayer player) {
        if (!(level instanceof ServerLevel server)) return false;
        if (spinning()) { tell(player, "设备正在运行，请等结果。"); return false; }
        if (owed > 0) {
            if (bettor != null && !bettor.equals(player.getUUID())) {
                tell(player, "本局奖金由投币的玩家领取。"); return false;
            }
            // Inventory.add can partially insert. Keep only the actual remainder as debt.
            net.minecraft.world.item.ItemStack coins = new net.minecraft.world.item.ItemStack(
                    com.tnc.tnc.TNMod.COPPER_COIN.get(), owed);
            player.getInventory().add(coins);
            int received = owed - coins.getCount();
            owed = coins.getCount();
            player.getInventory().setChanged();
            setChanged();
            sync(server, worldPosition, getBlockState(), false);
            tell(player, "领取 " + received + " 枚铜币" + (owed > 0 ? "，仍有 " + owed + " 枚待领。" : "。"));
            return false;
        }
        if (!ready(server)) { tell(player, "设备正在复位。"); return false; }
        if (player.isShiftKeyDown()) {
            selectedBet = (selectedBet + 1) % betNames().length;
            setChanged();
            sync(server, worldPosition, getBlockState(), false);
            tell(player, "投注：" + betName() + "。手持铜币右键开局，每局 1 枚。");
            return false;
        }
        net.minecraft.world.item.ItemStack held = player.getMainHandItem();
        if (!held.is(com.tnc.tnc.TNMod.COPPER_COIN.get())) {
            tell(player, "投注：" + betName() + "；潜行右键切换，手持铜币右键投入 1 枚，结果后右键领奖。");
            return false;
        }
        held.shrink(1);
        player.getInventory().setChanged();
        bettor = player.getUUID();
        stake = 1;
        lastPayout = 0;
        return true;
    }

    protected static void tell(net.minecraft.server.level.ServerPlayer player, String text) {
        player.displayClientMessage(net.minecraft.network.chat.Component.literal(text), true);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return getLevel()==null?new net.minecraft.world.phys.AABB(worldPosition).inflate(9):
                getBlockState().getShape(getLevel(),worldPosition).bounds().move(worldPosition).inflate(.5);
    }

    public void claim(java.util.UUID player) {
        owner = player;
        setChanged();
    }

    // ------------------------------------------------------------------
    //  给渲染器读的只读访问器
    // ------------------------------------------------------------------

    public Phase phase() { return phase; }

    public int result(int slot) { return result[Math.floorMod(slot, RESULT_SLOTS)]; }

    public long startedAt() { return startedAt; }

    public int owed() { return owed; }

    public int stake() { return stake; }

    public int spins() { return spins; }

    public java.util.UUID owner() { return owner; }
}
