package core;

import javax.swing.*;

public class WrappedJTree {

    private final Object MUTEX = new Object();

    private JTree tree;
    private JTree redirectTree;

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
