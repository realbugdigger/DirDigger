package core;

import utils.JTreeUtils;
import utils.UrlUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class WrappedJTree implements AddNodeListener {

    private final Object MUTEX = new Object();

    private JTree tree;
    private JTree redirectTree;

    // add if not present or correct http response status
    @Override
    public void addNode(DiggerNode node) {
        synchronized (MUTEX) {
            String url = node.getUrl();
            if (JTreeUtils.notContained(url, (DefaultMutableTreeNode) redirectTree.getModel().getRoot())) {
                if (JTreeUtils.contains(url, (DefaultMutableTreeNode) tree.getModel().getRoot()) || JTreeUtils.contains(url + "/", (DefaultMutableTreeNode) tree.getModel().getRoot())) {
                    System.out.println("Changing " + url + " http response status");
                    DefaultMutableTreeNode treeNode = JTreeUtils.getNode(url, (DefaultMutableTreeNode) tree.getModel().getRoot());
                    DiggerNode updatedDiggerNode = (DiggerNode) treeNode.getUserObject();
                    if (updatedDiggerNode.getResponseStatus() != UrlUtils.HttpResponseCodeStatus.SUCCESS && node.getResponseStatus() == UrlUtils.HttpResponseCodeStatus.SUCCESS) {
                        updatedDiggerNode.setResponseStatus(UrlUtils.HttpResponseCodeStatus.SUCCESS);
                        treeNode.setUserObject(updatedDiggerNode);
                        ((DefaultTreeModel) tree.getModel()).reload(treeNode);
                    }
                } else {
                    System.out.println("Adding " + url + " to regular tree from WrappedJTree#addNode");
                    JTreeUtils.addNode(node, tree);
                }
            }
        }
    }

    public JTree getTree() {
        return tree;
    }

    public void setTree(JTree tree) {
        this.tree = tree;
    }

    public JTree getRedirectTree() {
        return redirectTree;
    }

    public void setRedirectTree(JTree redirectTree) {
        this.redirectTree = redirectTree;
    }
}
