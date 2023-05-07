package core;

import org.slf4j.bridge.SLF4JBridgeHandler;

import javax.swing.*;
import java.awt.*;

public class MainTest {

    static {
        // Remove existing handlers attached to the JUL root logger
        SLF4JBridgeHandler.removeHandlersForRootLogger();

        // Add the SLF4JBridgeHandler to the JUL root logger
        SLF4JBridgeHandler.install();
    }

    public static void main(String[] args) {

        JFrame frame = new JFrame();
        JPanel panel = new DirDigger(null).getFrame();
        frame.add(panel);
        frame.setSize(new Dimension(1700, 1200));
        frame.setVisible(true);
    }
}
