import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;

public class BurpExtender implements BurpExtension {

    private MontoyaApi api;

    @Override
    public void initialize(MontoyaApi montoyaApi) {

        this.api = montoyaApi;
        api.extension().setName("DirDigger");

        JPanel panel = new DirDigger(api.logging()).getFrame();
        api.userInterface().registerSuiteTab("DirDigger", panel);
    }

}
