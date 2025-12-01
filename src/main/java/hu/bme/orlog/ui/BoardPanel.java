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

    // Ordered list of resolution steps (melee, ranged, steal) that
    // the animation will play through one by one.
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
        // Store the initial game state that this panel will render.
        this.gs = gs;
        // Dark background color so the bowls, dice and HP stones stand out.
        setBackground(new Color(30, 34, 44));

        // Mouse listener for clicks and when the mouse leaves the board area.
        // Only care about two events here:
        //  - mouseClicked: map the click position to a die and toggle lock/unlock
        //  - mouseExited: clear hover state and reset the cursor when leaving
        addMouseListener(new BoardMouseListener());
        // Mouse motion listener to track hover over dice and change cursor.
        // This is responsible for the hand cursor and light highlight when
        // the user moves the mouse over a die they can click.
        addMouseMotionListener(new BoardMouseMotionListener());
        // Timer for short damage flashes on the bowls and HP stones.
        // This fires every 40 ms and simply decreases the tick counters
        // for the damage overlays. When all counters reach zero, it stops
        // itself. The actual drawing is handled in paintComponent.
        animTimer = new Timer(40, new DamageFlashTimerListener());
        // Timer that steps through the resolution sequence (melee/ranged/steal)
        // and then triggers the final HP flash and damage overlay.
        // Every 1650 ms it advances resolutionIdx to the next ResolutionStep
        // so that shouldHighlight() can change which dice are emphasized.
        // When all steps are done, it starts the short damage/HP flash timer.
        Timer tmp = new Timer(1650, new ResolutionTimerListener());
        resolutionTimer = tmp;
    }

    /**
     * Replace the internal GameState used by this panel.
     * When no animation is running, also update prevHp1/prevHp2 so
     * the next HP flash starts from the latest HP values.
     *
     * @param gs game state to display
     */
    public void setGameState(GameState gs) {
        this.gs = gs;
        // Only refresh prevHp1/prevHp2 when nothing is animating.
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
        // Copy the faces so the animation is independent of later dice changes.
        this.animFacesP1 = new ArrayList<>(p1Faces);
        this.animFacesP2 = new ArrayList<>(p2Faces);
        // Remember HP values from before the resolution so we can
        // draw the "old" HP during the whole sequence.
        this.prevHp1 = hpBeforeP1;
        this.prevHp2 = hpBeforeP2;
        // Reset HP flash state – flash will only start after resolution ends.
        this.hpFlashTicks = 0;
        // Store damage values so the ResolutionTimerListener can convert
        // them into animation ticks later.
        this.pendingDmgP1 = dmgToP1;
        this.pendingDmgP2 = dmgToP2;

        // Build the fixed order of resolution steps the timer will play.
        resolutionSteps.clear();
        resolutionSteps.add(new ResolutionStep(ResolutionType.MELEE_P1));
        resolutionSteps.add(new ResolutionStep(ResolutionType.MELEE_P2));
        resolutionSteps.add(new ResolutionStep(ResolutionType.RANGED_P1));
        resolutionSteps.add(new ResolutionStep(ResolutionType.RANGED_P2));
        resolutionSteps.add(new ResolutionStep(ResolutionType.STEAL_P1));
        resolutionSteps.add(new ResolutionStep(ResolutionType.STEAL_P2));
        // Start from the first step and restart the timer that advances them.
        resolutionIdx = 0;
        resolutionTimer.restart();
        // Immediate repaint so the first resolution frame appears at once.
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
        // Check player 1 unlocked dice in the bowl.
        for (Rectangle r : p1DiceBounds)
            if (r.contains(x, y))
                return r;
        // Then player 1 locked dice in the center row.
        for (Rectangle r : p1LockedBounds)
            if (r.contains(x, y))
                return r;
        // Then player 2 unlocked dice in the bowl.
        for (Rectangle r : p2DiceBounds)
            if (r.contains(x, y))
                return r;
        // Finally player 2 locked dice in the center row.
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
        // Special rule: in the very first roll, ignore clicks until
        // at least one die has a rolled face. This prevents locking
        // completely empty slots before the first dice roll.
        if (gs.rollPhase == 1 && gs.round == 1) {
            boolean hasRolledFace = false;
            // Scan player 1's current faces and check if any is non-null.
            for (Face f : gs.p1.getDice().currentFaces()) {
                if (f != null) {
                    hasRolledFace = true;
                    break;
                }
            }
            // If all faces are still null, do nothing on click.
            if (!hasRolledFace) {
                return;
            }
        }
        // Only allow locking/unlocking while we are still in roll phases 1–3.
        // After that the resolution phase starts and dice should not change.
        if (gs.rollPhase <= 3) {
            // First try to toggle a die that is still in the bowl
            // (unlocked dice around the wooden bowl).
            for (int k = 0; k < p1DiceBounds.size(); k++) {
                if (p1DiceBounds.get(k).contains(x, y)) {
                    int idx = p1DiceIdx.get(k);
                    gs.p1.getDice().toggle(idx);   // flip locked/unlocked state
                    logToggle(gs.p1, idx);         // write a line into the log
                    repaint();                     // update the UI immediately
                    return;
                }
            }
            // If the click was not on a bowl die, try the locked row
            // in the middle strip between the two bowls.
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
        // Convert the face type into a short, readable text.
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
            // Mark golden faces with a _GOLD suffix.
            if (f.gold)
                faceStr += "_GOLD";
        }
        // Append a line to the GameState log about the toggle.
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
        // Convert the generic Graphics object to Graphics2D so we can
        // use extra features like antialiasing and custom strokes.
        Graphics2D g2 = (Graphics2D) g.create();
        // Turn on antialiasing so circles, text and diagonals look smooth
        // instead of pixelated.
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Query the current panel size on every repaint so the layout
        // adapts when the window is resized.
        int w = getWidth();
        int h = getHeight();

        // Compute centers of the two wooden bowls (player 1 on top, player 2 bottom).
        Point p1Center = new Point(w / 2, h / 2 - 200);
        Point p2Center = new Point(w / 2, h / 2 + 200);
        drawBowl(g2, p1Center);
        drawBowl(g2, p2Center);

        // During resolution animation show the previous HP value so the
        // visual decrease happens together with the red flash at the end.
        int displayHp1 = (resolutionIdx >= 0) ? prevHp1 : gs.p1.getHp();
        int displayHp2 = (resolutionIdx >= 0) ? prevHp2 : gs.p2.getHp();
        // HP P1: small left/top margin.
        drawHpStones(g2, 40, 40, displayHp1, prevHp1, hpFlashTicks);
        // HP P2: same left margin, 80 px above bottom.
        drawHpStones(g2, 40, h - 80, displayHp2, prevHp2, hpFlashTicks);

        // Draw favor (God favor) tokens for both players on the right side.
        // Favor: 160 px from right, aligned with HP rows.
        drawFavorStack(g2, w - 160, 40, gs.p1.getFavor());
        drawFavorStack(g2, w - 160, h - 80, gs.p2.getFavor());

        g2.setColor(Color.WHITE);
        // Text label for roll phase and round number in the middle.
        g2.drawString("Dobás fázis: " + Math.min(gs.rollPhase, 3) + " / 3   |   Forduló: " + gs.round, 20, h / 2);

        // combat bars (last resolution snapshot)
        drawBars(g2, w, h);

        // Before redrawing dice, clear all hit detection lists.
        p1DiceBounds.clear();
        p1DiceIdx.clear();
        p2DiceBounds.clear();
        p2DiceIdx.clear();
        p1LockedBounds.clear();
        p1LockedIdx.clear();
        p2LockedBounds.clear();
        p2LockedIdx.clear();
        // During resolution animation, use animFaces* instead of live dice faces.
        List<Face> overrideP1 = (resolutionIdx >= 0) ? animFacesP1 : null;
        List<Face> overrideP2 = (resolutionIdx >= 0) ? animFacesP2 : null;
        drawDiceSet(g2, gs.p1.getDice(), p1Center, true, p1DiceBounds, p1DiceIdx, p1LockedBounds, p1LockedIdx,
                overrideP1);
        drawDiceSet(g2, gs.p2.getDice(), p2Center, false, p2DiceBounds, p2DiceIdx, p2LockedBounds, p2LockedIdx,
                overrideP2);

        // Overlay red damage flashes on top of the bowls.
        if (animTicksP1 > 0) {
            // Alpha = 40 + ticks*10: starts faint and gets stronger
            // with higher pending damage animation ticks.
            g2.setColor(new Color(200, 30, 30, 40 + animTicksP1 * 10));
            // Use the same center and radius as the wooden bowl so
            // the flash exactly covers the bowl area.
            g2.fillOval(p1Center.x - bowlRadius, p1Center.y - bowlRadius, bowlRadius * 2, bowlRadius * 2);
        }
        if (animTicksP2 > 0) {
            // Same logic for player 2's bowl flash.
            g2.setColor(new Color(200, 30, 30, 40 + animTicksP2 * 10));
            g2.fillOval(p2Center.x - bowlRadius, p2Center.y - bowlRadius, bowlRadius * 2, bowlRadius * 2);
        }

        g2.setColor(Color.LIGHT_GRAY);
        // Simple labels for who is at the top/bottom bowl.
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
        // Position bars on the right, roughly between the two bowls.
        // x = w - 280 leaves a margin from the right window edge.
        int x = w - 280;
        // Start slightly above the vertical center so all 6 rows fit.
        int yTop = getHeight() / 2 - 90;
        // Each bar is 220 px wide and 10 px high.
        int barW = 220;
        int barH = 10;
        // Vertical distance between bar rows so texts and bars do not overlap.
        int gap = 28;

        // Use a slightly smaller bold font for the "Last Round" header.
        Font base = g2.getFont();
        g2.setFont(base.deriveFont(Font.BOLD, 12f));
        g2.setColor(Color.WHITE);
        g2.drawString("Last Round:", x, yTop - 12);

        // Add extra space between the title and the first bar row.
        yTop += 12;

        // Your melee / ranged / damage and AI's defense from last round.
        int m1 = gs.melee1;   // your melee attacks
        int s2 = gs.shields2; // AI shields blocking your melee
        int r1 = gs.ranged1;  // your ranged attacks
        int h2 = gs.helmets2; // AI helmets blocking your ranged
        int d1 = gs.dmg1;     // damage dealt to AI
        // AI melee / ranged / damage and your defense from last round.
        int m2 = gs.melee2;   // AI melee attacks
        int s1 = gs.shields1; // your shields blocking AI melee
        int r2 = gs.ranged2;  // AI ranged attacks
        int h1 = gs.helmets1; // your helmets blocking AI ranged
        int d2 = gs.dmg2;     // damage taken by you

        // First three rows: your melee/ranged vs AI defense and resulting damage.
        drawBar(g2, x, yTop, "You melee vs AI shield", m1, s2, barW, barH);
        drawBar(g2, x, yTop + gap, "You ranged vs AI helmet", r1, h2, barW, barH);
        drawDamage(g2, x, yTop + 2 * gap, "Damage to AI", d1, barW);

        // Next three rows: AI melee/ranged vs your defense and damage to you.
        drawBar(g2, x, yTop + 3 * gap, "AI melee vs You shield", m2, s1, barW, barH);
        drawBar(g2, x, yTop + 4 * gap, "AI ranged vs You helmet", r2, h1, barW, barH);
        drawDamage(g2, x, yTop + 5 * gap, "Damage to You", d2, barW);
        // Restore original font after drawing the summary.
        g2.setFont(base);
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
        // Write the label and the raw attacker/defender numbers above the bar.
        g2.setColor(Color.WHITE);
        g2.drawString(label + " (" + att + " vs " + def + ")", x, y - 3);

        // Gray outline rectangle representing the full possible bar width.
        g2.setColor(Color.GRAY);
        g2.drawRect(x, y + 2, W, H);

        // Scale attacker/defender counts into pixels, capped at full width.
        int attW = Math.min(W, att * 20);
        int defW = Math.min(W, def * 20);

        // Orange bar: attacker strength (full height).
        g2.setColor(new Color(220, 120, 40)); // attacker
        g2.fillRect(x, y + 2, attW, H);

        // Blue bar: defender strength (half height) drawn on top of the base.
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
        // Label including the numeric damage value.
        g2.setColor(Color.WHITE);
        g2.drawString(label + ": " + dmg, x, y - 3);

        // Gray outline for the full possible damage bar.
        g2.setColor(Color.GRAY);
        g2.drawRect(x, y + 2, W, 10);

        // Red fill showing proportional damage (20 px per damage point, capped).
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
        // Fill a brown circle as the wooden bowl interior.
        g2.setColor(new Color(60, 50, 40));
        g2.fillOval(c.x - bowlRadius, c.y - bowlRadius, bowlRadius * 2, bowlRadius * 2);
        // Draw a lighter rim with a thicker stroke to make the bowl stand out.
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
        // Draw one blue stone per current HP in a grid (8 per row).
        for (int i = 0; i < hp; i++) {
            // i % 8 = column index (0..7), i / 8 = row index.
            int cx = x + (i % 8) * (r + gap);   // horizontal position in the HP grid
            int cy = y + (i / 8) * (r + gap);   // vertical position in the HP grid
            g2.fillOval(cx, cy, r, r);
            // Dark outline around each HP stone so the circle edge is visible.
            g2.setColor(new Color(20, 70, 110));
            g2.drawOval(cx, cy, r, r);
            // Reset to base blue for the next stone.
            g2.setColor(new Color(50, 140, 220));
        }
        // If we recently lost HP, overlay the missing stones in red.
        if (flashTicks > 0 && prevHp > hp) {
            // Number of stones to flash equals HP lost.
            int lost = prevHp - hp;
            // Alpha grows with flashTicks but is capped, so the red
            // overlay fades out smoothly during the animation.
            int alpha = Math.min(200, 40 + flashTicks * 10);
            g2.setColor(new Color(220, 50, 50, alpha));
            // Flash the stones that disappeared: indices from hp up to prevHp-1.
            for (int i = 0; i < lost; i++) {
                int idx = hp + i;  // index of a lost HP stone
                // Same grid logic as above (8 per row) using idx.
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
        // Radius of each golden token (small disk size).
        int r = 14;
        // Horizontal / vertical offset between stacked tokens to fake depth.
        int dx = 6;
        int dy = 3;
        // We only draw at most 5 disks; the rest is shown as "+N".
        int layers = Math.min(5, tokens);
        // Draw up to 5 overlapping golden disks as a stack.
        for (int i = 0; i < layers; i++) {
            // Each next token is shifted a bit right and up (dx, dy).
            int ox = x + i * dx;
            int oy = y - i * dy;
            // Fill token with bright gold color.
            g2.setColor(new Color(230, 200, 60));
            g2.fillOval(ox, oy, r, r);
            // Darker outline so the round shape is visible.
            g2.setColor(new Color(140, 120, 20));
            g2.drawOval(ox, oy, r, r);
        }
        // If there are more than 5 tokens, show the extra count as "+N".
        if (tokens > 5) {
            g2.setColor(Color.WHITE);
            // Place "+N" to the right of the topmost disk.
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
        // Either use the override faces for animation or the live dice faces.
        var faces = (overrideFaces != null) ? overrideFaces : set.currentFaces();
        int n = faces.size();        // number of dice to draw
        int die = 36;                // width/height of one die square in pixels
        int ring = bowlRadius - 42;  // radius of the ring around the bowl for unlocked dice
        // Start angle: player 1 dice on the top half, player 2 on the bottom half.
        double angle0 = isP1 ? Math.PI / 2 : -Math.PI / 2;

        // During resolution animation: draw dice in a horizontal strip
        // between the bowls, without a separate locked row.
        if (overrideFaces != null) {
            // Dice are drawn in a compact horizontal strip between the bowls.
            int gap = 8;                         // horizontal space between dice
            int total = n * die + (n - 1) * gap; // total width of all dice + gaps
            int startX = center.x - total / 2;   // center the strip around "center.x"
            int centerY = getHeight() / 2;       // vertical middle of the panel
            int spacing = 8;                     // gap between P1 and P2 rows
            // Player 1 dice row above the center line, player 2 below.
            int yP1 = centerY - die - spacing / 2;
            int yP2 = centerY + spacing / 2;
            int y = isP1 ? yP1 : yP2;
            for (int i = 0; i < n; i++) {
                Face f = faces.get(i);
                int rx = startX + i * (die + gap); // x-position of this die in the strip
                Rectangle r = new Rectangle(rx, y, die, die);
                boolean hover = (hoverRect != null && hoverRect.equals(r));
                boolean highlight = shouldHighlight(isP1, i, f);
                drawDie(g2, r, f, false, hover, highlight);
                // Even in animation mode we still populate hit rectangles.
                unlockedHit.add(r);
                unlockedIdx.add(i);
            }
            return;
        }

        // 1) Unlocked dice: keep them around the bowl on a ring.
        int u = 0; // how many unlocked dice we have placed so far
        for (int i = 0; i < n; i++) {
            Face f = (i < faces.size()) ? faces.get(i) : null;
            if (!set.isLocked(i)) {
            // Place unlocked dice evenly spaced on a circle around the bowl.
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

        // 2) Locked dice: move them to the center strip between bowls.
        java.util.List<Integer> locked = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++)
            if (set.isLocked(i))
                locked.add(i);

        int L = locked.size();
        if (L == 0)
            return; // nothing to draw when there are no locked dice

        int gapX = 8;                          // horizontal gap between locked dice
        int totalW = L * die + (L - 1) * gapX; // total width of locked dice row
        int startX = (getWidth() - totalW) / 2; // center locked row horizontally

        int centerY = getHeight() / 2;         // place locked rows between bowls
        int spacing = 8;                       // vertical distance between P1/P2 rows
        int yP1 = centerY - die - spacing / 2; // locked row for player 1 (upper strip)
        int yP2 = centerY + spacing / 2;       // locked row for player 2 (lower strip)

        int baseY = isP1 ? yP1 : yP2;          // choose row based on player

        for (int j = 0; j < L; j++) {
            int idx = locked.get(j);
            Face f = (idx < faces.size()) ? faces.get(idx) : null;

            int rx = startX + j * (die + gapX); // x-position of this locked die
            int ry = baseY;                     // y-position: row chosen above
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
        ResolutionType type = resolutionSteps.get(resolutionIdx).getType();
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
        // Base white rounded rectangle for the die body.
        g2.setColor(new Color(235, 235, 235));
        g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        // Slightly thicker border when hovered.
        float stroke = hover ? 3.5f : 2f;
        g2.setStroke(new BasicStroke(stroke));
        // Border color encodes locked/golden/default state.
        Color border;
        if (locked) {
            // Green border when the die is locked.
            border = new Color(30, 160, 80);
        } else if (f != null && f.gold) {
            // Golden border when the face itself is a golden face.
            border = new Color(200, 160, 20);
        } else {
            // Default dark border for normal, unlocked dice.
            border = Color.DARK_GRAY;
        }
        if (hover)
            border = border.brighter();
        if (highlight)
            border = new Color(220, 80, 40);
        g2.setColor(border);
        g2.drawRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        // Pick a small symbol based on the Face type.
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
        // Center the symbol text inside the die rectangle.
        int sx = r.x + (r.width - fm.stringWidth(sym)) / 2;
        int sy = r.y + (r.height + fm.getAscent()) / 2 - 6;
        g2.drawString(sym, sx, sy);
        // Green translucent overlay for locked dice.
        if (locked) {
            g2.setColor(new Color(30, 160, 80, 100));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        }
        // Soft orange glow for dice that are part of the current resolution step.
        if (highlight) {
            g2.setColor(new Color(255, 200, 120, 60));
            g2.fillRoundRect(r.x - 2, r.y - 2, r.width + 4, r.height + 4, 12, 12);
        }
        // Light white overlay when the mouse is hovering this die.
        if (hover) {
            g2.setColor(new Color(255, 255, 255, 40));
            g2.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
        }
    }

    /**
     * Mouse listener class handling clicks and when the mouse leaves
     * the board area. Delegates to handleClick and clears hover state
     * on exit.
     *
     * This is used by the constructor via addMouseListener(new BoardMouseListener()).
     */
    private class BoardMouseListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            // Convert the click position into a board coordinate and let
            // handleClick() decide whether a die of player 1 should toggle.
            handleClick(e.getX(), e.getY());
        }

        @Override
        public void mouseExited(MouseEvent e) {
            // When leaving the panel, remove any hover rectangle and reset
            // the cursor to default to avoid a stale hand cursor.
            hoverRect = null;
            setCursor(Cursor.getDefaultCursor());
            repaint();
        }
    }

    /**
     * Mouse motion listener used to track hover over dice and update
     * the hand cursor and hover highlight.
     *
     * This is attached in the constructor with addMouseMotionListener
     * and is responsible for the "hover" interaction only.
     */
    private class BoardMouseMotionListener extends MouseMotionAdapter {
        @Override
        public void mouseMoved(MouseEvent e) {
            // After roll phase 3 the resolution is running, so hovering
            // should not allow the player to interact with dice anymore.
            if (gs.rollPhase > 3) {
                hoverRect = null;
                setCursor(Cursor.getDefaultCursor());
                repaint();
                return;
            }
            // Ask the hit detection which die rectangle is under the mouse.
            Rectangle r = pickRectAt(e.getX(), e.getY());
            // Only update when the hovered rectangle actually changed to
            // avoid unnecessary repaints.
            if (r != hoverRect) {
                hoverRect = r;
                // Show a hand cursor when the mouse is over a clickable die,
                // otherwise fall back to the default cursor.
                setCursor(hoverRect == null ? Cursor.getDefaultCursor()
                        : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                repaint();
            }
        }
    }

    /**
     * ActionListener implementation for the short damage flash timer.
     * Decrements the tick counters and stops the timer when finished.
     *
     * The constructor wires this listener into animTimer. The listener
     * only updates the animation state; the visual effect is painted in
     * paintComponent() and drawHpStones().
     */
    private class DamageFlashTimerListener implements java.awt.event.ActionListener {
        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            boolean needsRepaint = false;
            // If player 1 has remaining bowl flash ticks, decrease by one
            // so the red overlay slowly fades out.
            if (animTicksP1 > 0) {
                animTicksP1--;
                needsRepaint = true;
            }
            // Same for player 2: count down its bowl flash ticks.
            if (animTicksP2 > 0) {
                animTicksP2--;
                needsRepaint = true;
            }
            // HP flash ticks control the red overlay on the lost HP stones.
            // When this reaches zero, the HP stones are drawn normally again.
            if (hpFlashTicks > 0) {
                hpFlashTicks--;
                needsRepaint = true;
            }
            // Repaint the board only when at least one counter changed.
            if (needsRepaint) {
                repaint();
            }
            // Once both bowl flash counters reach zero the timer can stop.
            if (animTicksP1 == 0 && animTicksP2 == 0) {
                ((Timer) e.getSource()).stop();
            }
        }
    }

    /**
     * ActionListener implementation for the resolution sequence timer.
     * Steps through ResolutionStep entries and then starts the damage
     * flash animation when done.
     *
     * Each tick moves to the next ResolutionStep so that shouldHighlight()
     * changes which dice are emphasized in the UI.
     */
    private class ResolutionTimerListener implements java.awt.event.ActionListener {
        @Override
        public void actionPerformed(java.awt.event.ActionEvent e) {
            if (resolutionIdx + 1 < resolutionSteps.size()) {
                // Move to the next resolution phase (melee/ranged/steal)
                // and repaint so different dice are highlighted.
                resolutionIdx++;
                repaint();
            } else {
                // All resolution steps finished: reset state and trigger
                // the short damage/HP flash animation.
                resolutionIdx = -1;
                resolutionSteps.clear();
                // Set how many ticks the red bowl flash should last
                // for each player based on the pending damage.
                animTicksP1 = pendingDmgP1 > 0 ? 12 : 0;
                animTicksP2 = pendingDmgP2 > 0 ? 12 : 0;
                // HP flash ticks are shared: if either player took damage
                // we show a short red overlay on the lost HP stones.
                hpFlashTicks = (pendingDmgP1 > 0 || pendingDmgP2 > 0) ? 12 : 0;
                // Damage has now been scheduled for animation, so it is
                // safe to clear the pending counters.
                pendingDmgP1 = 0;
                pendingDmgP2 = 0;
                // Only start the short flash timer when there is at least
                // one non-zero tick counter to animate.
                if (animTicksP1 > 0 || animTicksP2 > 0 || hpFlashTicks > 0) {
                    animTimer.start();
                }
                ((Timer) e.getSource()).stop();
            }
        }
    }

    /**
     * Small data class representing one resolution animation step.
     * It only holds a ResolutionType describing what to highlight
     * (e.g., melee for player 1, ranged for player 2, etc.).
     */
    private static class ResolutionStep {
        private final ResolutionType type;

        ResolutionStep(ResolutionType type) {
            this.type = type;
        }

        ResolutionType getType() {
            return type;
        }
    }

    /**
     * Enumeration of all resolution step kinds in the order they are
     * played: first melee for both players, then ranged, then steal.
     */
    private enum ResolutionType {
        MELEE_P1, // highlight player 1 melee attacks vs AI shields
        MELEE_P2, // highlight AI melee attacks vs player 1 shields
        RANGED_P1, // highlight player 1 ranged attacks vs AI helmets
        RANGED_P2, // highlight AI ranged attacks vs player 1 helmets
        STEAL_P1, // highlight player 1 steal faces
        STEAL_P2  // highlight AI steal faces
    }
}
