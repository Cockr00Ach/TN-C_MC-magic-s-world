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

    public static final String[] PROJECTILE_MODELS = {
            // 火系（用户自制）
            "projectile/fireball",
            // 水系（用户自制）
            "projectile/waterball",
            // 雷系（用户自制）
            "projectile/lightingball",
            // 雷系新版球（作者 2026-09-27 自制：58 个小方块拼的碎块球）
            LIGHTNINGBALL_2,
            // 雷系备用球（用户自制）
            "projectile/thunder_ball",
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