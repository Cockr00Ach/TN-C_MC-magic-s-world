package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 「宝箱传说残卷」六卷 —— 剧情道具（作者 2026-09-21 指定）。
 *
 * <p>内容来自 {@code 剧情/宝箱传说残卷.md}：上古一部残卷散成五片（六卷文本），
 * 落进各地箱子里；每则暗中暗示对应角色的结局。卷一按作者指定拆成<b>上下卷</b>。
 *
 * <pre>
 *   卷一（上）· 挥戈      卷一（下）· 归      卷三 · 衍
 *   卷四 · 垣             卷五 · 代           卷六 · 坐望
 * </pre>
 *
 * <p>道具本身只是"可读的残卷"（暂时没有任何右键功能 ✗）——
 * 后续要做的事写在 {@code docs/法术总表_按设计文档.md} 之外的剧情文档里；
 * 掉落（宝箱）与阅读界面属于**任务系统 / 文书专题**的范畴 ✓，
 * 这里只负责**把道具注册进游戏** ✓（模型 + 贴图 + 名字）。
 *
 * <p>模型用原版的 {@code item/generated}（平面物品 ✓ —— 残卷就是一张纸 ✓），
 * 贴图放在 {@code assets/tnc/textures/item/<id>.png}。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TNScrolls {

    private static final DeferredRegister<Item> SCROLLS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TNMod.MODID);

    /** 卷一（上）· 挥戈 */
    public static final RegistryObject<Item> JUAN_1A = SCROLLS.register("canjuan_1a",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    /** 卷一（下）· 归 */
    public static final RegistryObject<Item> JUAN_1B = SCROLLS.register("canjuan_1b",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    /** 卷三 · 衍 */
    public static final RegistryObject<Item> JUAN_3 = SCROLLS.register("canjuan_3",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    /** 卷四 · 垣 */
    public static final RegistryObject<Item> JUAN_4 = SCROLLS.register("canjuan_4",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    /** 卷五 · 代 */
    public static final RegistryObject<Item> JUAN_5 = SCROLLS.register("canjuan_5",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    /** 卷六 · 坐望 */
    public static final RegistryObject<Item> JUAN_6 = SCROLLS.register("canjuan_6",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    /**
     * 把注册器挂到 mod 事件总线上，由 {@code TNMod} 构造函数在**注册事件之前**调用一次 ✓。
     *
     * <p>注意这里<b>不能</b>写成静态初始化块：Java 的类加载是懒的 ✗，
     * 如果没人提前碰过本类的静态字段，静态块就不会跑，
     * 六个残卷会静默消失（jar 里有模型有贴图，游戏里就是没有 ✗）。
     * 显式调用保证顺序 ✓。
     */
    public static void register(IEventBus bus) {
        SCROLLS.register(bus);
    }

    private TNScrolls() {
    }
}
