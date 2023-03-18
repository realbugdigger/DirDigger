import utils.UrlUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class CustomIconRenderer extends DefaultTreeCellRenderer {

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        Object nodeObj = ((DefaultMutableTreeNode) value).getUserObject();
        DiggerNode diggerNode = (DiggerNode) nodeObj;
        UrlUtils.HttpResponseCodeStatus responseStatus = diggerNode.getResponseStatus();

        switch (responseStatus) {
            case INFORMATIONAL:
                if (leaf) {
                    setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/info-document.png")));
                } else {
                    if (expanded) {
                        setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/info-dir-open.png")));
                    } else {
                        setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/info-dir-closed.png")));
                    }
                }
                break;
            case SUCCESS:
                if (leaf) {
                    setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/success-document.png")));
                } else {
                    if (expanded) {
                        setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/success-dir-open.png")));
                    } else {
                        setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/success-dir-closed.png")));
                    }
                }
                break;
            case REDIRECTION:
                if (leaf) {
                    setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/redirect-document.png")));
                } else {
                    if (expanded) {
                        setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/redirect-dir-open.png")));
                    } else {
                        setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/redirect-dir-closed.png")));
                    }
                }
                break;
            case CLIENT_ERROR:
                if (leaf) {
                    setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/client_error-document.png")));
                } else {
                    if (expanded) {
                        setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/client_error-dir-open.png")));
                    } else {
                        setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/client_error-dir-closed.png")));
                    }
                }
                break;
            default:
                if (leaf) {
                    setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/server_error-document.png")));
                } else {
                    if (expanded) {
                        setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/server_error-dir-open.png")));
                    } else {
                        setIcon(new ImageIcon(CustomIconRenderer.class.getResource("images/server_error-dir-closed.png")));
                    }
                }

        }

        return this;
    }
}
