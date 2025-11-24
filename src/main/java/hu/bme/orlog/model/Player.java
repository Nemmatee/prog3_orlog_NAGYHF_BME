package hu.bme.orlog.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a player in the game and stores player-specific state such as
 * name, health points (HP), favor (tokens), dice set and chosen god favor.
 */
public class Player implements Serializable {
    private String name;
    private int hp = 15;
    private int favor = 0;
    private final DiceSet dice;
    private GodFavor chosenFavor;
    private int chosenTier = 0; // 0..2
    private final List<GodFavor> loadout = new ArrayList<>(3);

    /**
     * Creates a new Player with the given name and dice set.
     *
     * @param name player's display name
     * @param dice dice set owned by the player
     */
    public Player(String name, DiceSet dice) {
        this.name = name;
        this.dice = dice;
    }

    /**
     * Returns the player's name.
     *
     * @return player's name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the player's current HP.
     *
     * @return current health points
     */
    public int getHp() {
        return hp;
    }

    /**
     * Returns the player's current favor (token) count.
     *
     * @return favor tokens available to the player
     */
    public int getFavor() {
        return favor;
    }

    /**
     * Adds the given number of favor tokens to the player.
     *
     * @param n number of tokens to add (may be negative)
     */
    public void addFavor(int n) {
        favor += n;
    }

    /**
     * Spends up to the given number of favor tokens; the player's favor will
     * never drop below zero.
     *
     * @param n number of tokens to spend
     */
    public void spendFavor(int n) {
        favor = Math.max(0, favor - n);
    }

    /**
     * Applies damage to the player. HP will not drop below zero. A negative
     * value heals the player.
     *
     * @param n damage amount (negative to heal)
     */
    public void damage(int n) {
        hp = Math.max(0, hp - n);
    }

    /**
     * Returns the DiceSet owned by the player.
     *
     * @return player's DiceSet
     */
    public DiceSet getDice() {
        return dice;
    }

    /**
     * Sets the chosen favor and tier for this player for the current round.
     *
     * @param f chosen GodFavor (or null to choose none)
     * @param tier chosen tier index (0..2)
     */
    public void chooseFavor(GodFavor f, int tier) {
        this.chosenFavor = f;
        this.chosenTier = tier;
    }

    /**
     * Returns the currently chosen favor for this player, or null if none.
     *
     * @return chosen GodFavor or null
     */
    public GodFavor getChosenFavor() {
        return chosenFavor;
    }

    /**
     * Returns the chosen tier index for the player's chosen favor.
     *
     * @return chosen tier index (0..2)
     */
    public int getChosenTier() {
        return chosenTier;
    }

    /**
     * Returns the player's saved loadout of GodFavors.
     *
     * @return list of GodFavor in the player's loadout
     */
    public List<GodFavor> getLoadout() {
        return loadout;
    }

    /**
     * Replaces the player's loadout with the supplied list.
     *
     * @param favs new loadout (null clears the loadout)
     */
    public void setLoadout(List<GodFavor> favs) {
        loadout.clear();
        if (favs != null)
            loadout.addAll(favs);
    }
}
