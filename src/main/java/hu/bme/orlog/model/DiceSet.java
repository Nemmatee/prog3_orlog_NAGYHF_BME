package hu.bme.orlog.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Represents a collection of dice belonging to a player, with the ability to
 * lock/unlock individual dice and roll unlocked dice.
 */
public class DiceSet implements Serializable {
    private final List<Die> dice;
    private final boolean[] locked;

    /**
     * Creates a DiceSet with the given number of dice and a Random source.
     *
     * @param n number of dice
     * @param rng random number source (may be null)
     */
    public DiceSet(int n, Random rng) {
        dice = new ArrayList<>(n);
        locked = new boolean[n];
        for (int i = 0; i < n; i++)
            dice.add(new Die(rng));
    }

    /**
     * Rolls all unlocked dice and returns the resulting faces in order.
     * Locked dice keep their previous face.
     *
     * @return list of faces after rolling unlocked dice
     */
    public List<Face> rollUnlocked() {
        List<Face> faces = new ArrayList<>(dice.size());
        for (int i = 0; i < dice.size(); i++) {
            Face f = locked[i] ? dice.get(i).getFace() : dice.get(i).roll();
            faces.add(f);
        }
        return faces;
    }

    /**
     * Locks or unlocks the die at the given index.
     *
     * @param idx index of the die
     * @param b true to lock, false to unlock
     */
    public void setLocked(int idx, boolean b) {
        if (idx >= 0 && idx < locked.length)
            locked[idx] = b;
    }

    /**
     * Returns whether the die at the given index is locked.
     *
     * @param idx index of the die
     * @return true if locked
     */
    public boolean isLocked(int idx) {
        return idx >= 0 && idx < locked.length && locked[idx];
    }

    /**
     * Toggles the locked state of the die at the given index.
     *
     * @param idx index of the die
     */
    public void toggle(int idx) {
        if (idx >= 0 && idx < locked.length)
            locked[idx] = !locked[idx];
    }

    /**
     * Unlocks all dice.
     */
    public void clearLocks() {
        Arrays.fill(locked, false);
    }

    /**
     * Returns the number of dice in the set.
     *
     * @return number of dice
     */
    public int size() {
        return dice.size();
    }

    /**
     * Returns the currently shown faces for all dice without altering state.
     *
     * @return list of current faces (may contain null for unrolled dice)
     */
    public List<Face> currentFaces() {
        List<Face> fs = new ArrayList<>(dice.size());
        for (Die d : dice)
            fs.add(d.getFace());
        return fs;
    }
}
