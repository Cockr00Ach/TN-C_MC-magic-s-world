package com.tnc.tnc.casino.machine;

import com.tnc.tnc.TNMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 赌场机器的方块 / 方块实体 / 物品注册 ✓。
 *
 * <h2>为什么用 {@code RegisterEvent} 手工注册，而不是 {@code DeferredRegister}</h2>
 * {@code TNMod} 里只有 {@code BLOCKS} / {@code ITEMS} / {@code MENUS} 三个 {@code DeferredRegister} ✗，
 * <b>没有</b> {@code BLOCK_ENTITIES} ✓；再开一个要动主类，而本工程已有现成范式 ✓
 * （{@code life/pasture/PastureRegistry} ＋ {@code life/botanical/BotanicalContent} 都这么注册方块实体 ✓）。
 * 跟着现成范式走，少一处"编译过了但运行时静默不生效"的坑 ✗。
 *
 * <h2>⚠️ 注册顺序是刻意的：方块 → 方块实体类型 → 物品</h2>
 * {@code BlockEntityType.Builder.of(...)} 必须拿到<b>已经建好的方块实例</b> ✗，
 * 而方块是在 {@code BLOCK} 事件里才 new 出来的 ✓。反过来的话传进去是<b>空数组</b>
 * ⇒ 方块实体类型不认识任何方块 ⇒ 机器<b>不动、右键没反应，且一条错误都不报</b> ✗。
 * {@code RegisterEvent} 对同一注册表按挂载顺序触发 ✓，所以先后靠书写顺序保证 ✓。
 *
 * <p>⚠️ 注意：<b>每台机器一个自己的方块实体类型</b> ✓（不是共用一个 ✗）——
 * 方块实体类型决定反序列化时造哪个类 ✓，共用会导致转盘桌读出来变成老虎机 ✓。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TNCasinoRegistry {

    private TNCasinoRegistry() {
    }

    public static BlockEntityType<TNCasinoGameEntity> WHEEL_ENTITY, CARD_ENTITY;
    public static final String LUCKY_WHEEL = "lucky_wheel", CARD_TABLE = "card_table";

    public static final String SLOT_MACHINE = "slot_machine";
    public static final String ROULETTE_TABLE = "roulette_table";
    /**
     * 赌博桌的方块 id 仍是 {@code tnc:gamble_table} ✓，但它<b>归 TNMod 的 DeferredRegister 注册</b> ✗
     * —— 因为那个 id 早就在存档和物品栏里了，搬动注册器会有改名的风险 ✓。
     * 这里只补它缺的那一半：<b>方块实体类型</b> ✓（自绘渲染器必须绑一个 ✗）。
     */
    public static final String GAMBLE_TABLE = "gamble_table";

    /** 方块实体类型：<b>一台机器一个</b> ✓（见类注释）。 */
    public static BlockEntityType<TNSlotMachineEntity> SLOT_ENTITY;
    public static BlockEntityType<TNRouletteTableEntity> ROULETTE_ENTITY;
    public static BlockEntityType<TNGambleTableEntity> GAMBLE_ENTITY;

    /** 注册出来的方块与物品 ✓。 */
    public static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    public static final Map<String, Item> ITEMS = new LinkedHashMap<>();

    public static Block block(String name) {
        Block block = BLOCKS.get(name);
        if (block == null) throw new IllegalArgumentException("Unknown casino block: " + name);
        return block;
    }

    public static Item item(String name) {
        Item item = ITEMS.get(name);
        if (item == null) throw new IllegalArgumentException("Unknown casino item: " + name);
        return item;
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        // ⚠️ 2026-10-10 修（对面会话的赌场批次挡路 bug ✗）：{@code RegisterHelper.register(String, T)}
        // 把那个字符串当成**路径** ✗（命名空间取模组 id ✓），所以传 {@code "tnc:slot_machine"} 会变成
        // {@code tnc:tnc:slot_machine} ⇒ {@code ResourceLocationException} ⇒ **整个模组在
        // common_setup 阶段就崩**✗（GameTest 服务器连启动都做不到 ✗）。
        // 本工程其余注册器（{@code OriginalCrops} / {@code MagicForgeContent} …）一律传 ResourceLocation ✓，
        // 这里跟着改 ✓。
        // ① 方块
        event.register(Registries.BLOCK, helper -> {
            if (!BLOCKS.isEmpty()) return;
            BLOCKS.put(SLOT_MACHINE, new TNSlotMachineBlock());
            BLOCKS.put(ROULETTE_TABLE, new TNRouletteTableBlock());
            BLOCKS.put(LUCKY_WHEEL, new TNCasinoMachineBlock<>((pos, state) ->
                    new TNCasinoGameEntity(WHEEL_ENTITY, pos, state, TNCasinoGameEntity.Game.WHEEL), 4, 4.8, 1.8666667));
            BLOCKS.put(CARD_TABLE, new TNCasinoMachineBlock<>((pos, state) ->
                    new TNCasinoGameEntity(CARD_ENTITY, pos, state, TNCasinoGameEntity.Game.CARDS), 8, 1.15, 4));
            BLOCKS.forEach((name, block) -> helper.register(id(name), block));
        });

        // ② 方块实体类型（此时 BLOCKS 一定有值 ✓；每个类型只认自己的那个方块 ✓）
        event.register(Registries.BLOCK_ENTITY_TYPE, helper -> {
            if (WHEEL_ENTITY == null) {
                WHEEL_ENTITY = BlockEntityType.Builder.of((pos, state) -> new TNCasinoGameEntity(
                        WHEEL_ENTITY, pos, state, TNCasinoGameEntity.Game.WHEEL), block(LUCKY_WHEEL)).build(null);
                helper.register(id(LUCKY_WHEEL), WHEEL_ENTITY);
            }
            if (CARD_ENTITY == null) {
                CARD_ENTITY = BlockEntityType.Builder.of((pos, state) -> new TNCasinoGameEntity(
                        CARD_ENTITY, pos, state, TNCasinoGameEntity.Game.CARDS), block(CARD_TABLE)).build(null);
                helper.register(id(CARD_TABLE), CARD_ENTITY);
            }
            if (SLOT_ENTITY == null) {
                SLOT_ENTITY = BlockEntityType.Builder
                        .of(TNSlotMachineEntity::new, block(SLOT_MACHINE))
                        .build(null);
                helper.register(id(SLOT_MACHINE), SLOT_ENTITY);
            }
            if (ROULETTE_ENTITY == null) {
                ROULETTE_ENTITY = BlockEntityType.Builder
                        .of(TNRouletteTableEntity::new, block(ROULETTE_TABLE))
                        .build(null);
                helper.register(id(ROULETTE_TABLE), ROULETTE_ENTITY);
            }
            // 赌博桌：方块本身归 TNMod 注册 ✗，这里只补方块实体类型 ✓。
            // ⚠️ 直接读 TNMod.GAMBLE_TABLE.get() —— 是同一个方块实例 ✓，
            //    确认方法就是 BlockEntityType.isValid / getBlockEntity：它按"方块实例"比对 ✓。
            if (GAMBLE_ENTITY == null) {
                GAMBLE_ENTITY = BlockEntityType.Builder
                        .of(TNGambleTableEntity::new, TNMod.GAMBLE_TABLE.get())
                        .build(null);
                // 实体类型的 id 不能和方块重名 ✗ —— 它和方块是两个不同的注册表 ✓，
                // 但为了日志/存档里一眼能分辨，这里加个 _entity 后缀 ✓。
                helper.register(id(GAMBLE_TABLE + "_entity"), GAMBLE_ENTITY);
            }
        });

        // ③ 物品（方块物品 ✓，stacksTo(1) —— 机器是家具，不该堆叠 ✗）
        event.register(Registries.ITEM, helper -> {
            if (!ITEMS.isEmpty()) return;
            BLOCKS.forEach((name, block) ->
                    ITEMS.put(name, new BlockItem(block, new Item.Properties().stacksTo(1))));
            ITEMS.forEach((name, item) -> helper.register(id(name), item));
        });
    }

    private static net.minecraft.resources.ResourceLocation id(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path);
    }

    /** 进 TN-C 创造页 ✓（作者要能在创造里直接拿出来验收 ✓）。 */
    @SubscribeEvent
    public static void creative(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(TNMod.TNC_TAB.getKey())) {
            ITEMS.values().forEach(event::accept);
        }
    }
}
