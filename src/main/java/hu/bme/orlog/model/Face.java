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

    /**
     * True when this face is the gold (upgraded) variant.
     */
    public final boolean gold;

    Face(boolean gold) {
        this.gold = gold;
    }


    /**
     * Returns true when this face represents a melee attack (regular or gold).
     *
     * @return true if melee attack face
     */
    public boolean isAttackMelee() {
        return this == MELEE || this == MELEE_GOLD;
    }


    /**
     * Returns true when this face represents a ranged attack (regular or gold).
     *
     * @return true if ranged attack face
     */
    public boolean isAttackRanged() {
        return this == RANGED || this == RANGED_GOLD;
    }


    /**
     * Returns true when this face is a shield (regular or gold).
     *
     * @return true if shield face
     */
    public boolean isShield() {
        return this == SHIELD || this == SHIELD_GOLD;
    }


    /**
     * Returns true when this face is a helmet (regular or gold).
     *
     * @return true if helmet face
     */
    public boolean isHelmet() {
        return this == HELMET || this == HELMET_GOLD;
    }



    /**
     * Returns true when this face triggers a steal (regular or gold).
     *
     * @return true if steal face
     */
    public boolean isSteal() {
        return this == STEAL || this == STEAL_GOLD;
    }
}
