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
        assertFalse(MagicStoneLearning.isElementalAutoLearnCandidate(data,water), "Affinity is not purchased progress");
        data.setProgress(water.element(),water.chain(),water.tier());
        assertTrue(MagicStoneLearning.isElementalAutoLearnCandidate(data,water));
        data.learn(water.id());
        assertFalse(MagicStoneLearning.isElementalAutoLearnCandidate(data,water));
    }
    @Test void explicitForgetSurvivesSaveAndCopyAndCannotBeAutoRelearned() {
        var data=new MagicStoneData();
        var water=SpellCatalog.byId(ResourceLocation.fromNamespaceAndPath("tnc","dragon_ruin"));
        data.setAffinity(Element.WATER,5);
        data.setProgress(water.element(),water.chain(),water.tier());
        data.learn(water.id());
        assertTrue(data.forget(water.id()));
        var loaded=new MagicStoneData();loaded.deserializeNBT(data.serializeNBT());
        var copied=new MagicStoneData();copied.copyFrom(loaded);
        for(var state:java.util.List.of(data,loaded,copied)) {
            assertFalse(state.hasLearned(water.id()));
            assertTrue(state.hasPreviouslyLearned(water.id()));
            assertEquals(0,MagicStoneLearning.learningCost(state,water));
            assertFalse(MagicStoneLearning.isElementalAutoLearnCandidate(state,water), "Explicit forgetting beats catch-up");
        }
    }
    @Test void legacyLearnedOwnershipMigratesAndFullResetClearsHistory() {
        var water=SpellCatalog.byId(ResourceLocation.fromNamespaceAndPath("tnc","water_ball"));
        var data=new MagicStoneData();data.learn(water.id());
        var tag=data.serializeNBT();tag.remove("LearningHistory");tag.remove("ExplicitlyForgotten");
        var loaded=new MagicStoneData();loaded.deserializeNBT(tag);
        loaded.forget(water.id());
        assertEquals(0,MagicStoneLearning.learningCost(loaded,water));
        loaded.deserializeNBT(new net.minecraft.nbt.CompoundTag());
        assertFalse(loaded.hasPreviouslyLearned(water.id()));
        assertFalse(loaded.isExplicitlyForgotten(water.id()));
    }
    @Test void forgettingAllAlsoSuppressesCatalogReplacementSpells() {
        var data=new MagicStoneData();
        var water=SpellCatalog.byId(ResourceLocation.fromNamespaceAndPath("tnc","dragon_ruin"));
        data.setAffinity(Element.WATER,5);data.setProgress(water.element(),water.chain(),5);
        data.suppressElementalCatchUp();
        assertFalse(MagicStoneLearning.isElementalAutoLearnCandidate(data,water));
        data.learn(water.id());data.forget(water.id());
        assertFalse(MagicStoneLearning.isElementalAutoLearnCandidate(data,water));
    }
}
