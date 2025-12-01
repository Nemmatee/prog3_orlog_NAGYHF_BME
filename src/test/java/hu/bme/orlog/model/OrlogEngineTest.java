package hu.bme.orlog.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrlogEngineTest {

    private final OrlogEngine engine = new OrlogEngine();

    @Test
    @DisplayName("countFaces handles nulls and tallies duplicates")
    // Ensures that countFaces skips null entries and correctly counts
    // multiple occurrences of the same face.
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
    // Verifies that helper counter methods (stealAmount, melee, ranged,
    // shields, helmets) add up base and gold variants of each face.
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
    // Tests a simple round resolution with no favors: damage is computed
    // from melee/ranged vs blocks, HP is updated and round state is reset.
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
    // Checks that goldCount only counts faces whose gold flag is true
    // and ignores null values in the list.
    void goldCountCounts() {
        List<Face> faces = new ArrayList<>();
        faces.add(Face.MELEE_GOLD);
        faces.add(Face.MELEE);
        faces.add(Face.RANGED_GOLD);
        faces.add(Face.SHIELD);
        faces.add(null); // ensure null is ignored
        assertEquals(2, engine.goldCount(faces));
    }

    @Test
    @DisplayName("BEFORE favor REMOVE_OPP_HELMETS removes enemy helmets before damage")
    // Simulates a round where the attacker uses Vidar's Might to remove
    // opponent helmets so that more ranged damage goes through.
    void beforeFavorRemoveOppHelmetsAffectsDamage() {
        Player p1 = new Player("P1", new DiceSet(6, new Random(1)));
        Player p2 = new Player("P2", new DiceSet(6, new Random(2)));
        GameState gs = new GameState(p1, p2);

        // Give P1 enough favor to pay for Vidar's Might tier 0 (cost 2).
        p1.addFavor(3);
        GodFavor vidar = GodFavorCatalog.all().stream()
                .filter(f -> f.name.equals("Vidar's Might"))
                .findFirst()
                .orElseThrow();
        p1.chooseFavor(vidar, 0);

        // P1 has ranged, P2 has a helmet that would normally block it.
        List<Face> p1Faces = List.of(Face.RANGED);
        List<Face> p2Faces = List.of(Face.HELMET);

        engine.resolveRound(gs, p1Faces, p2Faces);

        // Without the favor, helmet would block the ranged hit.
        // With REMOVE_OPP_HELMETS, the helmet is removed and damage goes through.
        assertEquals(14, p2.getHp(), "P2 should take 1 damage after helmet is removed");
        assertEquals(1, p1.getFavor(), "P1 should have paid 2 favor (3-2) for the favor");
    }

    @Test
    @DisplayName("AFTER favor DAMAGE applies extra damage after base resolution")
    // Simulates a round where Thor's Strike is used to deal additional
    // damage after the normal melee/ranged damage has been applied.
    void afterFavorDamageAddsExtraDamage() {
        Player p1 = new Player("P1", new DiceSet(6, new Random(1)));
        Player p2 = new Player("P2", new DiceSet(6, new Random(2)));
        GameState gs = new GameState(p1, p2);

        // Give P1 enough favor to pay for Thor's Strike tier 0 (cost 4).
        p1.addFavor(4);
        GodFavor thor = GodFavorCatalog.all().stream()
                .filter(f -> f.name.equals("Thor's Strike"))
                .findFirst()
                .orElseThrow();
        p1.chooseFavor(thor, 0);

        // Simple roll: one melee from P1, no blocks from P2.
        List<Face> p1Faces = List.of(Face.MELEE);
        List<Face> p2Faces = List.of();

        engine.resolveRound(gs, p1Faces, p2Faces);

        // Base melee damage: 1. Thor's Strike tier 0 adds magnitude 2 damage.
        assertEquals(12, p2.getHp(), "P2 should take 3 total damage (1 base + 2 favor)");
        assertEquals(0, p1.getFavor(), "P1 should have spent all 4 favor on Thor's Strike");
    }
}
