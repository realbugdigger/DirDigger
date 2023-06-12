package core;

import org.apache.http.HttpHost;
import utils.UrlUtils;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;

public class ApplicationContainer implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<String> dirList;
    private List<String> fileExList;
    private List<ThreadStateInfo> threads; //NOSONAR
    private int progressBarValue;
    private int dirDepth;
    private int threadNum;
    private HttpHost proxy;
    private List<Integer> responseCodes;
    private boolean followRedirect;
    private DefaultMutableTreeNode regularTreeRoot;
    private DefaultMutableTreeNode redirectTreeRoot;

    public List<String> getDirList() {
        return dirList;
    }

    public void setDirList(List<String> dirList) {
        this.dirList = dirList;
    }

    public List<String> getFileExList() {
        return fileExList;
    }

    public void setFileExList(List<String> fileExList) {
        this.fileExList = fileExList;
    }

    public List<ThreadStateInfo> getThreads() {
        return threads;
    }

    public void setThreads(List<ThreadStateInfo> threads) {
        this.threads = threads;
    }

    public int getProgressBarValue() {
        return progressBarValue;
    }

    public void setProgressBarValue(int progressBarValue) {
        this.progressBarValue = progressBarValue;
    }

    public HttpHost getProxy() {
        return proxy;
    }

    public void setProxy(String proxy) {
        this.proxy = UrlUtils.loadProxy(proxy);
    }

    public boolean isFollowRedirect() {
        return followRedirect;
    }

    public void setFollowRedirect(boolean followRedirect) {
        this.followRedirect = followRedirect;
    }

    public int getDirDepth() {
        return dirDepth;
    }

    public void setDirDepth(int dirDepth) {
        this.dirDepth = dirDepth;
    }

    public int getThreadNum() {
        return threadNum;
    }

    public void setThreadNum(int threadNum) {
        this.threadNum = threadNum;
    }

    public List<Integer> getResponseCodes() {
        return responseCodes;
    }

    public void setResponseCodes(List<Integer> responseCodes) {
        this.responseCodes = responseCodes;
    }

    public DefaultMutableTreeNode getRegularTreeRoot() {
        return regularTreeRoot;
    }

    public void setRegularTreeRoot(DefaultMutableTreeNode regularTreeRoot) {
        this.regularTreeRoot = regularTreeRoot;
    }

    public DefaultMutableTreeNode getRedirectTreeRoot() {
        return redirectTreeRoot;
    }

    public void setRedirectTreeRoot(DefaultMutableTreeNode redirectTreeRoot) {
        this.redirectTreeRoot = redirectTreeRoot;
    }
}
