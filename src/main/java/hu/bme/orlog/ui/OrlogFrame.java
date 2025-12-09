package hu.bme.orlog.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;

import hu.bme.orlog.io.SaveLoadService;
import hu.bme.orlog.model.DiceSet;
import hu.bme.orlog.model.Face;
import hu.bme.orlog.model.GameState;
import hu.bme.orlog.model.GodFavor;
import hu.bme.orlog.model.GodFavorCatalog;
import hu.bme.orlog.model.OrlogEngine;
import hu.bme.orlog.model.Player;

/**
 * Main application window that composes the board, log and control
 * widgets, and coordinates game actions between the UI and the engine.
 *
 * The frame owns a GameState instance and invokes the OrlogEngine to
 * resolve rounds. It also triggers the board animations and updates the
 * log model.
 */
public class OrlogFrame extends JFrame {
    private static final String RULES_TEXT = """
            You play with 6 dice and have 3 rolls per round.
            Click dice to LOCK/UNLOCK them. Locked dice appear outside the bowl, in a row.
            You can press 'God Favor…' at any time to select a favor (if you have enough tokens).
            After the 3rd roll, the 'End round' state resolves the combat.
            On the right side you see attack/defense bars and a round summary.

            In the game we have several God Favors based on Norse gods.
            Each favor has three tiers: higher tiers are stronger but cost more favor tokens.
            The main effect types are:
            - Pure damage (Thor's Strike): deals extra damage after normal combat.
            - Healing (Idun's Rejuvenation, Odin's Sacrifice): restore health after the round.
            - Remove or ignore defense (Vidar's Might, Ullr's Aim): remove enemy helmets or ignore ranged blocks before damage is calculated.
            - Stronger defense (Baldr's Invulnerability): doubles your shields and helmets for one round.
            - Bonus from blocked hits (Heimdall's Watch): heal for each attack you successfully block.
            - Multiply melee damage (Brunhild's Fury): greatly increases your melee damage if you have enough axes.
            - Majority bonuses (Freyr's Gift): give extra damage or tokens if you have the majority of a certain symbol.
            - Heal from incoming damage (Hel's Grip): heal based on how much melee damage is coming in.
            - Ranged bonuses (Skadi's Hunt): reward you for having many arrows.
            - Destroy opponent tokens (Skuld's Claim): remove the opponent's favor tokens based on your arrows.
            - Ban opponent dice (Frigg's Sight, Loki's Trick): disable some of the opponent's dice for this round.
            - Gain tokens directly (Freyja's Plenty): give you extra favor tokens.
            - Tokens based on damage taken (Mimir's Wisdom): you gain tokens for each damage you suffer this round.
            - Tokens based on stealing (Bragi's Verve): you gain extra tokens for each steal effect you use.
            - Heal from opponent spending tokens (Var's Bond): heal when the opponent spends favor on their own favors.
            - Reduce opponent favor level (Thrymr's Theft): reduce the tier of the opponent's chosen favor or their available tokens.
            """;
    private final SaveLoadService io = new SaveLoadService();
    private final OrlogEngine engine = new OrlogEngine();
    public GameState gs;
    private final BoardPanel board;
    private final LogTableModel logModel = new LogTableModel();

    /**
     * Creates the main application frame, initializes a fresh GameState
     * and builds the UI layout.
     */
    public OrlogFrame() {
        // Set up main window title, close behavior and size
        super("Orlog (Swing) — v6");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1320, 900);
        setLocationRelativeTo(null); // center on screen

        // Create players with their own dice sets using a shared RNG
        Random rng = new Random();
        Player p1 = new Player("You", new DiceSet(6, rng));
        Player p2 = new Player("AI", new DiceSet(6, rng));
        // Create the initial game state object
        gs = new GameState(p1, p2);

        // Build menu bar and main board panel
        setJMenuBar(buildMenu());
        board = new BoardPanel(gs);
        // Table on the right side to show the game log
        JTable logTable = new JTable(logModel);
        logTable.setFillsViewportHeight(true); // always fill the area

        // Main button: either roll dice or close the round
        JButton btnRoll = new JButton(new RollAction());

        // Button to select God Favor for this round
        JButton btnFavor = new JButton(new FavorAction());

        // Right side panel: log table on top, buttons at the bottom
        JPanel right = new JPanel(new BorderLayout());
        right.add(new JScrollPane(logTable), BorderLayout.CENTER);
        JPanel btns = new JPanel(new GridLayout(1, 2, 8, 8));
        btns.add(btnFavor);
        btns.add(btnRoll);
        right.add(btns, BorderLayout.SOUTH);

        // Put the board in the center and the side panel on the right
        setLayout(new BorderLayout());
        add(board, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    /**
     * Handles the click event for the Roll/Next button.
     * Manages the game loop: rolling dice, AI strategy, and resolving rounds.
     *
     * @param e the action event triggered by the button click
     */
    private void onRollClicked(ActionEvent e) {
        // Ignore clicks while resolution animation is running
        if (board.isAnimating()) {
            return;
        }
        // If someone already won, tell the user to start a new game
        if (gs.isGameOver()) {
            JOptionPane.showMessageDialog(OrlogFrame.this, "Game over! Use File > New to start a new game.");
            return;
        }
        // Before the very first roll, ensure both players have a loadout
        if (gs.rollPhase == 1) {
            ensureLoadoutsSelected();
        }
        if (gs.rollPhase <= 3) {
            // Player rolls whatever is unlocked
            gs.p1.getDice().rollUnlocked();
            // AI: roll + apply lock strategy
            gs.p2.getDice().rollUnlocked();
            // AI decides which dice to lock for the next roll
            aiLockStrategy();
            gs.addLog("Roll " + gs.rollPhase + " (AI lock strategy applied)");
            gs.rollPhase++;
            if (gs.rollPhase == 4) {
                ((JButton) e.getSource()).setText("End round");
            }
        } else {
            // After 3 rolls: resolve combat using current faces
            List<Face> f1 = gs.p1.getDice().currentFaces();
            List<Face> f2 = gs.p2.getDice().currentFaces();
            int hpBeforeAI = gs.p2.getHp();
            int hpBeforeP1 = gs.p1.getHp();
            int favorBeforeAI = gs.p2.getFavor();
            int favorBeforeP1 = gs.p1.getFavor();
            // Call the game engine to compute damage and favors
            engine.resolveRound(gs, f1, f2);
            ((JButton) e.getSource()).setText("Roll / Next");
            // Trigger animation sequence (resolution steps + HP flash)
            int dmgToAI = hpBeforeAI - gs.p2.getHp();
            int dmgToP1 = hpBeforeP1 - gs.p1.getHp();
            board.startResolutionAnim(f1, f2, hpBeforeP1, hpBeforeAI, dmgToP1, dmgToAI);
            if (gs.isGameOver()) {
                String winner = gs.p1.getHp() > 0 ? gs.p1.getName() : gs.p2.getName();
                // Offer a quick way to start a new game once there is a winner.
                int choice = JOptionPane.showConfirmDialog(OrlogFrame.this,
                        "Winner: " + winner + "\nStart a new game?",
                        "Game over",
                        JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) {
                    // Recreate players and GameState, similar to File > New.
                    Random rng = new Random();
                    gs = new GameState(new Player("You", new DiceSet(6, rng)),
                                       new Player("AI", new DiceSet(6, rng)));
                    logModel.setLog(gs.log);
                    board.setGameState(gs);
                    board.repaint();
                    // Also reset the roll button text back to the initial state.
                    if (e.getSource() instanceof JButton b) {
                        b.setText("Roll / Next");
                    }
                    return;
                }
            }
            // Detailed round summary log (damage and favor changes)
            // Effective damage: attack minus defense, but never below zero
            int youMeleeDmg = Math.max(0, gs.melee1 - gs.shields2);
            int youRangedDmg = Math.max(0, gs.ranged1 - gs.helmets2);
            int aiMeleeDmg = Math.max(0, gs.melee2 - gs.shields1);
            int aiRangedDmg = Math.max(0, gs.ranged2 - gs.helmets1);
            // Steal amount: what dice allow, but not more than opponent's favor
            int youSteal = Math.min(engine.stealAmount(engine.countFaces(f1)), favorBeforeAI);
            int aiSteal = Math.min(engine.stealAmount(engine.countFaces(f2)), favorBeforeP1);
            int youGold = engine.goldCount(f1);
            int aiGold = engine.goldCount(f2);
            int favorDeltaYou = gs.p1.getFavor() - favorBeforeP1;
            int favorDeltaAI = gs.p2.getFavor() - favorBeforeAI;
            gs.addLog("Favor summary (You/AI): +gold " + youGold + "/" + aiGold
                + ", steal " + youSteal + "/" + aiSteal
                + ", net: " + favorDeltaYou + "/" + favorDeltaAI);
            gs.addLog("Summary: You dealt " + gs.dmg1
                + " base damage (melee: " + youMeleeDmg
                + ", ranged: " + youRangedDmg + ") + favor effects");
            gs.addLog("Summary: AI dealt " + gs.dmg2
                + " base damage (melee: " + aiMeleeDmg
                + ", ranged: " + aiRangedDmg + ") + favor effects");
            gs.addLog("Details: You melee " + gs.melee1 + " vs AI shield " + gs.shields2
                + " | You ranged " + gs.ranged1 + " vs AI helmet " + gs.helmets2);
            gs.addLog("Details: AI melee " + gs.melee2 + " vs Your shield " + gs.shields1
                + " | AI ranged " + gs.ranged2 + " vs Your helmet " + gs.helmets1);
        }
        // Refresh log model and board after each click
        logModel.setLog(gs.log);
        board.setGameState(gs);
        board.repaint();
    }

    /**
     * Build the top menu bar with File and Help menus.
     *
     * @return constructed JMenuBar
     */
    private JMenuBar buildMenu() {
        // Create the menu bar and the main "File" menu
        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("File");

        // File/New: start a completely fresh game state
        JMenuItem miNew = new JMenuItem("New");
        JMenuItem miSave = new JMenuItem("Save...");
        JMenuItem miLoad = new JMenuItem("Load...");
        JMenuItem miExit = new JMenuItem("Exit");
        miNew.addActionListener(e -> {
            // New game: create new players, new dice sets and GameState
            Random rng = new Random();
            gs = new GameState(new Player("You", new DiceSet(6, rng)), new Player("AI", new DiceSet(6, rng)));
            logModel.setLog(gs.log);
            board.setGameState(gs);
            board.repaint();
        });
        miSave.addActionListener(e -> {
            // Save current GameState to a file chosen by the user
            JFileChooser fc = new JFileChooser();
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    io.save(gs, fc.getSelectedFile());
                } catch (Exception ex) {
                    showErr(ex);
                }
            }
        });
        miLoad.addActionListener(e -> {
            // Load a previously saved GameState from file
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    gs = io.load(fc.getSelectedFile());
                    logModel.setLog(gs.log);
                    board.setGameState(gs);
                    board.repaint();
                } catch (Exception ex) {
                    showErr(ex);
                }
            }
        });
        miExit.addActionListener(e -> dispose());
        file.add(miNew);
        file.add(miSave);
        file.add(miLoad);
        file.addSeparator();
        file.add(miExit);
        mb.add(file);

        // Help menu with a simple rules dialog
        JMenu help = new JMenu("Help");
        JMenuItem rules = new JMenuItem("Rules");
        rules.addActionListener(e -> JOptionPane.showMessageDialog(this, RULES_TEXT, "Rules",
                JOptionPane.INFORMATION_MESSAGE));
        help.add(rules);
        mb.add(help);
        return mb;
    }

    /**
     * Ensure both players have exactly three selected God favors. If not,
     * prompt the user to pick and set default AI loadout.
     */
    private void ensureLoadoutsSelected() {
        // If both players already have 3 favors, nothing to do.
        if (gs.p1.getLoadout().size() == 3 && gs.p2.getLoadout().size() == 3)
            return;
        // Get the full catalog of all available God Favors.
        List<GodFavor> catalog = GodFavorCatalog.all();
        // Human player: open a dialog to pick exactly 3 favors.
        gs.p1.setLoadout(selectThreeFavorsDialog(this, "Select 3 God Favors (You)", catalog));
        // AI: pick 3 aggressive favors from the catalog.
        // First sort: DAMAGE-type favors come first (value 0), others after (value 1).
        // Second sort: within each group, lower priority value comes earlier.
        gs.p2.setLoadout(catalog.stream()
            .sorted(Comparator.comparing((GodFavor f) -> f.type == GodFavor.EffectType.DAMAGE ? 0 : 1)
                .thenComparingInt(f -> f.priority))
            .limit(3).collect(Collectors.toList()));
        // Log initial loadouts in two shorter lines so both your and AI's
        // starting favors are clearly visible in the log table.
        gs.addLog("Loadout (You): " + gs.p1.getLoadout());
        gs.addLog("Loadout (AI): " + gs.p2.getLoadout());
    }

    /**
     * Show a dialog allowing the human player to pick exactly three God
     * favors from the provided catalog. The method blocks until a valid
     * selection is made and returns the chosen list.
     *
     * @param parent parent component for the dialog
     * @param title dialog title
     * @param catalog available favors
     * @return list of exactly three selected GodFavor instances
     */
    private List<GodFavor> selectThreeFavorsDialog(Component parent, String title, List<GodFavor> catalog) {
        // Keep asking until the user selects exactly 3 favors.
        while (true) {
            // Build a simple panel with a scrollable list of all favors.
            JPanel panel = new JPanel(new BorderLayout());
            // List model: contains every available God Favor from the catalog.
            DefaultListModel<GodFavor> model = new DefaultListModel<>();
            catalog.forEach(model::addElement); // method reference == f -> model.addElement(f)
            JList<GodFavor> list = new JList<>(model);
            // Allow selecting multiple entries in the list.
            list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            panel.add(new JScrollPane(list), BorderLayout.CENTER);
            panel.add(new JLabel("Select exactly 3 items."), BorderLayout.NORTH);
            // Show OK/Cancel dialog containing the list component.
            int res = JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (res == JOptionPane.OK_OPTION) {
                // OK pressed: check how many items were selected.
                List<GodFavor> sel = list.getSelectedValuesList();
                if (sel.size() == 3) {
                    // Exactly 3 items selected: return this loadout.
                    return sel;
                }
                // Wrong number of items: show warning and loop again.
                JOptionPane.showMessageDialog(parent, "Please select exactly 3 favors!", "Warning",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                // Cancel/close: do not allow leaving without a valid loadout.
                JOptionPane.showMessageDialog(parent,
                    "You must select 3 favors to play. Please try again.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    /**
     * Prompt the given player to choose a God Favor and tier for the
     * current round. Validates affordability and updates the player's
     * chosen favor on success.
     *
     * @param parent parent component for dialogs
     * @param title dialog title
     * @param p player choosing the favor
     * @return true if the player selected a favor and tier, false otherwise
     */
    private boolean chooseFavorForRound(Component parent, String title, Player p) {
        // Make sure both players have a predefined loadout of 3 favors.
        ensureLoadoutsSelected();
        // Work with the current player's chosen loadout.
        List<GodFavor> load = p.getLoadout();
        if (load.isEmpty())
            return false;
        // First check if the player can afford at least one favor at any tier.
        boolean affordable = false;
        for (GodFavor f : load) {
            for (int c : f.costs) {
                if (c <= p.getFavor()) {
                    affordable = true;
                    break;
                }
            }
            if (affordable)
                break;
        }
        if (!affordable) {
            // Early exit: no favor is affordable, show info and clear choice.
            JOptionPane.showMessageDialog(this, "You do not have enough tokens for any favor.");
            p.chooseFavor(null, 0);
            return false;
        }
        // Ask the player to choose a favor and a tier until a valid,
        // affordable combination is selected or the player cancels.
        while (true) {
            // First dialog: pick one favor from the player's loadout.
            GodFavor choice = (GodFavor) JOptionPane.showInputDialog(parent,
                    "Choose a God Favor for this round.", title,
                    JOptionPane.PLAIN_MESSAGE, null, load.toArray(), null);
            if (choice == null) {
                // Cancel/close: no favor is used this round.
                p.chooseFavor(null, 0);
                return false;
            }
            // Second dialog: pick the tier (1-3) for the chosen favor.
            Integer tier = (Integer) JOptionPane.showInputDialog(parent,
                    "Select tier (1-3)", "Tier", JOptionPane.PLAIN_MESSAGE, null,
                    new Integer[] { 1, 2, 3 }, 1);
            if (tier == null) {
                // Cancel/close on tier selection: also means no favor this round.
                p.chooseFavor(null, 0);
                return false;
            }
            // Check if the player has enough tokens for this favor+tier.
            int need = choice.costs[tier - 1];
            if (p.getFavor() < need) {
                JOptionPane.showMessageDialog(this,
                    "Not enough tokens (required: " + need + ", you have: " + p.getFavor() + ")");
                continue;
            }
            // Valid and affordable: store the chosen favor and tier index.
            p.chooseFavor(choice, tier - 1);
            gs.addLog(p.getName() + " selected favor: " + choice.name + " (Tier " + tier + ")");
            break;
        }
        return true;
    }

    /**
     * Simple AI decision routine to pick one favor and tier from its
     * loadout based on heuristic scoring.
     */
    private void aiChooseFavor() {
        // Work only with the AI player and its current loadout.
        Player ai = gs.p2;
        List<GodFavor> load = ai.getLoadout();
        if (load.isEmpty())
            return;
        // We search for the favor+tier combination with the highest score.
        GodFavor best = null;
        int bestTier = 0;
        int bestScore = -999;
        // Try all favors and all tiers (from highest tier downwards).
        for (GodFavor f : load) {
            for (int t = 2; t >= 0; t--) {
                int cost = f.costs[t];
                // Skip tiers we cannot afford with current tokens.
                if (ai.getFavor() < cost)
                    continue;
                int score = 0; // base score before we look at favor type
                // Very simple heuristic: damage is most valuable, then heal,
                // then gaining tokens; everything else gets a small default.
                switch (f.type) {
                    case DAMAGE -> score = 100 + f.magnitudes[t] * 15; 
                    // big base + stronger damage increases score a lot
                    case HEAL -> score = (gs.p1.getHp() > gs.p2.getHp() ? 40 : 0) + f.magnitudes[t] * 10; 
                    // extra bonus if AI is behind on HP
                    case GAIN_TOKENS -> score = 20 + f.magnitudes[t] * 5; 
                    // modest base, tokens are useful but less than raw damage
                    default -> score = 10; 
                    // all other types get a small constant score
                }
                // Keep the best-scoring combination seen so far.
                if (score > bestScore) {
                    bestScore = score;
                    best = f;
                    bestTier = t;
                }
            }
        }
        // Finally, tell the AI player which favor and tier to use.
        ai.chooseFavor(best, bestTier);
        if (best != null)
            gs.addLog("AI favor: " + best.name + " (Tier " + (bestTier + 1) + ")");
    }

    /**
     * Light-weight AI lock strategy: counts face types and decides which
     * faces to keep locked for the next roll, with basic defensive
     * heuristics.
     */
    private void aiLockStrategy() {
        // Look at the current AI dice faces.
        var faces = gs.p2.getDice().currentFaces();
        // Count how many melee, ranged and steal symbols we see.
        int melee = 0, ranged = 0, steal = 0;
        for (int i = 0; i < faces.size(); i++) {
            Face f = faces.get(i);
            if (f == null)
                continue;
            if (f.isAttackMelee())
                melee++;
            if (f.isAttackRanged())
                ranged++;
            if (f.isSteal())
                steal++;
        }
        // Plan: if we are behind in tokens, prefer STEAL faces;
        // otherwise choose the majority between melee/ranged; always keep gold.
        boolean favorBehind = gs.p2.getFavor() < gs.p1.getFavor();
        Face keepType;
        if (favorBehind && steal > 0)
            keepType = Face.STEAL;
        else
            keepType = (melee >= ranged) ? Face.MELEE : Face.RANGED;

        // Decide for each die whether to lock it based on the chosen strategy.
        for (int i = 0; i < faces.size(); i++) {
            Face f = faces.get(i);
            if (f == null)
                continue;
            boolean keep = false;
            // Always keep gold faces.
            if (f.gold)
                keep = true;
            if (keepType == Face.STEAL && f.isSteal())
                keep = true;
            if (keepType == Face.MELEE && f.isAttackMelee())
                keep = true;
            if (keepType == Face.RANGED && f.isAttackRanged())
                keep = true;
            // Defensive bias: if AI has too few helmets vs. Player ranged,
            // prefer to keep helmets.
            if (gs.round > 1 && (gs.helmets2 < gs.ranged1) && f.isHelmet())
                keep = true;
            // Similarly, if AI has too few shields vs. Player melee,
            // prefer to keep shields.
            if (gs.round > 1 && (gs.shields2 < gs.melee1) && f.isShield())
                keep = true;
            gs.p2.getDice().setLocked(i, keep);
        }
    }

    /**
     * Show a simple error dialog and print the stack trace to the console.
     *
     * @param ex exception to display
     */
    private void showErr(Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, ex.toString(), "Hiba", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Action for the Roll/Next button.
     */
    private class RollAction extends AbstractAction {
        public RollAction() {
            super("Roll / Next");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            onRollClicked(e);
        }
    }

    /**
     * Action for the God Favor button.
     */
    private class FavorAction extends AbstractAction {
        public FavorAction() {
            super("God Favor…");
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (gs.isGameOver())
                return;
            // Előző favor loadout méret
            int prevLoadout = gs.p1.getLoadout().size();
            ensureLoadoutsSelected();
            // Ha most választott favorokat, csak frissít és return
            if (prevLoadout != 3 && gs.p1.getLoadout().size() == 3) {
                logModel.setLog(gs.log);
                board.repaint();
                return;
            }
            // Ha már volt favor loadout, akkor jöhet a kör favor választás affordability checkkel
            if (gs.p1.getLoadout().size() == 3) {
                boolean chosen = chooseFavorForRound(OrlogFrame.this, "Select God Favor for this round (You)", gs.p1);
                if (chosen) {
                    aiChooseFavor();
                }
            }
            // After (possible) favors, refresh log and board
            logModel.setLog(gs.log);
            board.repaint();
        }
    }
}
