package hu.bme.orlog.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.Timer;

import hu.bme.orlog.model.DiceSet;
import hu.bme.orlog.model.Face;
import hu.bme.orlog.model.GameState;
import hu.bme.orlog.model.Player;

public class BoardPanel extends JPanel {
    private GameState gs;
    private final List<Rectangle> p1DiceBounds = new ArrayList<>();
    private final List<Integer> p1DiceIdx = new ArrayList<>();
    private final List<Rectangle> p2DiceBounds = new ArrayList<>();
    private final List<Integer> p2DiceIdx = new ArrayList<>();
    private final List<Rectangle> p1LockedBounds = new ArrayList<>();
    private final List<Integer> p1LockedIdx = new ArrayList<>();
    private final List<Rectangle> p2LockedBounds = new ArrayList<>();
    private final List<Integer> p2LockedIdx = new ArrayList<>();

    private final int bowlRadius = 150;
    private Rectangle hoverRect = null;

    // Damage animation
    private int animTicksP1 = 0;
    private int animTicksP2 = 0;
    private final Timer animTimer;

    public BoardPanel(GameState gs) {
        this.gs = gs;
        setBackground(new Color(30, 34, 44));

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hoverRect = null;
                setCursor(Cursor.getDefaultCursor());
                repaint();
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (gs.rollPhase > 3) {
                    hoverRect = null;
                    setCursor(Cursor.getDefaultCursor());
                    repaint();
                    return;
                }
                Rectangle r = pickRectAt(e.getX(), e.getY());
                if (r != hoverRect) {
                    hoverRect = r;
                    setCursor(hoverRect == null ? Cursor.getDefaultCursor()
                            : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    repaint();
                }
            }
        });

        animTimer = new Timer(40, e -> {
            boolean repaint = false;
            if (animTicksP1 > 0) {
                animTicksP1--;
                repaint = true;
            }
            if (animTicksP2 > 0) {
                animTicksP2--;
                repaint = true;
            }
            if (repaint)
                repaint();
            if (animTicksP1 == 0 && animTicksP2 == 0)
                ((Timer) e.getSource()).stop();
        });
    }

    public void setGameState(GameState gs) {
        this.gs = gs;
    }

    public void triggerDamageAnim(int dmgToP1, int dmgToP2) {
        if (dmgToP1 > 0) {
            animTicksP1 = 12;
        }
        if (dmgToP2 > 0) {
            animTicksP2 = 12;
        }
        if (animTicksP1 > 0 || animTicksP2 > 0)
            animTimer.start();
    }

    private Rectangle pickRectAt(int x, int y) {
        for (Rectangle r : p1DiceBounds)
            if (r.contains(x, y))
                return r;
        for (Rectangle r : p1LockedBounds)
            if (r.contains(x, y))
                return r;
        for (Rectangle r : p2DiceBounds)
            if (r.contains(x, y))
                return r;
        for (Rectangle r : p2LockedBounds)
            if (r.contains(x, y))
                return r;
        return null;
    }

    private void handleClick(int x, int y) {
        if (gs.rollPhase == 1 && gs.round == 1) {
            boolean hasRolledFace = false;
            for (Face f : gs.p1.getDice().currentFaces()) {
                if (f != null) {
                    hasRolledFace = true;
                    break;
                }
            }
            if (!hasRolledFace) {
                return;
            }
        }
        if (gs.rollPhase <= 3) {
            for (int k = 0; k < p1DiceBounds.size(); k++) {
                if (p1DiceBounds.get(k).contains(x, y)) {
                    int idx = p1DiceIdx.get(k);
                    gs.p1.getDice().toggle(idx);
                    logToggle(gs.p1, idx);
                    repaint();
                    return;
                }
            }
            for (int k = 0; k < p1LockedBounds.size(); k++) {
                if (p1LockedBounds.get(k).contains(x, y)) {
                    int idx = p1LockedIdx.get(k);
                    gs.p1.getDice().toggle(idx);
                    logToggle(gs.p1, idx);
                    repaint();
                    return;
                }
            }
        }
    }

    private void logToggle(Player p, int dieIdx) {
        var faces = p.getDice().currentFaces();
        Face f = (dieIdx < faces.size()) ? faces.get(dieIdx) : null;
        boolean locked = p.getDice().isLocked(dieIdx);
        String who = p.getName();
        String faceStr = (f == null ? "?"
                : (f.isAttackMelee() ? "MELEE"
                        : f.isAttackRanged() ? "RANGED"
                                : f.isShield() ? "SHIELD" : f.isHelmet() ? "HELMET" : f.isSteal() ? "STEAL" : "?"))
                + (f != null && f.gold ? "_GOLD" : "");
        gs.addLog(String.format("%s %s die #%d %s", who, faceStr, dieIdx + 1, locked ? "LOCK" : "UNLOCK"));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();

        Point p1Center = new Point(w / 2, h / 2 - 200);
        Point p2Center = new Point(w / 2, h / 2 + 200);
        drawBowl(g2, p1Center);
        drawBowl(g2, p2Center);

        drawHpStones(g2, 40, 40, gs.p1.getHp());
        drawHpStones(g2, 40, h - 80, gs.p2.getHp());

        drawFavorStack(g2, w - 160, 40, gs.p1.getFavor());
        drawFavorStack(g2, w - 160, h - 80, gs.p2.getFavor());

        g2.setColor(Color.WHITE);
        g2.drawString("Dobás fázis: " + Math.min(gs.rollPhase, 3) + " / 3   |   Forduló: " + gs.round, 20, h / 2);

        // combat bars (last resolution snapshot)
        drawBars(g2, w, h);

        p1DiceBounds.clear();
        p1DiceIdx.clear();
        p2DiceBounds.clear();
        p2DiceIdx.clear();
        p1LockedBounds.clear();
        p1LockedIdx.clear();
        p2LockedBounds.clear();
        p2LockedIdx.clear();
        drawDiceSet(g2, gs.p1.getDice(), p1Center, true, p1DiceBounds, p1DiceIdx, p1LockedBounds, p1LockedIdx);
        drawDiceSet(g2, gs.p2.getDice(), p2Center, false, p2DiceBounds, p2DiceIdx, p2LockedBounds, p2LockedIdx);

        // overlay damage flashes
        if (animTicksP1 > 0) {
            g2.setColor(new Color(200, 30, 30, 40 + animTicksP1 * 10));
            g2.fillOval(p1Center.x - bowlRadius, p1Center.y - bowlRadius, bowlRadius * 2, bowlRadius * 2);
        }
        if (animTicksP2 > 0) {
            g2.setColor(new Color(200, 30, 30, 40 + animTicksP2 * 10));
            g2.fillOval(p2Center.x - bowlRadius, p2Center.y - bowlRadius, bowlRadius * 2, bowlRadius * 2);
        }

        g2.setColor(Color.LIGHT_GRAY);
        g2.drawString("You", 20, 24);
        g2.drawString("AI", 20, h - 24);
        g2.dispose();
    }

    private void drawBars(Graphics2D g2, int w, int h) {
        // jobb-középre, a két tál közé
        int x = w - 280;
        int yTop = getHeight() / 2 - 90;
        int barW = 220;
        int barH = 10;
        int gap = 28; // nagyobb sorköz, hogy ne érjenek össze

        // kisebb cím/betű
        Font base = g2.getFont();
        g2.setFont(base.deriveFont(Font.BOLD, 12f));
        g2.setColor(Color.WHITE);
        g2.drawString("Last Round:", x, yTop - 12);

        // egy kis extra térköz a cím és az első sáv között
        yTop += 12;

        int m1 = gs.melee1, s2 = gs.shields2, r1 = gs.ranged1, h2 = gs.helmets2, d1 = gs.dmg1;
        int m2 = gs.melee2, s1 = gs.shields1, r2 = gs.ranged2, h1 = gs.helmets1, d2 = gs.dmg2;

        drawBar(g2, x, yTop, "You melee vs AI shield", m1, s2, barW, barH);

        drawBar(g2, x, yTop + gap, "You ranged vs AI helmet", r1, h2, barW, barH);
        drawDamage(g2, x, yTop + 2 * gap, "Damage to AI", d1, barW);

        drawBar(g2, x, yTop + 3 * gap, "AI melee vs You shield", m2, s1, barW, barH);
        drawBar(g2, x, yTop + 4 * gap, "AI ranged vs You helmet", r2, h1, barW, barH);
        drawDamage(g2, x, yTop + 5 * gap, "Damage to You", d2, barW);
        g2.setFont(base); // vissza
    }

    private void drawBar(Graphics2D g2, int x, int y, String label, int att, int def, int W, int H) {
        g2.setColor(Color.WHITE);
        g2.drawString(label + " (" + att + " vs " + def + ")", x, y - 3);

        g2.setColor(Color.GRAY);
        g2.drawRect(x, y + 2, W, H);

        int attW = Math.min(W, att * 20);
        int defW = Math.min(W, def * 20);

        g2.setColor(new Color(220, 120, 40)); // attacker
        g2.fillRect(x, y + 2, attW, H);

        g2.setColor(new Color(80, 140, 220)); // defender (fél magasság)
        g2.fillRect(x, y + 2, defW, H / 2);
    }

    private void drawDamage(Graphics2D g2, int x, int y, String label, int dmg, int W) {
        g2.setColor(Color.WHITE);
        g2.drawString(label + ": " + dmg, x, y - 3);

        g2.setColor(Color.GRAY);
        g2.drawRect(x, y + 2, W, 10);

        g2.setColor(new Color(200, 50, 50));
        g2.fillRect(x, y + 2, Math.min(W, dmg * 20), 10);
    }

    private void drawBowl(Graphics2D g2, Point c) {
        g2.setColor(new Color(60, 50, 40));
        g2.fillOval(c.x - bowlRadius, c.y - bowlRadius, bowlRadius * 2, bowlRadius * 2);
        g2.setColor(new Color(90, 72, 55));
        g2.setStroke(new BasicStroke(4f));
        g2.drawOval(c.x - bowlRadius, c.y - bowlRadius, bowlRadius * 2, bowlRadius * 2);
    }

    private void drawHpStones(Graphics2D g2, int x, int y, int hp) {
        int r = 12;
        int gap = 6;
        g2.setColor(new Color(50, 140, 220));
        for (int i = 0; i < hp; i++) {
            int cx = x + (i % 8) * (r + gap);
            int cy = y + (i / 8) * (r + gap);
            g2.fillOval(cx, cy, r, r);
            g2.setColor(new Color(20, 70, 110));
            g2.drawOval(cx, cy, r, r);
            g2.setColor(new Color(50, 140, 220));
        }
    }

    private void drawFavorStack(Graphics2D g2, int x, int y, int tokens) {
        int r = 14, dx = 6, dy = 3;
        int layers = Math.min(5, tokens);
        for (int i = 0; i < layers; i++) {
            int ox = x + i * dx;
            int oy = y - i * dy;
            g2.setColor(new Color(230, 200, 60));
            g2.fillOval(ox, oy, r, r);
            g2.setColor(new Color(140, 120, 20));
            g2.drawOval(ox, oy, r, r);
        }
        if (tokens > 5) {
            g2.setColor(Color.WHITE);
            g2.drawString("+" + (tokens - 5), x + 5 * dx + r + 4, y - 5 * dy + r / 2);
        }
    }

    private void drawDiceSet(Graphics2D g2, DiceSet set, Point center, boolean isP1,
            List<Rectangle> unlockedHit, List<Integer> unlockedIdx,
            List<Rectangle> lockedHit, List<Integer> lockedIdx) {
        int n = set.size();
        int die = 36;
        int ring = bowlRadius - 42;
        double angle0 = isP1 ? Math.PI / 2 : -Math.PI / 2;

        // 1) UNLOCK-oltak továbbra is a tálban körben
        int u = 0;
        var faces = set.currentFaces();
        for (int i = 0; i < n; i++) {
            Face f = (i < faces.size()) ? faces.get(i) : null;
            if (!set.isLocked(i)) {
                double ang = angle0 + u * (Math.PI * 2 / n);
                int rx = (int) (center.x + Math.cos(ang) * ring) - die / 2;
                int ry = (int) (center.y + Math.sin(ang) * ring) - die / 2;
                Rectangle r = new Rectangle(rx, ry, die, die);
                boolean hover = (hoverRect != null && hoverRect.equals(r));
                drawDie(g2, r, f, false, hover);
                unlockedHit.add(r);
                unlockedIdx.add(i);
                u++;
            }
        }

        // 2) LOCK-oltak: KÖZÉPRE, a két tál közé, két vízszintes sorba
        // - felül: játékos (isP1==true)
        // - alul: AI (isP1==false)

        // gyűjtsük ki a lockolt indexeket, hogy a szélességre valós darabszámmal
        // számoljunk
        java.util.List<Integer> locked = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++)
            if (set.isLocked(i))
                locked.add(i);

        int L = locked.size();
        if (L == 0)
            return; // nincs mit kirajzolni

        int gapX = 8;
        int totalW = L * die + (L - 1) * gapX;
        int startX = (getWidth() - totalW) / 2;

        // sorok Y pozíciója középen, a két tál között
        // sorok Y pozíciója: fixen a két tál közötti sáv közepén
        int centerY = getHeight() / 2;
        int spacing = 8; // a két sor közti rést itt tudod állítani
        int yP1 = centerY - die - spacing / 2; // felső sor (You) → h/2 - 40 alapbeállításnál
        int yP2 = centerY + spacing / 2; // alsó sor (AI) → h/2 + 4 alapbeállításnál

        int baseY = isP1 ? yP1 : yP2;

        for (int j = 0; j < L; j++) {
            int idx = locked.get(j);
            Face f = (idx < faces.size()) ? faces.get(idx) : null;

            int rx = startX + j * (die + gapX);
            int ry = baseY;
            Rectangle r = new Rectangle(rx, ry, die, die);
            boolean hover = (hoverRect != null && hoverRect.equals(r));
            drawDie(g2, r, f, true, hover);

            lockedHit.add(r);
            lockedIdx.add(idx);
        }
    }

    private void drawDie(Graphics2D g2, Rectangle r, Face f, boolean locked, boolean hover) {
        g2.setColor(new Color(235, 235, 235));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        float stroke = hover ? 3.5f : 2f;
        g2.setStroke(new BasicStroke(stroke));
        Color border = locked ? new Color(30, 160, 80)
                : (f != null && f.gold ? new Color(200, 160, 20) : Color.DARK_GRAY);
        if (hover)
            border = border.brighter();
        g2.setColor(border);
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        String sym = f == null ? "?"
                : (f.isAttackMelee() ? "⚔"
                        : f.isAttackRanged() ? "🏹"
                                : f.isShield() ? "🛡" : f.isHelmet() ? "⛑" : f.isSteal() ? "🖐" : "?");
        g2.setColor(Color.BLACK);
        g2.setFont(g2.getFont().deriveFont(Font.BOLD, 18f));
        FontMetrics fm = g2.getFontMetrics();
        int sx = r.x + (r.width - fm.stringWidth(sym)) / 2;
        int sy = r.y + (r.height + fm.getAscent()) / 2 - 6;
        g2.drawString(sym, sx, sy);
        if (locked) {
            g2.setColor(new Color(30, 160, 80, 100));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        }
        if (hover) {
            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        }
    }
}
