package com.tnc.tnc.warrior;

import com.tnc.tnc.bronze.BronzeProfile;
import java.lang.reflect.Method;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;
import static org.junit.jupiter.api.Assertions.*;

/** Replay the real private basicAttack body with only the game/network boundaries replaced. */
public class WarriorBasicAttackSoundTest {
    public static class Item {}
    public static class BronzeItem extends Item {
        public final BronzeProfile profile;
        public BronzeItem(BronzeProfile profile){this.profile=profile;}
    }
    public static class Stack {
        private final Item item;
        public Stack(Item item){this.item=item;}
        public Item getItem(){return item;}
    }
    public static class Position {}
    public static class Sound {}
    public enum Source { PLAYERS }
    public static class Sounds { public static final Sound PLAYER_ATTACK_SWEEP=new Sound(); }
    public static class Level {
        int sounds;
        public void playSound(Player ignored,Position at,Sound sound,Source source,float volume,float pitch){
            assertSame(Sounds.PLAYER_ATTACK_SWEEP,sound);assertSame(Source.PLAYERS,source);
            assertTrue(volume>0);assertTrue(pitch>0);sounds++;
        }
    }
    public static class Player {
        final Level level=new Level();final Stack stack;
        public Player(Item item){stack=new Stack(item);}
        public Level serverLevel(){return level;}
        public Position blockPosition(){return new Position();}
        public Stack getMainHandItem(){return stack;}
    }
    public static class State {
        public WarriorAnimation lastAnimation;
        public long lastAnimationAt=Long.MIN_VALUE,lastSlash=Long.MIN_VALUE;
        public Map<WarriorMove,Long> lastSlashByMove=new EnumMap<>(WarriorMove.class);
    }
    public static class Network {
        static int animations;
        public static void broadcast(Player player,WarriorAnimation animation,int duration){animations++;}
    }
    public static class Boundary {
        public static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("basic-attack-replay");
        static int extraDamage;
        public static long elapsed(long now,long previous){return previous==Long.MIN_VALUE?Long.MAX_VALUE:now-previous;}
        public static void slash(Player p,WarriorMove move,boolean animate){extraDamage++;}
    }
    private static Method replay() throws Exception {
        String owner="com/tnc/tnc/warrior/WarriorComboService";
        ClassNode original=new ClassNode();
        try(var input=WarriorBasicAttackSoundTest.class.getResourceAsStream("/"+owner+".class")){
            assertNotNull(input);new ClassReader(input).accept(original,0);
        }
        var body=original.methods.stream().filter(m->m.name.equals("basicAttack")).findFirst().orElseThrow();
        String generated="com/tnc/tnc/warrior/BasicAttackSoundReplay";
        Map<String,String> map=new HashMap<>();
        map.put(owner,generated);map.put(owner+"$State",Type.getInternalName(State.class));
        map.put("net/minecraft/server/level/ServerPlayer",Type.getInternalName(Player.class));
        map.put("net/minecraft/world/entity/player/Player",Type.getInternalName(Player.class));
        map.put("net/minecraft/server/level/ServerLevel",Type.getInternalName(Level.class));
        map.put("net/minecraft/world/item/ItemStack",Type.getInternalName(Stack.class));
        map.put("net/minecraft/world/item/Item",Type.getInternalName(Item.class));
        map.put("com/tnc/tnc/bronze/BronzeWeaponItem",Type.getInternalName(BronzeItem.class));
        map.put("net/minecraft/core/BlockPos",Type.getInternalName(Position.class));
        map.put("net/minecraft/sounds/SoundEvents",Type.getInternalName(Sounds.class));
        map.put("net/minecraft/sounds/SoundEvent",Type.getInternalName(Sound.class));
        map.put("net/minecraft/sounds/SoundSource",Type.getInternalName(Source.class));
        map.put("com/tnc/tnc/warrior/WarriorAnimationPacket",Type.getInternalName(Network.class));
        Remapper names=new SimpleRemapper(map);ClassWriter result=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        result.visit(Opcodes.V17,Opcodes.ACC_PUBLIC|Opcodes.ACC_SUPER,generated,null,Type.getInternalName(Boundary.class),null);
        body.accept(new MethodRemapper(result.visitMethod(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC,body.name,names.mapMethodDesc(body.desc),null,null),names));
        result.visitEnd();
        // Use the same loader/package so javac's package-private enum switch table remains accessible.
        return java.lang.invoke.MethodHandles.lookup().defineHiddenClass(result.toByteArray(),true).lookupClass()
            .getMethod("basicAttack",Player.class,State.class,WarriorMove.class,long.class);
    }
    @Test void acceptedDaoLeftClickSwishesEvenWithNoTargetAndNeverAddsDamage() throws Exception {
        var run=replay();var player=new Player(new BronzeItem(BronzeProfile.DAO));var state=new State();
        Network.animations=Boundary.extraDamage=0;
        run.invoke(null,player,state,WarriorMove.HORIZONTAL,100L);
        assertEquals(1,Network.animations);
        assertEquals(1,player.level.sounds,"An accepted bronze dao left-click must emit its sweep sound even on a miss");
        assertEquals(0,Boundary.extraDamage,"Vanilla left-click already owns damage; audio must not call slash");
        run.invoke(null,player,state,WarriorMove.HORIZONTAL,101L);
        assertEquals(1,Network.animations);assertEquals(1,player.level.sounds,"Suppressed duplicate input must not stack sounds");
        run.invoke(null,player,state,WarriorMove.HORIZONTAL,100L+WarriorAnimation.SLASH_HORIZONTAL.durationTicks());
        assertEquals(2,player.level.sounds);
    }
    @Test void otherNativeLeftClickWeaponsKeepTheirExistingAudioPath() throws Exception {
        var run=replay();Network.animations=Boundary.extraDamage=0;
        for(Item item:List.of(new Item(),new BronzeItem(BronzeProfile.GREATSWORD),new BronzeItem(BronzeProfile.HALBERD))){
            var player=new Player(item);run.invoke(null,player,new State(),WarriorMove.HORIZONTAL,100L);
            assertEquals(0,player.level.sounds,"Do not layer the dao-specific fallback over another weapon");
        }
        assertEquals(0,Boundary.extraDamage);
    }
}
