package com.tnc.tnc.world.ink;

/** Server and renderer share the same named attack phases. */
public final class InkTigerAttackRules {
    public static final int RISE=25, SPIT=65, DESCEND=85, AIR_END=120;
    public static final int GHOST_RELEASE=22, GHOST_END=125;
    public static final double GHOST_SPEED=2.3;
    public static final int GATHER=20, DETONATE=90, SPIN=102, SPIN_END=138, ROAR_END=150;
    public static final float BODY_MULTIPLIER=4, BODY_SCALE=1.15F*BODY_MULTIPLIER;
    public static final double GHOST_SCALE=5, SUCTION_RADIUS=22, SUCTION_CAP=.45, SUCTION_ACCELERATION=.18;
    private InkTigerAttackRules(){}
    public static int duration(int move){return switch(move){case 1->AIR_END;case 2->GHOST_END;case 3->ROAR_END;case 4->64;default->0;};}
    public static boolean slashAt(int age){return age>=SPIN&&age<SPIN_END&&(age-SPIN)%12==0;}
    public static double roarBallRadius(double age){return Math.max(0,Math.min(1,(age-GATHER)/(DETONATE-GATHER)))*5.5;}
}
