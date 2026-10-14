package dev.firmages.core.gate;

import dev.firmages.core.age.AgeId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The KubeJS markers of one load (FirmAges.stationAge / requireAge / untilAge), SPEC §3.4. */
class RecipeMarkersTest {

    @BeforeEach
    void empty() {
        RecipeMarkers.drain();
    }

    @Test
    void drainHandsOutOnceAndStartsEmpty() {
        RecipeMarkers.stationAge("immersiveengineering:crusher", "age_4");
        RecipeMarkers.requireAge("firmages:injecting/hematite_rich", "age_7");
        RecipeMarkers.untilAge("firmages:byproducts/create/crushing/tuff/before_age_2", "age_2");
        RecipeMarkers.Snapshot s = RecipeMarkers.drain();
        assertEquals(Map.of("immersiveengineering:crusher", AgeId.AGE_4), s.stationAges());
        assertEquals(Map.of("firmages:injecting/hematite_rich", Set.of(AgeId.AGE_7)), s.requireAges());
        assertEquals(Map.of("firmages:byproducts/create/crushing/tuff/before_age_2", Set.of(AgeId.AGE_2)), s.untilAges());
        assertTrue(s.errors().isEmpty(), s.errors().toString());
        assertEquals(0, RecipeMarkers.drain().size(), "the next load starts empty");
    }

    @Test
    void scriptsRunTwicePerLoadWithoutHarm() {
        for (int i = 0; i < 2; i++) {
            RecipeMarkers.stationAge("mekanism:enriching", "age_6");
            RecipeMarkers.requireAge("p:r", "age_7");
            RecipeMarkers.requireAge("p:r", "age_8");
        }
        RecipeMarkers.Snapshot s = RecipeMarkers.drain();
        assertEquals(Set.of(AgeId.AGE_7, AgeId.AGE_8), s.requireAges().get("p:r"), "requireAge calls add up");
        assertTrue(s.errors().isEmpty(), s.errors().toString());
    }

    @Test
    void badInputIsReportedNotApplied() {
        RecipeMarkers.stationAge("t:x", "age_12");
        RecipeMarkers.requireAge("bad id", "age_2");
        RecipeMarkers.stationAge("t:y", "age_2");
        RecipeMarkers.stationAge("t:y", "age_3");
        RecipeMarkers.Snapshot s = RecipeMarkers.drain();
        assertEquals(Map.of("t:y", AgeId.AGE_3), s.stationAges());
        assertTrue(s.requireAges().isEmpty());
        assertEquals(3, s.errors().size(), s.errors().toString());
    }

    @Test
    void bareIdsGetTheMinecraftNamespace() {
        RecipeMarkers.untilAge("stone_from_dirt", "age_1");
        assertEquals(Set.of("minecraft:stone_from_dirt"), RecipeMarkers.drain().untilAges().keySet());
    }
}
