package hu.bme.orlog.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerTest {

    @Test
    @DisplayName("Favor add and spend clamps at zero")
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
    void chooseFavorStores() {
        Player p = new Player("Tester", new DiceSet(1, new Random(1)));
        GodFavor f = new GodFavor("Test", new int[] { 1, 2, 3 }, new int[] { 1, 2, 3 }, 1,
                GodFavor.Phase.BEFORE, GodFavor.EffectType.DAMAGE);
        assertNull(p.getChosenFavor());
        p.chooseFavor(f, 2);
        assertEquals(f, p.getChosenFavor());
        assertEquals(2, p.getChosenTier());
    }
}
