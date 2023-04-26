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
import utils.JTreeUtils;
import utils.UrlUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
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
    private WrappedJTree tree;
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
        this.root = (DefaultMutableTreeNode) tree.getTree().getModel().getRoot();
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
                                tree.addNode(node);
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
