package com.tnc.tnc.skyacademy;

import java.util.List;

/** Shared timing for the mountain conversation and the player's first response. */
public final class FirstSightScript {
    public record Line(String text, int ticks) {}

    public static List<Line> lines(String name) {
        return List.of(
                new Line(name + "，听。风还没有睡。", 70),
                new Line("魔力也是。它穿过树根，停在水面。", 90),
                new Line("让心里的魔力，轻轻回应它。", 90),
                new Line("你会看见它的流向，也会发现藏起来的路。", 110),
                new Line("不必追逐那些光。静下来，感受它。", 100),
                new Line(name + "，现在，让这个世界也向你走近一点。", 120));
    }

    public static int duration(String name) {
        return lines(name).stream().mapToInt(Line::ticks).sum();
    }

    public static int lineAt(String name, int tick) {
        var lines = lines(name);
        int end = 0;
        for (int index = 0; index < lines.size(); index++) {
            end += lines.get(index).ticks();
            if (tick < end) return index;
        }
        return lines.size() - 1;
    }

    /** Blackout lasts three seconds. Each card has its own interval and a blank gap. */
    public static String blackoutCaption(double seconds) {
        if (seconds >= .25 && seconds < .95) return "那就";
        if (seconds >= 1.25 && seconds < 2.85) return "让我好好看看这个世界吧";
        return "";
    }

    private FirstSightScript() {}
}
