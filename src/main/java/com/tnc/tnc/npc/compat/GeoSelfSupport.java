package com.tnc.tnc.npc.compat;

import com.tnc.tnc.npc.SelfNpcEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/**
 * Self 的"要不要用 Bedrock 模型"分流点 —— 与 {@link MaidNpcSupport} 同一套写法 ✓。
 *
 * <p>为什么要有它：{@code SelfBedrockNpcEntity} 引用了 GeckoLib 的类型，
 * 一旦那个类被加载，**没装 GeckoLib 的环境就 NoClassDefFoundError** ✗ ——
 * 而法术/魔法石不该因为一个 NPC 的外观整个加载不了。所以：
 * <ul>
 *   <li>{@link #available()} 只用 {@code ModList}（不碰 GeckoLib 的类 ✓）；</li>
 *   <li>{@link #create} 的方法体里才 new 那个类 —— JVM 是**用到才解析** ✓。</li>
 * </ul>
 *
 * <p>整合包里 GeckoLib 一定在（TLM 依赖它），所以正常情况 self 都走 Bedrock 模型 ✓；
 * 降级路径（原版人形 + 64×64 皮肤）只为"万一"准备 ✓。
 *
 * <p>★ 为什么要给 self 也上 Bedrock 模型（2026-09-22，动画系统MMM）：
 * 原版人形模型是 Java 里烘焙的、<b>没有可录关键帧的骨架</b> ✗ ——
 * 剧情演出要的动作（擦手/放抹布/看酒杯/回头看门）只能在 Blockbench 里录，
 * 而 Blockbench 导出的是 Bedrock 动画，必须由 GeckoLib 渲染器来播 ✓。
 */
public final class GeoSelfSupport {

    private GeoSelfSupport() {
    }

    /** 装了 GeckoLib 吗？（不引用 GeckoLib 的任何类 ✓） */
    public static boolean available() {
        return MaidNpcSupport.available();
    }

    /**
     * 实体工厂：装了就返回 Bedrock 模型版（{@code SelfBedrockNpcEntity}），
     * 行为与降级版完全一样（对话/NPC 逻辑都在 {@link SelfNpcEntity} 里）✓。
     *
     * <p>⚠️ 只有在 {@link #available()} 为真时才会被调用 —— 调用方负责判断。
     */
    public static SelfNpcEntity create(EntityType<SelfNpcEntity> type, Level level) {
        return new com.tnc.tnc.npc.SelfBedrockNpcEntity(type, level);
    }
}
