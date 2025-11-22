package hu.bme.orlog.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Player implements Serializable {
    private String name;
    private int hp = 15;
    private int favor = 0;
    private final DiceSet dice;
    private GodFavor chosenFavor;
    private int chosenTier = 0; // 0..2
    private final List<GodFavor> loadout = new ArrayList<>(3);

    public Player(String name, DiceSet dice) {
        this.name = name;
        this.dice = dice;
    }

    public String getName() {
        return name;
    }

    public int getHp() {
        return hp;
    }

    public int getFavor() {
        return favor;
    }

    public void addFavor(int n) {
        favor += n;
    }

    public void spendFavor(int n) {
        favor = Math.max(0, favor - n);
    }

    public void damage(int n) {
        hp = Math.max(0, hp - n);
    }

    public DiceSet getDice() {
        return dice;
    }

    public void chooseFavor(GodFavor f, int tier) {
        this.chosenFavor = f;
        this.chosenTier = tier;
    }

    public GodFavor getChosenFavor() {
        return chosenFavor;
    }

    public int getChosenTier() {
        return chosenTier;
    }

    public List<GodFavor> getLoadout() {
        return loadout;
    }

    public void setLoadout(List<GodFavor> favs) {
        loadout.clear();
        if (favs != null)
            loadout.addAll(favs);
    }
}
