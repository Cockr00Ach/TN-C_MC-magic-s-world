package com.tnc.tnc.world.ink;

/** Deterministic, cross-chunk ink cliffs and bent umbrella pines. No world access. */
public final class InkLandscapePattern {
    public static final int MAX_HEIGHT = 88;
    public enum Material { INK, CHARCOAL, WASH, PAPER, NEEDLES, PALE_NEEDLES }
    private InkLandscapePattern() {}

    private static long hash(int x, int z) {
        long n = x * 341873128712L + z * 132897987541L + 0x693e21L;
        n = (n ^ (n >>> 29)) * 0x319642b2d24d8ec3L;
        return n ^ (n >>> 32);
    }

    public static int mountainHeight(int x, int z) {
        if ((double)x*x + (double)z*z < 52*52) return 0;
        int gx = Math.floorDiv(x, 192), gz = Math.floorDiv(z, 192);
        double highest = 0;
        for (int a = gx-1; a <= gx+1; a++) for (int b = gz-1; b <= gz+1; b++) {
            long seed = hash(a,b);
            int cx = a*192+72+(int)Math.floorMod(seed,49);
            int cz = b*192+72+(int)Math.floorMod(seed>>>12,49);
            for (int peak=0;peak<3;peak++) {
                double dx = x-cx+(peak-1)*23, dz = z-cz+(peak-1)*13;
                double distance = Math.sqrt(dx*dx/(34.0*34) + dz*dz/(25.0*25));
                double summit = 37 + Math.floorMod(seed>>>20,17) - Math.abs(peak-1)*9;
                double ridge = 1.7*Math.sin(x*.31+z*.12)+Math.sin(z*.43);
                highest = Math.max(highest, summit*Math.max(0,1-Math.pow(distance,1.7))+ridge*(distance<1?1:0));
            }
        }
        return Math.max(0,(int)highest);
    }

    public record Column(int x, int z, int mountain, int treeX, int treeZ, int treeBase, int treeHeight, boolean pine) {
        public Material at(int y) {
            if (y<=0 || y>MAX_HEIGHT) return null;
            if (y<=mountain) {
                // White sunward crests, grey washes, black broken vertical fissures.
                if (Math.floorMod(x+z/3,29)<2) return Material.PAPER;
                if (y==mountain) return Math.floorMod(x+z,13)<3 ? Material.PAPER : Material.WASH;
                if (Math.floorMod(x*3+z+y/9,23)<4) return Material.INK;
                return Math.floorMod(z+y/4,11)<4 ? Material.WASH : Material.CHARCOAL;
            }
            if (!pine) return null;
            int h=y-treeBase;
            if(h<1 || h>treeHeight+2)return null;
            double bend=4*Math.sin(h/(double)treeHeight*1.8);
            double dx=x-treeX-bend,dz=z-treeZ-h*.07;
            if (Math.abs(dx)<(h<5?1.2:.75) && Math.abs(dz)<.85) return Material.INK;
            for(int tier=0;tier<4;tier++) {
                int crown=treeHeight-tier*4;
                double r=tier==0?4.8:tier==1?7:tier==2?8.5:6.5;
                double offset=tier==0?-1:tier%2==0?-4:4;
                double canopyX=dx-offset,canopyZ=dz+(tier%2==0?1:-1);
                double spread=canopyX*canopyX/(r*r)+canopyZ*canopyZ/(r*r*.40);
                if(h==crown-1 && Math.abs(dz)<.8 && Math.abs(dx-offset*.5)<r*.75) return Material.INK;
                if(h>=crown && h<=crown+1 && spread<1-(h-crown)*.28) {
                    if(Math.floorMod(x*7+z*11+h,19)==0 && spread>.7)return null;
                    return h==crown+1 && Math.floorMod(x+z,4)==0 ? Material.PALE_NEEDLES : Material.NEEDLES;
                }
            }
            return null;
        }
    }

    public static Column column(int x,int z) {
        int gx=Math.floorDiv(x,64),gz=Math.floorDiv(z,64);
        long seed=hash(gx,gz);
        int tx=gx*64+20+(int)Math.floorMod(seed,25);
        int tz=gz*64+20+(int)Math.floorMod(seed>>>10,25);
        boolean pine=Math.floorMod(seed,3)!=0 && (double)tx*tx+(double)tz*tz>32*32;
        return new Column(x,z,mountainHeight(x,z),tx,tz,mountainHeight(tx,tz),
                19+(int)Math.floorMod(seed>>>24,7),pine);
    }
}
