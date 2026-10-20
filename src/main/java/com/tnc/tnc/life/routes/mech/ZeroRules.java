package com.tnc.tnc.life.routes.mech;

/** A separate circuit: crystal-gun heat and overdrive do not interrupt Zero's rated endurance. */
public final class ZeroRules {
    public static final int PERIOD = 2, COST = 72;
    public enum Form {
        WATER("水", "water_cannon", "水炮弹", 0),
        FIRE("火", "great_fireball", "大火球术", 1),
        EARTH("土", "earth_ball", "土球", 4),
        LIGHTNING("雷", "great_thunder_orb", "大雷球", 2),
        WIND("风", "wind_ball_2", "大风球", 3);
        public final String name, spell, spellName;
        public final int colorIndex;
        Form(String name,String spell,String spellName,int colorIndex){this.name=name;this.spell=spell;this.spellName=spellName;this.colorIndex=colorIndex;}
        public static Form of(int index){return values()[Math.floorMod(index,values().length)];}
    }
    public static int shots(int mana){return Math.max(0,mana)/COST;}
    public static double seconds(int mana){return shots(mana)*PERIOD/20.0;}
    private ZeroRules(){}
}
