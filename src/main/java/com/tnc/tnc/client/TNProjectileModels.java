package com.tnc.tnc.client;

import com.tnc.tnc.TNMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 把我们的投射物模型登记给 SpellEngine —— <b>必须早于客户端资源重载（模型烘焙）</b>。
 *
 * <h2>为什么需要这个类（踩了一整晚的坑）</h2>
 * <ol>
 *   <li>SpellEngine 渲染投射物时直接查"已烘焙的模型"：
 *       {@code Minecraft.getInstance().getModelManager().getModel(modelId)}
 *       （反编译 {@code net.spell_engine.api.render.CustomModels#render} 可见）。</li>
 *   <li>Minecraft 只烘焙<b>被 blockstate / item 引用过</b>的模型 ✗ ——
 *       我们那种独立的 {@code models/projectile/*.json} 无人引用，<b>从不被烘焙</b>，
 *       于是 {@code getModel()} 查不到 → 渲染 missing model → <b>紫黑方块</b>。</li>
 *   <li>SpellEngine 提供公开 API {@code CustomModels.registerModelIds(List<ResourceLocation>)}，
 *       把模型号加进它的表，资源重载时一起烘焙。</li>
 *   <li><b>时机是决定性的</b>：日志实测 ——
 *       {@code 22:19:38 Reloading ResourceManager}（模型此刻烘焙）而
 *       {@code 22:19:50 onClientSetup}（之前把登记放这里，太晚 ✗）。
 *       所以这个类在<b>类加载的静态块</b>里就登记（mod 构造期，早于一切资源重载），
 *       并在 common setup 再登记一次做双保险。</li>
 * </ol>
 *
 * <p>登记之后就能用<b>我们自己的</b>命名空间（{@code tnc:projectile/...}），
 * 不必去征用别的模组的路径、也不会影响任何别人的法术。
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TNProjectileModels {

    private static final Logger LOGGER = LogManager.getLogger("TN-C/models");

    /**
     * ★ <b>投射物模型的唯一清单</b> —— 加模型只改这里 ✗ 不要另抄一份。
     *
     * <p>踩过的坑：这份清单以前和 {@link TNModelBaking} 里那份<b>各写了一份</b>，
     * 结果两边不一致（有的号登记了没烘焙、有的烘焙了没登记），
     * 而<b>漏掉任何一半都会渲染成紫黑方块</b> ✗。现在 TNModelBaking 直接读这个数组 ✓。
     *
     * <p>加新模型的完整步骤见 {@code docs/投射物模型_配方.md}：
     * 模型文件 + 贴图 + <b>写进这个数组</b> + 法术 JSON 写 {@code "model_id": "tnc:<path>"}。
     */
    /**
     * 雷系新版球（作者 2026-09-27 自制）：58 个小方块拼的"碎块球"，基础尺寸
     * <b>13/16 = 0.8125 格</b>（老 {@code lightingball} 只有 6/16 = 0.375 格）。
     *
     * <p>环绕雷球实体与神级大雷球共用它 —— 两边的 {@code scale} 都按同一个基准换算
     * （见 {@code docs/法术专题_交接.md} 3.2f）。
     */
    public static final String LIGHTNINGBALL_2 = "projectile/lightingball_2";

    /**
     * 作者自制闪电（{@code flashinggg}，2026-09-27）：15 个方块、<b>竖直闪电</b>，
     * 基础尺寸 <b>0.3125 × 2.25 × 0.125 格</b>（高 2.25 格），11 个元素带 ±22.5°/±45° 的
     * Z 轴旋转（＝闪电的锯齿 ✓ 合法角度 ✓）。
     *
     * <p>给法术 {@code heavenly_thunder}（神级天雷，从 30 格高空砸下来的那几道）当投射物模型，
     * 换掉原来借用的 {@code berserker_rpg:projectile/lightning_bolt}（它在整合包里**缺贴图** ✗）。
     */
    public static final String FLASH = "projectile/flash";

    /**
     * ★ 黑暗衍（{@code tnc:yan_dark}，公孙衍·迷失）的<b>暗色三件套</b>
     * （作者 2026-09-30 自制：{@code flashinggg_dark} / {@code LINGTINGGOD_dark} /
     * {@code lightingball_2_dark}）。
     *
     * <p>几何与对应的亮色模型**完全一致**（生成脚本里逐元素校验过 ✓），
     * 只有 UV 与贴图不同 ⇒ 由 {@code tools/gen_dark_projectile_models.ps1} 生成：
     * 以**已发货的亮色模型**为模板，只把每个面的 UV/贴图换成暗版 ✓。
     */
    public static final String FLASH_DARK = "projectile/flash_dark";
    /** 暗色雷霆之神（三个灰度贴图版本，见 god_dark.json 的 textures ✓）。 */
    public static final String GOD_DARK = "projectile/god_dark";
    /** 暗色新版雷球。 */
    public static final String LIGHTNINGBALL_DARK = "projectile/lightingball_dark";

    /**
     * ★ <b>黑夜之手</b>（暗系第一条链:黑夜之手 → 黑夜之袭 → 黑夜之拥 → 黑之破灭 → 戮光）。
     *
     * <p>作者 2026-10-02："给我一个手的模型" —— 这五档以前全都借用
     * {@code projectile/lightingball}（一个黄色雷球 ✗），和"黑夜之手"毫无关系。
     *
     * <p>模型由 {@code tools/gen_dark_hand_model.py} 程序化生成（和 {@code light_wings} /
     * {@code angel} 同一套做法：脚本 → 模型 + 贴图 + 预览 ✓，改一个数字就能重来）。
     *
     * <p>★ <b>朝向约定</b>：模型里<b>手指指向 +Z</b>（＝飞行方向），手腕在 -Z，
     * 包围盒已居中到原点（否则投射物会绕着自己前方的一点翻滚，像在自转手腕 ✗）。
     * 所以如果以后要让它"伸着手抓过去"，在法术 JSON 的 model 块里加
     * {@code "orientation": "TOWARDS_MOTION"} 即可 ✓ ——
     * 但现在<b>故意不设</b>，和包里其它投射物保持一致（默认朝向镜头/翻滚 ✓）。
     */
    public static final String DARK_HAND = "projectile/dark_hand";

    /**
     * ★ <b>黑雾的穹顶</b>（暗系第四条链：黑雾 / 领域）—— 作者 2026-10-09："你全做吧"。
     *
     * <p>它<b>不是投射物</b>，但走的是同一条模型通道 ✓：引擎的 {@code SpellCloudRenderer}
     * 会把 {@code release.target.cloud.client_data.model} 喂给
     * {@code CustomModels.render(...)}（和投射物渲染器同一个函数），
     * 所以它同样<b>必须登记进这份唯一的清单、必须被烘焙</b> —— 否则引擎取到的是
     * "缺失模型"，画出来就是紫黑方块 ✗（这条链是<b>第一个</b>用这个槽的法术）。
     *
     * <p>模型由 {@code tools/gen_dark_fog_model.py} 程序化生成：三层抖动过的方块环
     * （外圈薄、里圈厚）+ 顶盖 + 少量"余烬"块；贴图是程序画的暗紫烟灰图集 ✓。
     *
     * <p>★ <b>几何约定</b>：包围盒在 x/z 上以 (8,8) 单位为中心、<b>底边正好落在 y=8</b>，
     * 因为 {@code CustomModels.render} 先 {@code translate(-0.5,-0.5,-0.5)} ⇒
     * 模型的底边正好贴在实体位置上 ✓（雾是贴地的一层，不能像投射物那样居中到原点 ✗）。
     * 法术 JSON 里的 {@code scale} 就等于<b>雾的半径（格）</b> ✓。
     */
    public static final String DARK_FOG = "projectile/dark_fog";

    public static final String[] PROJECTILE_MODELS = {
            // 火系（用户自制）
            "projectile/fireball",
            // 水系（用户自制）
            "projectile/waterball",
            // 雷系（用户自制）
            "projectile/lightingball",
            // 雷系新版球（作者 2026-09-27 自制：58 个小方块拼的碎块球）
            LIGHTNINGBALL_2,
            // 雷系闪电（作者 2026-09-27 自制：竖直锯齿闪电）
            FLASH,
            // 作者 2026-09-29 自制：雷霆之神（神在投篮 t5 天上那三尊 ✓）
            "projectile/lightning_god",
            // 作者 2026-09-30 自制：黑暗衍（公孙衍·迷失）专用的暗色三件套 ✓
            //   （闪电 / 雷霆之神 / 新版雷球 的暗色版；几何与上面三个一致，只有 UV+贴图不同）
            FLASH_DARK,
            GOD_DARK,
            LIGHTNINGBALL_DARK,
            // 雷系备用球（用户自制）
            "projectile/thunder_ball",
            // 暗系黑夜之手链（生成器：tools/gen_dark_hand_model.py）
            DARK_HAND,
            // 暗系黑雾链的"领域"穹顶（生成器：tools/gen_dark_fog_model.py）——
            //   挂在 CLOUD 的 client_data.model 上，不是投射物，但走同一条渲染/烘焙通道 ✓
            DARK_FOG,
    };

    /**
     * 取一个"已烘焙的额外模型"的 key —— 必须与 {@link TNModelBaking} 注册时用的变体一致
     * （{@code "standalone"} ✓），否则拿到的是 missing model（紫黑）✗。
     */
    public static net.minecraft.client.resources.model.ModelResourceLocation standalone(String path) {
        return new net.minecraft.client.resources.model.ModelResourceLocation(
                ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path), "standalone");
    }

    static {
        // 先注册"模型载体物品"（投射物用物品 id 兜底渲染，见 TNProjectileCarriers 的说明）
        try {
            com.tnc.tnc.magic.TNProjectileCarriers.register();
        } catch (Throwable t) {
            LOGGER.warn("TN-C: carrier item registration skipped ({})", t.toString());
        }
        register();
    }

    private TNProjectileModels() {
    }

    /** common setup 再登记一次（双保险：万一静态块比 SpellEngine 初始化还早）。 */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        register();
    }

    private static void register() {
        try {
            java.util.List<ResourceLocation> ids = new java.util.ArrayList<>();
            for (String path : PROJECTILE_MODELS) {
                ids.add(ResourceLocation.fromNamespaceAndPath(TNMod.MODID, path));
            }
            net.spell_engine.api.render.CustomModels.registerModelIds(ids);
            LOGGER.info("TN-C: registered {} TN-C projectile model id(s) to SpellEngine", ids.size());
        } catch (Throwable t) {
            LOGGER.warn("TN-C: projectile model registration skipped ({})", t.toString());
        }
    }
}