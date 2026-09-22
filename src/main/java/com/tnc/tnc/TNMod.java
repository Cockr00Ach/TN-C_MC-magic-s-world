package com.tnc.tnc;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.network.MagicStoneNetwork;
import com.tnc.tnc.world.SkyIslandEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(TNMod.MODID)
public class TNMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "tnc";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();

    // 三个注册器。所有 TN-C 的内容都通过它们注册到 Forge，命名空间统一是 "tnc"。
    // 注意：注册器必须在构造函数里挂到 mod 事件总线上才会生效（见下方 TNMod()）。
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ------------------------------------------------------------------
    //  物品
    // ------------------------------------------------------------------

    // 试作之剑 —— 第一件使用 Blockbench 自定义模型的物品。
    //   模型: assets/tnc/models/item/sword.json
    //   贴图: assets/tnc/textures/item/sword.png
    //   名字: assets/tnc/lang/{zh_cn,en_us}.json -> item.tnc.sword
    //
    // Tiers.IRON 表示沿用原版铁质工具的属性（挖掘等级、耐久）；3 和 -2.4F 分别是
    // 攻击力附加值（实际伤害 = 玩家基础 1 + 3 = 4）和攻击速度修正。
    // 之后做真正的 TN-C 武器时应该自定义一个 Tier，而不是借用原版的。
    public static final RegistryObject<Item> SWORD = ITEMS.register("sword",
            () -> new SwordItem(Tiers.IRON, 3, -2.4F, new Item.Properties()));

    /**
     * 魔法法杖 —— 施法的前提（手上有法杖才能按数字键放法术）。
     *
     * <p>物品的实际类型由 {@code SpellEngineBridge.createWandItem()} 决定：
     * <b>装了 SpellEngine 就是真正的法杖</b>（实现引擎的 {@code SpellBookItem}，
     * 否则引擎的法术快捷栏按 {@code instanceof SpellBookItem} 判定，根本不会取用它），
     * <b>没装引擎就退化成普通物品</b>，保证 mod 在任何环境都能正常加载。
     *
     * <p>"法杖能放哪些法术"完全由 SpellEngine 写在物品 NBT 里的 {@code SpellContainer} 表达，
     * 而那个容器由魔法石的"已解锁集合"单向同步（见 {@code SpellEngineBridge.ensureWand}）。
     */
    public static final RegistryObject<Item> WAND = ITEMS.register("magic_wand",
            com.tnc.tnc.magic.compat.SpellEngineBridge::createWandItem);

    // ------------------------------------------------------------------
    //  创造模式标签页
    // ------------------------------------------------------------------

    // TN-C 专属标签页，排在原版「战斗」标签页之后。
    // 翻译键是 itemGroup.tnc.main，写在语言文件里。
    // 以后新增物品时，在这里 output.accept(...) 加一行即可出现在本标签页中。
    public static final RegistryObject<CreativeModeTab> TNC_TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> SWORD.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(SWORD.get());
                output.accept(WAND.get());
                // 剧情道具「宝箱传说残卷」六卷（卷一拆成上下卷）—— 方便验收时直接拿 ✓
                output.accept(com.tnc.tnc.magic.TNScrolls.JUAN_1A.get());
                output.accept(com.tnc.tnc.magic.TNScrolls.JUAN_1B.get());
                output.accept(com.tnc.tnc.magic.TNScrolls.JUAN_3.get());
                output.accept(com.tnc.tnc.magic.TNScrolls.JUAN_4.get());
                output.accept(com.tnc.tnc.magic.TNScrolls.JUAN_5.get());
                output.accept(com.tnc.tnc.magic.TNScrolls.JUAN_6.get());
                // 剧情道具「一周目正史」五张记录纸（前情 + 主线四段）—— 同样是可右键阅读的 ✓
                output.accept(com.tnc.tnc.magic.TNRecords.QIANQING.get());
                output.accept(com.tnc.tnc.magic.TNRecords.PART_1.get());
                output.accept(com.tnc.tnc.magic.TNRecords.PART_2.get());
                output.accept(com.tnc.tnc.magic.TNRecords.PART_3.get());
                output.accept(com.tnc.tnc.magic.TNRecords.PART_4.get());
                // NPC 刷怪蛋（调试与放置用）—— 剧情 NPC 上线后由生成逻辑接管
                output.accept(com.tnc.tnc.npc.TNNpcAttributes.SELF_SPAWN_EGG.get());
                output.accept(com.tnc.tnc.npc.TNNpcAttributes.CAVA_SPAWN_EGG.get());
                output.accept(com.tnc.tnc.npc.TNNpcAttributes.HUAI_SPAWN_EGG.get());
                output.accept(com.tnc.tnc.npc.TNNpcAttributes.ZHUANGQUERANG_SPAWN_EGG.get());
            }).build());

    // ------------------------------------------------------------------
    //  初始化
    // ------------------------------------------------------------------

    public TNMod(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // 把三个注册器挂到 mod 事件总线上，否则上面注册的内容不会生效
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        com.tnc.tnc.world.stonecrest.TNStructures.register(modEventBus);
        // 我们自己的状态效果（雷速 / 极速雷风 / 环绕雷球 / 闪电登神）—— 法术 JSON 按 id 引用
        com.tnc.tnc.magic.TNEffects.register(modEventBus);
        // 剧情道具「宝箱传说残卷」六卷 —— 必须在注册事件之前挂上总线（原因见 TNScrolls）
        com.tnc.tnc.magic.TNScrolls.register(modEventBus);
        // 剧情道具「一周目正史」五张记录纸 —— 同上
        com.tnc.tnc.magic.TNRecords.register(modEventBus);
        // TN-C 自己的 NPC（第一个：A. Self 酒馆老板）。实体类型 + 属性表 + 刷怪蛋
        com.tnc.tnc.npc.TNNpcs.register(modEventBus);
        com.tnc.tnc.npc.TNNpcAttributes.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new SkyIslandEvents());
        // 固定 NPC：世界加载时补位 + 周期性自检（NPC 没了会自己回来）
        MinecraftForge.EVENT_BUS.register(new com.tnc.tnc.npc.NpcPlacementEvents());
        // /tnc npc ... —— 登记/查看/重放固定 NPC 的坐标
        MinecraftForge.EVENT_BUS.register(new com.tnc.tnc.npc.NpcCommand());
        // 服务端资源重载（/reload 或重启）时清对话剧本缓存 ——
        // 否则剧本 txt 改了、游戏里还是旧的（缓存是静态 Map）
        MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.AddReloadListenerEvent event) ->
                com.tnc.tnc.dialogue.DialogueLoader.clearCache());

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // 魔法石数据同步用的网络通道（服务端 → 客户端）
        // 对话系统的包也挂在同一条通道上（在 MagicStoneNetwork.register() 里一并注册）——
        // 一个 mod 只能有一条 SimpleChannel，另建同名通道会在构造期抛异常、整包启动失败。
        MagicStoneNetwork.register();
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("TN-C common setup complete, {} item(s) registered", ITEMS.getEntries().size());
        LOGGER.info("TN-C magic stone data layer ready (affinity/mana/points/progress/learned spells)");
        // 把注册到的效果 id 打出来：法术 JSON 是按 id 引用它们的，
        // 哪天改名/漏注册，"效果不生效"至少能在日志里一眼看到，而不是静默失败
        LOGGER.info("TN-C effects registered: {}", com.tnc.tnc.magic.TNEffects.EFFECTS.getEntries().stream()
                .map(entry -> entry.getId().toString()).toList());

        // 有 SpellEngine 就挂上"施法扣魔力"的钩子；没有就安静跳过（dev 环境）
        com.tnc.tnc.magic.compat.SpellEngineManaHook.register();
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        LOGGER.info("TN-C server starting");
    }

    /**
     * 服务端启动完成（数据包已就绪）→ 跑剧本自检。
     *
     * <p>把"剧本文件写错 → 玩家右键只说一句加载失败"这种**静默失效**提前到开服日志里暴露，
     * 编剧也能自己看日志确认台词被读到了、共几行。
     */
    @SubscribeEvent
    public void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event)
    {
        com.tnc.tnc.dialogue.DialogueDiagnostics.run(event.getServer());
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            LOGGER.info("TN-C client setup, player = {}", Minecraft.getInstance().getUser().getName());

            // ★ 关键一步：把我们的投射物模型【登记】给 SpellEngine。
            //   原因（反编译 SpellEngine 0.15.12 得到）：
            //     SpellEngine 渲染投射物时直接查"已烘焙的模型"(ModelManager.getModel(modelId))，
            //     而 Minecraft 只烘焙被 blockstate/item 引用过的模型 —— 我们那个独立的
            //     models/projectile/*.json 没人引用，从未被烘焙 → 查不到 → 渲染成紫黑方块。
            //     别人的模组能用，是因为它们初始化时调用过这个 API 把自己的模型号登记进去。
            //   登记后即可使用【我们自己的】路径，不影响任何别人的法术。
            try {
                net.spell_engine.api.render.CustomModels.registerModelIds(java.util.List.of(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "projectile/lightingball"),
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MODID, "projectile/thunder_ball")));
                LOGGER.info("TN-C: registered 2 projectile model id(s) to SpellEngine");
            } catch (Throwable t) {
                LOGGER.warn("TN-C: projectile model registration skipped ({})", t.toString());
            }
        }

        /**
         * 注册 TN-C 的按键（HUD 上按 V 开魔法石界面）。
         *
         * <p>必须走 MOD 总线 —— {@code RegisterKeyMappingsEvent} 是 mod 生命周期事件，
         * 挂到 FORGE 总线上根本不会被调用，按键会静默不生效。
         */
        @SubscribeEvent
        public static void onRegisterKeyMappings(net.minecraftforge.client.event.RegisterKeyMappingsEvent event)
        {
            com.tnc.tnc.client.MagicStoneKeys.register(event);
        }
    }
}
