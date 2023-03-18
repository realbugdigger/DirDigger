import burp.IBurpExtenderCallbacks;
import utils.UrlUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.*;

public class DiggerWorker extends SwingWorker<String, DiggerNode> {

    private static final int MAX_WORKER_THREADS = 10;

    private ExecutorService executorService;

    private IBurpExtenderCallbacks callbacks;

    private final String url;
    private final int currentDepth;
    private int maxDepth;
    private final JList<String> dirsAndFilesList;
    private final ListModel<String> dirList;
    private JTree tree;
    private JProgressBar progressBar;
    private DefaultMutableTreeNode root;

    private DiggerWorker(DiggerWorkerBuilder builder) {
        this.callbacks = builder.callbacks;
        this.executorService = builder.executorService;
        this.url = builder.url;
        this.currentDepth = builder.currentDepth;
        this.maxDepth = builder.maxDepth;
        this.dirsAndFilesList = builder.dirsAndFilesList;
        this.tree = builder.tree;
        this.progressBar = builder.progressBar;

        this.dirList = this.dirsAndFilesList.getModel();
        this.root = (DefaultMutableTreeNode) tree.getModel().getRoot();
    }

    @Override
    protected String doInBackground() throws Exception {

        recursive(url, currentDepth);

        return "Successfully finished";
    }

    public void recursive(String url, int depth) {
        for (int i = 0; i < 30 /*dirList.getSize()*/; i++) {

            setProgress((i+1)*10);

            String potential = url + dirList.getElementAt(i);
            if (potential.equals(url))
                continue;
            int rCode = makeRequest(potential);
            if (rCode != 404 && depth < maxDepth) {

                DefaultMutableTreeNode parent = findParentNode(potential, root);
                DiggerNode node = new DiggerNode(parent, potential);
                publish(node);

                DiggerWorker diggerWorker = new DiggerWorker.DiggerWorkerBuilder(potential  + "/", currentDepth + 1)
                        .threadPool(executorService)
                        .directoryList(dirsAndFilesList)
                        .maxDepth(maxDepth)
                        .callbacks(callbacks)
                        .tree(tree)
                        .progressBar(progressBar)
                        .build();
                executorService.submit(diggerWorker);
            }
        }
    }

    @Override
    protected void process(List<DiggerNode> nodes) {
        for (DiggerNode node : nodes) {
            addNode(node.getParent(), node.getUrl());
            progressBar.setMaximum(progressBar.getMaximum() + 300);
        }
    }

    @Override
    protected void done() {
        progressBar.setVisible(false);
        dirsAndFilesList.setModel(new DefaultListModel<>());
    }

    private void addNode(DefaultMutableTreeNode parent, String url) {
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        parent.add(new DefaultMutableTreeNode(url));
        model.reload(parent);
    }

    private DefaultMutableTreeNode findParentNode(String newUrl, DefaultMutableTreeNode root) {
        DefaultMutableTreeNode parent = root;

        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) root.getChildAt(i);
            String childNodeUrl = (String) childNode.getUserObject();

            if(shouldLookIntoChild(newUrl, childNodeUrl)) {
                if (shouldAddInThisChild(newUrl, childNodeUrl)) {
                    parent = childNode;
                    break;
                } else {
                    parent = findParentNode(newUrl, childNode);
                }
            }
        }

        return parent;
    }

    private boolean shouldLookIntoChild(String newUrl, String childNodeUrl) {
        return newUrl.contains(childNodeUrl);
    }

    private boolean shouldAddInThisChild(String newUrl, String childNodeUrl) {
        List<String> newUrlDirs = UrlUtils.getDirectoriesOfUrl(newUrl);
        List<String> childNodeUrlDirs = UrlUtils.getDirectoriesOfUrl(childNodeUrl);

        if (newUrlDirs.size() != childNodeUrlDirs.size() + 1)
            return false;

        newUrlDirs.remove(newUrlDirs.size() - 1);
        for (int i = 0; i < newUrlDirs.size(); i++) {
            if (!newUrlDirs.get(i).equals(childNodeUrlDirs.get(i)))
                return false;
        }
        return true;
    }

    private int makeRequest(String url) {
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4254.0 Safari/537.36");
            return con.getResponseCode();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 404;
    }

    public static class DiggerWorkerBuilder {

        private IBurpExtenderCallbacks callbacks;

        private ExecutorService executorService;

        private final String url;
        private final int currentDepth;
        private int maxDepth;
        private JList<String> dirsAndFilesList;
        private JTree tree;
        private JProgressBar progressBar;

        public DiggerWorkerBuilder(String url, int currentDepth) {
            this.url = url;
            this.currentDepth = currentDepth;
        }

        public DiggerWorkerBuilder threadPool(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public DiggerWorkerBuilder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public DiggerWorkerBuilder directoryList(JList<String> dirsAndFilesList) {
            this.dirsAndFilesList = dirsAndFilesList;
            return this;
        }

        public DiggerWorkerBuilder tree(JTree tree) {
            this.tree = tree;
            return this;
        }

        public DiggerWorkerBuilder progressBar(JProgressBar progressBar) {
            this.progressBar = progressBar;
            return this;
        }

        public DiggerWorkerBuilder callbacks(IBurpExtenderCallbacks callbacks) {
            this.callbacks = callbacks;
            return this;
        }

        public DiggerWorker build() {
            return new DiggerWorker(this);
        }
    }
}
