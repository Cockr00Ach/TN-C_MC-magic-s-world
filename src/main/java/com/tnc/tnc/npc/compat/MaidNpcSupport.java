package com.tnc.tnc.npc.compat;

import com.tnc.tnc.npc.ZhuangquerangNpcEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;

/**
 * 庄鹊让的"要不要用女仆模型"分流点 —— <b>整个 mod 里唯一知道 GeckoLib 存在与否的地方</b> ✓。
 *
 * <p>为什么要有它：{@code ZhuangquerangMaidNpcEntity} 引用了 GeckoLib 的类型，
 * 一旦那个类被加载，**没装 GeckoLib 的环境就 NoClassDefFoundError** ✗ ——
 * 而法术/魔法石不该因为一个 NPC 的外观整个加载不了。所以：
 * <ul>
 *   <li>{@link #available()} 只用 {@code ModList}（不碰 GeckoLib 的类 ✓）；</li>
 *   <li>{@link #create} 的方法体里才 new 那个类 —— JVM 是**用到才解析** ✓，
 *       所以只有真的走这条路时才会加载它 ✓。</li>
 * </ul>
 *
 * <p>整合包里 GeckoLib 一定在（TLM 依赖它），所以正常情况都走女仆模型 ✓；
 * 降级路径（原版人形 + 64×64 皮肤）只为"万一"准备 ✓。
 */
public final class MaidNpcSupport {

    /** GeckoLib 的 modid（TLM 依赖它，整合包 mods 里有 geckolib-forge-1.20.1-4.8.4） */
    public static final String GECKOLIB = "geckolib";

    private MaidNpcSupport() {
    }

    /** 装了 GeckoLib 吗？（不引用 GeckoLib 的任何类 ✓） */
    public static boolean available() {
        try {
            return ModList.get() != null && ModList.get().isLoaded(GECKOLIB);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 实体工厂：装了就返回女仆模型版（{@code ZhuangquerangMaidNpcEntity}），
     * 行为与降级版完全一样（对话/NPC 逻辑都在 {@link ZhuangquerangNpcEntity} 里）✓。
     *
     * <p>⚠️ 只有在 {@link #available()} 为真时才会被调用 —— 调用方负责判断。
     */
    public static ZhuangquerangNpcEntity create(EntityType<ZhuangquerangNpcEntity> type, Level level) {
        return new com.tnc.tnc.npc.ZhuangquerangMaidNpcEntity(type, level);
    }
}
