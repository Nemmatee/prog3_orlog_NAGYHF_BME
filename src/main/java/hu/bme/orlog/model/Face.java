package hu.bme.orlog.model;

/**
 * Represents a single die face and whether it is the gold (upgraded) variant.
 */
public enum Face {
    MELEE(false), MELEE_GOLD(true),
    RANGED(false), RANGED_GOLD(true),
    SHIELD(false), SHIELD_GOLD(true),
    HELMET(false), HELMET_GOLD(true),
    STEAL(false), STEAL_GOLD(true);

    public final boolean gold;

    Face(boolean gold) {
        this.gold = gold;
    }

    public boolean isAttackMelee() {
        return this == MELEE || this == MELEE_GOLD;
    }

    public boolean isAttackRanged() {
        return this == RANGED || this == RANGED_GOLD;
    }

    public boolean isShield() {
        return this == SHIELD || this == SHIELD_GOLD;
    }

    public boolean isHelmet() {
        return this == HELMET || this == HELMET_GOLD;
    }

    public boolean isSteal() {
        return this == STEAL || this == STEAL_GOLD;
    }
}
