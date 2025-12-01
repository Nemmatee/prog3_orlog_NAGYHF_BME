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

/**
 * Visual board panel that renders the game bowls, dice, HP stones and
 * resolution animations.
 *
 * This component paints the current GameState and provides methods to
 * start the resolution animation sequence. The animation sequence keeps
 * the visual HP value unchanged until the resolution steps finish so
 * that the red HP flash and the actual HP decrease happen together.
 */
public class BoardPanel extends JPanel {
    // Current game state this panel renders.
    private GameState gs;
    // Hitbox rectangles and indices for player 1 and player 2 dice
    // (both unlocked in the bowls and locked in the center rows).
    private final List<Rectangle> p1DiceBounds = new ArrayList<>();
    private final List<Integer> p1DiceIdx = new ArrayList<>();
    private final List<Rectangle> p2DiceBounds = new ArrayList<>();
    private final List<Integer> p2DiceIdx = new ArrayList<>();
    private final List<Rectangle> p1LockedBounds = new ArrayList<>();
    private final List<Integer> p1LockedIdx = new ArrayList<>();
    private final List<Rectangle> p2LockedBounds = new ArrayList<>();
    private final List<Integer> p2LockedIdx = new ArrayList<>();

    // Visual radius of each wooden bowl.
    private final int bowlRadius = 150;
    // Rectangle under the mouse cursor (for hover effect), or null.
    private Rectangle hoverRect = null;

    // Damage/HP flash animation state.
    private int animTicksP1 = 0;
    private int animTicksP2 = 0;
    private int hpFlashTicks = 0;
    // HP values before the current resolution animation.
    private int prevHp1 = 15;
    private int prevHp2 = 15;
    // Damage that will be animated after the resolution steps finish.
    private int pendingDmgP1 = 0;
    private int pendingDmgP2 = 0;

    private final List<ResolutionStep> resolutionSteps = new ArrayList<>();
    private List<Face> animFacesP1 = List.of();
    private List<Face> animFacesP2 = List.of();
    // Index of the current resolution step, -1 means no resolution animation.
    private int resolutionIdx = -1;
    // Timer driving the damage bowl flash animation.
    private final Timer animTimer;
    // Timer stepping through the resolution steps (melee/ranged/steal).
    private final Timer resolutionTimer;

    /**
     * Create a BoardPanel bound to the provided GameState.
     *
     * @param gs initial game state to display
     */
    public BoardPanel(GameState gs) {
        this.gs = gs;
        setBackground(new Color(30, 34, 44));

        // Mouse listener for clicks and leaving the board area.
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
        // Mouse motion listener to track hover over dice and change cursor.
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                // During resolution phase, hovering does nothing.
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

        // Timer for short damage flashes on the bowls and HP stones.
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
            if (hpFlashTicks > 0) {
                hpFlashTicks--;
                repaint = true;
            }
            if (repaint)
                repaint();
            if (animTicksP1 == 0 && animTicksP2 == 0)
                ((Timer) e.getSource()).stop();
        });

        // Timer that steps through the resolution sequence (melee/ranged/steal)
        // and then triggers the final HP flash and damage overlay.
        Timer tmp = new Timer(1650, e -> {
            if (resolutionIdx + 1 < resolutionSteps.size()) {
                resolutionIdx++;
                repaint();
            } else {
                resolutionIdx = -1;
                resolutionSteps.clear();
                animTicksP1 = pendingDmgP1 > 0 ? 12 : 0;
                animTicksP2 = pendingDmgP2 > 0 ? 12 : 0;
                hpFlashTicks = (pendingDmgP1 > 0 || pendingDmgP2 > 0) ? 12 : 0;
                pendingDmgP1 = 0;
                pendingDmgP2 = 0;
                if (animTicksP1 > 0 || animTicksP2 > 0 || hpFlashTicks > 0) {
                    animTimer.start();
                }
                ((Timer) e.getSource()).stop();
            }
        });
        resolutionTimer = tmp;
    }

    /**
     * Replace the internal GameState instance displayed by this panel.
     * The panel will use the provided GameState for subsequent painting
     * and animations. This method also updates the stored previous HP
     * values so animations start from a consistent baseline.
     *
     * @param gs game state to display
     */
    public void setGameState(GameState gs) {
        this.gs = gs;
        // Do not overwrite previous HP while a resolution animation is active.
        // When starting a resolution animation, startResolutionAnim explicitly
        // sets prevHp1/prevHp2 to the HP values before damage; calling
        // setGameState afterwards (from the frame) must not replace them
        // with the already-updated model HP.
        if (!isAnimating()) {
            this.prevHp1 = gs.p1.getHp();
            this.prevHp2 = gs.p2.getHp();
        }
    }

    /**
     * Returns true if a resolution or damage animation is currently active.
     *
     * @return true when an animation is running
     */
    public boolean isAnimating() {
        return resolutionIdx >= 0 || animTicksP1 > 0 || animTicksP2 > 0 || hpFlashTicks > 0;
    }

    /**
     * Convenience entry point to trigger the damage animation only.
     *
     * This method exists for compatibility and calls startResolutionAnim
     * with the current faces and HP values.
     *
     * @param dmgToP1 damage applied to player 1
     * @param dmgToP2 damage applied to player 2
     */
    public void triggerDamageAnim(int dmgToP1, int dmgToP2) {
        // Kept for backward compatibility; use startResolutionAnim for full sequence
        startResolutionAnim(gs.p1.getDice().currentFaces(), gs.p2.getDice().currentFaces(),
                gs.p1.getHp(), gs.p2.getHp(), dmgToP1, dmgToP2);
    }

    /**
     * Starts the full resolution animation sequence.
     *
     * The sequence visualizes melee, ranged and steal resolution steps in
     * order, then flashes HP loss and plays the final damage overlay. The
     * method receives the face lists and the HP values before damage was
     * applied so the panel can show the previous HP during the sequence
     * and flip to the reduced HP when the flash starts.
     *
     * @param p1Faces    faces rolled by player 1
     * @param p2Faces    faces rolled by player 2
     * @param hpBeforeP1 HP of player 1 before damage
     * @param hpBeforeP2 HP of player 2 before damage
     * @param dmgToP1    damage applied to player 1 (after resolution)
     * @param dmgToP2    damage applied to player 2 (after resolution)
     */
    public void startResolutionAnim(List<Face> p1Faces, List<Face> p2Faces, int hpBeforeP1, int hpBeforeP2,
            int dmgToP1, int dmgToP2) {
        this.animFacesP1 = new ArrayList<>(p1Faces);
        this.animFacesP2 = new ArrayList<>(p2Faces);
        this.prevHp1 = hpBeforeP1;
        this.prevHp2 = hpBeforeP2;
        this.hpFlashTicks = 0;
        this.pendingDmgP1 = dmgToP1;
        this.pendingDmgP2 = dmgToP2;

        resolutionSteps.clear();
        resolutionSteps.add(new ResolutionStep(ResolutionType.MELEE_P1));
        resolutionSteps.add(new ResolutionStep(ResolutionType.MELEE_P2));
        resolutionSteps.add(new ResolutionStep(ResolutionType.RANGED_P1));
        resolutionSteps.add(new ResolutionStep(ResolutionType.RANGED_P2));
        resolutionSteps.add(new ResolutionStep(ResolutionType.STEAL_P1));
        resolutionSteps.add(new ResolutionStep(ResolutionType.STEAL_P2));
        resolutionIdx = 0;
        resolutionTimer.restart();
        repaint();
    }

    /**
     * Returns the rectangle for a dice or locked slot at the given
     * coordinates, or null when none match.
     *
     * @param x x coordinate
     * @param y y coordinate
     * @return hit rectangle or null
     */
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

    /**
     * Handle mouse click on the board. Toggles locks when clicking dice
     * during the roll phase and ignores clicks when not allowed.
     *
     * @param x mouse x coordinate
     * @param y mouse y coordinate
     */
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

    /**
     * Log a lock/unlock toggle action for a player's die.
     *
     * @param p      player whose die changed
     * @param dieIdx index of the die that was toggled
     */
    private void logToggle(Player p, int dieIdx) {
        var faces = p.getDice().currentFaces();
        Face f = (dieIdx < faces.size()) ? faces.get(dieIdx) : null;
        boolean locked = p.getDice().isLocked(dieIdx);
        String who = p.getName();
        String faceStr = "?";
        if (f != null) {
            if (f.isAttackMelee())
                faceStr = "MELEE";
            else if (f.isAttackRanged())
                faceStr = "RANGED";
            else if (f.isShield())
                faceStr = "SHIELD";
            else if (f.isHelmet())
                faceStr = "HELMET";
            else if (f.isSteal())
                faceStr = "STEAL";
            if (f.gold)
                faceStr += "_GOLD";
        }
        gs.addLog(String.format("%s %s die #%d %s", who, faceStr, dieIdx + 1, locked ? "LOCK" : "UNLOCK"));
    }

    /**
     * Paint the full board including bowls, dice, HP, favor stacks and
     * active animations. This method uses the internal GameState to
     * decide what to render and the animation state to overlay effects.
     *
     * @param g Graphics context
     */
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

        // During resolution animation show the previous HP value so the
        // visual decrease happens together with the red flash at the end.
        int displayHp1 = (resolutionIdx >= 0) ? prevHp1 : gs.p1.getHp();
        int displayHp2 = (resolutionIdx >= 0) ? prevHp2 : gs.p2.getHp();
        drawHpStones(g2, 40, 40, displayHp1, prevHp1, hpFlashTicks);
        drawHpStones(g2, 40, h - 80, displayHp2, prevHp2, hpFlashTicks);

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
        List<Face> overrideP1 = (resolutionIdx >= 0) ? animFacesP1 : null;
        List<Face> overrideP2 = (resolutionIdx >= 0) ? animFacesP2 : null;
        drawDiceSet(g2, gs.p1.getDice(), p1Center, true, p1DiceBounds, p1DiceIdx, p1LockedBounds, p1LockedIdx,
                overrideP1);
        drawDiceSet(g2, gs.p2.getDice(), p2Center, false, p2DiceBounds, p2DiceIdx, p2LockedBounds, p2LockedIdx,
                overrideP2);

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

    /**
     * Draw the combat summary bars and damage indicators on the right
     * side of the board.
     *
     * @param g2 graphics context
     * @param w  total panel width
     * @param h  total panel height
     */
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

    /**
     * Draw a single attacker/defender bar with the provided counts.
     *
     * @param g2    graphics context
     * @param x     left coordinate
     * @param y     top coordinate
     * @param label bar label
     * @param att   attacker count
     * @param def   defender count
     * @param W     bar width
     * @param H     bar height
     */
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

    /**
     * Draw a damage bar and label.
     *
     * @param g2    graphics context
     * @param x     left coordinate
     * @param y     top coordinate
     * @param label label text
     * @param dmg   damage value
     * @param W     bar width
     */
    private void drawDamage(Graphics2D g2, int x, int y, String label, int dmg, int W) {
        g2.setColor(Color.WHITE);
        g2.drawString(label + ": " + dmg, x, y - 3);

        g2.setColor(Color.GRAY);
        g2.drawRect(x, y + 2, W, 10);

        g2.setColor(new Color(200, 50, 50));
        g2.fillRect(x, y + 2, Math.min(W, dmg * 20), 10);
    }

    /**
     * Draw a single bowl at the provided center point.
     *
     * @param g2 graphics context
     * @param c  center point of the bowl
     */
    private void drawBowl(Graphics2D g2, Point c) {
        g2.setColor(new Color(60, 50, 40));
        g2.fillOval(c.x - bowlRadius, c.y - bowlRadius, bowlRadius * 2, bowlRadius * 2);
        g2.setColor(new Color(90, 72, 55));
        g2.setStroke(new BasicStroke(4f));
        g2.drawOval(c.x - bowlRadius, c.y - bowlRadius, bowlRadius * 2, bowlRadius * 2);
    }

    /**
     * Draw the HP stones for a player and the red flash for recently
     * lost HP based on prevHp and the current hp value.
     *
     * @param g2         graphics context
     * @param x          left coordinate
     * @param y          top coordinate
     * @param hp         current HP to display
     * @param prevHp     previous HP value used to compute the flash
     * @param flashTicks remaining flash animation ticks
     */
    private void drawHpStones(Graphics2D g2, int x, int y, int hp, int prevHp, int flashTicks) {
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
        if (flashTicks > 0 && prevHp > hp) {
            int lost = prevHp - hp;
            int alpha = Math.min(200, 40 + flashTicks * 10);
            g2.setColor(new Color(220, 50, 50, alpha));
            for (int i = 0; i < lost; i++) {
                int idx = hp + i;
                int cx = x + (idx % 8) * (r + gap);
                int cy = y + (idx / 8) * (r + gap);
                g2.fillOval(cx, cy, r, r);
            }
        }
    }

    /**
     * Draw a stack of favor tokens at the given position.
     *
     * @param g2     graphics context
     * @param x      left coordinate
     * @param y      top coordinate
     * @param tokens number of tokens to render
     */
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

    /**
     * Draws the dice for a player either in the bowl layout or in the
     * resolution strip when overrideFaces is provided. Populates the hit
     * detection lists with rectangles and indices.
     *
     * @param g2            graphics context
     * @param set           dice set to render
     * @param center        bowl center point
     * @param isP1          true for player 1 layout, false for player 2
     * @param unlockedHit   list to populate with unlocked dice rectangles
     * @param unlockedIdx   list to populate with unlocked dice indices
     * @param lockedHit     list to populate with locked dice rectangles
     * @param lockedIdx     list to populate with locked dice indices
     * @param overrideFaces when non-null, use this face list for the
     *                      resolution animation strip
     */
    private void drawDiceSet(Graphics2D g2, DiceSet set, Point center, boolean isP1,
            List<Rectangle> unlockedHit, List<Integer> unlockedIdx,
            List<Rectangle> lockedHit, List<Integer> lockedIdx,
            List<Face> overrideFaces) {
        var faces = (overrideFaces != null) ? overrideFaces : set.currentFaces();
        int n = faces.size();
        int die = 36;
        int ring = bowlRadius - 42;
        double angle0 = isP1 ? Math.PI / 2 : -Math.PI / 2;

        // anim?ci? alatt: kock?k a k?t t?l k?z?tti s?vban, nincs locked sor
        if (overrideFaces != null) {
            int gap = 8;
            int total = n * die + (n - 1) * gap;
            int startX = center.x - total / 2;
            int centerY = getHeight() / 2;
            int spacing = 8;
            int yP1 = centerY - die - spacing / 2;
            int yP2 = centerY + spacing / 2;
            int y = isP1 ? yP1 : yP2;
            for (int i = 0; i < n; i++) {
                Face f = faces.get(i);
                int rx = startX + i * (die + gap);
                Rectangle r = new Rectangle(rx, y, die, die);
                boolean hover = (hoverRect != null && hoverRect.equals(r));
                boolean highlight = shouldHighlight(isP1, i, f);
                drawDie(g2, r, f, false, hover, highlight);
                unlockedHit.add(r);
                unlockedIdx.add(i);
            }
            return;
        }

        // 1) UNLOCK-oltak tov?bbra is a t?lban k?rben
        int u = 0;
        for (int i = 0; i < n; i++) {
            Face f = (i < faces.size()) ? faces.get(i) : null;
            if (!set.isLocked(i)) {
                double ang = angle0 + u * (Math.PI * 2 / n);
                int rx = (int) (center.x + Math.cos(ang) * ring) - die / 2;
                int ry = (int) (center.y + Math.sin(ang) * ring) - die / 2;
                Rectangle r = new Rectangle(rx, ry, die, die);
                boolean hover = (hoverRect != null && hoverRect.equals(r));
                boolean highlight = shouldHighlight(isP1, i, f);
                drawDie(g2, r, f, false, hover, highlight);
                unlockedHit.add(r);
                unlockedIdx.add(i);
                u++;
            }
        }

        // 2) LOCK-oltak: k?z?pre, k?t v?zszintes sorba
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

        int centerY = getHeight() / 2;
        int spacing = 8;
        int yP1 = centerY - die - spacing / 2;
        int yP2 = centerY + spacing / 2;

        int baseY = isP1 ? yP1 : yP2;

        for (int j = 0; j < L; j++) {
            int idx = locked.get(j);
            Face f = (idx < faces.size()) ? faces.get(idx) : null;

            int rx = startX + j * (die + gapX);
            int ry = baseY;
            Rectangle r = new Rectangle(rx, ry, die, die);
            boolean hover = (hoverRect != null && hoverRect.equals(r));
            boolean highlight = shouldHighlight(isP1, idx, f);
            drawDie(g2, r, f, true, hover, highlight);

            lockedHit.add(r);
            lockedIdx.add(idx);
        }
    }

    /**
     * Overload that draws a die without explicit highlight flag.
     *
     * @param g2     graphics context
     * @param r      rectangle to draw into
     * @param f      face value or null
     * @param locked whether the die is locked
     * @param hover  whether the mouse hovers this die
     */
    private void drawDie(Graphics2D g2, Rectangle r, Face f, boolean locked, boolean hover) {
        drawDie(g2, r, f, locked, hover, false);
    }

    /**
     * Determine whether the given face should be visually highlighted
     * for the current resolution step.
     *
     * @param isP1   true when checking player 1's die
     * @param dieIdx index of the die
     * @param f      face to inspect
     * @return true when the face should be highlighted
     */
    private boolean shouldHighlight(boolean isP1, int dieIdx, Face f) {
        if (resolutionIdx < 0 || resolutionIdx >= resolutionSteps.size() || f == null)
            return false;
        ResolutionType type = resolutionSteps.get(resolutionIdx).type();
        return switch (type) {
            case MELEE_P1 -> isP1 ? f.isAttackMelee() : f.isShield();
            case MELEE_P2 -> !isP1 ? f.isAttackMelee() : f.isShield();
            case RANGED_P1 -> isP1 ? f.isAttackRanged() : f.isHelmet();
            case RANGED_P2 -> !isP1 ? f.isAttackRanged() : f.isHelmet();
            case STEAL_P1 -> isP1 && f.isSteal();
            case STEAL_P2 -> !isP1 && f.isSteal();
        };
    }

    /**
     * Draw a single die rectangle with the provided face and state flags.
     *
     * @param g2        graphics context
     * @param r         rectangle to draw into
     * @param f         face value or null
     * @param locked    whether the die is locked
     * @param hover     whether the mouse hovers this die
     * @param highlight whether the die should be highlighted (resolution effect)
     */
    private void drawDie(Graphics2D g2, Rectangle r, Face f, boolean locked, boolean hover, boolean highlight) {
        g2.setColor(new Color(235, 235, 235));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        float stroke = hover ? 3.5f : 2f;
        g2.setStroke(new BasicStroke(stroke));
        Color border = locked ? new Color(30, 160, 80)
                : (f != null && f.gold ? new Color(200, 160, 20) : Color.DARK_GRAY);
        if (hover)
            border = border.brighter();
        if (highlight)
            border = new Color(220, 80, 40);
        g2.setColor(border);
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        String sym = "?";
        if (f != null) {
            if (f.isAttackMelee()) {
                sym = "⚔";
            } else if (f.isAttackRanged()) {
                sym = "🏹";
            } else if (f.isShield()) {
                sym = "🛡";
            } else if (f.isHelmet()) {
                sym = "⛑";
            } else if (f.isSteal()) {
                sym = "🖐";
            }
        }
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
        if (highlight) {
            g2.setColor(new Color(255, 200, 120, 60));
            g2.fillRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 12, 12);
        }
        if (hover) {
            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        }
    }

    private record ResolutionStep(ResolutionType type) {
    }

    private enum ResolutionType {
        MELEE_P1, MELEE_P2, RANGED_P1, RANGED_P2, STEAL_P1, STEAL_P2
    }
}
