package hu.bme.orlog.model;

import java.io.Serializable;
import java.util.Random;

/**
 * A single die used in the game. A die can be rolled to produce a Face.
 */
public class Die implements Serializable {
    private static final Face[] FACES = Face.values();
    private final Random rng;
    private Face face;

    public Die(Random rng) {
        this.rng = rng == null ? new Random() : rng;
    }

    /**
     * Rolls the die and updates its current face.
     *
     * @return the resulting Face
     */
    public Face roll() {
        face = FACES[rng.nextInt(FACES.length)];
        return face;
    }

    /**
     * Returns the last rolled face or null if the die has not been rolled yet.
     *
     * @return current Face or null
     */
    public Face getFace() {
        return face;
    }
}
