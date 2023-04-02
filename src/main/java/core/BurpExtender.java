package core;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;

public class BurpExtender implements BurpExtension {

    private MontoyaApi api;

    @Override
    public void initialize(MontoyaApi montoyaApi) {

        this.api = montoyaApi;
        api.extension().setName("core.DirDigger");

        JPanel panel = new DirDigger(api.logging()).getFrame();
        api.userInterface().registerSuiteTab("core.DirDigger", panel);
    }

}
