package com.tnc.tnc.warrior.forge;

/** Tick-based authoritative workshop rules, independent of Minecraft/client frame rate. */
public final class ForgeSession {
    public enum Stage { HEATING, SHAPING, QUENCHING, ASSEMBLY, READY }
    public ForgePattern pattern;
    public Stage stage = Stage.HEATING;
    public int heat = 80, strokes, corrections, overheatTicks, pumps, heatingTicks, goodHeatTicks;
    public int quenchTicks, pose, assemblyTicks, alignedTicks;
    public double heatScore, hammerScore, shapeScore, quenchScore, assemblyScore;
    public double strikeTotal;
    public final double[] deviation = new double[3];
    public boolean burned, cracked, quenched, slow;
    public int lastStrike = -1000, activeTicks;

    public ForgeSession(ForgePattern pattern) { this.pattern = pattern; }

    public void tick() {
        activeTicks++;
        if (stage == Stage.HEATING) {
            heatingTicks++;
            heat = Math.min(1000, heat + (heatingTicks % 2 == 0 ? 3 : 2));
            if (heat >= 700 && heat <= 800) goodHeatTicks++;
            if (heat > 850 && ++overheatTicks >= 100) burned = true;
        } else if (stage == Stage.SHAPING && activeTicks % (slow ? 8 : 5) == 0) {
            heat = Math.max(0, heat - 1);
        }
    }

    public boolean pump() {
        if (stage != Stage.HEATING || pumps >= 2) return false;
        heat = Math.min(1000, heat + 100); pumps++; return true;
    }

    public boolean takeFromFire() {
        if (stage != Stage.HEATING || heat < 650) return false;
        heatScore = ForgeQuality.clamp(100 - Math.max(0, Math.abs(heat - 750) - 25) * .25 - (burned ? 40 : 0));
        stage = Stage.SHAPING; return true;
    }

    /** A missed beat costs only 15%; wrong region/force is visible and remains repairable. */
    public double strike(int zone, int heldTicks) {
        if (stage != Stage.SHAPING || strokes >= 12 || zone < 0 || zone > 2
                || heldTicks < 0 || heldTicks > 100 || activeTicks - lastStrike < 8 || heat < 650) return -1;
        boolean heavy = heldTicks >= 9;
        double position = zone == pattern.zone(strokes) ? 100 : 25;
        double force = heavy == pattern.heavy(strokes) ? 100 : 35;
        int gap = activeTicks - lastStrike;
        double rhythm = strokes == 0 ? 100 : ForgeQuality.clamp(100 - Math.max(0, Math.abs(gap - 28) - (slow ? 7 : 4)) * 3);
        double result = .50 * position + .35 * force + .15 * rhythm;
        if (position < 100 || force < 100) deviation[zone] = Math.min(100, deviation[zone] + (100 - result) * .65);
        else deviation[zone] = Math.max(0, deviation[zone] - 8);
        strikeTotal += result; strokes++; lastStrike = activeTicks;
        if (strokes == 12) finishShape();
        return result;
    }

    private void finishShape() {
        hammerScore = strikeTotal / 12;
        shapeScore = ForgeQuality.clamp(100 - (deviation[0] + deviation[1] + deviation[2]) / 3);
    }

    public boolean correct(int zone) {
        if (stage != Stage.SHAPING || strokes != 12 || corrections >= 2 || zone < 0 || zone > 2 || deviation[zone] <= 0) return false;
        deviation[zone] = Math.max(0, deviation[zone] - 45); corrections++; finishShape(); return true;
    }

    public boolean toQuench() {
        if (stage != Stage.SHAPING || strokes != 12) return false;
        stage = Stage.QUENCHING; heat = Math.min(650, heat); return true;
    }

    public boolean reheat() {
        if (stage != Stage.SHAPING || heat >= 650) return false;
        heat = 750; return true;
    }

    public boolean finishQuench(int heldTicks, int selectedPose) {
        if (stage != Stage.QUENCHING || heldTicks < 20 || heldTicks > 72000) return false;
        double timing = ForgeQuality.clamp(100 - Math.max(0, Math.abs(heldTicks - 80) - (slow ? 12 : 8)) * 1.5);
        double angle = selectedPose == pattern.pose() ? 100 : 35;
        double temperature = ForgeQuality.clamp(100 - Math.max(0, Math.abs(heat - 625) - 25) * .3);
        quenchScore = .3 * temperature + .3 * angle + .4 * timing;
        quenchTicks = heldTicks; pose = selectedPose; cracked = quenchScore < 40;
        quenched = true; stage = Stage.ASSEMBLY; return true;
    }

    public boolean requench() {
        if (stage != Stage.ASSEMBLY || !quenched) return false;
        // Costs another bath use at the station; never rerolls earlier scores.
        stage = Stage.QUENCHING; return true;
    }

    public boolean assemble(int heldTicks, boolean aligned) {
        if (stage != Stage.ASSEMBLY || heldTicks < 10 || heldTicks > 100) return false;
        assemblyScore = (aligned ? 75 : 25) + .25 * ForgeQuality.clamp(100 - Math.abs(heldTicks - 20) * 3);
        assemblyTicks = heldTicks; stage = Stage.READY; return true;
    }

    public ForgeQuality quality() {
        return ForgeQuality.grade(heatScore, hammerScore, shapeScore, quenchScore, assemblyScore, burned || cracked);
    }

    public double score() { return ForgeQuality.score(heatScore, hammerScore, shapeScore, quenchScore, assemblyScore); }
}
