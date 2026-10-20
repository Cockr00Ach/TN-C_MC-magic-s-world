package com.tnc.tnc.world.ink;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InkWingOpacityTest {
    @Test void rearFeathersAreHalfTransparent(){
        assertEquals(.5F,InkWingOpacity.alpha("wing_l_primary_14"));
        assertEquals(.5F,InkWingOpacity.alpha("wing_r_covert_2_9"));
    }
    @Test void middleHasSeventyFivePercentTransparency(){
        assertEquals(.25F,InkWingOpacity.alpha("wing_l_fore"));
        assertEquals(.25F,InkWingOpacity.alpha("wing_r_covert_1_4"));
    }
    @Test void shoulderAndBodyStaySolid(){
        for(String name:new String[]{"wing_l","wing_r_covert_0_0","head","body","tail_0"})
            assertEquals(1F,InkWingOpacity.alpha(name));
    }
    @Test void foldedFeathersKeepTheSameOpacityZones(){
        assertEquals(1F,InkWingOpacity.alpha("folded_wing_l_feather_0"));
        assertEquals(.25F,InkWingOpacity.alpha("folded_wing_r_feather_6"));
        assertEquals(.5F,InkWingOpacity.alpha("folded_wing_l_feather_14"));
    }
}
