package hu.bme.orlog;

import javax.swing.SwingUtilities;

import hu.bme.orlog.ui.OrlogFrame;

public class App {
    public static void main(String[] args){
        // Start the Swing user interface on the Event Dispatch Thread,
        // which is the recommended way to create and show Swing windows.
        SwingUtilities.invokeLater(() -> new OrlogFrame().setVisible(true));
    }
}