package net.spell_engine.api.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 【开发用桩】对应真引擎的 {@code net.spell_engine.api.event.Event}，
 * 签名照抄 javap 结果：
 *
 * <pre>
 *   public final class Event&lt;T&gt; {
 *     public Event();
 *     public void register(T);
 *     public boolean isListened();
 *     public void invoke(java.util.function.Consumer&lt;T&gt;);
 *   }
 * </pre>
 */
public final class Event<T> {

    private final List<T> handlers = new ArrayList<>();

    public void register(T handler) {
        handlers.add(handler);
    }

    public boolean isListened() {
        return !handlers.isEmpty();
    }

    public void invoke(Consumer<T> consumer) {
        for (T handler : handlers) {
            consumer.accept(handler);
        }
    }
}
