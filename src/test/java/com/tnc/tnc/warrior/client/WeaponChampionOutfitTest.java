package com.tnc.tnc.warrior.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponChampionOutfitTest {
    @Test
    void bakesEverySkeletonAndAnimatesAnimalsWithoutHumanoidGeometry() throws Exception {
        var forms = EnumSet.noneOf(WeaponChampionOutfit.Form.class);
        var directory = Path.of("src/main/resources/assets/tnc/models/entity/champions");
        try (var files = Files.list(directory)) {
            for (var file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                try (var source = Files.newBufferedReader(file)) {
                    var model = WeaponChampionOutfit.bake(source);
                    if (model.beast() == null) {
                        assertFalse(model.root().getChild("body").isEmpty(), file.toString());
                        continue;
                    }
                    forms.add(model.beast().form());
                    for (String bone : List.of("head", "hat", "body", "right_arm", "left_arm", "right_leg", "left_leg"))
                        assertTrue(model.root().getChild(bone).isEmpty(), file + " exposes the vanilla mesh on " + bone);
                    assertTrue(model.root().hasChild(model.beast().gripBone()), file.toString());
                    if (model.beast().form() == WeaponChampionOutfit.Form.UPRIGHT) {
                        assertEquals("right_arm", model.beast().gripBone(), file.toString());
                        assertTrue(model.root().getChild("right_arm").y + 6 < model.root().getChild("right_leg").y,
                            file + " needs a shoulder above the hip in the standing rig");
                    }
                    assertFalse(model.joints().isEmpty(), file.toString());
                    for (var joint : model.joints()) {
                        joint.pose(0, 0, 1, 0, 0);
                        var charged = joint.part().storePose();
                        joint.pose(0, 0, 0, 1, 0);
                        joint.pose(0, 0, 1, 0, 0);
                        assertEquals(charged.xRot, joint.part().xRot, .00001, file.toString());
                        assertEquals(charged.yRot, joint.part().yRot, .00001, file.toString());
                        assertEquals(charged.zRot, joint.part().zRot, .00001, file.toString());
                    }
                }
            }
        }
        assertEquals(EnumSet.allOf(WeaponChampionOutfit.Form.class), forms);
    }
}
