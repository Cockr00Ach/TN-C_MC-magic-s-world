package net.spell_engine;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * 【开发用桩，不是真引擎！】modId 故意叫 spell_engine，这样 TN-C 的
 * {@code TncMixinPlugin} 会认为"引擎在"，于是真的去注入我们的 Mixin。
 *
 * <p>这个类本身不做任何事 —— 探针在 {@code SpellHelper} 的静态块里，
 * 由 TN-C 的自检强制初始化那个类时触发。这样桩就不需要碰事件总线，
 * 编译时也少一堆依赖。
 */
@Mod(StubSpellEngine.MOD_ID)
public class StubSpellEngine {

    public static final String MOD_ID = "spell_engine";

    public StubSpellEngine(FMLJavaModLoadingContext context) {
        // 桩不需要任何生命周期回调
    }
}
