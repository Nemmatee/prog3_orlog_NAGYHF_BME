package hu.bme.orlog;

import hu.bme.orlog.ui.OrlogFrame;
import javax.swing.SwingUtilities;

public class App {
    public static void main(String[] args){
        SwingUtilities.invokeLater(() -> new OrlogFrame().setVisible(true));
    }
}
