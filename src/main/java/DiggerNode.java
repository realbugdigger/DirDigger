import utils.UrlUtils;

import javax.swing.tree.DefaultMutableTreeNode;

public class DiggerNode {

    private DefaultMutableTreeNode parent;
    private String url;
    private UrlUtils.HttpResponseCodeStatus responseStatus;

    public DiggerNode(DefaultMutableTreeNode parent, String url, UrlUtils.HttpResponseCodeStatus responseStatus) {
        this.parent = parent;
        this.url = url;
        this.responseStatus = responseStatus;
    }

    @Override
    public String toString() {
        return getUrl();
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

    public UrlUtils.HttpResponseCodeStatus getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(UrlUtils.HttpResponseCodeStatus responseStatus) {
        this.responseStatus = responseStatus;
    }

}
