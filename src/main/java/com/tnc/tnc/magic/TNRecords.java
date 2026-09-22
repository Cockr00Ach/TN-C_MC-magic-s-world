package com.tnc.tnc.magic;

import com.tnc.tnc.TNMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 「一周目正史」五张记录纸 —— <b>前情 1 张 ＋ 主线四段 4 张</b>（作者 2026-09-22 要求：
 * "类似残卷那样，记录主线四场的主要剧情和前情"）。
 *
 * <p>和 {@link TNScrolls 残卷} 共用同一套机制（{@link TNScrollItem} 右键读 + 原版书本界面），
 * 区别只在<b>外观与内容</b>：
 * <ul>
 *   <li>残卷 = 一张撕破的残页（碎片感，六卷同一张）；</li>
 *   <li>正史 = 两张纸＋火漆印的"文书"（{@code textures/item/zhengshi.png}）——
 *       一眼能和残卷区分开 ✓。</li>
 * </ul>
 *
 * <p><b>正文的唯一来源</b>：{@code 剧情\主线概要.md}（**编剧系统 AAA** 维护）——
 * 由 {@code tools/gen_records_lang.ps1} 生成 lang 键 {@code scroll.tnc.zhengshi_*}，
 * 一并生成物品名 {@code item.tnc.zhengshi_*} ✓。改文案不用碰 Java ✓：
 * 改 md → 跑脚本 → 重新编译装机。
 *
 * <p>⚠️ 该文档里有几句是写给制作组看的（"见正文"、"最终 boss"这类元注释），
 * 生成时会**按名单剥掉**并在输出里列出来 ✓（名单在 {@code tools\records_lang_extra.json}）。
 */
public final class TNRecords {

    private static final DeferredRegister<Item> RECORDS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TNMod.MODID);

    /** 前情（动画那一段）：吴归衡与公孙衍分道扬镳。 */
    public static final RegistryObject<Item> QIANQING = RECORDS.register("zhengshi_qianqing",
            () -> new TNScrollItem("zhengshi_qianqing", Rarity.UNCOMMON));
    /** 一 · 分离之后 */
    public static final RegistryObject<Item> PART_1 = RECORDS.register("zhengshi_1",
            () -> new TNScrollItem("zhengshi_1", Rarity.RARE));
    /** 二 · 黑暗潮 */
    public static final RegistryObject<Item> PART_2 = RECORDS.register("zhengshi_2",
            () -> new TNScrollItem("zhengshi_2", Rarity.RARE));
    /** 三 · 黑暗深处 */
    public static final RegistryObject<Item> PART_3 = RECORDS.register("zhengshi_3",
            () -> new TNScrollItem("zhengshi_3", Rarity.EPIC));
    /** 四 · 挥戈 */
    public static final RegistryObject<Item> PART_4 = RECORDS.register("zhengshi_4",
            () -> new TNScrollItem("zhengshi_4", Rarity.EPIC));

    static {
        // 注意：**不能**把注册放进静态块 ✗（TNMod 构造函数会显式调用 register()，
        // 原因见 TNScrolls 的说明：Java 类懒加载会让静态块根本不跑）
    }

    private TNRecords() {
    }

    /**
     * 挂到 mod 事件总线 —— 由 {@code TNMod} 构造函数在**注册事件之前**调用一次 ✓。
     */
    public static void register(IEventBus bus) {
        RECORDS.register(bus);
    }
}
