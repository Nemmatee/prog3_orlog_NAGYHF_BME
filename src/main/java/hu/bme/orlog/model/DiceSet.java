package hu.bme.orlog.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class DiceSet implements Serializable {
    private final List<Die> dice;
    private final boolean[] locked;

    public DiceSet(int n, Random rng) {
        dice = new ArrayList<>(n);
        locked = new boolean[n];
        for (int i = 0; i < n; i++)
            dice.add(new Die(rng));
    }

    public List<Face> rollUnlocked() {
        List<Face> faces = new ArrayList<>(dice.size());
        for (int i = 0; i < dice.size(); i++) {
            Face f = locked[i] ? dice.get(i).getFace() : dice.get(i).roll();
            faces.add(f);
        }
        return faces;
    }

    public void setLocked(int idx, boolean b) {
        if (idx >= 0 && idx < locked.length)
            locked[idx] = b;
    }

    public boolean isLocked(int idx) {
        return idx >= 0 && idx < locked.length && locked[idx];
    }

    public void toggle(int idx) {
        if (idx >= 0 && idx < locked.length)
            locked[idx] = !locked[idx];
    }

    public void clearLocks() {
        Arrays.fill(locked, false);
    }

    public int size() {
        return dice.size();
    }

    public List<Face> currentFaces() {
        List<Face> fs = new ArrayList<>(dice.size());
        for (Die d : dice)
            fs.add(d.getFace());
        return fs;
    }
}
