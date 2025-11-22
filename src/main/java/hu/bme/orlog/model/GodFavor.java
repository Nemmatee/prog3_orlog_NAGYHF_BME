package hu.bme.orlog.model;

import java.io.Serializable;

public class GodFavor implements Serializable {
    public enum Phase {
        BEFORE, AFTER
    }

    public enum EffectType {
        DAMAGE, HEAL, GAIN_TOKENS, STEAL_TOKENS,
        REMOVE_OPP_HELMETS, IGNORE_OPP_RANGED_BLOCKS,
        DOUBLE_BLOCKS, BONUS_PER_RANGED, MULTIPLY_MELEE, BONUS_MAJORITY,
        HEAL_PER_BLOCKED, HEAL_PER_INCOMING_MELEE,
        DESTROY_OPP_TOKENS_PER_ARROW, TOKENS_PER_DAMAGE_TAKEN, TOKENS_PER_STEAL,
        REDUCE_OPP_FAVOR_LEVEL, HEAL_PER_OPP_FAVOR_SPENT,
        BAN_OPP_DICE_THIS_ROUND
    }

    public final String name;
    public final int[] costs;
    public final int[] magnitudes;
    public final int priority;
    public final Phase phase;
    public final EffectType type;

    public GodFavor(String name, int[] costs, int[] magnitudes, int priority, Phase phase, EffectType type) {
        this.name = name;
        this.costs = costs;
        this.magnitudes = magnitudes;
        this.priority = priority;
        this.phase = phase;
        this.type = type;
    }

    @Override
    public String toString() {
        return name + " [" + costs[0] + "/" + costs[1] + "/" + costs[2] + "]";
    }
}
