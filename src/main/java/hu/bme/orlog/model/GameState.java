package hu.bme.orlog.model;

import java.io.Serializable;
import java.util.*;

public class GameState implements Serializable {
    public Player p1;
    public Player p2;
    public int rollPhase = 1;
    public int round = 1;
    public final Deque<String> log = new ArrayDeque<>();

    // last round stats for UI
    public int melee1, ranged1, shields2, helmets2, dmg1;
    public int melee2, ranged2, shields1, helmets1, dmg2;

    public GameState(Player a, Player b){ this.p1=a; this.p2=b; }
    public boolean isGameOver(){ return p1.getHp()==0 || p2.getHp()==0; }
    public void addLog(String s){ log.addFirst(s); if(log.size()>300) log.removeLast(); }
}
