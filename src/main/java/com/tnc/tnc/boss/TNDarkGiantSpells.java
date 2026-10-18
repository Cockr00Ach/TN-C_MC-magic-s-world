package com.tnc.tnc.boss;

import net.minecraft.world.entity.LivingEntity;

/** Boss casts use the same hand, drain, fog and summon entities as the player chains. */
public final class TNDarkGiantSpells {
    public static final double CAST_RANGE=96, FOLLOW_RANGE=128;
    private static final int SUMMON=0,HAND=1,FOG=2,SIPHON=3;
    public record Move(String path,int tier,int chain,int castTicks,int weight,String name) {}
    private static final Move[] MOVES={
        new Move("dark_king",4,SUMMON,56,2,"暗之国王"),
        new Move("evil_god",5,SUMMON,56,2,"邪神"),
        new Move("night_embrace",3,HAND,44,2,"黑夜之手 T3"),
        new Move("black_ruin",4,HAND,44,12,"黑夜之手 T4"),
        new Move("slay_light",5,HAND,56,3,"黑夜之手 T5"),
        new Move("devour_light",5,FOG,52,2,"黑雾 T5"),
        new Move("sacrifice",3,SIPHON,44,3,"以伤换伤 T3")
    };
    private TNDarkGiantSpells(){}
    public static int count(){return MOVES.length;}
    public static Move move(int index){return MOVES[Math.max(0,Math.min(MOVES.length-1,index))];}
    public static int pick(net.minecraft.util.RandomSource random){return weightedPick(random,-1,true);}
    /** Start with siphon; alternate the two T3 moves once every three completed casts. */
    public static int pick(net.minecraft.util.RandomSource random,int completedCasts,int previous){
        if(completedCasts%3==0)return (completedCasts/3)%2==0?6:2;
        if(completedCasts%3==1)return 3;
        return weightedPick(random,previous,false);
    }
    private static int weightedPick(net.minecraft.util.RandomSource random,int previous,boolean includeTierThree){
        int total=0;
        for(int i=0;i<MOVES.length;i++)if((i!=previous||i==3)&&(includeTierThree||MOVES[i].tier()!=3))total+=MOVES[i].weight();
        int roll=random.nextInt(total);
        for(int i=0;i<MOVES.length;i++)if((i!=previous||i==3)&&(includeTierThree||MOVES[i].tier()!=3)){
            roll-=MOVES[i].weight();if(roll<0)return i;
        }
        return 0;
    }
    public static String animation(Move move){
        return switch(move.chain()){
            case SUMMON -> "command_army";
            case FOG -> "cast_mist";
            case SIPHON -> "cast_siphon";
            default -> move.tier()>=5?"cast_sky_hands":"cast_hands";
        };
    }
    public static boolean cast(LivingEntity caster,int index){
        return TNDarkGiantCasting.cast(caster,move(index));
    }
    public static String describe(){return "巨兽人领主招式："+java.util.Arrays.toString(paths());}
    public static String[] paths(){return java.util.Arrays.stream(MOVES).map(Move::path).toArray(String[]::new);}
    public static int tierOf(String path){for(var m:MOVES)if(m.path().equals(path))return m.tier();return -1;}
}
