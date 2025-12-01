package hu.bme.orlog.model;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerTest {

    @Test
    @DisplayName("Favor add and spend clamps at zero")
    // Verifies that favor points can be added and spent, and that
    // the internal favor value is never allowed to go below zero.
    void favorAddSpend() {
        Player p = new Player("Tester", new DiceSet(1, new Random(1)));
        p.addFavor(5);
        assertEquals(5, p.getFavor());
        p.spendFavor(3);
        assertEquals(2, p.getFavor());
        p.spendFavor(10); // cannot go negative
        assertEquals(0, p.getFavor());
    }

    @Test
    @DisplayName("Damage reduces HP and negative damage heals")
    // Checks that positive damage reduces HP, negative damage acts
    // as healing, and that HP cannot drop below zero.
    void damageAndHeal() {
        Player p = new Player("Tester", new DiceSet(1, new Random(1)));
        p.damage(4);
        assertEquals(11, p.getHp());
        p.damage(-3); // heal
        assertEquals(14, p.getHp());
        p.damage(20); // clamp at zero
        assertEquals(0, p.getHp());
    }

    @Test
    @DisplayName("chooseFavor stores reference and tier")
    // Ensures that choosing a favor stores both the favor reference
    // and the selected tier index inside the Player.
    void chooseFavorStores() {
        Player p = new Player("Tester", new DiceSet(1, new Random(1)));
        GodFavor f = new GodFavor("Test", new int[] { 1, 2, 3 }, new int[] { 1, 2, 3 }, 1,
                GodFavor.Phase.BEFORE, GodFavor.EffectType.DAMAGE);
        assertNull(p.getChosenFavor());
        p.chooseFavor(f, 2);
        assertEquals(f, p.getChosenFavor());
        assertEquals(2, p.getChosenTier());
    }

    @Test
    @DisplayName("chooseFavor can clear previously selected favor")
    // Verifies that calling chooseFavor with null resets the stored
    // favor and tier, matching how the engine clears choices each round.
    void chooseFavorCanBeCleared() {
        Player p = new Player("Tester", new DiceSet(1, new Random(1)));
        GodFavor f = new GodFavor("Test", new int[] { 1, 2, 3 }, new int[] { 1, 2, 3 }, 1,
                GodFavor.Phase.BEFORE, GodFavor.EffectType.DAMAGE);
        p.chooseFavor(f, 1);
        assertEquals(f, p.getChosenFavor());
        assertEquals(1, p.getChosenTier());

        p.chooseFavor(null, 0);
        assertNull(p.getChosenFavor());
        assertEquals(0, p.getChosenTier());
    }
}
