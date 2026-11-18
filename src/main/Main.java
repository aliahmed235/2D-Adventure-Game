package main;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class Main {
    private Main() { }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                JFrame window = new JFrame("True Adventure | 2D Adventure");
                GamePanel panel = new GamePanel();
                window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                window.setResizable(false);
                window.add(panel);
                window.pack();
                window.setLocationRelativeTo(null);
                WindowMode mode = new WindowMode(window, panel);
                Runnable quit = () -> { panel.close(); window.dispose(); };
                panel.setWindowActions(mode::toggle, quit);
                window.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosing(WindowEvent event) { quit.run(); }
                });
                window.setVisible(true);
                panel.start();
            } catch (RuntimeException exception) {
                exception.printStackTrace();
                JOptionPane.showMessageDialog(null, exception.getMessage(), "Cannot start the adventure",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
