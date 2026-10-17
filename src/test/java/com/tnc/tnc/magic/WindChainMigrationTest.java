package com.tnc.tnc.magic;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import com.tnc.tnc.magic.wind.WindChainRules;
import static org.junit.jupiter.api.Assertions.*;
class WindChainMigrationTest {
    @Test void oldSummonOwnershipAndKeysBecomeWindBallsWithoutGrantingWeapons(){
        var old=new MagicStoneData();old.setProgress(Element.WIND,SpellCatalog.Chain.SUMMON,4);
        old.learn(ResourceLocation.fromNamespaceAndPath("tnc","triple_wind_spirit"));
        var restored=new MagicStoneData();restored.deserializeNBT(old.serializeNBT());
        assertEquals(4,restored.getProgress(Element.WIND,SpellCatalog.Chain.WIND_BALL));
        assertEquals(0,restored.getProgress(Element.WIND,SpellCatalog.Chain.WIND_WEAPON));
        assertTrue(restored.hasLearned(ResourceLocation.fromNamespaceAndPath("tnc","wind_ball_4")));
        assertFalse(restored.serializeNBT().toString().contains("triple_wind_spirit"));
        assertFalse(SpellCatalog.chainsOf(Element.WIND).contains(SpellCatalog.Chain.SUMMON));
        assertEquals(4,SpellCatalog.chainsOf(Element.WIND).size());
    }
    @Test void requestedCountsAndCompressionAreExact(){
        assertArrayEquals(new int[]{3,10,0,20,3},java.util.stream.IntStream.rangeClosed(1,5).map(WindChainRules::swords).toArray());
        assertArrayEquals(new int[]{0,0,5,10,2},java.util.stream.IntStream.rangeClosed(1,5).map(WindChainRules::spears).toArray());
        assertArrayEquals(new float[]{2,3,5,5,60},new float[]{WindChainRules.modelScale(1),WindChainRules.modelScale(2),WindChainRules.modelScale(3),WindChainRules.modelScale(4),WindChainRules.modelScale(5)});
        for(int t=3;t<=5;t++){double previous=WindChainRules.ballSize(t,0);for(int a=1;a<=WindChainRules.compressionTicks(t);a++){double current=WindChainRules.ballSize(t,a);assertTrue(current<previous&&current>0);previous=current;}}
        assertEquals(.12,WindChainRules.ballSize(5,30),1E-6);
        assertTrue(WindChainRules.compressionTicks(5)<WindChainRules.compressionTicks(4));
        for(int t=2;t<=5;t++)assertTrue(WindChainRules.ballRadius(t)>WindChainRules.ballRadius(t-1));
    }
}
