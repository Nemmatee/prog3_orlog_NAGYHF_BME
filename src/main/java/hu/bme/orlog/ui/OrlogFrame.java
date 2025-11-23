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

public class OrlogFrame extends JFrame {
    private final SaveLoadService io = new SaveLoadService();
    private final OrlogEngine engine = new OrlogEngine();
    public GameState gs;
    private final BoardPanel board;
    private final LogTableModel logModel = new LogTableModel();

    public OrlogFrame() {
        super("Orlog (Swing) — v6");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1320, 900);
        setLocationRelativeTo(null);

        var rng = new Random();
        var p1 = new Player("You", new DiceSet(6, rng));
        var p2 = new Player("AI", new DiceSet(6, rng));
        gs = new GameState(p1, p2);

        setJMenuBar(buildMenu());
        board = new BoardPanel(gs);
        var logTable = new JTable(logModel);
        logTable.setFillsViewportHeight(true);

        var btnRoll = new JButton(new AbstractAction("Dobás / Következő") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (gs.isGameOver()) {
                    JOptionPane.showMessageDialog(OrlogFrame.this, "Játék vége! File > New az újrakezdéshez.");
                    return;
                }
                if (gs.rollPhase == 1) {
                    ensureLoadoutsSelected();
                }
                if (gs.rollPhase <= 3) {
                    // Player rolls whatever is unlocked
                    gs.p1.getDice().rollUnlocked();
                    // AI: roll + apply lock strategy
                    gs.p2.getDice().rollUnlocked();
                    aiLockStrategy();
                    gs.addLog("Dobás " + gs.rollPhase + " (AI lock stratégia alkalmazva)");
                    gs.rollPhase++;
                    if (gs.rollPhase == 4) {
                        ((JButton) e.getSource()).setText("Kör lezárása");
                    }
                } else {
                    var f1 = gs.p1.getDice().currentFaces();
                    var f2 = gs.p2.getDice().currentFaces();
                    int hpBeforeAI = gs.p2.getHp();
                    int hpBeforeP1 = gs.p1.getHp();
                    engine.resolveRound(gs, f1, f2);
                    ((JButton) e.getSource()).setText("Dobás / Következő");
                    // trigger anim sequence (resolution steps + hp flash)
                    int dmgToAI = hpBeforeAI - gs.p2.getHp();
                    int dmgToP1 = hpBeforeP1 - gs.p1.getHp();
                    board.startResolutionAnim(f1, f2, hpBeforeP1, hpBeforeAI, dmgToP1, dmgToAI);
                    if (gs.isGameOver()) {
                        String winner = gs.p1.getHp() > 0 ? gs.p1.getName() : gs.p2.getName();
                        JOptionPane.showMessageDialog(OrlogFrame.this, "Nyertes: " + winner);
                    }
                }
                logModel.setLog(gs.log);
                board.setGameState(gs);
                board.repaint();
            }
        });

        var btnFavor = new JButton(new AbstractAction("God Favor…") {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (gs.isGameOver())
                    return;
                chooseFavorForRound(OrlogFrame.this, "Kör favor kiválasztása (You)", gs.p1);
                aiChooseFavor();
                logModel.setLog(gs.log);
                board.repaint();
            }
        });

        var right = new JPanel(new BorderLayout());
        right.add(new JScrollPane(logTable), BorderLayout.CENTER);
        var btns = new JPanel(new GridLayout(1, 2, 8, 8));
        btns.add(btnFavor);
        btns.add(btnRoll);
        right.add(btns, BorderLayout.SOUTH);

        setLayout(new BorderLayout());
        add(board, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    private JMenuBar buildMenu() {
        var mb = new JMenuBar();
        var file = new JMenu("File");
        var miNew = new JMenuItem("New");
        var miSave = new JMenuItem("Save...");
        var miLoad = new JMenuItem("Load...");
        var miExit = new JMenuItem("Exit");
        miNew.addActionListener(e -> {
            var rng = new Random();
            gs = new GameState(new Player("You", new DiceSet(6, rng)), new Player("AI", new DiceSet(6, rng)));
            logModel.setLog(gs.log);
            board.setGameState(gs);
            board.repaint();
        });
        miSave.addActionListener(e -> {
            var fc = new JFileChooser();
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    io.save(gs, fc.getSelectedFile());
                } catch (Exception ex) {
                    showErr(ex);
                }
            }
        });
        miLoad.addActionListener(e -> {
            var fc = new JFileChooser();
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

        var help = new JMenu("Help");
        var rules = new JMenuItem("Rules");
        rules.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "6 kocka, 3 dobás/forduló. Kattints a kockákra a LOCK/UNLOCK-hoz. "
                        + "A LOCK-olt kockák a tálon kívül, sorban látszanak. A 'God Favor…' gombbal bármikor "
                        + "kiválaszthatsz favor-t (ha elég tokened van). A 3. dobás után a 'Kör lezárása' lezárja a kört. "
                        + "A jobb oldalon támadás-védekezés sávok és kör-összegzés.",
                "Rules",
                JOptionPane.INFORMATION_MESSAGE));
        help.add(rules);
        mb.add(help);
        return mb;
    }

    private void ensureLoadoutsSelected() {
        if (gs.p1.getLoadout().size() == 3 && gs.p2.getLoadout().size() == 3)
            return;
        List<GodFavor> catalog = GodFavorCatalog.all();
        gs.p1.setLoadout(selectThreeFavorsDialog(this, "Válassz 3 God Favor-t (You)", catalog));
        // AI: pick 3 aggressive favors
        gs.p2.setLoadout(catalog.stream()
                .sorted(Comparator.comparing((GodFavor f) -> f.type == GodFavor.EffectType.DAMAGE ? 0 : 1)
                        .thenComparingInt(f -> f.priority))
                .limit(3).collect(Collectors.toList()));
        gs.addLog("Loadout kiválasztva. You: " + gs.p1.getLoadout() + " | AI: " + gs.p2.getLoadout());
    }

    private List<GodFavor> selectThreeFavorsDialog(Component parent, String title, List<GodFavor> catalog) {
        while (true) {
            JPanel panel = new JPanel(new BorderLayout());
            DefaultListModel<GodFavor> model = new DefaultListModel<>();
            catalog.forEach(model::addElement);
            JList<GodFavor> list = new JList<>(model);
            list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            panel.add(new JScrollPane(list), BorderLayout.CENTER);
            panel.add(new JLabel("Jelölj ki pontosan 3-at."), BorderLayout.NORTH);
            int res = JOptionPane.showConfirmDialog(parent, panel, title, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (res == JOptionPane.OK_OPTION) {
                List<GodFavor> sel = list.getSelectedValuesList();
                if (sel.size() == 3) {
                    return sel;
                }
                JOptionPane.showMessageDialog(parent, "Pontosan 3-at válassz!", "Figyelem",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(parent,
                        "A játékhoz kötelező 3 favor kiválasztása. Próbáld újra.",
                        "Figyelem", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void chooseFavorForRound(Component parent, String title, Player p) {
        ensureLoadoutsSelected();
        List<GodFavor> load = p.getLoadout();
        if (load.isEmpty())
            return;
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
            JOptionPane.showMessageDialog(this, "Jelenleg egyik favorra sincs elég tokened.");
            p.chooseFavor(null, 0);
            return;
        }
        while (true) {
            GodFavor choice = (GodFavor) JOptionPane.showInputDialog(parent,
                    "Válassz egy God Favor-t erre a körre (kötelező!).",
                    title, JOptionPane.PLAIN_MESSAGE, null, load.toArray(), null);
            if (choice == null) {
                JOptionPane.showMessageDialog(this, "Válassz favor-t a körre, nem hagyhatod ki.");
                continue;
            }
            Integer tier = (Integer) JOptionPane.showInputDialog(parent, "Szint (1-3) kiválasztása",
                    "Tier", JOptionPane.PLAIN_MESSAGE, null, new Integer[] { 1, 2, 3 }, 1);
            if (tier == null) {
                JOptionPane.showMessageDialog(this, "A szint kiválasztása kötelező.");
                continue;
            }
            int need = choice.costs[tier - 1];
            if (p.getFavor() < need) {
                JOptionPane.showMessageDialog(this,
                        "Nincs elég tokened ehhez (kell: " + need + ", van: " + p.getFavor() + ")");
                continue;
            }
            p.chooseFavor(choice, tier - 1);
            gs.addLog(p.getName() + " kiválasztotta: " + choice.name + " (Tier " + tier + ")");
            break;
        }
    }

    private void aiChooseFavor() {
        Player ai = gs.p2;
        List<GodFavor> load = ai.getLoadout();
        if (load.isEmpty())
            return;
        GodFavor best = null;
        int bestTier = 0;
        int bestScore = -999;
        for (GodFavor f : load) {
            for (int t = 2; t >= 0; t--) {
                int cost = f.costs[t];
                if (ai.getFavor() < cost)
                    continue;
                int score = 0;
                switch (f.type) {
                    case DAMAGE -> score = 100 + f.magnitudes[t] * 15;
                    case HEAL -> score = (gs.p1.getHp() > gs.p2.getHp() ? 40 : 0) + f.magnitudes[t] * 10;
                    case GAIN_TOKENS -> score = 20 + f.magnitudes[t] * 5;
                    default -> score = 10;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = f;
                    bestTier = t;
                }
            }
        }
        ai.chooseFavor(best, bestTier);
        if (best != null)
            gs.addLog("AI favor: " + best.name + " (Tier " + (bestTier + 1) + ")");
    }

    private void aiLockStrategy() {
        var faces = gs.p2.getDice().currentFaces();
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
        // Plan: if favor deficit, keep steals; else choose majority between
        // melee/ranged; always keep gold
        boolean favorBehind = gs.p2.getFavor() < gs.p1.getFavor();
        Face keepType;
        if (favorBehind && steal > 0)
            keepType = Face.STEAL;
        else
            keepType = (melee >= ranged) ? Face.MELEE : Face.RANGED;

        for (int i = 0; i < faces.size(); i++) {
            Face f = faces.get(i);
            if (f == null)
                continue;
            boolean keep = false;
            if (f.gold)
                keep = true;
            if (keepType == Face.STEAL && f.isSteal())
                keep = true;
            if (keepType == Face.MELEE && f.isAttackMelee())
                keep = true;
            if (keepType == Face.RANGED && f.isAttackRanged())
                keep = true;
            // Defensive bias if player showed many ranged helmets deficit
            if (gs.round > 1 && (gs.helmets1 < gs.ranged2) && f.isHelmet())
                keep = true;
            if (gs.round > 1 && (gs.shields1 < gs.melee2) && f.isShield())
                keep = true;
            gs.p2.getDice().setLocked(i, keep);
        }
    }

    private void showErr(Exception ex) {
        ex.printStackTrace();
        JOptionPane.showMessageDialog(this, ex.toString(), "Hiba", JOptionPane.ERROR_MESSAGE);
    }
}
