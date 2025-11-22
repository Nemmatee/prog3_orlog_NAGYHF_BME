package hu.bme.orlog.model;

import java.io.Serializable;
import java.util.Random;

public class Die implements Serializable {
    private static final Face[] FACES = Face.values();
    private final Random rng;
    private Face face;

    public Die(Random rng) {
        this.rng = rng == null ? new Random() : rng;
    }

    public Face roll() {
        face = FACES[rng.nextInt(FACES.length)];
        return face;
    }

    public Face getFace() {
        return face;
    }
}
