package hu.bme.orlog.model;

import java.io.Serializable;

/**
 * Represents a selectable god favor (ability) that players can use during a round.
 *
 * Stores the favor name, costs per tier, magnitude per tier, priority,
 * phase (BEFORE/AFTER) and the effect type.
 */
public class GodFavor implements Serializable {
    /** Phase when the favor effect is applied. */
    public enum Phase {
        BEFORE, AFTER
    }

    /** Effect type identifiers used to determine what the favor does. */
    public enum EffectType {
        DAMAGE, HEAL, GAIN_TOKENS, STEAL_TOKENS,
        REMOVE_OPP_HELMETS, IGNORE_OPP_RANGED_BLOCKS,
        DOUBLE_BLOCKS, BONUS_PER_RANGED, MULTIPLY_MELEE, BONUS_MAJORITY,
        HEAL_PER_BLOCKED, HEAL_PER_INCOMING_MELEE,
        DESTROY_OPP_TOKENS_PER_ARROW, TOKENS_PER_DAMAGE_TAKEN, TOKENS_PER_STEAL,
        REDUCE_OPP_FAVOR_LEVEL, HEAL_PER_OPP_FAVOR_SPENT,
        BAN_OPP_DICE_THIS_ROUND
    }

    /** Human readable favor name */
    public final String name;
    /** Costs per tier for this favor */
    public final int[] costs;
    /** Effect magnitudes per tier for this favor */
    public final int[] magnitudes;
    /** Priority used to order before/after effects */
    public final int priority;
    /** Phase when this favor applies (BEFORE or AFTER) */
    public final Phase phase;
    /** Effect type enum describing the favor's behavior */
    public final EffectType type;

    /**
     * Constructs a GodFavor instance.
     *
     * @param name human-readable name
     * @param costs costs per tier
     * @param magnitudes effect magnitudes per tier
     * @param priority ordering priority for simultaneous effects
     * @param phase phase when the favor applies
     * @param type effect type identifier
     */
    public GodFavor(String name, int[] costs, int[] magnitudes, int priority, Phase phase, EffectType type) {
        this.name = name;
        this.costs = costs;
        this.magnitudes = magnitudes;
        this.priority = priority;
        this.phase = phase;
        this.type = type;
    }

    /**
     * Returns a short human readable representation of the favor including its base costs.
     *
     * @return short description containing the name and tier costs
     */
    @Override
    public String toString() {
        return name + " [" + costs[0] + "/" + costs[1] + "/" + costs[2] + "]";
    }
}
