import java.lang.reflect.*;
import com.tnc.tnc.boss.fire.VolcanicTortoiseEntity;
import com.tnc.tnc.boss.fire.TortoiseDeathClock;
import net.minecraft.network.syncher.EntityDataAccessor;

public class SpawnConstructorTest {
    static Field field(String name)throws Exception{Field f=VolcanicTortoiseEntity.class.getDeclaredField(name);f.setAccessible(true);return f;}
    static void expect(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        for(boolean client:new boolean[]{false,true}){
            VolcanicTortoiseEntity boss=new VolcanicTortoiseEntity(client);
            expect(boss.health==900,"Superclass health initialization failed on "+client);
            expect(!boss.isDyingAnimation(),"New spawn mistaken for corpse on "+client);
            boss.setHealth(850);expect(boss.health==850,"Healthy mob health changes blocked");
            expect(boss.deathAge(.5F)==-1,"Healthy entity death age must be -1");
            if(client){
                expect(boss.constructorAge==-1,"Client constructor must not report active death age");
                var clock=(TortoiseDeathClock)field("clientDeathClock").get(boss);
                clock.receive(10,5);boss.tickCount=7;
                expect(boss.isDyingAnimation(),"Client death fallback not recognized");
                expect(boss.deathAge(.5F)==12.5F,"Client clock not advancing");
            }else{
                var age=(EntityDataAccessor<Integer>)field("DATA_DEATH_AGE").get(null);age.value=40;
                field("serverDeathTicks").setInt(boss,40);
                expect(boss.isDyingAnimation(),"Server metadata death not recognized");
                expect(boss.deathAge(.5F)==40.5F,"Server death age not retained");
            }
            boss.setHealth(800);expect(boss.health==850,"Dying health guard broken");
            // Restore the shared accessor so the next side represents a fresh spawn.
            var age=(EntityDataAccessor<Integer>)field("DATA_DEATH_AGE").get(null);age.value=-1;
            System.out.println("PASS "+(client?"client":"server")+" parent-constructor virtual setHealth and initialized death state");
        }
    }
}
