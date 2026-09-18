package com.tnc.tnc.magic.compat;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.magic.SpellCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 和 SpellEngine 的桥 —— <b>法杖</b>这一条路。
 *
 * <h2>为什么能这么写</h2>
 * SpellEngine 本身是 <b>Fabric mod</b>，原版 jar 里是 intermediary 名字，Forge 侧编译不了。
 * 但整合包 `mods\.connector\` 里有 Sinytra Connector <b>重映射过</b>的版本
 * （`spell_engine-..._mapped_srg_1.20.1.jar`），签名已经是正常的 MC 类名 ——
 * 所以我们把它放进 `libs\` 用 `compileOnly` 编译，运行时由整合包提供。
 *
 * <h2>设计：魔法石是权威，法杖只是"派生道具"</h2>
 * 早期版本是"解锁 → 往法术书里写"，那条路废弃了（2026-09-15 用户拍板），因为：
 * <ul>
 *   <li>那本书是<b>作者的</b>物品，池子里有他自己 5 个法术 → 玩家随手按到别人的法术、
 *       TN-C 不扣魔力、看起来像坏了；</li>
 *   <li>书会丢、会少写、槽位和"第几个已解锁法术"对不上 —— 全是白白多出来的一层。</li>
 * </ul>
 * 现在：<b>手上有法杖，按数字键就放已解锁的法术</b>。法杖的内容由魔法石里的"已解锁集合"
 * 单向同步（{@link #ensureWand}），玩家不需要手动绑定任何东西。
 * 法杖认的是<b>我们自己的法术池</b> {@link #WAND_POOL}，所以槽位顺序 = 目录里等级的顺序。
 *
 * <h2>为什么要分内外两层</h2>
 * dev 环境（比如 `runServer`）里没有 SpellEngine。直接引用它的类会让类加载失败，
 * 所以：{@link #enginePresent()} 先判断，<b>所有真正碰引擎类的代码都塞在 {@link Impl} 里</b> ——
 * 只有引擎存在时那些指令才会被执行到，JVM 也就不会去解析那些类。
 */
public final class SpellEngineBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String ENGINE_MOD_ID = "spell_engine";

    /**
     * 法杖物品（TN-C 自己的）。
     *
     * <p>注册在 {@code TNMod.WAND}，名字见语言文件。
     */
    public static final ResourceLocation WAND =
            ResourceLocation.fromNamespaceAndPath("tnc", "magic_wand");

    /**
     * 法杖认的法术池 —— <b>我们自己的池</b>，只含 tnc 的 5 个雷法。
     *
     * <p>数据文件：`src/main/resources/data/tnc/spell_pools/tnc_lightning.json`。
     * 用它而不是作者的池，是为了让法杖槽位里<b>只有我们的法术</b>，
     * 槽位号 = 已解锁法术按等级排序后的序号。
     */
    public static final ResourceLocation WAND_POOL =
            ResourceLocation.fromNamespaceAndPath("tnc", "tnc_lightning");

    /** 法杖最多装几个法术（够放 5 个雷法）。 */
    private static final int WAND_CAPACITY = 8;

    /** 每个法术的冷却时间（秒）在法术 json 里，这里不碰。 */

    /**
     * 老方案的遗留：作者的雷系法术书。
     *
     * <p><b>已经不用了</b>（用户决定抛弃法术书）。留着常量只是为了排查历史问题时能对上号。
     */
    @Deprecated
    public static final ResourceLocation LIGHTNING_BOOK =
            ResourceLocation.fromNamespaceAndPath("ysjx_weapons", "lightning_spell_book");

    public enum WandResult {
        /** 一个法术都没解锁 —— 什么都不做（也不白发法杖）。 */
        NOTHING_LEARNED,
        /** 身上那根法杖已经和魔法石一致，没动。 */
        ALREADY_COMPLETE,
        /** 法杖在但内容对不上魔法石，已重写。 */
        UPDATED,
        /** 身上没有法杖，补发了一根（含全部已解锁法术）。 */
        GAVE_NEW_WAND,
        /** 连法杖物品都找不到（注册失败？）。 */
        NO_WAND_ITEM,
        /** 出错了，见日志。 */
        FAILED,
        /** 没有装引擎。 */
        NO_ENGINE
    }

    private SpellEngineBridge() {
    }

    public static boolean enginePresent() {
        return ModList.get().isLoaded(ENGINE_MOD_ID);
    }

    /**
     * 造一根法杖。
     *
     * <h2>为什么是<b>普通 Item</b>，而不是引擎的 {@code SpellBookVanillaItem}</h2>
     * 这里踩过一个大坑，读 {@code client.input.SpellHotbar.update()} 的字节码才看清：
     *
     * <pre>
     *   74: stack.getItem()
     *   77: instanceof net/spell_engine/api/item/trinket/SpellBookItem
     *   80: ifne 516        ← 是 SpellBookItem 就"跳过"下面整段
     *   85: ifnull 516      ← 没有容器也跳过
     *   88: getfield SpellContainer.spell_ids    ← 只有"不是 SpellBookItem 但有容器"才读这里
     * </pre>
     *
     * {@code ifne} 是"成立就跳走"，所以快捷栏有<b>两条互斥</b>的路：
     * <ul>
     *   <li>物品<b>是</b> {@code SpellBookItem} → 走另一条（要靠池 / {@code SpellBooks} 登记）；</li>
     *   <li>物品<b>不是</b> {@code SpellBookItem}，但有 {@code spell_assignments} 给的容器
     *       → <b>直接读容器的 spell_ids</b> —— 这条最简单、最可控。</li>
     * </ul>
     * 作者的杖 {@code extends StaffItem}（<b>不是</b> SpellBookItem）+ 自带 spell_assignments，
     * 走的正是第二条，所以它们一直好用。我们一开始把法杖做成 SpellBookItem，正好被 {@code ifne} 跳掉，
     * 于是"不显示数字键"。
     *
     * <p>顺带的好处：普通 Item 的构造函数不碰任何引擎类 ——
     * 没装引擎的环境（dev / 别的包）也能正常注册，不需要再做嵌套类隔离。
     */
    public static Item createWandItem() {
        return new Item(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON));
    }

    /**
     * 把玩家身上的法杖内容对齐到"魔法石里已解锁的法术"。
     *
     * <p>登录、解锁之后、以及 {@code /tnc wand} 都会调它。幂等：已经一致就什么都不写。
     *
     * @param learned 玩家已解锁的法术（来自魔法石数据，是权威）
     */
    public static WandResult ensureWand(Player player, Collection<ResourceLocation> learned) {
        if (!enginePresent()) {
            return WandResult.NO_ENGINE;
        }
        return Impl.ensureWand(player, learned);
    }

    /**
     * 引擎认识这个法术吗？（防呆闸门用）
     *
     * <p>目录里可以存在"还没写 JSON 的骨架"法术，但那种法术**学得到却放不出** ✗，
     * 法杖里还会多一个空槽 ✗（用户实测遇到过）。所以学之前先问这一句。
     *
     * <p>没装引擎时返回 {@code true}（dev 环境不该因为缺引擎就什么都学不了）。
     */
    public static boolean hasSpell(ResourceLocation spellId) {
        if (!enginePresent()) {
            return true;
        }
        return Impl.hasSpell(spellId);
    }

    /** 给玩家看的一句话说明。 */
    public static String describeWand(WandResult result, int learnedCount) {        return switch (result) {
            case NOTHING_LEARNED -> "还没解锁任何法术，先不用拿法杖";
            case ALREADY_COMPLETE -> "法杖已经是最新的（" + learnedCount + " 个已解锁法术都在里面）";
            case UPDATED -> "法杖内容已同步（" + learnedCount + " 个已解锁法术）";
            case GAVE_NEW_WAND -> "没找到法杖，补发了一根（含 " + learnedCount + " 个已解锁法术）";
            case NO_WAND_ITEM -> "找不到法杖物品，补不了（注册出问题了？）";
            case FAILED -> "同步法杖时出错，详见日志";
            case NO_ENGINE -> "没有装 SpellEngine，跳过";
        };
    }

    // ------------------------------------------------------------------
    //  真正碰 SpellEngine 类的部分
    // ------------------------------------------------------------------

    private static final class Impl {

        static WandResult ensureWand(Player player, Collection<ResourceLocation> learned) {
            try {
                Item wandItem = ForgeRegistries.ITEMS.getValue(WAND);
                if (wandItem == null) {
                    return WandResult.NO_WAND_ITEM;
                }
                // 按目录（= 等级）排序，让槽位顺序稳定可预期
                List<String> wanted = orderedSpellIds(learned);

                int found = 0;
                int rewritten = 0;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    if (stack.isEmpty() || !stack.is(wandItem)) {
                        continue;
                    }
                    found++;
                    if (writeIfDifferent(stack, wanted)) {
                        rewritten++;
                    }
                }

                if (found == 0) {
                    if (wanted.isEmpty()) {
                        return WandResult.NOTHING_LEARNED;
                    }
                    ItemStack wand = new ItemStack(wandItem);
                    writeContainer(wand, wanted);
                    if (!player.getInventory().add(wand)) {
                        player.drop(wand, false);
                    }
                    LOGGER.info("TN-C: gave a new magic wand to {} ({} spells)",
                            player.getName().getString(), wanted.size());
                    return WandResult.GAVE_NEW_WAND;
                }
                if (rewritten == 0) {
                    return WandResult.ALREADY_COMPLETE;
                }
                // 就地改 ItemStack 的 NBT 不一定让客户端收到。
                // 快捷栏跑在客户端，读的是客户端那份物品 —— 所以这里强制同步一次背包栏位，
                // 否则"服务端明明写好了、客户端还是看不到法术"。
                player.inventoryMenu.broadcastChanges();
                LOGGER.info("TN-C: synced {} magic wand(s) of {} to {} spell(s)",
                        rewritten, player.getName().getString(), wanted.size());
                return WandResult.UPDATED;
            } catch (Throwable error) {
                LOGGER.warn("TN-C: failed to sync the magic wand of {}", player.getName().getString(), error);
                return WandResult.FAILED;
            }
        }

        /** 已解锁法术 → 按目录等级排序的 id 字符串。不在目录里的忽略（法杖池也装不下）。 */
        private static List<String> orderedSpellIds(Collection<ResourceLocation> learned) {
            List<SpellCatalog.Entry> entries = new ArrayList<>();
            for (SpellCatalog.Entry entry : SpellCatalog.all()) {
                if (learned.contains(entry.id())) {
                    entries.add(entry);
                }
            }
            entries.sort((a, b) -> Integer.compare(a.tier(), b.tier()));
            List<String> ids = new ArrayList<>(entries.size());
            for (SpellCatalog.Entry entry : entries) {
                // ⚠️ 防呆闸门：只把"引擎真的认识"的法术写进法杖。
                // 目录里有、引擎里没有的法术（比如还没写 JSON 的骨架）会让槽位空白 ✗
                // —— 用户实测遇到过（风系当时目录 15 条、JSON 只有 5 个）。
                // 这里直接过滤掉，法杖就永远不会出现"学得到但放不出"的空格。
                if (net.spell_engine.internals.SpellRegistry.getSpell(entry.id()) == null) {
                    continue;
                }
                ids.add(entry.id().toString());
            }
            return ids;
        }

        /** 引擎的法术注册表里有没有这个 id（null = 没有）。 */
        static boolean hasSpell(ResourceLocation spellId) {
            try {
                return net.spell_engine.internals.SpellRegistry.getSpell(spellId) != null;
            } catch (Throwable t) {
                return true;                     // 出错时放行，别把玩家卡死
            }
        }

        /** 只在这个物品上写 container。 */
        private static void writeContainer(ItemStack stack, List<String> spellIds) {
            // is_proxy = false：这样容器的 spell_ids 就是权威（快捷栏直接读它），
            // 槽位顺序 = 已解锁法术按等级排序 —— "魔法石驱动法杖"就落在这里。
            net.spell_engine.api.spell.SpellContainer container =
                    new net.spell_engine.api.spell.SpellContainer(
                            net.spell_engine.api.spell.SpellContainer.ContentType.MAGIC,
                            false,
                            WAND_POOL.toString(),
                            WAND_CAPACITY,
                            new ArrayList<>(spellIds));
            net.spell_engine.internals.SpellContainerHelper.addContainerToItemStack(container, stack);
        }

        /**
         * 法杖内容已经和魔法石一致了吗？不一致才重写。
         *
         * <p>为什么要在意"一致就不写"：这个方法是登录/解锁时都调的，
         * 每次都无脑重写会平白改动物品 NBT（还会让"物品被改过"的判断失效）。
         */
        private static boolean writeIfDifferent(ItemStack stack, List<String> wanted) {
            net.spell_engine.api.spell.SpellContainer current =
                    net.spell_engine.internals.SpellContainerHelper.containerFromItemStack(stack);
            if (current != null
                    && current.spell_ids != null
                    && WAND_POOL.toString().equals(current.pool)
                    && new ArrayList<>(current.spell_ids).equals(wanted)) {
                return false;
            }
            writeContainer(stack, wanted);
            return true;
        }
    }
}
