package com.tnc.tnc.warrior;
import java.util.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
/**
 * Lesson completions are practice records, never a second source of growth rewards.
 *
 * <p>两套计数并存：
 * <ul>
 *   <li>{@code role} —— 这位教官一共结课多少次（旧版就在用，含义不变，只增不减）；</li>
 *   <li>{@code role/courseId} —— 这一节<b>具体哪一课</b>出师了没有（0/1）。
 *       课程表靠它决定下一课开哪一节，所以出师后再练不会把进度打回第一课。</li>
 * </ul>
 */
public final class SpecialistTrainingData extends SavedData {
    private final Map<UUID,CompoundTag> records=new HashMap<>();
    public static SpecialistTrainingData get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(SpecialistTrainingData::load,SpecialistTrainingData::new,"tnc_specialist_training_v1");}
    /** 课程键；改了这个规则等于把所有人的专项课进度清零，别动。 */
    static String key(String role,String course){return role+"/"+course;}
    public int count(UUID id,String role){return Math.max(0,records.getOrDefault(id,new CompoundTag()).getInt(role));}
    public void complete(UUID id,String role){var t=records.computeIfAbsent(id,k->new CompoundTag());int n=count(id,role);t.putInt(role,n==Integer.MAX_VALUE?n:n+1);setDirty();}
    /** 这一课出师了没有。 */
    public boolean done(UUID id,String role,String course){return count(id,key(role,course))>0;}
    /** 记一次结课：总次数 +1，并把这一课标记为已出师（已出师的课复习不重复记）。 */
    public void graduate(UUID id,String role,String course){complete(id,role);if(!done(id,role,course))complete(id,key(role,course));}
    /**
     * 只留还存在的课程键。
     *
     * <p>课程 id 是存档键的一半，改一次名就留下一堆 {@code role/老id}。它们不只是占地方：
     * 将来若有新课程用了同一个 id，老进度会被当成"已经出师"，玩家直接跳过一课。
     * 加载时顺手扔掉，下次保存就干净了。
     */
    static CompoundTag keepKnownCourses(CompoundTag lessons){
        var out=new CompoundTag();
        for(String key:lessons.getAllKeys()){
            int slash=key.indexOf('/');
            if(slash>0){
                String role=key.substring(0,slash);
                if(!InstructorCourses.known(role)||InstructorCourses.course(role,key.substring(slash+1))==null)continue;
            }
            out.put(key,lessons.get(key));
        }
        return out;
    }
    public static SpecialistTrainingData load(CompoundTag tag){var d=new SpecialistTrainingData();for(Tag raw:tag.getList("Records",Tag.TAG_COMPOUND)){var t=(CompoundTag)raw;if(t.hasUUID("Id"))d.records.put(t.getUUID("Id"),keepKnownCourses(t.getCompound("Lessons").copy()));}return d;}
    @Override public CompoundTag save(CompoundTag tag){var list=new ListTag();records.forEach((id,t)->{var r=new CompoundTag();r.putUUID("Id",id);r.put("Lessons",t.copy());list.add(r);});tag.put("Records",list);return tag;}
}
