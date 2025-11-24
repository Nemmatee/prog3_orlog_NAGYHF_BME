package hu.bme.orlog.model;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds the global game state for a match, including players, round counters
 * and a simple log buffer for UI display.
 */
public class GameState implements Serializable {
    /** Player one */
    public Player p1;
    /** Player two */
    public Player p2;
    /** Roll phase indicator */
    public int rollPhase = 1;
    /** Current round number */
    public int round = 1;
    /** In-memory deque holding the recent game log messages */
    public final Deque<String> log = new ArrayDeque<>();

    // last round stats for UI
    public int melee1, ranged1, shields2, helmets2, dmg1;
    public int melee2, ranged2, shields1, helmets1, dmg2;

    /**
     * Creates a new GameState for two players.
     *
     * @param a player one
     * @param b player two
     */
    public GameState(Player a, Player b){ this.p1=a; this.p2=b; }

    /**
     * Returns true when either player's HP reached zero.
     *
     * @return true if the game is over
     */
    public boolean isGameOver(){ return p1.getHp()==0 || p2.getHp()==0; }

    /**
     * Adds an entry to the internal log. The log is kept to a bounded size.
     *
     * @param s message to append to the log
     */
    public void addLog(String s){ log.addFirst(s); if(log.size()>300) log.removeLast(); }
}
