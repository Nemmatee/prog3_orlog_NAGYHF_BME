package hu.bme.orlog.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrlogEngineTest {

    private final OrlogEngine engine = new OrlogEngine();

    @Test
    @DisplayName("countFaces handles nulls and tallies duplicates")
    void countFacesHandlesNulls() {
        List<Face> faces = new ArrayList<>();
        faces.add(Face.MELEE);
        faces.add(null);
        faces.add(Face.MELEE);
        faces.add(Face.RANGED_GOLD);

        Map<Face, Integer> counted = engine.countFaces(faces);

        assertEquals(2, counted.get(Face.MELEE));
        assertEquals(1, counted.get(Face.RANGED_GOLD));
        assertEquals(0, counted.getOrDefault(Face.HELMET, 0));
    }

    @Test
    @DisplayName("Helper counters sum gold and base variants together")
    void helperCountersWork() {
        Map<Face, Integer> m = new EnumMap<>(Face.class);
        m.put(Face.STEAL, 1);
        m.put(Face.STEAL_GOLD, 2);
        m.put(Face.MELEE, 2);
        m.put(Face.MELEE_GOLD, 1);
        m.put(Face.RANGED, 3);
        m.put(Face.RANGED_GOLD, 0);
        m.put(Face.SHIELD, 1);
        m.put(Face.HELMET_GOLD, 2);

        assertEquals(3, engine.stealAmount(m));
        assertEquals(3, engine.melee(m));
        assertEquals(3, engine.ranged(m));
        assertEquals(1, engine.shields(m));
        assertEquals(2, engine.helmets(m));
    }

    @Test
    @DisplayName("resolveRound applies base damage and resets round state")
    void resolveRoundBaseDamage() {
        Player p1 = new Player("P1", new DiceSet(6, new Random(1)));
        Player p2 = new Player("P2", new DiceSet(6, new Random(2)));
        GameState gs = new GameState(p1, p2);

        List<Face> p1Faces = List.of(Face.MELEE, Face.MELEE, Face.RANGED);
        List<Face> p2Faces = List.of(Face.SHIELD);

        engine.resolveRound(gs, p1Faces, p2Faces);

        assertEquals(15, p1.getHp(), "P1 should take no damage");
        assertEquals(13, p2.getHp(), "P2 should take 2 damage (melee 2-1 + ranged 1)");
        assertEquals(2, gs.round, "Round should advance");
        assertEquals(1, gs.rollPhase, "Roll phase resets for next round");
        assertEquals(0, gs.p1.getFavor(), "No gold rolled -> no favor gain");
        assertEquals(0, gs.p2.getFavor(), "No gold rolled -> no favor gain");
        assertTrue(gs.log.size() > 0, "Log should record the round summary");
    }

    @Test
    @DisplayName("goldCount counts only gold faces")
    void goldCountCounts() {
        List<Face> faces = new ArrayList<>();
        faces.add(Face.MELEE_GOLD);
        faces.add(Face.MELEE);
        faces.add(Face.RANGED_GOLD);
        faces.add(Face.SHIELD);
        faces.add(null); // ensure null is ignored
        assertEquals(2, engine.goldCount(faces));
    }
}
