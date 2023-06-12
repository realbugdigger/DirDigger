package core;

import burp.api.montoya.logging.Logging;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JTreeUtils;
import utils.UrlUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DiggerWorker extends SwingWorker<String, Boolean> {

    private static final Logger log = LoggerFactory.getLogger(DiggerWorker.class);

    private Logging logging;

    private PausableThreadPoolExecutor executorService;

    private org.apache.http.impl.nio.client.CloseableHttpAsyncClient httpAsyncClient;

    private List<String> dirList;
    private List<Integer> watchedResponseCodes;

    private final String url;
    private String scheme;
    private String hostname;
    private int port;

    List<String> fileExtensions;
    private final int currentDepth;
    private int maxDepth;
    private boolean followRedirects;
    private final JList<String> dirsAndFilesListGui;
    private final ListModel<String> dirListGui;
    private WrappedJTree tree;
    private JProgressBar progressBar;
    private DefaultMutableTreeNode root;
    private DefaultMutableTreeNode redirectTreeRoot;

    private boolean enabledTimeoutBetweenRequests = false; // enable when rate limiter detected
    private int timeoutBetweenRequests = 100; // time is in milliseconds

    private AtomicBoolean isDiggingSave;
    private List<ThreadStateInfo> saveThreadList;

    private DiggerWorker(DiggerWorkerBuilder builder) {
        this.executorService = (PausableThreadPoolExecutor) builder.executorService;
        this.httpAsyncClient = builder.httpAsyncClient;
        this.url = builder.url;
        this.fileExtensions = builder.fileExtensions;
        this.currentDepth = builder.currentDepth;
        this.maxDepth = builder.maxDepth;
        this.followRedirects = builder.followRedirects;
        this.dirList = builder.dirList;
        this.watchedResponseCodes = builder.watchedResponseCodes;
        this.dirsAndFilesListGui = builder.dirsAndFilesListGui;
        this.tree = builder.tree;
        this.progressBar = builder.progressBar;
        this.logging = builder.logging;

        this.dirListGui = this.dirsAndFilesListGui.getModel();
        this.root = (DefaultMutableTreeNode) tree.getTree().getModel().getRoot();
        this.redirectTreeRoot = (DefaultMutableTreeNode) tree.getRedirectTree().getModel().getRoot();

        this.scheme = UrlUtils.getScheme(url);
        this.hostname = UrlUtils.getHostname(url);
        this.port = UrlUtils.getPort(url);

        this.isDiggingSave = builder.isDiggingSave;
        this.saveThreadList = builder.saveThreadList;
    }

    @Override
    protected String doInBackground() throws Exception {

        digAsync();
        return "Successfully finished";
    }

    public void digAsync() throws Exception {

        org.apache.http.client.config.RequestConfig requestConfig = org.apache.http.client.config.RequestConfig.custom()
                .setRedirectsEnabled(followRedirects)
                .setCircularRedirectsAllowed(true)
                .setMaxRedirects(50)
                .setRelativeRedirectsAllowed(true)
                .build();
        try {
            httpAsyncClient.start();
            for (int i = 0; i < dirList.size(); i++) {
                if (isDiggingSave.get()) {
                    ThreadStateInfo tsi = new ThreadStateInfo();
                    tsi.setI(i);
                    tsi.setUrl(url);
                    saveThreadList.add(tsi);
                }

                String requestUri = dirList.get(i);
                String potentialHit = scheme + "://" + hostname + "/" + requestUri;
                org.apache.http.client.methods.HttpGet request = new org.apache.http.client.methods.HttpGet(potentialHit);
                request.setConfig(requestConfig);

                if (enabledTimeoutBetweenRequests) {
                    try {
                        Thread.sleep(timeoutBetweenRequests);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                Future<org.apache.http.HttpResponse> future = httpAsyncClient.execute(request, new org.apache.http.concurrent.FutureCallback<>() {
                    @Override
                    public void completed(org.apache.http.HttpResponse response) {
                        int responseCode = response.getStatusLine().getStatusCode();

                        // possible rate limiter in place?
                        if (responseCode == 429 || responseCode == 503) {
                            log.warn("****** [RATE LIMITER DETECTED] ******");
                            try {
                                if (enabledTimeoutBetweenRequests)
                                    timeoutBetweenRequests += 300;
                                else
                                    enabledTimeoutBetweenRequests = true;

                                executorService.pause();

                                try {
                                    if (response.getFirstHeader("Retry-After").getValue() != null) {
                                        log.warn("Retry-After header value = {}", response.getFirstHeader("Retry-After"));
                                        timeoutBetweenRequests = Integer.parseInt(response.getFirstHeader("Retry-After").getValue());
                                    }
                                } catch (Exception e) {
                                    // possibly there was a date inside header instead of num of milliseconds
                                    log.error("[Retry-After] Unexpected exception: {}", e.getMessage());
                                }

                                log.warn("Going to sleep for: {}", timeoutBetweenRequests);
                                Thread.sleep(timeoutBetweenRequests);
                                executorService.resume();
                            } catch (InterruptedException e) {
                                log.warn("Interrupted rate limiter sleep!!!\n\t\t{}", e.getMessage());
                            }
                        } else if (watchedResponseCodes.contains(responseCode) && currentDepth < maxDepth) {
                            log.debug("{} =========> {}", potentialHit, responseCode);

                            DefaultMutableTreeNode parent = JTreeUtils.findParentNode(potentialHit, root);
                            DiggerNode node = new DiggerNode(parent, potentialHit, UrlUtils.getResponseStatus(responseCode));

                            // if url is not present in redirected tree, add it to regular
                            //              potentialHit (url) is present in redirect tree only if there was redirection
                            if (JTreeUtils.notContained(potentialHit, redirectTreeRoot)) {

                                // if "url" or "url + /" already present in regular tree, update response status if needed
                                if (JTreeUtils.contains(potentialHit, root) || JTreeUtils.contains(potentialHit + "/", root)) {

                                    DefaultMutableTreeNode treeNode = JTreeUtils.getNode(potentialHit, root);
                                    if (treeNode == null)
                                        treeNode = JTreeUtils.getNode(potentialHit + "/", root);

                                    DiggerNode updatedDiggerNode = (DiggerNode) treeNode.getUserObject();

                                    if (updatedDiggerNode.getResponseStatus() != UrlUtils.HttpResponseCodeStatus.SUCCESS && node.getResponseStatus() == UrlUtils.HttpResponseCodeStatus.SUCCESS) {
                                        log.debug("Updating {} http response status from {} to {}", potentialHit, updatedDiggerNode.getResponseStatus(), node.getResponseStatus());

                                        updatedDiggerNode.setResponseStatus(UrlUtils.HttpResponseCodeStatus.SUCCESS);
                                        treeNode.setUserObject(updatedDiggerNode);
                                    }
                                } else {
                                    log.debug("Adding {} to regular tree (looks like there wasn't any redirect)", potentialHit);
                                    JTreeUtils.addNode(node, tree.getTree());
                                }
                            }

                            publish(true);

                            if (followRedirects) {
                                DiggerWorker diggerWorker = new DiggerWorkerBuilder(potentialHit, currentDepth + 1)
                                        .fileExtensions(fileExtensions)
                                        .threadPool(executorService)
                                        .httpClient(httpAsyncClient)
                                        .dirList(dirList)
                                        .responseCodes(watchedResponseCodes)
                                        .directoryList(dirsAndFilesListGui)
                                        .maxDepth(maxDepth)
                                        .followRedirects(followRedirects)
                                        .tree(tree)
                                        .progressBar(progressBar)
                                        .logger(logging)
                                        .save(isDiggingSave, saveThreadList)
                                        .build();
                                executorService.submit(diggerWorker);
                            }
                        }
                    }

                    @Override
                    public void failed(Exception ex) {
                        log.warn("Exception from failed() http client: {}", ex.getMessage());
                        log.warn("****** [POSSIBLE RATE LIMITER DETECTED] ******");
                        try {
                            executorService.pause();
                            Thread.sleep(10000);
                            executorService.resume();
                        } catch (InterruptedException e) {
                            log.warn("failed() Interrupted rate limiter sleep!!!\n\t\t{}", e.getMessage());
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void cancelled() {
                        log.warn("Http request cancelled for some reason {}", potentialHit);
                    }
                });
                future.get();

                publish(false);

                if (fileExtensions != null) {
                    for (String fileExtension : fileExtensions) {
                        String potentialWithExtension = potentialHit + "." + fileExtension;
                        org.apache.http.client.methods.HttpGet extensionRequest = new org.apache.http.client.methods.HttpGet(potentialWithExtension);
                        request.setConfig(requestConfig);
                        Future<org.apache.http.HttpResponse> extensionFuture = httpAsyncClient.execute(extensionRequest, new org.apache.http.concurrent.FutureCallback<>() {
                            @Override
                            public void completed(HttpResponse response) {
                                int responseCode = response.getStatusLine().getStatusCode();

                                // possible rate limiter in place?
                                if (responseCode == 429 || responseCode == 503) {
                                    log.warn("****** [RATE LIMITER DETECTED] ******");
                                    try {
                                        if (enabledTimeoutBetweenRequests)
                                            timeoutBetweenRequests += 300;
                                        else
                                            enabledTimeoutBetweenRequests = true;

                                        executorService.pause();

                                        try {
                                            if (response.getFirstHeader("Retry-After").getValue() != null || response.getFirstHeader("Retry-after").getValue() != null) {
                                                timeoutBetweenRequests = Integer.parseInt(response.getFirstHeader("Retry-After").getValue());
                                            }
                                        } catch (Exception e) {
                                            log.error("Unexpected exception: {}", e.getMessage());
                                        }

                                        log.warn("Going to sleep for: {}", timeoutBetweenRequests);
                                        Thread.sleep(timeoutBetweenRequests);
                                        executorService.resume();
                                    } catch (InterruptedException e) {
                                        log.warn("Interrupted rate limiter sleep!!!\n{}", e.getMessage());
                                    }
                                } else if (watchedResponseCodes.contains(responseCode) && currentDepth < maxDepth) {
                                    log.debug("{} =========> {}", potentialWithExtension, responseCode);

                                    DefaultMutableTreeNode parent = JTreeUtils.findParentNode(potentialWithExtension, root);
                                    DiggerNode node = new DiggerNode(parent, potentialWithExtension, UrlUtils.getResponseStatus(responseCode));

                                    // if url is not present in redirected tree, add it to regular
                                    //              potentialHit (url) is present in redirect tree only if there was redirection
                                    if (JTreeUtils.notContained(potentialWithExtension, redirectTreeRoot)) {

                                        // if "url" or "url + /" already present in regular tree, update response status if needed
                                        if (JTreeUtils.contains(potentialWithExtension, root) || JTreeUtils.contains(potentialWithExtension + "/", root)) {

                                            DefaultMutableTreeNode treeNode = JTreeUtils.getNode(potentialWithExtension, root);
                                            if (treeNode == null)
                                                treeNode = JTreeUtils.getNode(potentialWithExtension + "/", root);

                                            DiggerNode updatedDiggerNode = (DiggerNode) treeNode.getUserObject();

                                            if (updatedDiggerNode.getResponseStatus() != UrlUtils.HttpResponseCodeStatus.SUCCESS && node.getResponseStatus() == UrlUtils.HttpResponseCodeStatus.SUCCESS) {
                                                log.debug("Updating {} http response status from {} to {}", potentialWithExtension, updatedDiggerNode.getResponseStatus(), node.getResponseStatus());

                                                updatedDiggerNode.setResponseStatus(UrlUtils.HttpResponseCodeStatus.SUCCESS);
                                                treeNode.setUserObject(updatedDiggerNode);
                                            }
                                        } else {
                                            log.debug("Adding {} to regular tree (looks like there wasn't any redirect)", potentialWithExtension);
                                            JTreeUtils.addNode(node, tree.getTree());
                                        }
                                    }
                                }
                            }

                            @Override
                            public void failed(Exception ex) {
                                log.warn("Exception from failed() http client: {}", ex.getMessage());
                                log.warn("****** [POSSIBLE RATE LIMITER DETECTED] ******");
                                try {
                                    executorService.pause();
                                    Thread.sleep(10000);
                                    executorService.resume();
                                } catch (InterruptedException e) {
                                    log.warn("failed() Interrupted rate limiter sleep!!!\n\t\t{}", e.getMessage());
                                    throw new RuntimeException(e);
                                }
                            }

                            @Override
                            public void cancelled() {
                                log.warn("Http request cancelled for some reason {}", potentialHit);
                            }
                        });
                        extensionFuture.get();
                        publish(false);
                    }
                }

                // setting refresh rate for better UX
                if (i % 20 == 0 || i == dirList.size() - 1) {
                    ((DefaultTreeModel) tree.getTree().getModel()).reload();
                    ((DefaultTreeModel) tree.getRedirectTree().getModel()).reload();
                    JTreeUtils.expandAllNodesOnReload(tree.getTree());
                    JTreeUtils.expandAllNodesOnReload(tree.getRedirectTree());
                }
            }
        } catch (Exception e) {
            log.warn("Should this even happen???");
            try {
                executorService.pause();
                Thread.sleep(10000);
                executorService.resume();
            } catch (InterruptedException ex) {
                log.warn("Interrupted rate limiter sleep!!!");
                throw new RuntimeException(ex);
            }
            // let this finally be here for now
            finally {
                if (fileExtensions != null && !fileExtensions.isEmpty())
                    progressBar.setMaximum(progressBar.getMaximum() - (dirListGui.getSize() * fileExtensions.size()));
                else
                    progressBar.setMaximum(progressBar.getMaximum() - dirListGui.getSize());
            }
        } finally {
            if (progressBar.getValue() >= progressBar.getMaximum())
                httpAsyncClient.close();
        }
    }

    // true when another directory has been found, so new max has to be set
    // otherwise false
    @Override
    protected void process(List<Boolean> chunks) {
        for (Boolean chunk : chunks) {
            if (chunk) {
                if (CollectionUtils.isNotEmpty(fileExtensions))
                    progressBar.setMaximum(progressBar.getMaximum() + (dirListGui.getSize() * fileExtensions.size()));
                else
                    progressBar.setMaximum(progressBar.getMaximum() + dirListGui.getSize());

                log.debug("new maximum {}", progressBar.getMaximum());
            } else {
                log.debug("current value for progressBar {}", progressBar.getValue() + 1);
                progressBar.setValue(progressBar.getValue() + 1);
            }
        }
    }

    @Override
    protected void done() {
        log.debug("[FINISH] One worker thread has finished!!!");
        if (progressBar.getValue() >= progressBar.getMaximum()) {
            progressBar.setVisible(false);
            dirsAndFilesListGui.setModel(new DefaultListModel<>());
        }
    }

    public static class DiggerWorkerBuilder {

        private ExecutorService executorService;

        private org.apache.http.impl.nio.client.CloseableHttpAsyncClient httpAsyncClient;

        private Logging logging;

        private List<String> dirList;
        private List<Integer> watchedResponseCodes;
        private final String url;
        private List<String> fileExtensions;
        private final int currentDepth;
        private int maxDepth;
        private boolean followRedirects;
        private JList<String> dirsAndFilesListGui;
        private WrappedJTree tree;
        private JProgressBar progressBar;
        private AtomicBoolean isDiggingSave;
        private List<ThreadStateInfo> saveThreadList;

        public DiggerWorkerBuilder(String url, int currentDepth) {
            this.url = url;
            this.currentDepth = currentDepth;
        }

        public DiggerWorkerBuilder fileExtensions(List<String> fileExtensions) {
            this.fileExtensions = fileExtensions;
            return this;
        }

        public DiggerWorkerBuilder httpClient(org.apache.http.impl.nio.client.CloseableHttpAsyncClient httpAsyncClient) {
            this.httpAsyncClient = httpAsyncClient;
            return this;
        }

        public DiggerWorkerBuilder threadPool(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public DiggerWorkerBuilder dirList(List<String> dirList) {
            this.dirList = dirList;
            return this;
        }

        public DiggerWorkerBuilder responseCodes(List<Integer> responseCodes) {
            this.watchedResponseCodes = responseCodes;
            return this;
        }

        public DiggerWorkerBuilder maxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
            return this;
        }

        public DiggerWorkerBuilder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        public DiggerWorkerBuilder directoryList(JList<String> dirsAndFilesList) {
            this.dirsAndFilesListGui = dirsAndFilesList;
            return this;
        }

        public DiggerWorkerBuilder tree(WrappedJTree tree) {
            this.tree = tree;
            return this;
        }

        public DiggerWorkerBuilder progressBar(JProgressBar progressBar) {
            this.progressBar = progressBar;
            return this;
        }

        public DiggerWorkerBuilder save(AtomicBoolean isDiggingSave, List<ThreadStateInfo> saveThreadList) {
            this.isDiggingSave = isDiggingSave;
            this.saveThreadList = saveThreadList;
            return this;
        }

        public DiggerWorkerBuilder logger(Logging logging) {
            this.logging = logging;
            return this;
        }

        public DiggerWorker build() {
            return new DiggerWorker(this);
        }
    }
}
