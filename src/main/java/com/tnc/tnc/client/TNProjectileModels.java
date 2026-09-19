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

    static {
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
            net.spell_engine.api.render.CustomModels.registerModelIds(java.util.List.of(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "projectile/fire_ball"),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "projectile/fire_ray"),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "projectile/bolt_copy"),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "projectile/thunder_ball"),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "projectile/thunder_ball_min"),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "projectile/thunder_orb"),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "projectile/thunder_probe2"),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(TNMod.MODID, "projectile/lightingball")));
            LOGGER.info("TN-C: registered TN-C projectile model id(s) to SpellEngine");
        } catch (Throwable t) {
            LOGGER.warn("TN-C: projectile model registration skipped ({})", t.toString());
        }
    }
}