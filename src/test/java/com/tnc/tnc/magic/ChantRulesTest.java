package com.tnc.tnc.magic;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChantRulesTest {
    @Test void onlyLegendaryAndGodTierChant() {
        for(var entry:SpellCatalog.all())assertEquals(!entry.independent()&&entry.tier()>=4,ChantRules.chants(entry));
    }
    @Test void waterAndLightningUseDifferentCompleteClauses() {
        var water=SpellCatalog.byId(ResourceLocation.fromNamespaceAndPath("tnc","dragon_ruin"));
        var thunder=SpellCatalog.byId(ResourceLocation.fromNamespaceAndPath("tnc","heavenly_thunder"));
        assertEquals(3,ChantRules.words(water).length);assertEquals(3,ChantRules.words(thunder).length);
        assertNotEquals(ChantRules.line(water,0),ChantRules.line(thunder,0));
        assertEquals(ChantRules.words(water)[2],ChantRules.line(water,1));
        assertEquals(ChantRules.words(water)[0],ChantRules.line(water,-1));
    }
    @Test void learningStreamsConvergeToChest() {
        var chest=new net.minecraft.world.phys.Vec3(1,2,3);
        assertTrue(LearningVisuals.point(chest,0,0,0).distanceTo(chest)>3);
        for(int i=0;i<6;i++)assertEquals(chest,LearningVisuals.point(chest,40,i,0));
        assertNotEquals(LearningVisuals.color(Element.WATER),LearningVisuals.color(Element.FIRE));
    }
}
