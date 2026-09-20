package com.tnc.tnc.mixin;

import java.util.function.UnaryOperator;

public final class ConquestBushPropertyGuard {
    private ConquestBushPropertyGuard() {
    }

    public static <S> S writeIfPresent(S state, boolean propertyPresent, UnaryOperator<S> writer) {
        if (!propertyPresent) {
            return state;
        }
        return writer.apply(state);
    }
}
