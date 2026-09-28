package com.tnc.tnc.magic;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AutoLearningRulesTest {
    @Test void independentChaosIsNeverAnElementalAutoLearnCandidate() {
        var data=new MagicStoneData();
        var chaos=SpellCatalog.byId(ResourceLocation.fromNamespaceAndPath("tnc","chaos_magic"));
        assertFalse(MagicStoneLearning.isElementalAutoLearnCandidate(data,chaos));
    }
    @Test void elementalCatchUpKeepsAffinityAndAlreadyLearnedFilters() {
        var data=new MagicStoneData();
        var water=SpellCatalog.byId(ResourceLocation.fromNamespaceAndPath("tnc","dragon_ruin"));
        data.setAffinity(Element.WATER,3);
        assertFalse(MagicStoneLearning.isElementalAutoLearnCandidate(data,water));
        data.setAffinity(Element.WATER,5);
        assertTrue(MagicStoneLearning.isElementalAutoLearnCandidate(data,water));
        data.learn(water.id());
        assertFalse(MagicStoneLearning.isElementalAutoLearnCandidate(data,water));
    }
}
