import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class Practice {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createAndShowGUI());
    }

    private static void createAndShowGUI() {
        // Create the JFrame
        JFrame frame = new JFrame("Scrollable JTree Example");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(300, 200);

        // Create the root node
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Root");

        // Add some child nodes to the root node
        for (int i = 0; i < 10; i++) {
            DefaultMutableTreeNode childNode = new DefaultMutableTreeNode("Node " + i);
            rootNode.add(childNode);

            // Add some sub-child nodes to each child node
            for (int j = 0; j < 5; j++) {
                DefaultMutableTreeNode subChildNode = new DefaultMutableTreeNode("Sub-Node " + i + "." + j);
                childNode.add(subChildNode);
            }
        }

        // Create the JTree with the root node
        JTree tree = new JTree(rootNode);

        // Create a JScrollPane and add the JTree to it
        JScrollPane scrollPane = new JScrollPane(tree);

        // Add the JScrollPane to the JFrame
        frame.add(scrollPane);

        // Display the JFrame
        frame.setVisible(true);
    }
}
