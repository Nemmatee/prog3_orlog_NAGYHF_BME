package hu.bme.orlog.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Engine that contains the core Orlog round resolution logic and helpers
 * for counting faces and computing damage/tokens.
 */
public class OrlogEngine {

    /**
     * Small per-player context for one round so we do not have to pass
     * many separate parameters (me, opponent, my face counts, enemy face
     * counts, damage taken) into every favor-handling method.
     */
    private static class RoundCtx {
        private final Player me;
        private final Player opp;
        private final Map<Face, Integer> self;
        private final Map<Face, Integer> enemy;
        private final int dmgTaken;

        RoundCtx(Player me, Player opp, Map<Face, Integer> self, Map<Face, Integer> enemy, int dmgTaken) {
            this.me = me;
            this.opp = opp;
            this.self = self;
            this.enemy = enemy;
            this.dmgTaken = dmgTaken;
        }

        Player me() {
            return me;
        }

        Player opp() {
            return opp;
        }

        Map<Face, Integer> self() {
            return self;
        }

        Map<Face, Integer> enemy() {
            return enemy;
        }

        int dmgTaken() {
            return dmgTaken;
        }
    }

    /**
     * Counts occurrences of each Face in the provided list and returns a map
     * from Face to its count.
     *
     * @param faces list of faces to count
     * @return map with counts for each face type
     */
    public Map<Face, Integer> countFaces(List<Face> faces) {
        // Use EnumMap because Face is an enum and this map is compact/fast.
        Map<Face, Integer> m = new EnumMap<>(Face.class);
        for (Face f : faces) {
            if (f != null) {
                // If key already exists, increase its value by 1, otherwise insert it with value 1.
                m.merge(f, 1, Integer::sum);
            }
        }
        return m;
    }

    /**
     * Sums the counts for the provided face types from the given map.
     *
     * @param m map of face counts
     * @param faces face types to include in the sum
     * @return total count for the specified faces
     */
    private int count(Map<Face, Integer> m, Face... faces) {
        int sum = 0;
        for (Face f : faces) {
            // Add the stored count for this face type to the running total (0 if missing).
            sum += m.getOrDefault(f, 0);
        }
        return sum;
    }
    

    /**
     * Returns how many steal faces (including gold) are present in the attack map.
     *
     * @param att map of face counts
     * @return number of steal faces
     */
    public int stealAmount(Map<Face, Integer> att) {
        return count(att, Face.STEAL, Face.STEAL_GOLD);
    }

    /**
     * Counts melee faces (including gold) in the provided attack map.
     *
     * @param att map of face counts
     * @return number of melee faces
     */
    public int melee(Map<Face, Integer> att) {
        return count(att, Face.MELEE, Face.MELEE_GOLD);
    }

    /**
     * Counts ranged faces (including gold) in the provided attack map.
     *
     * @param att map of face counts
     * @return number of ranged faces
     */
    public int ranged(Map<Face, Integer> att) {
        return count(att, Face.RANGED, Face.RANGED_GOLD);
    }

    /**
     * Counts shield faces (including gold) in the provided defense map.
     *
     * @param def map of face counts
     * @return number of shield faces
     */
    public int shields(Map<Face, Integer> def) {
        return count(def, Face.SHIELD, Face.SHIELD_GOLD);
    }

    /**
     * Counts helmet faces (including gold) in the provided defense map.
     *
     * @param def map of face counts
     * @return number of helmet faces
     */
    public int helmets(Map<Face, Integer> def) {
        return count(def, Face.HELMET, Face.HELMET_GOLD);
    }


    /**
     * Returns how many gold faces are present in the given list.
     *
     * @param faces list of faces to inspect
     * @return number of gold faces
     */
    public int goldCount(List<Face> faces) {
        int c = 0;
        for (Face f : faces) {
            if (f != null && f.gold) {
                c++;
            }
        }
        return c;
    }

    /**
     * Tries to remove up to the given amount of a face type
     * from the map: first from the non-gold face, then from the
     * corresponding gold face. Returns how many icons were actually removed.
     *
     * Used by BEFORE-phase God Favors that delete helmets/shields
     * from the opponent's rolled icons.
     */
    private int removeUpTo(Map<Face, Integer> m, Face base, Face gold, int amount) {
        // Total number of icons removed from both base and gold faces.
        int removed = 0;

        // Remove as many base (non-gold) icons as possible, up to 'amount'.
        int baseCount = m.getOrDefault(base, 0);
        int rmBase = Math.min(baseCount, amount);
        m.put(base, baseCount - rmBase);
        removed += rmBase;

        // Remaining amount we still want to remove (if any).
        int left = amount - rmBase;
        if (left > 0) {
            // If there is still something to remove, take it from the gold icons.
            int goldCount = m.getOrDefault(gold, 0);
            int rmGold = Math.min(goldCount, left);
            // Ensure we never store a negative count in the map.
            m.put(gold, Math.max(0, goldCount - rmGold));
            removed += rmGold;
        }

        // Return how many icons we actually removed in total.
        return removed;
    }

    /**
     * Resolves a full round given the current GameState and the faces rolled
     * by both players. This method applies before/after favors, computes
     * damage and favor transfers, updates GameState fields and logs the round.
     *
     * @param gs current GameState to update
     * @param p1Faces faces rolled by player 1
     * @param p2Faces faces rolled by player 2
     */
    public void resolveRound(GameState gs, List<Face> p1Faces, List<Face> p2Faces) {
        // Count how many of each face type was rolled by each player.
        Map<Face, Integer> a = countFaces(p1Faces);
        Map<Face, Integer> b = countFaces(p2Faces);

        // FIRST: apply all BEFORE-phase God favors, they can modify a/b.
        applyBeforeFavors(gs, a, b);

        // STEAL: each steal icon tries to take 1 favor point from the opponent.
        int s1 = stealAmount(a);
        int s2 = stealAmount(b);
        // You cannot steal more favor than the opponent currently has.
        int s1real = Math.min(s1, gs.p2.getFavor());
        int s2real = Math.min(s2, gs.p1.getFavor());
        gs.p1.addFavor(s1real);
        gs.p2.spendFavor(s1real);
        gs.p2.addFavor(s2real);
        gs.p1.spendFavor(s2real);

        // Count melee/ranged attacks and shields/helmets after BEFORE-effects.
        int melee1 = melee(a);
        int melee2 = melee(b);
        int ranged1 = ranged(a);
        int ranged2 = ranged(b);
        int sh2 = shields(b);
        int he2 = helmets(b);
        int sh1 = shields(a);
        int he1 = helmets(a);

        // Damage = positive part of (attack - block) for melee and ranged.
        int dmg1 = Math.max(0, melee1 - sh2) + Math.max(0, ranged1 - he2);
        int dmg2 = Math.max(0, melee2 - sh1) + Math.max(0, ranged2 - he1);

        // Each gold face grants 1 favor token at the end of the round.
        gs.p1.addFavor(goldCount(p1Faces));
        gs.p2.addFavor(goldCount(p2Faces));

        // Apply raw damage to HP before AFTER-phase favors.
        gs.p2.damage(dmg1);
        gs.p1.damage(dmg2);

        // THEN: apply AFTER-phase favors which may depend on the damage numbers.
        applyAfterFavors(gs, a, b, dmg1, dmg2);

        gs.melee1 = melee1;
        gs.ranged1 = ranged1;
        gs.shields2 = sh2;
        gs.helmets2 = he2;
        gs.dmg1 = dmg1;
        gs.melee2 = melee2;
        gs.ranged2 = ranged2;
        gs.shields1 = sh1;
        gs.helmets1 = he1;
        gs.dmg2 = dmg2;

        // Log a summary line for the round with melee/ranged vs blocks.
        gs.addLog(String.format(
            "R%d: %s dealt %d (M:%d vs S:%d, R:%d vs H:%d) | %s dealt %d (M:%d vs S:%d, R:%d vs H:%d)",
            gs.round, gs.p1.getName(), dmg1, melee1, sh2, ranged1, he2,
            gs.p2.getName(), dmg2, melee2, sh1, ranged2, he1));
        // Prepare for the next round: increase round, reset phase, clear favor choice and dice locks.
        gs.round++;
        gs.rollPhase = 1;
        gs.p1.chooseFavor(null, 0);
        gs.p2.chooseFavor(null, 0);
        gs.p1.getDice().clearLocks();
        gs.p2.getDice().clearLocks();
    }

    /**
     * Deducts the favor cost for the chosen GodFavor from the given player.
     *
     * @param p player who pays the cost
     * @param f chosen GodFavor definition
     * @param tier tier index of the chosen favor
     */
    private void spend(Player p, GodFavor f, int tier) {
        p.spendFavor(f.costs[tier]);
    }

    /**
     * Applies all chosen God favors that trigger in the BEFORE phase.
     *
     * Favors are resolved in priority order. A favor may modify the face
     * count maps, affect player favor or tokens, or otherwise mutate the
     * GameState. This method will call {@code applyBeforeFavor} for each
     * actor in the round.
     *
     * @param gs current game state
     * @param a face counts for player 1
     * @param b face counts for player 2
     */
    private void applyBeforeFavors(GameState gs, Map<Face, Integer> a, Map<Face, Integer> b) {
        // Build round contexts for both players: first (p1 acting on p2), then (p2 acting on p1).
        RoundCtx ctx1 = new RoundCtx(gs.p1, gs.p2, a, b, 0);
        RoundCtx ctx2 = new RoundCtx(gs.p2, gs.p1, b, a, 0);

        // Determine BEFORE-phase priority for each player (higher value = lower priority).
        GodFavor f1 = ctx1.me().getChosenFavor();
        GodFavor f2 = ctx2.me().getChosenFavor();

        int prio1 = (f1 != null && f1.phase == GodFavor.Phase.BEFORE) ? f1.priority : Integer.MAX_VALUE;
        int prio2 = (f2 != null && f2.phase == GodFavor.Phase.BEFORE) ? f2.priority : Integer.MAX_VALUE;

        // Lower priority value means the favor should be applied earlier.
        if (prio2 < prio1) {
            RoundCtx tmp = ctx1;
            ctx1 = ctx2;
            ctx2 = tmp;
        }

        applyBeforeFavor(gs, ctx1);
        applyBeforeFavor(gs, ctx2);
    }

    /**
     * Applies all chosen God favors that trigger in the AFTER phase.
     *
     * The provided damage values are used to populate the RoundCtx so favors
     * that depend on damage taken can compute tokens or healing correctly.
     * Favors are resolved in priority order and applied via
     * {@code applyAfterFavor}.
     *
     * @param gs current game state
     * @param a face counts for player 1
     * @param b face counts for player 2
     * @param dmg1 damage dealt by player 1 to player 2
     * @param dmg2 damage dealt by player 2 to player 1
     */
    private void applyAfterFavors(GameState gs, Map<Face, Integer> a, Map<Face, Integer> b, int dmg1, int dmg2) {
        // In AFTER phase we also store how much damage each player took in this round.
        RoundCtx ctx1 = new RoundCtx(gs.p1, gs.p2, a, b, dmg2);
        RoundCtx ctx2 = new RoundCtx(gs.p2, gs.p1, b, a, dmg1);

        // Determine AFTER-phase priority for each player.
        GodFavor f1 = ctx1.me().getChosenFavor();
        GodFavor f2 = ctx2.me().getChosenFavor();

        int prio1 = (f1 != null && f1.phase == GodFavor.Phase.AFTER) ? f1.priority : Integer.MAX_VALUE;
        int prio2 = (f2 != null && f2.phase == GodFavor.Phase.AFTER) ? f2.priority : Integer.MAX_VALUE;

        // Apply favors in order of increasing priority value.
        if (prio2 < prio1) {
            RoundCtx tmp = ctx1;
            ctx1 = ctx2;
            ctx2 = tmp;
        }

        applyAfterFavor(gs, ctx1);
        applyAfterFavor(gs, ctx2);
    }

    /**
     * Applies a single BEFORE-phase favor for the given round context.
     *
     * This checks affordability and the favor phase before executing the
     * favor's effect. The method may modify the face maps or GameState and
     * will log the action to the GameState.
     *
     * @param gs current game state
     * @param ctx round context containing actor, opponent and face maps
     */
    private void applyBeforeFavor(GameState gs, RoundCtx ctx) {
        GodFavor f = ctx.me().getChosenFavor();
        int tier = ctx.me().getChosenTier();
        // Only handle BEFORE-phase favors that the player can actually use.
        // If there is no chosen favor, or it belongs to another phase,
        // or the player cannot afford it, we simply do nothing.
        if (f == null || f.phase != GodFavor.Phase.BEFORE) {
            return;
        }
        if (ctx.me().getFavor() < f.costs[tier]) {
            return;
        }
        // Dispatch to the concrete BEFORE favor effect based on its type.
        switch (f.type) {
            case REMOVE_OPP_HELMETS -> {
                // Remove up to N opponent helmets so your ranged hits harder.
                spend(ctx.me(), f, tier);
                int rm = Math.min(f.magnitudes[tier], count(ctx.enemy(), Face.HELMET, Face.HELMET_GOLD));
                removeUpTo(ctx.enemy(), Face.HELMET, Face.HELMET_GOLD, rm);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (-" + rm + " helmets)");
            }
            case IGNORE_OPP_RANGED_BLOCKS -> {
                // Remove up to N opponent shields so your ranged can pass.
                spend(ctx.me(), f, tier);
                int rm = Math.min(f.magnitudes[tier], count(ctx.enemy(), Face.SHIELD, Face.SHIELD_GOLD));
                removeUpTo(ctx.enemy(), Face.SHIELD, Face.SHIELD_GOLD, rm);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (ignore " + rm + " shields)");
            }
            case DOUBLE_BLOCKS -> {
                // Multiply your own helmets and shields by a factor.
                spend(ctx.me(), f, tier);
                int add = f.magnitudes[tier];
                ctx.self().put(Face.HELMET, ctx.self().getOrDefault(Face.HELMET, 0) + ctx.self().getOrDefault(Face.HELMET, 0) * add);
                ctx.self().put(Face.HELMET_GOLD, ctx.self().getOrDefault(Face.HELMET_GOLD, 0) + ctx.self().getOrDefault(Face.HELMET_GOLD, 0) * add);
                ctx.self().put(Face.SHIELD, ctx.self().getOrDefault(Face.SHIELD, 0) + ctx.self().getOrDefault(Face.SHIELD, 0) * add);
                ctx.self().put(Face.SHIELD_GOLD, ctx.self().getOrDefault(Face.SHIELD_GOLD, 0) + ctx.self().getOrDefault(Face.SHIELD_GOLD, 0) * add);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (+blocks)");
            }
            case BONUS_PER_RANGED -> {
                // For each ranged icon you get some extra ranged icons.
                spend(ctx.me(), f, tier);
                int bonus = f.magnitudes[tier] * count(ctx.self(), Face.RANGED, Face.RANGED_GOLD);
                ctx.self().put(Face.RANGED, ctx.self().getOrDefault(Face.RANGED, 0) + bonus);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (+" + bonus + " arrows)");
            }
            case MULTIPLY_MELEE -> {
                // Multiply melee count by a percentage (e.g., 150% melee).
                spend(ctx.me(), f, tier);
                int percent = f.magnitudes[tier];
                int base = count(ctx.self(), Face.MELEE, Face.MELEE_GOLD);
                int extra = (base * (percent - 100)) / 100;
                ctx.self().put(Face.MELEE, ctx.self().getOrDefault(Face.MELEE, 0) + extra);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (x" + percent + "% melee)");
            }
            case BONUS_MAJORITY -> {
                // Find which icon type you have the most of and add N to that.
                spend(ctx.me(), f, tier);
                int m = count(ctx.self(), Face.MELEE, Face.MELEE_GOLD);
                int r = count(ctx.self(), Face.RANGED, Face.RANGED_GOLD);
                int h = count(ctx.self(), Face.HELMET, Face.HELMET_GOLD);
                int s = count(ctx.self(), Face.SHIELD, Face.SHIELD_GOLD);
                int st = count(ctx.self(), Face.STEAL, Face.STEAL_GOLD);
                int add = f.magnitudes[tier];
                if (m >= r && m >= h && m >= s && m >= st) {
                    ctx.self().put(Face.MELEE, ctx.self().getOrDefault(Face.MELEE, 0) + add);
                } else if (r >= h && r >= s && r >= st) {
                    ctx.self().put(Face.RANGED, ctx.self().getOrDefault(Face.RANGED, 0) + add);
                } else if (h >= s && h >= st) {
                    ctx.self().put(Face.HELMET, ctx.self().getOrDefault(Face.HELMET, 0) + add);
                } else if (s >= st) {
                    ctx.self().put(Face.SHIELD, ctx.self().getOrDefault(Face.SHIELD, 0) + add);
                } else {
                    ctx.self().put(Face.STEAL, ctx.self().getOrDefault(Face.STEAL, 0) + add);
                }
                gs.addLog(ctx.me().getName() + " used " + f.name + " (+majority)");
            }
            case DESTROY_OPP_TOKENS_PER_ARROW -> {
                // Destroy opponent favor tokens based on how many arrows you have.
                spend(ctx.me(), f, tier);
                int arrows = count(ctx.self(), Face.RANGED, Face.RANGED_GOLD);
                int toDestroy = arrows * f.magnitudes[tier];
                int real = Math.min(toDestroy, ctx.opp().getFavor());
                ctx.opp().spendFavor(real);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (-" + real + " opp tokens)");
            }
            case GAIN_TOKENS -> {
                // Simple: pay cost, then gain a flat number of tokens.
                spend(ctx.me(), f, tier);
                ctx.me().addFavor(f.magnitudes[tier]);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (+" + f.magnitudes[tier] + " tokens)");
            }
            default -> {
                // No BEFORE-phase behavior defined for this favor type.
            }
        }
    }

    /**
     * Applies a single AFTER-phase favor for the given round context.
     *
     * AFTER-phase favors may depend on the damage numbers, so the
     * RoundCtx carries the damage taken value for proper computation.
     * This method will deduct costs, apply effects and write a log entry.
     *
     * @param gs current game state
     * @param ctx round context containing actor, opponent, face maps and damage taken
     */
    private void applyAfterFavor(GameState gs, RoundCtx ctx) {
        GodFavor f = ctx.me().getChosenFavor();
        int tier = ctx.me().getChosenTier();
        // Only handle AFTER-phase favors that the player can actually use.
        // If there is no chosen favor, or it belongs to another phase,
        // or the player cannot afford it, we simply do nothing.
        if (f == null || f.phase != GodFavor.Phase.AFTER) {
            return;
        }
        if (ctx.me().getFavor() < f.costs[tier]) {
            return;
        }
        // Dispatch to the concrete AFTER favor effect based on its type.
        switch (f.type) {
            case DAMAGE -> {
                // Direct damage to the opponent, independent of dice.
                spend(ctx.me(), f, tier);
                ctx.opp().damage(f.magnitudes[tier]);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (" + f.magnitudes[tier] + " dmg)");
            }
            case HEAL -> {
                // Flat heal: negative damage heals HP.
                spend(ctx.me(), f, tier);
                ctx.me().damage(-f.magnitudes[tier]);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (heal " + f.magnitudes[tier] + ")");
            }
            case HEAL_PER_BLOCKED -> {
                // Heal based on how many incoming attacks you successfully blocked.
                spend(ctx.me(), f, tier);
                int blocked = Math.min(melee(ctx.enemy()), helmets(ctx.self()))
                        + Math.min(ranged(ctx.enemy()), shields(ctx.self()));
                int heal = blocked * f.magnitudes[tier];
                ctx.me().damage(-heal);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (heal " + heal + ")");
            }
            case HEAL_PER_INCOMING_MELEE -> {
                // Heal based on how many melee hits still got through your helmets.
                spend(ctx.me(), f, tier);
                int inc = Math.max(0, melee(ctx.enemy()) - helmets(ctx.self()));
                int heal = inc * f.magnitudes[tier];
                ctx.me().damage(-heal);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (heal " + heal + ")");
            }
            case TOKENS_PER_DAMAGE_TAKEN -> {
                // Gain tokens proportional to damage taken in this round.
                spend(ctx.me(), f, tier);
                int tokens = ctx.dmgTaken() * f.magnitudes[tier];
                ctx.me().addFavor(tokens);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (+" + tokens + " tokens)");
            }
            case TOKENS_PER_STEAL -> {
                // Gain tokens based on how many steal icons you rolled.
                spend(ctx.me(), f, tier);
                int steals = count(ctx.self(), Face.STEAL, Face.STEAL_GOLD);
                int tokens = steals * f.magnitudes[tier];
                ctx.me().addFavor(tokens);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (+" + tokens + " tokens)");
            }
            case HEAL_PER_OPP_FAVOR_SPENT -> {
                // Heal based on how many favor points the opponent spent this round.
                int spent = (ctx.opp().getChosenFavor() != null) ? ctx.opp().getChosenFavor().costs[ctx.opp().getChosenTier()] : 0;
                spend(ctx.me(), f, tier);
                int heal = spent * f.magnitudes[tier];
                ctx.me().damage(-heal);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (heal " + heal + ")");
            }
            default -> {
                // No AFTER-phase behavior defined for this favor type.
            }
        }
    }
}
