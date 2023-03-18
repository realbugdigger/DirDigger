import burp.IBurpExtender;
import burp.IBurpExtenderCallbacks;
import burp.IExtensionHelpers;

public class BurpExtender implements IBurpExtender {

    private IBurpExtenderCallbacks callbacks;
    private IExtensionHelpers helpers;

    @Override
    public void registerExtenderCallbacks(IBurpExtenderCallbacks callbacks) {

        this.callbacks = callbacks;
        helpers = callbacks.getHelpers();
        callbacks.setExtensionName("DirDigger");
        callbacks.addSuiteTab(new DirDiggerTab(callbacks, helpers));

        // This is how we receive helper's object reference from callback instance.
//        IExtensionHelpers helpers = callbacks.getHelpers();

    }
}
