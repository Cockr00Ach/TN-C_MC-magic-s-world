package com.tnc.tnc.bosshud;

import java.util.Locale;

/** Display-only arithmetic. Invalid combat values can never become invalid HUD vertices. */
public final class BossHudRules {
    public static final int CACHE_LIMIT=128,LEASE_TICKS=200;
    private BossHudRules(){}
    public static float maximum(float value){return Float.isFinite(value)&&value>0?value:1;}
    public static float health(float value,float max){return Float.isFinite(value)?Math.max(0,Math.min(maximum(max),value)):0;}
    public static float progress(float health,float max){return health(health,max)/maximum(max);}
    public static int phase(int phase,int total){return Math.max(1,Math.min(Math.max(1,total),phase));}
    public static int width(int screenWidth){return Math.max(64,Math.min(326,screenWidth-76));}
    public static int rowHeight(boolean compact){return compact?48:60;}
    public static String roman(int n){return switch(n){case 2->"II";case 3->"III";default->"I";};}
    public static String amount(float value){
        double v=Float.isFinite(value)?Math.max(0,value):0;
        if(v>=1e12)return String.format(Locale.ROOT,"%.1e",v);
        if(v>=1e9)return String.format(Locale.ROOT,"%.1fB",v/1e9);
        if(v>=1e6)return String.format(Locale.ROOT,"%.1fM",v/1e6);
        if(v>=10000)return String.format(Locale.ROOT,"%.1fk",v/1000);
        return Long.toString(Math.round(v));
    }
    public static int mix(int a,int b,double f){f=Math.max(0,Math.min(1,f));int r=(int)(((a>>16)&255)*(1-f)+((b>>16)&255)*f),g=(int)(((a>>8)&255)*(1-f)+((b>>8)&255)*f),bl=(int)((a&255)*(1-f)+(b&255)*f);return (r<<16)|(g<<8)|bl;}
    public static final class Trail {
        private float shown,ghost;private long previous,hold;private int phase;private boolean ready;
        public void update(float target,int currentPhase,long now){
            target=Float.isFinite(target)?Math.max(0,Math.min(1,target)):0;
            if(!ready||phase!=currentPhase||now<previous){shown=ghost=target;phase=currentPhase;previous=now;ready=true;return;}
            double dt=Math.min(100,Math.max(0,now-previous));previous=now;
            if(target<shown-.002F){ghost=Math.max(ghost,shown);hold=now+380;}
            if(target>shown+.002F){ghost=target;hold=0;}
            shown+=(target-shown)*(float)(1-Math.exp(-dt/60));if(Math.abs(shown-target)<.001F)shown=target;
            if(now>hold)ghost+=(shown-ghost)*(float)(1-Math.exp(-dt/280));ghost=Math.max(shown,ghost);
        }
        public float shown(){return shown;}public float ghost(){return ghost;}
    }
}
