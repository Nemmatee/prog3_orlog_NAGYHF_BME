package hu.bme.orlog.model;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DiceSetTest {

    @Test
    @DisplayName("toggle and setLocked switch lock state")
    // Verifies that toggling and setting lock flags correctly changes
    // the locked state of individual dice positions.
    void toggleLock() {
        DiceSet ds = new DiceSet(2, new Random(1));
        assertFalse(ds.isLocked(0));
        ds.toggle(0);
        assertTrue(ds.isLocked(0));
        ds.setLocked(1, true);
        assertTrue(ds.isLocked(1));
        ds.toggle(1);
        assertFalse(ds.isLocked(1));
    }

    @Test
    @DisplayName("rollUnlocked keeps locked dice face unchanged")
    // Ensures that rolling only re-rolls unlocked dice: a locked die
    // must keep its face value between rolls.
    void rollRespectsLocks() {
        DiceSet ds = new DiceSet(2, new Random(2));
        List<Face> firstRoll = ds.rollUnlocked();
        ds.setLocked(0, true);
        List<Face> secondRoll = ds.rollUnlocked();

        assertEquals(firstRoll.get(0), secondRoll.get(0), "Locked die face should stay the same");
        assertNotNull(secondRoll.get(1), "Unlocked die should have a face after rolling");
    }
}
