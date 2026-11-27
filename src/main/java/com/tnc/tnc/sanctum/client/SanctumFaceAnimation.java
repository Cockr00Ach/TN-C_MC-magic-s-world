package com.tnc.tnc.sanctum.client;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import static com.tnc.tnc.sanctum.SanctumTimings.AWAKEN;
import static com.tnc.tnc.sanctum.SanctumTimings.SMILE_OPEN;

/** Rigid face samples are bound to the head. Lip and eyelid cuts give successive samples different topology. */
final class SanctumFaceAnimation {
    private static final int FIRST = 24, LOOK_AHEAD = 10;
    private static final String PREFIX = "awaken/face_";
    private static final String[] DETAIL = new String[SMILE_OPEN + 1], OVERVIEW = new String[SMILE_OPEN + 1];
    private static long expires = Long.MIN_VALUE;
    private static int firstCached = FIRST, interval;

    static {
        for (int tick = 0; tick <= SMILE_OPEN; tick++) {
            DETAIL[tick] = PREFIX + String.format(Locale.ROOT, "%03d", tick);
            OVERVIEW[tick] = DETAIL[tick] + "_view";
        }
    }

    static void prime(long now) {
        expires = Math.max(expires, now + AWAKEN + 20);
        SanctumMesh.request(DETAIL[0]);
        SanctumMesh.request(OVERVIEW[0]);
    }

    static void tick(long now) {
        if (now >= expires) end();
    }

    static void end() {
        if (expires != Long.MIN_VALUE) SanctumMesh.forgetFaceFrames();
        expires = Long.MIN_VALUE;
        firstCached = FIRST;
    }

    static void resourcesChanged() {
        end();
        interval = 0;
    }

    static boolean isFrameKey(String key) {
        if (!key.startsWith(PREFIX)) return false;
        String suffix = key.substring(PREFIX.length());
        if (suffix.length() != 3 && suffix.length() != 8) return false;
        int tick = 0;
        for (int i = 0; i < 3; i++) {
            int digit = suffix.charAt(i) - '0';
            if (digit < 0 || digit > 9) return false;
            tick = tick * 10 + digit;
        }
        return tick <= SMILE_OPEN && (key.equals(DETAIL[tick]) || key.equals(OVERVIEW[tick]))
                && (tick == 0 || tick >= FIRST);
    }

    private static int interval() {
        if (interval == 0) {
            var odd = ResourceLocation.fromNamespaceAndPath("tnc", "models/sanctum/" + DETAIL[FIRST + 1] + ".mesh.gz");
            interval = Minecraft.getInstance().getResourceManager().getResource(odd).isPresent() ? 1 : 2;
        }
        return interval;
    }

    static SanctumMesh at(float frame, boolean near) {
        if (frame >= SMILE_OPEN) return null;
        int step = interval();
        int chosen = frame < FIRST ? 0 : FIRST + ((int)frame - FIRST) / step * step;
        String[] keys = near ? DETAIL : OVERVIEW;
        String[] other = near ? OVERVIEW : DETAIL;
        int first = Math.max(FIRST, chosen);
        for (int k = first; k <= Math.min(first + LOOK_AHEAD, SMILE_OPEN - 1); k += step) SanctumMesh.request(keys[k]);
        int previous = Math.max(FIRST, chosen - 5);
        for (; firstCached < previous; firstCached++) {
            SanctumMesh.forget(DETAIL[firstCached]);
            SanctumMesh.forget(OVERVIEW[firstCached]);
        }
        for (int k = chosen; k >= previous; k -= step) {
            var current = SanctumMesh.get(keys[k]);
            if (current != null) return current;
        }
        var closed = SanctumMesh.get(keys[0]);
        return closed != null ? closed : SanctumMesh.get(other[0]);
    }

    static boolean replaces(String bone) {
        return switch (bone) {
            case "head_shell", "head_oculus", "head_jaw", "head_plumage_L", "head_plumage_R" -> true;
            default -> false;
        };
    }

    private SanctumFaceAnimation() {}
}
