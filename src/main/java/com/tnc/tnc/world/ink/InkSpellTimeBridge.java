package com.tnc.tnc.world.ink;

import net.minecraft.world.entity.LivingEntity;

/** Optional Spell Engine bridge. Retimes an already running cast without resetting its progress. */
public final class InkSpellTimeBridge {
    private static boolean warned;
    private record Stamp(long tick,int length,float speed){}
    private static final java.util.Map<LivingEntity,Stamp> STAMPS=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<Object,Boolean> SLOWED=java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    public static void markNewCast(LivingEntity e,float speed,int length){STAMPS.put(e,new Stamp(e.level().getGameTime(),length,speed));}
    public static long retimedStart(long now,long start,double factor){return now-Math.round(Math.max(0,now-start)*factor);}
    public static int retimedLength(int length,double factor){return (int)Math.max(1,Math.min(Integer.MAX_VALUE,Math.round(length*factor)));}
    public static void retime(LivingEntity entity,double factor){
        if(entity==null)return;
        try{
            Class<?> caster=Class.forName("net.spell_engine.internals.casting.SpellCasterEntity",false,InkSpellTimeBridge.class.getClassLoader());
            if(!caster.isInstance(entity))return;Object process=caster.getMethod("getSpellCastProcess").invoke(entity);if(process==null)return;
            Class<?> type=process.getClass();int length=(int)type.getMethod("length").invoke(process);if(length<=0)return;
            long start=(long)type.getMethod("startedAt").invoke(process);float speed=(float)type.getMethod("speed").invoke(process);
            var stamp=STAMPS.get(entity);boolean alreadySlow=SLOWED.containsKey(process)||stamp!=null&&Math.abs(stamp.tick-start)<=2&&stamp.length==length&&Math.abs(stamp.speed-speed)<.00001F;
            if(factor>1&&alreadySlow||factor<1&&!alreadySlow)return;
            Object updated=type.getDeclaredConstructors()[0].newInstance(type.getMethod("id").invoke(process),type.getMethod("spell").invoke(process),
                type.getMethod("item").invoke(process),(float)(speed/factor),retimedLength(length,factor),retimedStart(entity.level().getGameTime(),start,factor));
            if(factor>1)SLOWED.put(updated,true);else SLOWED.remove(process);
            if(entity.level().isClientSide){
                // ClientPlayer's local cast is separate from its synchronized spectator copy.
                var field=entity.getClass().getDeclaredField("spellCastProcess");field.setAccessible(true);field.set(entity,updated);
            }else caster.getMethod("setSpellCastProcess",type).invoke(entity,updated);
        }catch(ClassNotFoundException ignored){}catch(ReflectiveOperationException|LinkageError|RuntimeException error){
            if(!warned){warned=true;org.slf4j.LoggerFactory.getLogger(InkSpellTimeBridge.class).warn("Cannot retime an in-progress Spell Engine cast",error);}
        }
    }
}
