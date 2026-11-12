package com.tnc.tnc.travel;

/**
 * 当前客户端实际绑定的「元素附身键」显示名。
 *
 * <p>默认 Q；玩家在「选项 → 控制 → TN-C」里把战士左斩击改到别的键时，
 * 法杖 tooltip 也跟着改。服务端/无客户端环境保持默认 Q。
 */
public final class TravelKeyHint {

    private static volatile String key = "Q";

    private TravelKeyHint() {
    }

    public static String key() {
        String value = key;
        return value == null || value.isBlank() ? "Q" : value;
    }

    public static void set(String value) {
        if (value != null && !value.isBlank()) {
            key = value;
        }
    }
}
