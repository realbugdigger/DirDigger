import javax.swing.tree.DefaultMutableTreeNode;

public class DiggerNode {

    private DefaultMutableTreeNode parent;
    private String url;

    public DiggerNode(DefaultMutableTreeNode parent, String url) {
        this.parent = parent;
        this.url = url;
    }

    public DefaultMutableTreeNode getParent() {
        return parent;
    }

    public void setParent(DefaultMutableTreeNode parent) {
        this.parent = parent;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
