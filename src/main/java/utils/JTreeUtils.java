package utils;

import core.DiggerNode;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;

public final class JTreeUtils {

    private JTreeUtils() {
    }

    public static synchronized void addNode(DiggerNode node, JTree tree) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode parent = node.getParent();
        parent.add(new DefaultMutableTreeNode(node));
        model.reload(parent);
    }

    public static synchronized DefaultMutableTreeNode findParentNode(String newUrl, DefaultMutableTreeNode root) {
        DefaultMutableTreeNode parent = root;

        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) root.getChildAt(i);
            DiggerNode childDiggerNode = (DiggerNode) childNode.getUserObject();
            String childNodeUrl = childDiggerNode.getUrl();

            if(shouldLookIntoChild(newUrl, childNodeUrl)) {
                if (shouldAddInThisChild(newUrl, childNodeUrl)) {
                    parent = childNode;
                    break;
                } else {
                    parent = findParentNode(newUrl, childNode);
                }
            }
        }

        return parent;
    }

    private static synchronized boolean shouldLookIntoChild(String newUrl, String childNodeUrl) {
        return newUrl.contains(childNodeUrl);
    }

    private static synchronized boolean shouldAddInThisChild(String newUrl, String childNodeUrl) {
        List<String> newUrlDirs = UrlUtils.getDirectoriesOfUrl(newUrl);
        List<String> childNodeUrlDirs = UrlUtils.getDirectoriesOfUrl(childNodeUrl);

        if (newUrlDirs.size() != childNodeUrlDirs.size() + 1)
            return false;

        newUrlDirs.remove(newUrlDirs.size() - 1);
        for (int i = 0; i < newUrlDirs.size(); i++) {
            if (!newUrlDirs.get(i).equals(childNodeUrlDirs.get(i)))
                return false;
        }
        return true;
    }
}
