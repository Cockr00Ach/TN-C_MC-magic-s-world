package net.spell_engine.internals.casting;

/**
 * 【开发用桩】对应真引擎的 {@code SpellCast}，只提供 TNC 用到的部分：
 * <ul>
 *   <li>{@code SpellCast.Attempt} 及 {@code success()} / {@code none()} 工厂
 *       —— Mixin 硬拦截要返回它；</li>
 *   <li>{@code SpellCast.Action} 枚举（CHANNEL / RELEASE）
 *       —— 施法钩子靠它区分"起手警告"和"真放出去扣魔力"。</li>
 * </ul>
 *
 * <p>真引擎里的 Action 是嵌套枚举，javap 实测只有 CHANNEL / RELEASE 两个常量。
 */
public class SpellCast {

    /** 施法阶段（真引擎：{@code net.spell_engine.internals.casting.SpellCast$Action}）。 */
    public enum Action {
        CHANNEL,
        RELEASE
    }

    public static class Attempt {

        private final String result;

        private Attempt(String result) {
            this.result = result;
        }

        public static Attempt success() {
            return new Attempt("SUCCESS");
        }

        public static Attempt none() {
            return new Attempt("NONE");
        }

        public String result() {
            return result;
        }

        @Override
        public String toString() {
            return "Attempt[" + result + "]";
        }
    }
}
