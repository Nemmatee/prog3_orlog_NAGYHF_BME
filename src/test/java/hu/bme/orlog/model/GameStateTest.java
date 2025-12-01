package hu.bme.orlog.model;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GameStateTest {

    @Test
    @DisplayName("Log keeps most recent entries first and caps at 300")
    // Checks that the log stores at most 300 entries, always
    // keeping the newest message at the front and trimming old ones.
    void logCapacityCapped() {
        GameState gs = new GameState(new Player("A", new DiceSet(1, new Random(1))),
                new Player("B", new DiceSet(1, new Random(2))));

        for (int i = 1; i <= 305; i++) {
            gs.addLog("msg-" + i);
        }

        assertEquals(300, gs.log.size(), "Log should be capped at 300 entries");
        assertEquals("msg-305", gs.log.peekFirst(), "Newest entry should be at the front");
        assertEquals("msg-6", gs.log.peekLast(), "Oldest entry should have been trimmed");
    }
}
