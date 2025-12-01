package hu.bme.orlog;

import javax.swing.SwingUtilities;

import hu.bme.orlog.ui.OrlogFrame;

public class App {
    public static void main(String[] args){
        // Open the main Orlog game window (start the GUI)
        SwingUtilities.invokeLater(() -> new OrlogFrame().setVisible(true));
    }
}