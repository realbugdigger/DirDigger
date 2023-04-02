package core;

import burp.api.montoya.logging.Logging;
import org.apache.commons.collections4.ListUtils;
import org.apache.hc.client5.http.async.methods.*;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.config.Http1Config;
import org.apache.hc.core5.http.message.StatusLine;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.reactor.IOReactorConfig;
import org.apache.hc.core5.util.Timeout;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import utils.DebugRedirectStrategy;
import utils.JTreeUtils;
import utils.UrlUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

public class DiggerWorker extends SwingWorker<String, DiggerNode> {

    private Logging logging;

    private ExecutorService executorService;

    private org.apache.http.impl.nio.client.CloseableHttpAsyncClient httpAsyncClient;

    private List<String> dirList;
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
    private JTree tree;
    private JProgressBar progressBar;
    private DefaultMutableTreeNode root;

    private DiggerWorker(DiggerWorkerBuilder builder) {
        this.executorService = builder.executorService;
        this.httpAsyncClient = builder.httpAsyncClient;
        this.url = builder.url;
        this.fileExtensions = builder.fileExtensions;
        this.currentDepth = builder.currentDepth;
        this.maxDepth = builder.maxDepth;
        this.followRedirects = builder.followRedirects;
        this.dirList = builder.dirList;
        this.dirsAndFilesListGui = builder.dirsAndFilesListGui;
        this.tree = builder.tree;
        this.progressBar = builder.progressBar;
        this.logging = builder.logging;

        this.dirListGui = this.dirsAndFilesListGui.getModel();
        this.root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        this.partitionedDirList = ListUtils.partition(dirList, 25);

        this.scheme = UrlUtils.getScheme(url);
        this.hostname = UrlUtils.getHostname(url);
        this.port = UrlUtils.getPort(url);
    }

    @Override
    protected String doInBackground() throws Exception {

//        dig(url, currentDepth);
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
            for (List<String> partition : partitionedDirList) {
                for (String requestUri : partition) {
                    String potentialHit = scheme + "://" + hostname + "/" + requestUri;
                    org.apache.http.client.methods.HttpGet request = new org.apache.http.client.methods.HttpGet(potentialHit);
                    request.setConfig(requestConfig);
                    Future<org.apache.http.HttpResponse> future = httpAsyncClient.execute(request, new org.apache.http.concurrent.FutureCallback<>() {
                        @Override
                        public void completed(org.apache.http.HttpResponse response) {
                            System.out.println(potentialHit + " =========> " + response.getStatusLine().getStatusCode() + "\n"
                                                    + "\t\t");
                            if (response.getStatusLine().getStatusCode() != 404 && currentDepth < maxDepth) {

                                DefaultMutableTreeNode parent = JTreeUtils.findParentNode(potentialHit, root);
                                DiggerNode node = new DiggerNode(parent, potentialHit, UrlUtils.getResponseStatus(response.getStatusLine().getStatusCode()));
                                publish(node);

                                DiggerWorker diggerWorker = new DiggerWorkerBuilder(potentialHit, currentDepth + 1)
                                        .fileExtensions(fileExtensions)
                                        .threadPool(executorService)
                                        .httpClient(httpAsyncClient)
                                        .dirList(dirList)
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

                        }

                        @Override
                        public void cancelled() {

                        }
                    });
                    future.get();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            httpAsyncClient.close();
        }
    }

    public void dig(String url, int depth) throws Exception {

        for (List<String> partition : partitionedDirList) {

            final IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                    .setSoTimeout(Timeout.ofSeconds(5))
                    .build();

            final CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                    .setH2Config(null)
                    .setHttp1Config(Http1Config.DEFAULT)
                    .setIOReactorConfig(ioReactorConfig)
                    .build();

            client.start();

            final HttpHost target = new HttpHost(scheme, hostname);

            for (final String requestUri: partition) {
                final SimpleHttpRequest request = SimpleRequestBuilder.get()
                        .setHttpHost(target)
                        .setPath(requestUri)
                        .build();

                System.out.println("Executing request " + request);
                final Future<SimpleHttpResponse> future = client.execute(
                        SimpleRequestProducer.create(request),
                        SimpleResponseConsumer.create(),
                        new FutureCallback<SimpleHttpResponse>() {

                            @Override
                            public void completed(final SimpleHttpResponse response) {
                                System.out.println(request + " -> " + new StatusLine(response));
//                                System.out.println(response.getBody());
                            }

                            @Override
                            public void failed(final Exception ex) {
                                System.out.println(request + " -> " + ex);
                            }

                            @Override
                            public void cancelled() {
                                System.out.println(request + " cancelled");
                            }

                        });
                future.get();
            }

            System.out.println("Shutting down");
            client.close(CloseMode.GRACEFUL);
        }
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
            JTreeUtils.addNode(node, tree);
            progressBar.setMaximum(progressBar.getMaximum() + 300);
        }
    }

    @Override
    protected void done() {
        progressBar.setVisible(false);
        dirsAndFilesListGui.setModel(new DefaultListModel<>());
    }

//    private void addNode(DiggerNode node) {
//        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
//        DefaultMutableTreeNode parent = node.getParent();
//        parent.add(new DefaultMutableTreeNode(node));
//        model.reload(parent);
//    }
//
//    private DefaultMutableTreeNode findParentNode(String newUrl, DefaultMutableTreeNode root) {
//        DefaultMutableTreeNode parent = root;
//
//        for (int i = 0; i < root.getChildCount(); i++) {
//            DefaultMutableTreeNode childNode = (DefaultMutableTreeNode) root.getChildAt(i);
//            DiggerNode childDiggerNode = (DiggerNode) childNode.getUserObject();
//            String childNodeUrl = childDiggerNode.getUrl();
//
//            if(shouldLookIntoChild(newUrl, childNodeUrl)) {
//                if (shouldAddInThisChild(newUrl, childNodeUrl)) {
//                    parent = childNode;
//                    break;
//                } else {
//                    parent = findParentNode(newUrl, childNode);
//                }
//            }
//        }
//
//        return parent;
//    }
//
//    private boolean shouldLookIntoChild(String newUrl, String childNodeUrl) {
//        return newUrl.contains(childNodeUrl);
//    }
//
//    private boolean shouldAddInThisChild(String newUrl, String childNodeUrl) {
//        List<String> newUrlDirs = UrlUtils.getDirectoriesOfUrl(newUrl);
//        List<String> childNodeUrlDirs = UrlUtils.getDirectoriesOfUrl(childNodeUrl);
//
//        if (newUrlDirs.size() != childNodeUrlDirs.size() + 1)
//            return false;
//
//        newUrlDirs.remove(newUrlDirs.size() - 1);
//        for (int i = 0; i < newUrlDirs.size(); i++) {
//            if (!newUrlDirs.get(i).equals(childNodeUrlDirs.get(i)))
//                return false;
//        }
//        return true;
//    }

    public static class DiggerWorkerBuilder {

        private ExecutorService executorService;

        private org.apache.http.impl.nio.client.CloseableHttpAsyncClient httpAsyncClient;

        private Logging logging;

        private List<String> dirList;
        private final String url;
        private List<String> fileExtensions;
        private final int currentDepth;
        private int maxDepth;
        private boolean followRedirects;
        private JList<String> dirsAndFilesListGui;
        private JTree tree;
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

        public DiggerWorkerBuilder tree(JTree tree) {
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
