package hu.bme.orlog.model;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Engine that contains the core Orlog round resolution logic and helpers
 * for counting faces and computing damage/tokens.
 */
public class OrlogEngine {

    /**
     * Small per-player view for a round to avoid repeated ternaries.
     */
    private record RoundCtx(Player me, Player opp, Map<Face, Integer> self, Map<Face, Integer> enemy, int dmgTaken) {}

    /**
     * Counts occurrences of each Face in the provided list and returns a map
     * from Face to its count.
     *
     * @param faces list of faces to count
     * @return map with counts for each face type
     */
    public Map<Face, Integer> countFaces(List<Face> faces) {
        Map<Face, Integer> m = new EnumMap<>(Face.class);
        for (Face f : faces) {
            if (f != null) {
                m.merge(f, 1, Integer::sum);
            }
        }
        return m;
    }

    private int count(Map<Face, Integer> m, Face... faces) {
        int sum = 0;
        for (Face f : faces) {
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
     * Removes up to {@code amount} from base first, then gold; returns how many were removed.
     */
    private int removeUpTo(Map<Face, Integer> m, Face base, Face gold, int amount) {
        int removed = 0;
        int baseCount = m.getOrDefault(base, 0);
        int rmBase = Math.min(baseCount, amount);
        m.put(base, baseCount - rmBase);
        removed += rmBase;
        int left = amount - rmBase;
        if (left > 0) {
            int goldCount = m.getOrDefault(gold, 0);
            int rmGold = Math.min(goldCount, left);
            m.put(gold, Math.max(0, goldCount - rmGold));
            removed += rmGold;
        }
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
        Map<Face, Integer> a = countFaces(p1Faces);
        Map<Face, Integer> b = countFaces(p2Faces);

        applyBeforeFavors(gs, a, b);

        // STEAL
        int s1 = stealAmount(a);
        int s2 = stealAmount(b);
        int s1real = Math.min(s1, gs.p2.getFavor());
        int s2real = Math.min(s2, gs.p1.getFavor());
        gs.p1.addFavor(s1real);
        gs.p2.spendFavor(s1real);
        gs.p2.addFavor(s2real);
        gs.p1.spendFavor(s2real);

        // Count after before-effects
        int melee1 = melee(a), melee2 = melee(b);
        int ranged1 = ranged(a), ranged2 = ranged(b);
        int sh2 = shields(b), he2 = helmets(b);
        int sh1 = shields(a), he1 = helmets(a);

        int dmg1 = Math.max(0, melee1 - sh2) + Math.max(0, ranged1 - he2);
        int dmg2 = Math.max(0, melee2 - sh1) + Math.max(0, ranged2 - he1);

        gs.p1.addFavor(goldCount(p1Faces));
        gs.p2.addFavor(goldCount(p2Faces));

        gs.p2.damage(dmg1);
        gs.p1.damage(dmg2);

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

        gs.addLog(String.format(
                "R%d: %s dealt %d (M:%d vs S:%d, R:%d vs H:%d) | %s dealt %d (M:%d vs S:%d, R:%d vs H:%d)",
                gs.round, gs.p1.getName(), dmg1, melee1, sh2, ranged1, he2,
                gs.p2.getName(), dmg2, melee2, sh1, ranged2, he1));
        gs.round++;
        gs.rollPhase = 1;
        gs.p1.chooseFavor(null, 0);
        gs.p2.chooseFavor(null, 0);
        gs.p1.getDice().clearLocks();
        gs.p2.getDice().clearLocks();
    }

    private void spend(Player p, GodFavor f, int tier) {
        p.spendFavor(f.costs[tier]);
    }

    private void applyBeforeFavors(GameState gs, Map<Face, Integer> a, Map<Face, Integer> b) {
        List<RoundCtx> ctxs = List.of(new RoundCtx(gs.p1, gs.p2, a, b, 0), new RoundCtx(gs.p2, gs.p1, b, a, 0));
        ctxs.stream().sorted(Comparator.comparingInt(ctx -> {
            GodFavor f = ctx.me().getChosenFavor();
            return (f != null && f.phase == GodFavor.Phase.BEFORE) ? f.priority : Integer.MAX_VALUE;
        })).forEach(ctx -> applyBeforeFavor(gs, ctx));
    }

    private void applyAfterFavors(GameState gs, Map<Face, Integer> a, Map<Face, Integer> b, int dmg1, int dmg2) {
        List<RoundCtx> ctxs = List.of(
                new RoundCtx(gs.p1, gs.p2, a, b, dmg2),
                new RoundCtx(gs.p2, gs.p1, b, a, dmg1));

        ctxs.stream().sorted(Comparator.comparingInt(ctx -> {
            GodFavor f = ctx.me().getChosenFavor();
            return (f != null && f.phase == GodFavor.Phase.AFTER) ? f.priority : Integer.MAX_VALUE;
        })).forEach(ctx -> applyAfterFavor(gs, ctx));
    }

    private void applyBeforeFavor(GameState gs, RoundCtx ctx) {
        GodFavor f = ctx.me().getChosenFavor();
        int tier = ctx.me().getChosenTier();
        if (f == null || f.phase != GodFavor.Phase.BEFORE) {
            return;
        }
        if (ctx.me().getFavor() < f.costs[tier]) {
            return;
        }
        switch (f.type) {
            case REMOVE_OPP_HELMETS -> {
                spend(ctx.me(), f, tier);
                int rm = Math.min(f.magnitudes[tier], count(ctx.enemy(), Face.HELMET, Face.HELMET_GOLD));
                removeUpTo(ctx.enemy(), Face.HELMET, Face.HELMET_GOLD, rm);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (-" + rm + " helmets)");
            }
            case IGNORE_OPP_RANGED_BLOCKS -> {
                spend(ctx.me(), f, tier);
                int rm = Math.min(f.magnitudes[tier], count(ctx.enemy(), Face.SHIELD, Face.SHIELD_GOLD));
                removeUpTo(ctx.enemy(), Face.SHIELD, Face.SHIELD_GOLD, rm);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (ignore " + rm + " shields)");
            }
            case DOUBLE_BLOCKS -> {
                spend(ctx.me(), f, tier);
                int add = f.magnitudes[tier];
                ctx.self().put(Face.HELMET, ctx.self().getOrDefault(Face.HELMET, 0) + ctx.self().getOrDefault(Face.HELMET, 0) * add);
                ctx.self().put(Face.HELMET_GOLD, ctx.self().getOrDefault(Face.HELMET_GOLD, 0) + ctx.self().getOrDefault(Face.HELMET_GOLD, 0) * add);
                ctx.self().put(Face.SHIELD, ctx.self().getOrDefault(Face.SHIELD, 0) + ctx.self().getOrDefault(Face.SHIELD, 0) * add);
                ctx.self().put(Face.SHIELD_GOLD, ctx.self().getOrDefault(Face.SHIELD_GOLD, 0) + ctx.self().getOrDefault(Face.SHIELD_GOLD, 0) * add);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (+blocks)");
            }
            case BONUS_PER_RANGED -> {
                spend(ctx.me(), f, tier);
                int bonus = f.magnitudes[tier] * count(ctx.self(), Face.RANGED, Face.RANGED_GOLD);
                ctx.self().put(Face.RANGED, ctx.self().getOrDefault(Face.RANGED, 0) + bonus);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (+" + bonus + " arrows)");
            }
            case MULTIPLY_MELEE -> {
                spend(ctx.me(), f, tier);
                int percent = f.magnitudes[tier];
                int base = count(ctx.self(), Face.MELEE, Face.MELEE_GOLD);
                int extra = (base * (percent - 100)) / 100;
                ctx.self().put(Face.MELEE, ctx.self().getOrDefault(Face.MELEE, 0) + extra);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (x" + percent + "% melee)");
            }
            case BONUS_MAJORITY -> {
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
                spend(ctx.me(), f, tier);
                int arrows = count(ctx.self(), Face.RANGED, Face.RANGED_GOLD);
                int toDestroy = arrows * f.magnitudes[tier];
                int real = Math.min(toDestroy, ctx.opp().getFavor());
                ctx.opp().spendFavor(real);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (-" + real + " opp tokens)");
            }
            case GAIN_TOKENS -> {
                spend(ctx.me(), f, tier);
                ctx.me().addFavor(f.magnitudes[tier]);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (+" + f.magnitudes[tier] + " tokens)");
            }
            default -> {
            }
        }
    }

    private void applyAfterFavor(GameState gs, RoundCtx ctx) {
        GodFavor f = ctx.me().getChosenFavor();
        int tier = ctx.me().getChosenTier();
        if (f == null || f.phase != GodFavor.Phase.AFTER) {
            return;
        }
        if (ctx.me().getFavor() < f.costs[tier]) {
            return;
        }
        switch (f.type) {
            case DAMAGE -> {
                spend(ctx.me(), f, tier);
                ctx.opp().damage(f.magnitudes[tier]);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (" + f.magnitudes[tier] + " dmg)");
            }
            case HEAL -> {
                spend(ctx.me(), f, tier);
                ctx.me().damage(-f.magnitudes[tier]);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (heal " + f.magnitudes[tier] + ")");
            }
            case HEAL_PER_BLOCKED -> {
                spend(ctx.me(), f, tier);
                int blocked = Math.min(melee(ctx.enemy()), helmets(ctx.self()))
                        + Math.min(ranged(ctx.enemy()), shields(ctx.self()));
                int heal = blocked * f.magnitudes[tier];
                ctx.me().damage(-heal);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (heal " + heal + ")");
            }
            case HEAL_PER_INCOMING_MELEE -> {
                spend(ctx.me(), f, tier);
                int inc = Math.max(0, melee(ctx.enemy()) - helmets(ctx.self()));
                int heal = inc * f.magnitudes[tier];
                ctx.me().damage(-heal);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (heal " + heal + ")");
            }
            case TOKENS_PER_DAMAGE_TAKEN -> {
                spend(ctx.me(), f, tier);
                int tokens = ctx.dmgTaken() * f.magnitudes[tier];
                ctx.me().addFavor(tokens);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (+" + tokens + " tokens)");
            }
            case TOKENS_PER_STEAL -> {
                spend(ctx.me(), f, tier);
                int steals = count(ctx.self(), Face.STEAL, Face.STEAL_GOLD);
                int tokens = steals * f.magnitudes[tier];
                ctx.me().addFavor(tokens);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (+" + tokens + " tokens)");
            }
            case HEAL_PER_OPP_FAVOR_SPENT -> {
                int spent = (ctx.opp().getChosenFavor() != null) ? ctx.opp().getChosenFavor().costs[ctx.opp().getChosenTier()] : 0;
                spend(ctx.me(), f, tier);
                int heal = spent * f.magnitudes[tier];
                ctx.me().damage(-heal);
                gs.addLog(ctx.me().getName() + " used " + f.name + " (heal " + heal + ")");
            }
            default -> {
            }
        }
    }
}
