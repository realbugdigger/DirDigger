package core;

import burp.api.montoya.logging.Logging;
import org.apache.commons.collections4.ListUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.JTreeUtils;
import utils.UrlUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.List;
import java.util.concurrent.*;

public class DiggerWorker extends SwingWorker<String, DiggerNode> {

    private static final Logger log = LoggerFactory.getLogger(DiggerWorker.class);

    private Logging logging;

//    private ExecutorService executorService;
    private PausableThreadPoolExecutor executorService;

    private org.apache.http.impl.nio.client.CloseableHttpAsyncClient httpAsyncClient;

    private List<String> dirList;
    private List<Integer> watchedResponseCodes;
    private List<List<String>> partitionedDirList;

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
    private int timeoutBetweenRequests = 3600; // time is in milliseconds

    private DiggerWorker(DiggerWorkerBuilder builder) {
//        this.executorService = builder.executorService;
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
        this.partitionedDirList = ListUtils.partition(dirList, 25);

        this.scheme = UrlUtils.getScheme(url);
        this.hostname = UrlUtils.getHostname(url);
        this.port = UrlUtils.getPort(url);
    }

    @Override
    protected String doInBackground() throws Exception {

//        if (followRedirects)
            digAsync();
//        else
//            digPipelined();

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
            for (List<String> partition : partitionedDirList) {
                for (int i = 0; i < partition.size(); i++) {
                    String requestUri = partition.get(i);
                    String potentialHit = scheme + "://" + hostname + "/" + requestUri;
                    org.apache.http.client.methods.HttpGet request = new org.apache.http.client.methods.HttpGet(potentialHit);
                    request.setConfig(requestConfig);

//                    if (enabledTimeoutBetweenRequests) {
//                        try {
//                            Thread.sleep(timeoutBetweenRequests);
//                        } catch (InterruptedException e) {
//                            throw new RuntimeException(e);
//                        }
//                    }
                    Future<org.apache.http.HttpResponse> future = httpAsyncClient.execute(request, new org.apache.http.concurrent.FutureCallback<>() {
                        @Override
                        public void completed(org.apache.http.HttpResponse response) {
                                int responseCode = response.getStatusLine().getStatusCode();
                                // possible rate limiter in place?
                                if (responseCode == 429 || responseCode == 503) {
                                    log.warn("****** [RATE LIMITER DETECTED] ******");
                                    try {
                                        if (enabledTimeoutBetweenRequests)
                                            timeoutBetweenRequests += 3000;
                                        else
                                            enabledTimeoutBetweenRequests = true;

                                        executorService.pause();

                                        log.warn("Retry-After header value = {}", response.getFirstHeader("Retry-After"));
                                        try {
                                            if (response.getFirstHeader("Retry-After").getValue() != null) {
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
                                }
                                else if (watchedResponseCodes.contains(responseCode) && currentDepth < maxDepth) {
                                    log.debug("{} =========> {}",  potentialHit, responseCode);

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

                                    publish(node);

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
                                            .build();
                                    executorService.submit(diggerWorker);
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
                                log.warn("Interrupted rate limiter sleep!!!");
                                throw new RuntimeException(e);
//                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void cancelled() {

                        }
                    });
                    future.get();

                    // setting refresh rate for better UX
                    if (i % 20 == 0 || i == partition.size() - 1) {
                        ((DefaultTreeModel) tree.getTree().getModel()).reload();
                        ((DefaultTreeModel) tree.getRedirectTree().getModel()).reload();
                        JTreeUtils.expandAllNodesOnReload(tree.getTree());
                        JTreeUtils.expandAllNodesOnReload(tree.getRedirectTree());
                    }
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
//                                e.printStackTrace();
            }
//            throw new RuntimeException(e);
        } finally {
            httpAsyncClient.close();
        }
    }

    public void digPipelined() throws Exception {

    }

//    public void dig(String url, int depth) throws Exception {
//
////        logging.logToOutput("entering dig()");
//        System.out.println("entering dig()");
//
//        for (List<String> partition : partitionedDirList) {
//
//            setProgress(progressBar.getValue() + 10);
//
//            final MinimalHttpAsyncClient client = HttpAsyncClients.createMinimal(
//                    H2Config.DEFAULT,
//                    Http1Config.DEFAULT,
//                    IOReactorConfig.DEFAULT,
//                    PoolingAsyncClientConnectionManagerBuilder.create()
//                            .setDefaultTlsConfig(TlsConfig.custom()
//                                    .setVersionPolicy(HttpVersionPolicy.FORCE_HTTP_1)
//                                    .build())
//                            .build());
//
//            client.start();
//
////            final HttpHost target = new HttpHost(scheme, hostname, port);
//            final HttpHost target = new HttpHost(scheme, hostname);
//            final Future<AsyncClientEndpoint> leaseFuture = client.lease(target, null);
//            final AsyncClientEndpoint endpoint = leaseFuture.get(30, TimeUnit.SECONDS);
//            try {
//                final CountDownLatch latch = new CountDownLatch(partition.size());
//                for (final String requestUri: partition) {
//                    final SimpleHttpRequest request = SimpleRequestBuilder.get()
//                            .setHttpHost(target)
//                            .setPath(requestUri)
//                            .build();
//
////                    String potentialHit = scheme + "://" + hostname + ":" + port + "/" + request.getPath();
//                    String potentialHit = scheme + "://" + hostname + "/" + request.getPath();
//
//                    endpoint.execute(
//                            SimpleRequestProducer.create(request),
//                            SimpleResponseConsumer.create(),
//                            new FutureCallback<SimpleHttpResponse>() {
//
//                                @Override
//                                public void completed(final SimpleHttpResponse response) {
//                                    latch.countDown();
////                                    logging.logToOutput(request + " -------------- > " + new StatusLine(response));
//
//                                    System.out.println("Response code for " + potentialHit + " = " + response.getCode());
//
//                                    if (response.getCode() != 404 && depth < maxDepth) {
////                                        logging.logToOutput(request.getPath());
//                                        DefaultMutableTreeNode parent = findParentNode(potentialHit, root);
//                                        core.DiggerNode node = new core.DiggerNode(parent, potentialHit, UrlUtils.getResponseStatus(response.getCode()));
//                                        publish(node);
//
//                                        core.DiggerWorker diggerWorker = new DiggerWorkerBuilder(potentialHit  + "/", currentDepth + 1)
//                                                .fileExtensions(fileExtensions)
//                                                .threadPool(executorService)
//                                                .dirList(dirList)
//                                                .directoryList(dirsAndFilesListGui)
//                                                .maxDepth(maxDepth)
//                                                .tree(tree)
//                                                .progressBar(progressBar)
//                                                .logger(logging)
//                                                .build();
//                                        executorService.submit(diggerWorker);
//                                    }
//                                }
//
//                                @Override
//                                public void failed(final Exception ex) {
//                                    latch.countDown();
//                                    System.out.println(request + " -------------- > " + ex);
//                                    System.out.println(ex.getCause().getMessage());
//                                }
//
//                                @Override
//                                public void cancelled() {
//                                    latch.countDown();
//                                    System.out.println(request + " cancelled");
//                                }
//
//                            });
//                }
//                latch.await();
//            } finally {
//                endpoint.releaseAndReuse();
//            }
//
//            client.close(CloseMode.GRACEFUL);
//        }
//
////        for (int i = 0; i < dirList.size(); i++) {
////
////            setProgress((i+1)*10);
////
////            String potential = url + dirListGui.getElementAt(i);
////            if (potential.equals(url))
////                continue;
////            int rCode = makeRequest(potential);
////            if (rCode != 404 && depth < maxDepth) {
////
////                DefaultMutableTreeNode parent = findParentNode(potential, root);
////                core.DiggerNode node = new core.DiggerNode(parent, potential, UrlUtils.getResponseStatus(rCode));
////                publish(node);
////
////                core.DiggerWorker diggerWorker = new DiggerWorkerBuilder(potential  + "/", currentDepth + 1)
////                        .fileExtensions(fileExtensions)
////                        .threadPool(executorService)
////                        .dirList(dirList)
////                        .directoryList(dirsAndFilesListGui)
////                        .maxDepth(maxDepth)
////                        .tree(tree)
////                        .progressBar(progressBar)
////                        .logger(logging)
////                        .build();
////                executorService.submit(diggerWorker);
////            }
////
////            if (fileExtensions != null) {
////                for (String fileExtension : fileExtensions) {
////                    String potentialWithExtension = potential + "." + fileExtension;
////                    int responseCode = makeRequest(potentialWithExtension);
////                    if (responseCode != 404) {
////                        DefaultMutableTreeNode parent = findParentNode(potentialWithExtension, root);
////                        core.DiggerNode node = new core.DiggerNode(parent, potentialWithExtension, UrlUtils.getResponseStatus(responseCode));
////                        publish(node);
////                    }
////                }
////            }
////        }
//    }

    @Override
    protected void process(List<DiggerNode> nodes) {
        for (DiggerNode node : nodes) {
//            JTreeUtils.addNode(node, tree);
            progressBar.setMaximum(progressBar.getMaximum() + 300);
        }
    }

    @Override
    protected void done() {
        progressBar.setVisible(false);
        dirsAndFilesListGui.setModel(new DefaultListModel<>());
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

        public DiggerWorkerBuilder logger(Logging logging) {
            this.logging = logging;
            return this;
        }

        public DiggerWorker build() {
            return new DiggerWorker(this);
        }
    }
}
