package com.tnc.tnc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.tnc.tnc.Config;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.Element;
import com.tnc.tnc.magic.MagicStone;
import com.tnc.tnc.magic.MagicStoneData;
import com.tnc.tnc.magic.MagicStoneLearning;
import com.tnc.tnc.magic.MagicStoneSelfTest;
import com.tnc.tnc.magic.ManaGate;
import com.tnc.tnc.magic.SpellCatalog;
import com.tnc.tnc.magic.compat.ManaGateProbe;
import com.tnc.tnc.magic.compat.SpellEngineBridge;
import com.tnc.tnc.network.MagicStoneNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;
import java.util.List;

/**
 * 魔法石调试命令（GUI 还没做之前的唯一操作入口）。
 *
 * <pre>
 *   /tnc magic                                    查看自己的魔法石数据
 *   /tnc mana set &lt;值&gt; | add &lt;值&gt; | full          改魔力值
 *   /tnc affinity set &lt;元素&gt; &lt;1-6&gt; | roll [值]      改亲和力（roll 会重掷全部）
 *   /tnc progress set &lt;元素&gt; &lt;0-5&gt;                 改某元素的学习进度
 *   /tnc learn &lt;法术 id&gt; | forget &lt;法术 id&gt;        记下/忘掉一个法术
 *   /tnc cast &lt;法术 id&gt;                           直接放一个法术（不依赖法杖和键位）
 *   /tnc wand                                      同步/补发法杖（内容来自魔法石记录）
 *   /tnc points add &lt;n&gt; | set &lt;n&gt;                  赠送魔法点数（调试用）
 *   /tnc reset                                     清空整个魔法石（回到未初始化）
 *   /tnc selftest                                  数值/门槛/NBT 自检
 *   /tnc engine                                    引擎接线状态（引擎在不在、Mixin 加载没加载）
 *   /tnc gatetest [法术 id]                        硬拦截实测（没魔力到底拦不拦得住）
 * </pre>
 *
 * 需要权限等级 2（单人开作弊 / 服务器 OP）。
 *
 * <h2>⚠️ 带命名空间的 id 参数必须用 ResourceLocationArgument.id()</h2>
 * 这里踩过一次坑：一开始用 {@code StringArgumentType.string()}，
 * 结果 {@code /tnc learn tnc:spark} 直接报
 * 「参数后应有空格分隔，但发现了紧邻的数据」。
 * <p>原因：Brigadier 读<b>不带引号</b>的字符串时只接受
 * {@code [0-9a-zA-Z_.+-]}，<b>{@code :} 不在允许集合里</b> ——
 * 于是读到 {@code tnc} 就停下，剩下的 {@code :spark} 成了尾部多余数据。
 * <p>改用 {@link ResourceLocationArgument#id()} 后 {@code :} 由 {@code ResourceLocation.read} 正确处理，
 * 顺带还能给法术 id 做 Tab 补全。
 * （临时绕过办法：加引号，{@code /tnc learn "tnc:spark"} —— 引号内的 {@code :} 是允许的。）
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public class MagicStoneCommand {

    private static final List<String> ELEMENT_IDS =
            Arrays.stream(Element.values()).map(Element::id).toList();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("tnc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("magic")
                        .executes(MagicStoneCommand::show))
                .then(Commands.literal("mana")
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> manaSet(ctx, IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("value", IntegerArgumentType.integer())
                                        .executes(ctx -> manaAdd(ctx, IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("full")
                                .executes(MagicStoneCommand::manaFull)))
                .then(Commands.literal("affinity")
                        .then(Commands.literal("set")
                                .then(Commands.argument("element", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ELEMENT_IDS, builder))
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1, 6))
                                                .executes(ctx -> affinitySet(ctx,
                                                        StringArgumentType.getString(ctx, "element"),
                                                        IntegerArgumentType.getInteger(ctx, "value"))))))
                        .then(Commands.literal("roll")
                                .executes(ctx -> affinityRoll(ctx, Config.defaultAffinity))
                                .then(Commands.argument("value", IntegerArgumentType.integer(1, 6))
                                        .executes(ctx -> affinityRoll(ctx, IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(Commands.literal("progress")
                        .then(Commands.literal("set")
                                .then(Commands.argument("element", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ELEMENT_IDS, builder))
                                        .then(Commands.argument("chain", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(chainIds(), builder))
                                                .then(Commands.argument("tier", IntegerArgumentType.integer(0, 5))
                                                        .executes(ctx -> progressSet(ctx,
                                                                StringArgumentType.getString(ctx, "element"),
                                                                StringArgumentType.getString(ctx, "chain"),
                                                                IntegerArgumentType.getInteger(ctx, "tier"))))))))
                .then(Commands.literal("special")
                        .then(Commands.literal("grant")
                                .then(Commands.argument("element", StringArgumentType.word())
                                        .executes(ctx -> specialSet(ctx,
                                                StringArgumentType.getString(ctx, "element"), true))))
                        .then(Commands.literal("revoke")
                                .then(Commands.argument("element", StringArgumentType.word())
                                        .executes(ctx -> specialSet(ctx,
                                                StringArgumentType.getString(ctx, "element"), false))))
                        .then(Commands.literal("list")
                                .executes(MagicStoneCommand::specialList)))
                .then(Commands.literal("spells")
                        .executes(MagicStoneCommand::spells))
                .then(Commands.literal("learn")
                        .then(Commands.argument("spell", ResourceLocationArgument.id())
                                .suggests(MagicStoneCommand::suggestSpells)
                                .executes(ctx -> learn(ctx, ResourceLocationArgument.getId(ctx, "spell")))))
                .then(Commands.literal("forgetall")
                        .executes(MagicStoneCommand::forgetAll))
                .then(Commands.literal("forget")
                        .then(Commands.argument("spell", ResourceLocationArgument.id())
                                .suggests(MagicStoneCommand::suggestSpells)
                                .executes(ctx -> forget(ctx, ResourceLocationArgument.getId(ctx, "spell")))))
                .then(Commands.literal("reset")
                        .executes(MagicStoneCommand::reset))
                .then(Commands.literal("selftest")
                        .executes(MagicStoneCommand::selfTest))
                .then(Commands.literal("points")
                        .then(Commands.literal("add")
                                .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                        .executes(ctx -> pointsAdd(ctx, IntegerArgumentType.getInteger(ctx, "value")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                        .executes(ctx -> pointsSet(ctx, IntegerArgumentType.getInteger(ctx, "value"))))))
                .then(Commands.literal("cast")
                        .then(Commands.argument("spell", ResourceLocationArgument.id())
                                .suggests(MagicStoneCommand::suggestSpells)
                                .executes(ctx -> cast(ctx, ResourceLocationArgument.getId(ctx, "spell")))))
                .then(Commands.literal("wand")
                        .executes(MagicStoneCommand::wand))
                .then(Commands.literal("engine")
                        .executes(MagicStoneCommand::engineStatus))
                .then(Commands.literal("gatetest")
                        .executes(ctx -> gateTest(ctx, ManaGateProbe.defaultSpell()))
                        .then(Commands.argument("spell", ResourceLocationArgument.id())
                                .suggests(MagicStoneCommand::suggestSpells)
                                .executes(ctx -> gateTest(ctx, ResourceLocationArgument.getId(ctx, "spell"))))));
    }

    // ------------------------------------------------------------------
    //  子命令
    // ------------------------------------------------------------------

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MagicStoneData data = require(player, ctx);
        if (data == null) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§b=== TN-C 魔法石 ==="), false);
        for (String line : data.describe(Config.pointThresholds)) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7" + line), false);
        }
        return 1;
    }

    private static int manaSet(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        return mutate(ctx, (player, data) -> {
            data.setMana(value);
            return "魔力值设为 " + data.getMana() + " / " + data.getMaxMana();
        });
    }

    private static int manaAdd(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        return mutate(ctx, (player, data) -> {
            data.addMana(value);
            return "魔力值 " + data.getMana() + " / " + data.getMaxMana();
        });
    }

    private static int manaFull(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return mutate(ctx, (player, data) -> {
            data.setMana(data.getMaxMana());
            return "魔力值回满：" + data.getMana() + " / " + data.getMaxMana();
        });
    }

    private static int affinitySet(CommandContext<CommandSourceStack> ctx, String elementId, int value)
            throws CommandSyntaxException {
        Element element = Element.byId(elementId);
        if (element == null) {
            ctx.getSource().sendFailure(Component.literal("未知元素：" + elementId + "（可用：" + Element.allIds() + "）"));
            return 0;
        }
        return mutate(ctx, (player, data) -> {
            data.setAffinity(element, value);
            MagicStone.refreshMaxMana(player, data);
            return element.cn() + " 亲和力设为 " + data.getAffinity(element)
                    + "（可学到 " + Element.tierName(data.maxTierFor(element)) + "）";
        });
    }

    private static int affinityRoll(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        return mutate(ctx, (player, data) -> {
            data.assignDefaultAffinities(value);
            MagicStone.refreshMaxMana(player, data);
            return "七元素亲和力全部重设为 " + value + "，魔力上限 " + data.getMaxMana();
        });
    }

    private static int progressSet(CommandContext<CommandSourceStack> ctx, String elementId, String chainId, int tier)
            throws CommandSyntaxException {
        Element element = Element.byId(elementId);
        if (element == null) {
            ctx.getSource().sendFailure(Component.literal("未知元素：" + elementId + "（可用：" + Element.allIds() + "）"));
            return 0;
        }
        com.tnc.tnc.magic.SpellCatalog.Chain chain = chainById(chainId);
        if (chain == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "未知链：" + chainId + "（可用：" + chainIds() + "）"));
            return 0;
        }
        return mutate(ctx, (player, data) -> {
            data.setProgress(element, chain, tier);
            return element.cn() + " · " + chain.cn() + " 链进度设为 " + Element.tierName(tier);
        });
    }

    /** 链 id（= 枚举名小写）→ 枚举。 */
    private static com.tnc.tnc.magic.SpellCatalog.Chain chainById(String id) {
        for (com.tnc.tnc.magic.SpellCatalog.Chain chain : com.tnc.tnc.magic.SpellCatalog.Chain.values()) {
            if (chain.name().equalsIgnoreCase(id) || chain.cn().equals(id)) {
                return chain;
            }
        }
        return null;
    }

    private static List<String> chainIds() {
        List<String> ids = new java.util.ArrayList<>();
        for (com.tnc.tnc.magic.SpellCatalog.Chain chain : com.tnc.tnc.magic.SpellCatalog.Chain.values()) {
            ids.add(chain.name().toLowerCase(java.util.Locale.ROOT));
        }
        return ids;
    }

    /** 列出法术目录和每个法术当前的状态（可解锁 / 已学 / 差什么）。 */
    /** /tnc special grant|revoke <元素> —— 领域魔法（§12.F）：获得路线即获得，不能升级。 */
    private static int specialSet(CommandContext<CommandSourceStack> ctx, String elementId, boolean grant)
            throws CommandSyntaxException {
        Element element = elementById(elementId);
        if (element == null) {
            ctx.getSource().sendFailure(Component.literal("没有这个元素：" + elementId));
            return 0;
        }
        SpellCatalog.Special special = SpellCatalog.specialOf(element);
        String name = special != null ? special.name() : element.cn() + "领域";
        return mutate(ctx, (player, data) -> {
            boolean changed = grant ? data.grantSpecial(element) : data.revokeSpecial(element);
            String verb = grant ? "获得" : "撤销";
            return (changed ? "已" + verb : "本来就" + (grant ? "没有" : "没有")) + "：" + name + "（" + element.cn() + " · 领域魔法）";
        });
    }

    /** /tnc special list */
    private static int specialList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MagicStoneData data = require(player, ctx);
        if (data == null) {
            return 0;
        }
        StringBuilder sb = new StringBuilder("§b=== TN-C 领域魔法 ===§r  ");
        for (SpellCatalog.Special special : SpellCatalog.specials()) {
            boolean has = data.hasSpecial(special.element());
            sb.append(has ? "§a" : "§8").append(special.name())
              .append("§7(").append(special.element().cn()).append(")§r  ");
        }
        final String line = sb.toString();
        ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /** 按 id 找元素（命令参数用）。 */
    private static Element elementById(String id) {
        for (Element element : Element.values()) {
            if (element.id().equalsIgnoreCase(id) || element.name().equalsIgnoreCase(id) || element.cn().equals(id)) {
                return element;
            }
        }
        return null;
    }

    private static int spells(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MagicStoneData data = require(player, ctx);
        if (data == null) {
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§b=== TN-C 法术目录 ==="), false);
        for (SpellCatalog.Entry entry : SpellCatalog.all()) {
            MagicStoneLearning.Result state = MagicStoneLearning.check(data, entry);
            int cost = Config.learnCostForTier(entry.tier());
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7" + entry.fullName() + " §8[" + stateText(state) + " · " + cost + "点]"), false);
        }
        return 1;
    }

    /**
     * 解锁一个法术。判定逻辑和 GUI 完全共用 {@link MagicStoneLearning}，
     * 所以命令这边看到的结果就是界面上会看到的结果。
     */
    private static int learn(CommandContext<CommandSourceStack> ctx, ResourceLocation id) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MagicStoneData data = require(player, ctx);
        if (data == null) {
            return 0;
        }
        SpellCatalog.Entry entry = SpellCatalog.byId(id);
        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "§c法术目录里没有 " + id + " §7（目前只有雷系 5 个，用 /tnc spells 看列表）"));
            return 0;
        }
        MagicStoneLearning.Result result = MagicStoneLearning.unlock(data, entry);
        ctx.getSource().sendSuccess(() -> MagicStoneLearning.describe(result, entry, data), false);
        if (result == MagicStoneLearning.Result.OK || result == MagicStoneLearning.Result.ALREADY_LEARNED) {
            // 解锁后同步法杖内容（已经学过也同步一次 —— 相当于顺手修好丢了内容的法杖）
            // ⚠️ 喂进去的是 effectiveIds：学了高阶就把低阶顶下去（高阶替换低阶）
            java.util.List<ResourceLocation> effective = com.tnc.tnc.magic.SpellCatalog.effectiveIds(data);
            com.tnc.tnc.magic.compat.SpellEngineBridge.WandResult synced =
                    com.tnc.tnc.magic.compat.SpellEngineBridge.ensureWand(player, effective);
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7[TN-C] 法杖：" + com.tnc.tnc.magic.compat.SpellEngineBridge.describeWand(
                            synced, effective.size())), false);
        }
        MagicStone.refreshMaxMana(player, data);
        MagicStoneNetwork.syncTo(player);
        return result == MagicStoneLearning.Result.OK ? 1 : 0;
    }

    private static String stateText(MagicStoneLearning.Result state) {
        return switch (state) {
            case OK -> "可解锁";
            case ALREADY_LEARNED -> "已学";
            case AFFINITY_TOO_LOW -> "亲和力不足";
            case OUT_OF_ORDER -> "需先学上一级";
            case NOT_ENOUGH_POINTS -> "点数不足";
            case NOT_IMPLEMENTED -> "尚未实装";
        };
    }

    /**
     * /tnc forgetall —— 遗忘**全部**已学法术。
     *
     * <p>亲和力、魔法点数、每条链的进度都**保留** —— 忘完可以马上重新学，方便反复测试
     * （想连进度一起清就用 /tnc reset）。走 mutate() 所以法杖槽位会同步清空。
     */
    private static int forgetAll(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return mutate(ctx, (player, data) -> {
            java.util.List<ResourceLocation> ids = new java.util.ArrayList<>(data.getLearned());
            int removed = 0;
            for (ResourceLocation id : ids) {
                if (data.forget(id)) {
                    removed++;
                }
            }
            return "已遗忘全部 " + removed + " 个法术（亲和力 / 点数 / 链进度都保留）";
        });
    }

    private static int forget(CommandContext<CommandSourceStack> ctx, ResourceLocation spell)
            throws CommandSyntaxException {
        return mutate(ctx, (player, data) -> (data.forget(spell) ? "已遗忘 " : "本来就没学 ") + spell);
    }

    private static int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return mutate(ctx, (player, data) -> {
            data.deserializeNBT(new CompoundTag());
            return "魔法石已清空（下次登录会重新分配亲和力）";
        });
    }

    /**
     * 自检：跑一遍数值/门槛/NBT 的检查并把结果发到聊天栏。
     * 除了"capability 挂没挂上"，其余都在临时数据上跑 —— 不会动你自己的数据。
     */
    private static int selfTest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        List<MagicStoneSelfTest.Check> checks = MagicStoneSelfTest.run(player);
        int passed = 0;
        for (MagicStoneSelfTest.Check check : checks) {
            if (check.passed()) {
                passed++;
            }
        }
        final int passedCount = passed;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§b=== TN-C 魔法石自检 === §7" + passedCount + "/" + checks.size() + " 通过"), false);
        for (MagicStoneSelfTest.Check check : checks) {
            String line = (check.passed() ? "§a[ok]   " : "§c[FAIL] ") + "§r" + check.name() + " §8" + check.detail();
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return passed == checks.size() ? 1 : 0;
    }

    /**
     * 引擎接线自检：法术引擎在不在、魔力硬拦截的 Mixin 有没有真的注入。
     * 这两件事决定了"施法扣魔力"和"没魔力放不出来"到底生不生效。
     */
    /** 赠送魔法点数（调试/测试用）。 */
    private static int pointsAdd(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        return mutate(ctx, (player, data) -> {
            data.addBonusPoints(value);
            return "赠送 +" + value + " 魔法点数，现有 " + data.getPointsAvailable(Config.pointThresholds) + " 点可用";
        });
    }

    private static int pointsSet(CommandContext<CommandSourceStack> ctx, int value) throws CommandSyntaxException {
        return mutate(ctx, (player, data) -> {
            data.setBonusPoints(value);
            return "额外赠送点数设为 " + value + "，现有 "
                    + data.getPointsAvailable(Config.pointThresholds) + " 点可用";
        });
    }

    /**
     * 直接放一个法术（不依赖法术书和键位）。
     *
     * <p>走引擎真正的施放入口，所以一次就能验两件事：
     * 硬拦截有没有拦住（魔力不够/没解锁时魔力不会变），以及扣魔力有没有发生。
     */
    private static int cast(CommandContext<CommandSourceStack> ctx, ResourceLocation spellId)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        SpellCatalog.Entry entry = SpellCatalog.byId(spellId);
        if (entry == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "§c法术目录里没有 " + spellId + " §7（用 /tnc spells 看可选的）"));
            return 0;
        }
        int gateBefore = ManaGate.gateChecks();
        int blockedBefore = ManaGate.blockedCount();
        com.tnc.tnc.magic.compat.SpellEngineCaster.Result result =
                com.tnc.tnc.magic.compat.SpellEngineCaster.cast(player, spellId);

        if (!result.enginePresent()) {
            ctx.getSource().sendFailure(Component.literal("§c没有装 SpellEngine，放不了法术"));
            return 0;
        }
        MagicStoneNetwork.syncTo(player);

        boolean gated = ManaGate.blockedCount() > blockedBefore;
        ctx.getSource().sendSuccess(() -> Component.literal("§b=== 直接施放 " + entry.displayName() + " ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7魔力  " + result.manaBefore() + " → " + result.manaAfter()
                        + (result.manaChanged() ? " §a（扣了 " + result.spent() + "）" : " §c（没变！）")), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7判定  gateChecks " + gateBefore + " → " + ManaGate.gateChecks()
                        + " · " + (gated ? "§c被硬拦截拦住了（法术没放出去）" : "§a放行")), false);
        if (result.error() != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e引擎内部抛错：§7" + result.error()), false);
        }
        return 1;
    }

    /**
     * 把法杖内容对齐到魔法石记录（法杖丢了/内容少了时的补救），必要时补发一根。
     *
     * <p>设计前提：魔法石是权威数据，法杖只是派生道具 —— 所以"法杖丢了"不该等于"法术丢了"。
     * 登录时会自动做一次，这条命令是随时手动触发。
     */
    private static int wand(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MagicStoneData data = require(player, ctx);
        if (data == null) {
            return 0;
        }
        // 法杖内容 = 每条链的最高级（高阶替换低阶），不是"学过的全部"
        java.util.List<ResourceLocation> effective = com.tnc.tnc.magic.SpellCatalog.effectiveIds(data);
        com.tnc.tnc.magic.compat.SpellEngineBridge.WandResult result =
                com.tnc.tnc.magic.compat.SpellEngineBridge.ensureWand(player, effective);
        MagicStoneNetwork.syncTo(player);
        ctx.getSource().sendSuccess(() -> Component.literal("§b[TN-C] §r"
                + com.tnc.tnc.magic.compat.SpellEngineBridge.describeWand(result, effective.size())), false);
        return result == com.tnc.tnc.magic.compat.SpellEngineBridge.WandResult.FAILED ? 0 : 1;
    }

    private static int engineStatus(CommandContext<CommandSourceStack> ctx) {
        boolean engine = SpellEngineBridge.enginePresent();
        boolean gate = ManaGate.isMixinApplied();
        boolean hook = com.tnc.tnc.magic.compat.SpellEngineManaHook.isRegistered();
        ctx.getSource().sendSuccess(() -> Component.literal("§b=== TN-C 引擎接线 ==="), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                (engine ? "§a[ok]   " : "§c[FAIL] ") + "§rSpellEngine 已加载 §8" + engine), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                (hook ? "§a[ok]   " : "§c[FAIL] ") + "§r施法扣魔力的钩子已注册（SPELL_CAST）§8" + hook), false);
        ctx.getSource().sendSuccess(() -> Component.literal(
                (gate ? "§a[ok]   " : "§c[FAIL] ") + "§r魔力硬拦截 Mixin 已生效 §8" + gate), false);
        if (engine && !gate) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e→ 引擎在但 Mixin 没生效：施法会扣魔力（事后扣），但拦不住魔力不足的那一发"), false);
        } else if (!engine) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e→ 没有引擎：施法相关功能全部停用（正常，比如 dev 环境）"), false);
        }
        // 光看"注入成功"不算数，看它真的拦过才算数
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7拦截判定执行 " + ManaGate.gateChecks() + " 次 · 拦下 " + ManaGate.blockedCount()
                        + " 次（其中未解锁 " + ManaGate.unlearnedCount() + " 次）"), false);
        // 施法事件的账本 —— "魔力没扣"到底卡在哪一步，看这几行就知道
        int seen = com.tnc.tnc.magic.compat.SpellEngineManaHook.seenCount();
        int ours = com.tnc.tnc.magic.compat.SpellEngineManaHook.ourSpellCount();
        int others = com.tnc.tnc.magic.compat.SpellEngineManaHook.otherModCount();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§7施法事件 " + seen + " 次 · 其中 TN-C 法术 " + ours + " 次 · 别家法术 " + others + " 次"), false);
        if (seen > 0 && ours == 0) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§e→ 引擎在播报施法，但放的都是别人的法术 —— TN-C 只对自己的法术扣魔力"), false);
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§8最近: " + com.tnc.tnc.magic.compat.SpellEngineManaHook.lastSeen()), false);
        return 1;
    }

    /**
     * 硬拦截实测：在一次性假玩家上跑三级阶梯，直接给出"没魔力到底拦不拦得住"的结论。
     *
     * <p>存在的意义：{@link #engineStatus} 只能告诉你"混入的类被加载了"和"历史上拦过几次"，
     * 都是间接证据。<b>这个方法给的是直接证据</b> —— 真的去调引擎的施法前判定，
     * 再看计数器有没有按预期变化。整合包里换版本、升级引擎之后，跑这一条就知道还灵不灵。
     */
    private static int gateTest(CommandContext<CommandSourceStack> ctx, ResourceLocation id) {
        if (id == null) {
            ctx.getSource().sendFailure(Component.literal("§c法术目录是空的，没有可测的法术"));
            return 0;
        }
        ManaGateProbe.Report report = ManaGateProbe.run(ctx.getSource().getLevel(), id);

        ctx.getSource().sendSuccess(() -> Component.literal("§b=== TN-C 魔力硬拦截实测 ==="), false);
        if (!report.ran()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e[跳过] §r" + report.note()), false);
            return 0;
        }
        for (ManaGateProbe.Step step : report.steps()) {
            String line = (step.passed() ? "§a[ok]   " : "§c[FAIL] ") + "§r" + step.name()
                    + " §8" + step.detail();
            ctx.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        final int passed = report.passedCount();
        final int total = report.steps().size();
        boolean ok = report.allPassed();
        ctx.getSource().sendSuccess(() -> Component.literal(
                (ok ? "§a" : "§c") + "结论：" + passed + "/" + total
                        + (ok ? " —— 魔力不足真的放不出来（施法前拦截）" : " —— 硬拦截没完全生效")), false);
        if (report.note() != null) {
            ctx.getSource().sendSuccess(() -> Component.literal("§e→ " + report.note()), false);
        }
        return ok ? 1 : 0;
    }

    /**
     * 法术 id 的 Tab 补全。顺手把 {@code tnc:} 前缀补全出来，省得手打错字。
     * （id 用 {@link ResourceLocationArgument#id()} 解析，见下面那条注释 —— 不能用 string()。）
     */
    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestSpells(
            CommandContext<CommandSourceStack> ctx,
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        for (SpellCatalog.Entry entry : SpellCatalog.all()) {
            builder.suggest(entry.id().toString());
        }
        return builder.buildFuture();
    }

    // ------------------------------------------------------------------
    //  工具
    // ------------------------------------------------------------------

    /** 一次数据改动：拿到玩家与数据，返回给玩家的回执文字。 */
    private interface DataEdit {
        String apply(ServerPlayer player, MagicStoneData data);
    }

    /** 取玩家 → 改数据 → 重算上限 → 同步客户端 → 回执。 */
    private static int mutate(CommandContext<CommandSourceStack> ctx, DataEdit edit) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MagicStoneData data = require(player, ctx);
        if (data == null) {
            return 0;
        }
        String message = edit.apply(player, data);
        MagicStone.refreshMaxMana(player, data);
        MagicStoneNetwork.syncTo(player);
        // ⚠️ 必须同时重写法杖内容：forget / reset 会改变"每条链的最高级"，
        // 不重写的话被遗忘的法术还挂在槽位里（用户实测到的 bug）。
        // 学习 / 解锁 / 登录三条路都做了这一步，这里当初漏了。
        com.tnc.tnc.magic.compat.SpellEngineBridge.ensureWand(
                player, com.tnc.tnc.magic.SpellCatalog.effectiveIds(data));
        ctx.getSource().sendSuccess(() -> Component.literal("§a[TN-C] §r" + message), false);
        return 1;
    }

    private static MagicStoneData require(ServerPlayer player, CommandContext<CommandSourceStack> ctx) {
        MagicStoneData data = MagicStone.getOrNull(player);
        if (data == null) {
            ctx.getSource().sendFailure(Component.literal("拿不到魔法石数据（capability 没挂上？）"));
        }
        return data;
    }
}
