import burp.IBurpExtenderCallbacks;
import burp.IExtensionHelpers;
import burp.ITab;
import utils.Globals;

import javax.swing.*;
import java.awt.*;

public class DirDiggerTab implements ITab {

    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;

    public DirDiggerTab(IBurpExtenderCallbacks callbacks, IExtensionHelpers helpers) {
        this.callbacks = callbacks;
        this.helpers = helpers;
    }

    @Override
    public String getTabCaption() {
        return Globals.APP_NAME;
    }

    @Override
    public Component getUiComponent() {
        JPanel panel = new DirDigger(callbacks, helpers).getFrame();
        callbacks.customizeUiComponent(panel);
        return panel;
    }
}
