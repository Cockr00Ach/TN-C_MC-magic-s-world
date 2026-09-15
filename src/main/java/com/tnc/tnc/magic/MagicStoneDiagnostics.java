package com.tnc.tnc.magic;

import com.mojang.logging.LogUtils;
import com.tnc.tnc.TNMod;
import com.tnc.tnc.magic.compat.ManaGateProbe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 服务器启动后自动跑一遍魔法石自检，把结果写进日志。
 *
 * <p>为什么要这么干：编译通过 ≠ 运行时正常。开发机上的专用服务器是最便宜的真实验证环境 ——
 * 它能证明 capability 真的挂得上、数值算法真的按预期跑、NBT 真的存得进读得出。
 * （假玩家也会走实体构造流程，所以 AttachCapabilitiesEvent 会照常触发。）
 */
@Mod.EventBusSubscriber(modid = TNMod.MODID)
public class MagicStoneDiagnostics {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        try {
            ServerLevel level = server.overworld();
            FakePlayer probe = FakePlayerFactory.getMinecraft(level);
            MagicStoneSelfTest.log(LOGGER, "server start", MagicStoneSelfTest.run(probe));
        } catch (Throwable error) {
            // 自检失败不应该影响服务器启动，只记录
            LOGGER.warn("TN-C magic stone self-test could not run: {}", error.toString());
        }
        runGateProbe(server);
    }

    /**
     * 启动时自动跑一遍魔力硬拦截实测，把结论写进日志。
     *
     * <p>为什么连这个都要自动化：硬拦截靠 Mixin 注入，而 {@code required:false} 的配置
     * <b>注入失败是完全静默的</b> —— 游戏照常启动，只是魔力再也拦不住施法。
     * 每次开服都自动验一遍，日志里就有明确结论，不用靠"感觉施法有点怪"去猜。
     *
     * <p>（同一件事也能用 {@code /tnc gatetest} 手动跑。）
     */
    private static void runGateProbe(MinecraftServer server) {
        try {
            ResourceLocation spell = ManaGateProbe.defaultSpell();
            if (spell == null) {
                LOGGER.info("TN-C mana gate probe [server start]: skipped (spell catalog is empty)");
                return;
            }
            ManaGateProbe.Report report = ManaGateProbe.run(server.overworld(), spell);
            if (!report.ran()) {
                // dev 环境没装引擎，走到这里是正常的
                LOGGER.info("TN-C mana gate probe [server start]: skipped ({})", report.note());
                return;
            }
            LOGGER.info("TN-C mana gate probe [server start]: {}/{} passed ({})",
                    report.passedCount(), report.steps().size(),
                    report.allPassed() ? "硬拦截生效" : "硬拦截未完全生效！");
            for (ManaGateProbe.Step step : report.steps()) {
                LOGGER.info("TN-C mana gate probe   {} {} : {}",
                        step.passed() ? "[ok]  " : "[FAIL]", step.name(), step.detail());
            }
            if (report.note() != null) {
                LOGGER.warn("TN-C mana gate probe note: {}", report.note());
            }
        } catch (Throwable error) {
            // 探针失败绝不能拦住服务器启动
            LOGGER.warn("TN-C mana gate probe could not run: {}", error.toString());
        }
    }
}
