package com.tnc.tnc.warrior;

/** Plots occupy distinct 64-block cells, far below the vanilla world border. */
public final class TrainingGroundRules {
    public static final int MAX_PLOTS=65536,FLOOR_Y=1,RADIUS=12,STRIKE_PERIOD=50,WINDUP=15;
    private TrainingGroundRules(){}
    public static int centerX(int plot){return 32+(valid(plot)%256)*64;}
    public static int centerZ(int plot){return 32+(valid(plot)/256)*64;}
    private static int valid(int plot){if(plot<0||plot>=MAX_PLOTS)throw new IllegalArgumentException("Invalid practice plot");return plot;}
    public static boolean contains(int plot,double x,double y,double z){
        return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)
            && Math.abs(x-(centerX(plot)+.5))<11 && Math.abs(z-(centerZ(plot)+.5))<11 && y>=1 && y<8;
    }
}
