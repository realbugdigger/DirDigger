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

    public static synchronized boolean contains(String url, DefaultMutableTreeNode root) {
        DefaultMutableTreeNode parent = root;
        DiggerNode parentNode = (DiggerNode) parent.getUserObject();
        if (parentNode.getUrl().equals(url))
            return true;

        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            DiggerNode childNode = (DiggerNode) child.getUserObject();
            if (childNode.getUrl().equals(url)) {
                return true;
            }
            else {
                if (contains(url, child))
                    return true;
            }
        }

        return false;
    }

    public static synchronized boolean notContained(String url, DefaultMutableTreeNode root) {
        return !contains(url, root);
    }

    public static synchronized DefaultMutableTreeNode findRedirectParentNode(String redirectedUrl, DefaultMutableTreeNode root) {
        DefaultMutableTreeNode parent = root;
        DiggerNode parentNode = (DiggerNode) parent.getUserObject();
        if (parentNode.getUrl().equals(redirectedUrl))
            return parent.getParent() != null ? (DefaultMutableTreeNode) parent.getParent() : parent;

        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            DiggerNode childNode = (DiggerNode) child.getUserObject();
            if (childNode.getUrl().equals(redirectedUrl)) {
                return parent;
            }
            else {
                DefaultMutableTreeNode recursiveFind = findRedirectParentNode(redirectedUrl, child);
                if (recursiveFind != null)
                    return (DefaultMutableTreeNode) recursiveFind.getParent();
            }
        }

        return parent;
    }

    public static synchronized DefaultMutableTreeNode getNode(String url, DefaultMutableTreeNode root) {
        DefaultMutableTreeNode parent = root;
        DiggerNode parentNode = (DiggerNode) parent.getUserObject();
        if (parentNode.getUrl().equals(url))
            return parent;

        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            DiggerNode childNode = (DiggerNode) child.getUserObject();
            if (childNode.getUrl().equals(url)) {
                return child;
            }
            else {
                DefaultMutableTreeNode recursiveFind = getNode(url, child);
                if (recursiveFind != null)
                    return child;
            }
        }

        return null;
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
