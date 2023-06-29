package utils;

import core.DiggerNode;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.Enumeration;
import java.util.List;

public final class JTreeUtils {

    private JTreeUtils() {
    }

    private static synchronized void expandAllNodes(JTree tree, TreePath parent) {
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (!node.isLeaf()) {
            Enumeration<?> children = node.children();
            while (children.hasMoreElements()) {
                TreeNode childNode = (TreeNode) children.nextElement();
                TreePath path = parent.pathByAddingChild(childNode);
                expandAllNodes(tree, path);
            }
        }
        tree.expandPath(parent);
    }

    // Call this method after reloading the JTree model to expand all nodes
    public static synchronized void expandAllNodesOnReload(JTree tree) {
        TreeNode root = (TreeNode) tree.getModel().getRoot();
        expandAllNodes(tree, new TreePath(root));
    }

    public static synchronized void addNode(DiggerNode node, JTree tree) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode parent = node.getParent();

        parent.add(new DefaultMutableTreeNode(node));
        model.reload(parent);
    }

    public static synchronized void addChildToParent(DefaultMutableTreeNode child, DefaultMutableTreeNode parent) {
        parent.add(child);
    }

    public static synchronized void removeChildFromParent(DefaultMutableTreeNode child, DefaultMutableTreeNode parent) {
        parent.remove(child);
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

    /**
     * new version works with: "providedUrl", "providedUrl + /" or "providedUrl" without "/" at the end if present
     */
    public static synchronized boolean containsV2(String url, DefaultMutableTreeNode root) {
        boolean contained = contains(url, root);
        if (!contained)
            contained = contains(url + "/", root);
        else if (!contained && url.endsWith("/"))
            contained = contains(url.substring(0, url.length() - 1), root);

        return contained;
    }

    public static synchronized boolean notContainedV2(String url, DefaultMutableTreeNode root) {
        return !containsV2(url, root);
    }

    /**
     * does root have a child with this url?
     */
    public static synchronized boolean containsAtRootLevel(String url, DefaultMutableTreeNode root) {

        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            DiggerNode childNode = (DiggerNode) child.getUserObject();
            if (childNode.getUrl().equals(url)) {
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

        return null;
    }

    /**
     * new version works with: "providedUrl", "providedUrl + /" or "providedUrl" without "/" at the end if present
     */
    public static synchronized DefaultMutableTreeNode findRedirectParentNodeV2(String redirectedUrl, DefaultMutableTreeNode root) {
        DefaultMutableTreeNode result = findRedirectParentNode(redirectedUrl, root);
        if (result == null)
            result = findRedirectParentNode(redirectedUrl + "/", root);
        else if (result == null && redirectedUrl.endsWith("/"))
            result = findRedirectParentNode(redirectedUrl.substring(0, redirectedUrl.length() - 1), root);

        return result;
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
                    return recursiveFind;
            }
        }

        return null;
    }

    /**
     * new version works with: "providedUrl", "providedUrl + /" or "providedUrl" without "/" at the end if present
     */
    public static synchronized DefaultMutableTreeNode getNodeV2(String url, DefaultMutableTreeNode root) {
        DefaultMutableTreeNode result = getNode(url, root);
        if (result == null)
            result = getNode(url + "/", root);
        else if (result == null && url.endsWith("/"))
            result = getNode(url.substring(0, url.length() - 1), root);

        return result;
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

    public static synchronized void setDefaultRoot(JTree tree) {
        DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();
        treeModel.setRoot(new DefaultMutableTreeNode(new DiggerNode(null, "", UrlUtils.HttpResponseCodeStatus.SUCCESS)));
    }
}
