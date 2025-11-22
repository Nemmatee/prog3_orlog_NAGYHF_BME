package hu.bme.orlog.model;

import java.util.ArrayList;
import java.util.List;
import static hu.bme.orlog.model.GodFavor.EffectType.*;
import static hu.bme.orlog.model.GodFavor.Phase.*;

public class GodFavorCatalog {
    public static List<GodFavor> all(){
        List<GodFavor> ls = new ArrayList<>();
        ls.add(new GodFavor("Thor's Strike", new int[]{4,8,12}, new int[]{2,5,8}, 6, AFTER, DAMAGE));
        ls.add(new GodFavor("Idun's Rejuvenation", new int[]{4,7,10}, new int[]{2,4,6}, 5, AFTER, HEAL));
        ls.add(new GodFavor("Vidar's Might", new int[]{2,3,6}, new int[]{2,4,6}, 4, BEFORE, REMOVE_OPP_HELMETS));
        ls.add(new GodFavor("Ullr's Aim", new int[]{2,3,4}, new int[]{2,3,6}, 4, BEFORE, IGNORE_OPP_RANGED_BLOCKS));
        ls.add(new GodFavor("Heimdall's Watch", new int[]{4,7,10}, new int[]{1,2,3}, 4, AFTER, HEAL_PER_BLOCKED));
        ls.add(new GodFavor("Baldr's Invulnerability", new int[]{3,6,9}, new int[]{1,2,3}, 3, BEFORE, DOUBLE_BLOCKS));
        ls.add(new GodFavor("Brunhild's Fury", new int[]{6,10,18}, new int[]{150,200,300}, 5, BEFORE, MULTIPLY_MELEE));
        ls.add(new GodFavor("Freyr's Gift", new int[]{4,6,8}, new int[]{2,3,4}, 4, BEFORE, BONUS_MAJORITY));
        ls.add(new GodFavor("Hel's Grip", new int[]{6,12,18}, new int[]{1,2,3}, 6, AFTER, HEAL_PER_INCOMING_MELEE));
        ls.add(new GodFavor("Skadi's Hunt", new int[]{6,10,14}, new int[]{1,2,3}, 6, BEFORE, BONUS_PER_RANGED));
        ls.add(new GodFavor("Skuld's Claim", new int[]{4,6,8}, new int[]{2,3,4}, 2, BEFORE, DESTROY_OPP_TOKENS_PER_ARROW));
        ls.add(new GodFavor("Frigg's Sight", new int[]{2,3,4}, new int[]{2,3,4}, 1, BEFORE, BAN_OPP_DICE_THIS_ROUND));
        ls.add(new GodFavor("Loki's Trick", new int[]{3,6,9}, new int[]{1,2,3}, 1, BEFORE, BAN_OPP_DICE_THIS_ROUND));
        ls.add(new GodFavor("Freyja's Plenty", new int[]{2,4,6}, new int[]{1,2,3}, 3, BEFORE, GAIN_TOKENS));
        ls.add(new GodFavor("Mimir's Wisdom", new int[]{3,5,7}, new int[]{1,2,3}, 7, AFTER, TOKENS_PER_DAMAGE_TAKEN));
        ls.add(new GodFavor("Bragi's Verve", new int[]{4,8,12}, new int[]{2,3,4}, 7, AFTER, TOKENS_PER_STEAL));
        ls.add(new GodFavor("Odin's Sacrifice", new int[]{6,8,10}, new int[]{3,4,5}, 5, AFTER, HEAL));
        ls.add(new GodFavor("Var's Bond", new int[]{10,14,18}, new int[]{1,2,3}, 8, AFTER, HEAL_PER_OPP_FAVOR_SPENT));
        ls.add(new GodFavor("Thrymr's Theft", new int[]{3,6,9}, new int[]{1,2,3}, 2, BEFORE, REDUCE_OPP_FAVOR_LEVEL));
        return ls;
    }
}
