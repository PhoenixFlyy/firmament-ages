package dev.firmages.core.hardship;

import dev.firmages.core.age.AgeId;
import dev.firmages.core.age.AgeSnapshot;
import dev.firmages.core.immersion.AgeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SPEC §19: the values of {@code [hardship] caveDwellerUntilAge} and the decision behind the Cave Dweller gate. */
class CaveDwellerGateTest {

    @Test
    void defaultIsTheIronAge() {
        // Felix 2026-10-13 (H12); the key itself (hardship.caveDwellerUntilAge) is checked in HardshipGameTests,
        // ModConfigSpec is not on the unit-test classpath
        assertEquals(AgeId.AGE_2, CaveDwellerGate.DEFAULT_UNTIL);
        assertTrue(CaveDwellerGate.isValid(CaveDwellerGate.DEFAULT_UNTIL.id()));
        assertEquals("cave_dweller:cave_dweller", CaveDwellerGate.ENTITY_ID);
    }

    @Test
    void configValues() {
        assertEquals(Optional.of(AgeId.AGE_2), CaveDwellerGate.until("age_2"));
        assertEquals(Optional.of(AgeId.AGE_4), CaveDwellerGate.until("progressivestages:age_4"));
        assertEquals(Optional.of(AgeId.DAWN), CaveDwellerGate.until("dawn"));
        assertEquals(Optional.empty(), CaveDwellerGate.until("never"));
        assertEquals(Optional.empty(), CaveDwellerGate.until(" Never "));
        // an unknown value counts as the default
        assertEquals(Optional.of(AgeId.AGE_2), CaveDwellerGate.until("iron"));
        assertEquals(Optional.of(AgeId.AGE_2), CaveDwellerGate.until(null));
        assertTrue(CaveDwellerGate.isValid("never"));
        assertTrue(CaveDwellerGate.isValid("age_9"));
        assertFalse(CaveDwellerGate.isValid("iron"));
        assertFalse(CaveDwellerGate.isValid(""));
        assertFalse(CaveDwellerGate.isValid(2));
    }

    @Test
    void huntsUntilTheConfiguredAge() {
        Optional<AgeId> iron = Optional.of(AgeId.AGE_2);
        for (AgeId age : AgeId.all()) {
            assertEquals(age.ordinal() < AgeId.AGE_2.ordinal(), CaveDwellerGate.hunts(age, iron), "default in " + age);
            assertTrue(CaveDwellerGate.hunts(age, Optional.empty()), "never: hunts in " + age);
        }
        assertFalse(CaveDwellerGate.hunts(AgeId.DAWN, Optional.of(AgeId.DAWN)), "dawn: never hunts");
    }

    @Test
    void statusCarriesTheGate() {
        Optional<AgeId> iron = Optional.of(AgeId.AGE_2);
        AgeStatus bronze = AgeStatus.of(AgeSnapshot.of(List.of(AgeId.AGE_0, AgeId.AGE_1), 1, 0), t -> Optional.empty(),
            AgeId.AGE_1, AgeId.AGE_1, t -> Optional.empty(), iron, Optional.empty());
        assertTrue(bronze.caveDweller(), "Bronze Age: it still hunts");
        // a gap (age_3 granted without age_2) counts the highest Age
        AgeStatus gap = AgeStatus.of(AgeSnapshot.of(List.of(AgeId.AGE_0, AgeId.AGE_3), 1, 0), t -> Optional.empty(),
            AgeId.AGE_1, AgeId.AGE_1, t -> Optional.empty(), iron, Optional.empty());
        assertFalse(gap.caveDweller());
        assertTrue(AgeStatus.UNKNOWN.caveDweller(), "before the first status the client mutes nothing");
    }
}
