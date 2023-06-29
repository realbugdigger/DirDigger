package core;

import burp.api.montoya.logging.Logging;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.CustomRedirectStrategy;
import utils.Globals;
import utils.JTreeUtils;
import utils.UrlUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.LineBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class DirDigger {

    private static final Logger log = LoggerFactory.getLogger(DiggerWorker.class);

    private final Logging logging;

    private JPanel panel;

    private JLabel urlLabel;
    private JTextField urlTextField;
    private JLabel fileExtensionsLabel;
    private JTextField fileExtensionsTextField;

    private JLabel loadFileOfDirsLabel;
    private JButton browseFiles;
    private JLabel addDirListEntry;
    private JTextField entryTextField;
    private JButton addEntryButton;
    private JButton clearDirList;
    private JList<String> dirsAndFilesList;
    private JScrollPane dirScrollPane;

    private JLabel errorMessage;
    private JButton startDigging;
    private boolean isDigging = false;
    private boolean firstInitialization = true;
    private JButton cancelDigging;
    private JButton saveDigging;
    private AtomicBoolean isDiggingSave = new AtomicBoolean(false);
    private AtomicBoolean isKillSave = new AtomicBoolean(false);
    private final List<ThreadStateInfo> saveThreadList = Collections.synchronizedList(new ArrayList<>());
    private JButton loadDigging;
    private boolean loadedDigging = false;
    private JProgressBar progressBar;

    private JLabel sliderDepthLabel;
    private JSlider depthSlider;
    private JLabel threadNumSliderLabel;
    private JSlider threadNumSlider;
    private JLabel proxyAndPortLabel;
    private JTextField proxyAndPort;
    private JLabel responseCodesLabel;
    private JTextField responseCodes;
    private JCheckBox followRedirect;
    private JCheckBox seeRedirectTree;

    private JLabel legendHeader;
    private JLabel legendCircleInfo;
    private JLabel legendCircleSuccess;
    private JLabel legendCircleRedirect;
    private JLabel legendCircleClientError;
    private JLabel legendCircleServerError;
    private JLabel legendInfo;
    private JLabel legendSuccess;
    private JLabel legendRedirect;
    private JLabel legendClientError;
    private JLabel legendServerError;

    private JTree tree;
    private JScrollPane treeScrollPane;
    private JTree redirectTree;
    private JScrollPane redirectTreeScrollPane;
    private WrappedJTree wrappedTree = new WrappedJTree();

    private JSeparator verticalSeparator;
    private JSeparator firstHorizontalSeparator;
    private JSeparator secondHorizontalSeparator;
    private JSeparator thirdHorizontalSeparator;
    private JSeparator fourthHorizontalSeparator;

    private PausableThreadPoolExecutor executorService;
    private List<Runnable> tasks = new ArrayList<>();

    private org.apache.http.impl.nio.client.CloseableHttpAsyncClient httpAsyncClient;

    private DiggerWorker diggerWorker;

    // HashSet or ArrayList ????
    private final List<String> entryList = new ArrayList<>();

    public DirDigger(Logging logging) {

        this.logging = logging;

        createUIComponents();

        BrowseFileListener browseFileListener = new BrowseFileListener(dirsAndFilesList, entryList);
        browseFiles.addActionListener(browseFileListener);

        seeRedirectTree.addActionListener(e -> {
            if (seeRedirectTree.isSelected()) {
                tree.setVisible(false);
                redirectTreeScrollPane.setVisible(true);
            } else {
                redirectTreeScrollPane.setVisible(false);
                tree.setVisible(true);
            }
        });

        clearDirList.addActionListener(e -> dirsAndFilesList.setModel(new DefaultListModel<>()));

        addEntryButton.addActionListener(e -> {
            if (entryTextField.getText() != null || entryTextField.getText().equals("")) {
                DefaultListModel<String> listModel = (DefaultListModel<String>) dirsAndFilesList.getModel();
                listModel.add(0, entryTextField.getText());
                entryTextField.setText("");
            }
        });
    }

    private void initThreadPool() {
        ThreadFactory threadFactory =
                new ThreadFactory() {
                    final ThreadFactory defaultFactory =
                            Executors.defaultThreadFactory();
                    public Thread newThread(final Runnable r) {
                        Thread thread =
                                defaultFactory.newThread(r);
                        thread.setName("SwingWorker-"
                                + thread.getName());
                        thread.setDaemon(true);
                        return thread;
                    }
                };

        executorService = new PausableThreadPoolExecutor(threadNumSlider.getValue(), threadNumSlider.getValue(),
                10L, TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(), threadFactory,
                20);
    }

    private void initThreadPoolV2(int size) {
        ThreadFactory threadFactory =
                new ThreadFactory() {
                    final ThreadFactory defaultFactory =
                            Executors.defaultThreadFactory();
                    public Thread newThread(final Runnable r) {
                        Thread thread =
                                defaultFactory.newThread(r);
                        thread.setName("SwingWorker-"
                                + thread.getName());
                        thread.setDaemon(true);
                        return thread;
                    }
                };

        executorService = new PausableThreadPoolExecutor(threadNumSlider.getValue(), threadNumSlider.getValue(),
                10L, TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(), threadFactory,
                20);
    }

    public JPanel getFrame(){
        return this.panel;
    }

    private void createUIComponents() {

        panel = new JPanel();
        panel.setName(Globals.APP_NAME);

        initTargetSection();

        initLoadedDirectoriesSection();

        initSettingsSection();

        initStartAndProgressSection();

        initLegendSection();

        initSeparators();

        initTree();

        loadTestDirFile();

        positionUIComponents();
    }

    private void initTargetSection() {
        urlLabel = new JLabel("Target Url");

        urlTextField = new JFormattedTextField();
        urlTextField.setToolTipText("eg. https://example.org");
        urlTextField.setMaximumSize(new Dimension(500, urlTextField.getPreferredSize().height));
        urlTextField.setMinimumSize(new Dimension(500, urlTextField.getPreferredSize().height));

        fileExtensionsLabel = new JLabel("File Extensions");

        fileExtensionsTextField = new JFormattedTextField();
        fileExtensionsTextField.setToolTipText("php html sh asp txt");
        fileExtensionsTextField.setMaximumSize(new Dimension(500, fileExtensionsTextField.getPreferredSize().height));
        fileExtensionsTextField.setMinimumSize(new Dimension(500, fileExtensionsTextField.getPreferredSize().height));
    }

    private void initLoadedDirectoriesSection() {
        loadFileOfDirsLabel = new JLabel("Load file of dirs and files");

        browseFiles = new JButton("Browse");

        addDirListEntry = new JLabel("Add entry to list");

        entryTextField = new JTextField();
        entryTextField.setMaximumSize(new Dimension(200, entryTextField.getPreferredSize().height));
        entryTextField.setMinimumSize(new Dimension(200, entryTextField.getPreferredSize().height));

        addEntryButton = new JButton("Add");

        clearDirList = new JButton("Clear list");

        DefaultListModel<String> listModel = new DefaultListModel<>();
        dirsAndFilesList = new JList<>(listModel);
        dirsAndFilesList.setMaximumSize(new Dimension(600, 500));
        dirsAndFilesList.setMinimumSize(new Dimension(600, 500));
        dirsAndFilesList.setBorder(new LineBorder(new Color(0,0,0), 1));

        dirScrollPane = new JScrollPane(dirsAndFilesList);
        dirScrollPane.setMaximumSize(new Dimension(600, 500));
        dirScrollPane.setMinimumSize(new Dimension(600, 500));
    }

    private void initSettingsSection() {
        sliderDepthLabel = new JLabel("Depth of directories");

        depthSlider = new JSlider(1, 7);

        depthSlider.setPaintTrack(true);
        depthSlider.setPaintTicks(true);
        depthSlider.setPaintLabels(true);

        depthSlider.setMajorTickSpacing(20);
        depthSlider.setMinorTickSpacing(5);

        depthSlider.setMinimumSize(new Dimension(400, depthSlider.getPreferredSize().height));
        depthSlider.setMaximumSize(new Dimension(400, depthSlider.getPreferredSize().height));

        Hashtable<Integer, JLabel> labels = new Hashtable<>();
        labels.put(1, new JLabel("1"));
        labels.put(2, new JLabel("2"));
        labels.put(3, new JLabel("3"));
        labels.put(4, new JLabel("4"));
        labels.put(5, new JLabel("5"));
        labels.put(6, new JLabel("6"));
        labels.put(7, new JLabel("7"));
        depthSlider.setLabelTable(labels);

        threadNumSliderLabel = new JLabel("Number of threads");

        threadNumSlider = new JSlider(1, 100);

        threadNumSlider.setPaintTrack(true);
        threadNumSlider.setPaintTicks(true);
        threadNumSlider.setPaintLabels(true);

        threadNumSlider.setMajorTickSpacing(20);
        threadNumSlider.setMinorTickSpacing(5);

        threadNumSlider.setMinimumSize(new Dimension(400, threadNumSlider.getPreferredSize().height));
        threadNumSlider.setMaximumSize(new Dimension(400, threadNumSlider.getPreferredSize().height));

        Hashtable<Integer, JLabel> labelsTHSlider = new Hashtable<>();
        for (int i = 0; i <= 100; i=i+5) {
            if (i % 10 == 0)
                labelsTHSlider.put(i, new JLabel(String.format("%d", i)));
            else
                labelsTHSlider.put(i, new JLabel(""));
        }
        threadNumSlider.setLabelTable(labelsTHSlider);

        proxyAndPortLabel = new JLabel("Proxy");

        proxyAndPort = new JFormattedTextField();
        proxyAndPort.setToolTipText("Enter proxy (if no keep empty)");
        proxyAndPort.setMaximumSize(new Dimension(500, proxyAndPort.getPreferredSize().height));
        proxyAndPort.setMinimumSize(new Dimension(500, proxyAndPort.getPreferredSize().height));

        responseCodesLabel = new JLabel("Response codes");

        responseCodes = new JFormattedTextField();
        responseCodes.setText("200, 204, 301, 302, 307, 403");
        responseCodes.setToolTipText("Enter response codes you wish to see");
        responseCodes.setMaximumSize(new Dimension(450, responseCodes.getPreferredSize().height));
        responseCodes.setMinimumSize(new Dimension(450, responseCodes.getPreferredSize().height));

        followRedirect = new JCheckBox("Follow redirects");
        seeRedirectTree = new JCheckBox("See redirect tree");
        seeRedirectTree.setVisible(false);
    }

    private void initStartAndProgressSection() {
        errorMessage = new JLabel();
        errorMessage.setVisible(false);

        startDigging = new JButton("Start Digging");
        startDigging.addActionListener(e -> {
            if (startDigging.getText().equals("Continue Digging")) {
                cancelDigging.setVisible(false);
                saveDigging.setVisible(false);

                log.debug("Continuing digging with num of tasks: {}", tasks.size());
                if (!loadedDigging || executorService == null)
                    initThreadPoolV2(tasks.size());
                for (Runnable task : tasks) {
                    executorService.submit(task);
                }
                executorService.resume();
                startDigging.setText("Stop Digging");
            }

            if (!isDigging && isValidToStart()) {
                isDigging = true;
                startDigging.setText("Stop Digging");

                if (firstInitialization)
                    firstInit();
            } else if (isDigging) { // stop digging
                tasks = executorService.shutdownNow();
                isDigging = false;
                isDiggingSave.set(true);
                log.debug("[ExecutorService STOP] Thread pool should stop threads from executing");
                // show cancel and save buttons
                startDigging.setText("Continue Digging");
                cancelDigging.setVisible(true);
                saveDigging.setVisible(true);
            } else if (!isValidToStart()) {
                errorMessage.setText("Url not provided or list hasn't been loaded");
                errorMessage.setForeground(Color.RED);
                errorMessage.setVisible(true);
            }
        });
        startDigging.setMaximumSize(new Dimension(600, startDigging.getPreferredSize().height));
        startDigging.setMinimumSize(new Dimension(600, startDigging.getPreferredSize().height));

        cancelDigging = new JButton("Cancel Digging");
        cancelDigging.addActionListener(e -> {
            executorService.shutdownNow();
            tasks = new ArrayList<>();
            progressBar.setValue(0);
            progressBar.setVisible(false);
            dirsAndFilesList.setModel(new DefaultListModel<>());

            reEnableComponentsWhenCancel();
            firstInitialization = true;
            startDigging.setText("Start Digging");
            cancelDigging.setVisible(false);
            saveDigging.setVisible(false);
            urlTextField.setText("");
            JTreeUtils.setDefaultRoot(tree);
            JTreeUtils.setDefaultRoot(redirectTree);
            tree.setVisible(false);
            redirectTree.setVisible(false);
        });
        cancelDigging.setVisible(false);

        saveDigging = new JButton("Save Digging");
        saveDigging.addActionListener(e -> {
            isKillSave.set(true);
            ApplicationContainer container = new ApplicationContainer();
            container.setDirList(entryList);

            List<String> fileExtensions = null;
            if (fileExtensionsTextField.getText() != null && !fileExtensionsTextField.getText().equals("")) {
                fileExtensions = List.of(fileExtensionsTextField.getText().split(
                        fileExtensionsTextField.getText().contains(",") ? "," : " "
                ));
            }
            container.setFileExList(fileExtensions);

            container.setProgressBarValue(progressBar.getValue());
            container.setFollowRedirect(followRedirect.isSelected());
            container.setDirDepth(depthSlider.getValue());
            container.setThreadNum(threadNumSlider.getValue());
            container.setProxy(proxyAndPort.getText());
            container.setResponseCodes(UrlUtils.loadWatchedResponseCodes(responseCodes.getText()));

            // show dialog for saving file (open in Future????)
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }

            container.setThreads(saveThreadList);
            container.setRegularTreeRoot((DefaultMutableTreeNode) tree.getModel().getRoot());
            if (followRedirect.isSelected()) {
                container.setRedirectTreeRoot(((DefaultMutableTreeNode) redirectTree.getModel().getRoot()));
            }

            JFileChooser fileChooser = new JFileChooser();
            // Demonstrate "Save" dialog:
            FileNameExtensionFilter filter = new FileNameExtensionFilter(".ddg", "ddg");
            fileChooser.setFileFilter(filter);
            int rVal = fileChooser.showSaveDialog(null);
            if (rVal == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                // Append .ddg extension if necessary
                if (!fileToSave.getAbsolutePath().endsWith(".ddg")) {
                    fileToSave = new File(fileToSave.getAbsolutePath() + ".ddg");
                }

                try (FileOutputStream fileOut = new FileOutputStream(fileToSave);
                     ObjectOutputStream objectOut = new ObjectOutputStream(fileOut)) {

                    objectOut.writeObject(container);
                    log.debug("[FILE] Object serialized and saved to file.");

                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                reEnableComponentsWhenCancel();
                progressBar.setValue(0);
                progressBar.setVisible(false);
                firstInitialization = true;
                startDigging.setText("Start Digging");
                cancelDigging.setVisible(false);
                saveDigging.setVisible(false);
                urlTextField.setText("");
                JTreeUtils.setDefaultRoot(tree);
                JTreeUtils.setDefaultRoot(redirectTree);
                tree.setVisible(false);
                redirectTree.setVisible(false);
            }
        });
        saveDigging.setVisible(false);

        loadDigging = new JButton("Load Digging");
        loadDigging.setMaximumSize(new Dimension(600, loadDigging.getPreferredSize().height));
        loadDigging.setMinimumSize(new Dimension(600, loadDigging.getPreferredSize().height));
        loadDigging.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            int rVal = fileChooser.showOpenDialog(null);
            if (rVal == JFileChooser.APPROVE_OPTION) {
                File file = fileChooser.getSelectedFile();

                try (FileInputStream fileIn = new FileInputStream(file);
                     ObjectInputStream objectIn = new ObjectInputStream(fileIn)) {

                    ApplicationContainer container = (ApplicationContainer) objectIn.readObject();

                    log.debug("Loaded ApplicationContainer\n\t\t{}", container);

                    DefaultTreeModel treeModel = (DefaultTreeModel) tree.getModel();
                    treeModel.setRoot(container.getRegularTreeRoot());
                    treeModel.reload();

                    followRedirect.setSelected(container.isFollowRedirect());
                    if (container.isFollowRedirect()) {
                        DefaultTreeModel redirectedTreeModel = (DefaultTreeModel) redirectTree.getModel();
                        redirectedTreeModel.setRoot(container.getRedirectTreeRoot());
                        redirectedTreeModel.reload();

                        seeRedirectTree.setVisible(true);
                    }

                    responseCodes.setText(StringUtils.join(container.getResponseCodes(), ","));

                    threadNumSlider.setValue(container.getThreadNum());

                    depthSlider.setValue(container.getDirDepth());

                    DefaultListModel<String> entries = new DefaultListModel<>();
                    entries.addAll(container.getDirList());
                    dirsAndFilesList.setModel(entries);

                    fileExtensionsTextField.setText(StringUtils.join(container.getFileExList(), ","));

                    disableComponentsWhenStart();

                    tasks = loadThreadPool(container);

                    DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
                    if (root.getUserObject() != null)
                        urlTextField.setText(((DiggerNode) root.getUserObject()).getUrl());

                    startDigging.setText("Continue Digging");
                    cancelDigging.setVisible(true);
                    saveDigging.setVisible(true);
                    loadedDigging = true;
//                    firstInitialization = false;
                } catch (IOException | ClassNotFoundException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        progressBar = new JProgressBar(0, 100);
        progressBar.setMaximumSize(new Dimension(600, progressBar.getPreferredSize().height));
        progressBar.setMinimumSize(new Dimension(600, progressBar.getPreferredSize().height));
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        Timer timer = new Timer(100, e -> {
            if (progressBar.getValue() >= progressBar.getMaximum()) {
                log.info("Finished all tasks!!!");
                progressBar.setValue(0);
                progressBar.setVisible(false);
//                dirsAndFilesList.setModel(new DefaultListModel<>());
//                reEnableComponentsWhenCancel();
//                urlTextField.setText("");
//                JTreeUtils.setDefaultRoot(tree);
//                JTreeUtils.setDefaultRoot(redirectTree);
//                tree.setVisible(false);
//                redirectTree.setVisible(false);
                startDigging.setText("Start Digging");
                firstInitialization = true;
            }
        });
        timer.start();
    }

    private List<Runnable> loadThreadPool(ApplicationContainer container) {
        List<Runnable> tasks = new ArrayList<>();

        for (ThreadStateInfo t: container.getThreads()) {
            DiggerWorker diggerWorker = new DiggerWorker.DiggerWorkerBuilder(t.getUrl(), t.getCurrentDepth())
                    .fileExtensions(container.getFileExList())
                    .threadPool(executorService)
                    .httpClient(httpAsyncClient)
                    .dirList(container.getDirList())
                    .responseCodes(container.getResponseCodes())
                    .directoryList(dirsAndFilesList)
                    .maxDepth(container.getDirDepth())
                    .iterator(t.getIterator())
                    .followRedirects(container.isFollowRedirect())
                    .tree(wrappedTree)
                    .progressBar(progressBar)
                    .logger(logging)
                    .save(isDiggingSave, saveThreadList, isKillSave)
                    .build();

            tasks.add(diggerWorker);
        }
        return tasks;
    }

    private void firstInit() {
        firstInitialization = false;
        try {
            String scheme = UrlUtils.getScheme(urlTextField.getText());
            String hostname = UrlUtils.getHostname(urlTextField.getText());
            if (hostname.endsWith("/"))
                hostname = hostname.substring(0, hostname.length() - 1);
            initHttpClient(scheme, hostname);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        initThreadPool();

        if (!followRedirect.isSelected())
            updateUIComponentsPositions();

        errorMessage.setVisible(false);
        progressBar.setVisible(true);
        tree.setVisible(true);
        seeRedirectTree.setVisible(followRedirect.isSelected());

        List<String> fileExtensions = null;
        if (fileExtensionsTextField.getText() != null && !fileExtensionsTextField.getText().equals("")) {
            fileExtensions = List.of(fileExtensionsTextField.getText().split(
                    fileExtensionsTextField.getText().contains(",") ? "," : " "
            ));
        }

        if (fileExtensions != null && !fileExtensions.isEmpty())
            progressBar.setMaximum(dirsAndFilesList.getModel().getSize() * fileExtensions.size());
        else
            progressBar.setMaximum(dirsAndFilesList.getModel().getSize());

        String url = urlTextField.getText().trim();
        if (url.endsWith("/"))
            url = url.substring(0, url.length() - 1);

        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode child = new DefaultMutableTreeNode(new DiggerNode(null, url, UrlUtils.HttpResponseCodeStatus.SUCCESS));
        model.setRoot(child);
        tree.scrollPathToVisible(new TreePath(child.getPath()));

        DefaultTreeModel redirectTreeModel = (DefaultTreeModel) redirectTree.getModel();
        DefaultMutableTreeNode redirectRoot = (DefaultMutableTreeNode) redirectTreeModel.getRoot();
        DefaultMutableTreeNode target = new DefaultMutableTreeNode(new DiggerNode(redirectRoot, url, UrlUtils.HttpResponseCodeStatus.SUCCESS));
        redirectRoot.add(target);
        redirectTree.scrollPathToVisible(new TreePath(child.getPath()));

        disableComponentsWhenStart();

        diggerWorker = new DiggerWorker.DiggerWorkerBuilder(url, 0)
                .fileExtensions(fileExtensions)
                .threadPool(executorService)
                .httpClient(httpAsyncClient)
                .dirList(entryList)
                .responseCodes(UrlUtils.loadWatchedResponseCodes(responseCodes.getText()))
                .directoryList(dirsAndFilesList)
                .maxDepth(depthSlider.getValue())
                .followRedirects(followRedirect.isSelected())
                .tree(wrappedTree)
                .progressBar(progressBar)
                .logger(logging)
                .save(isDiggingSave, saveThreadList, isKillSave)
                .build();
        diggerWorker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);
                if (progressBar.getValue() == progressBar.getMaximum())
                    startDigging.setText("Start Digging");
            }
        });

        diggerWorker.execute();
    }

    private boolean isValidToStart() {
        return urlTextField.getText() != null && !urlTextField.getText().equals("") && dirsAndFilesList.getModel().getSize() > 0;
    }

    private void initLegendSection() {
        legendHeader = new JLabel("Legend");
        legendHeader.setFont(new Font("Calibri", Font.BOLD, 20));

        // success color - #81F77E
        // info color - #0D84A1
        // redirect color - #FB4F4F
        // client error color - #EBF582
        // server error color - #890DA1

        BufferedImage iconInfo = null;
        BufferedImage iconSuccess = null;
        BufferedImage iconRedirect = null;
        BufferedImage iconClientError = null;
        BufferedImage iconServerError = null;
        try {
            iconInfo = ImageIO.read((DirDigger.class.getResource("../images/info-circle.png")));
            iconSuccess = ImageIO.read((DirDigger.class.getResource("../images/success-circle.png")));
            iconRedirect = ImageIO.read((DirDigger.class.getResource("../images/redirect-circle.png")));
            iconClientError = ImageIO.read((DirDigger.class.getResource("../images/client_error-circle.png")));
            iconServerError = ImageIO.read((DirDigger.class.getResource("../images/server_error-circle.png")));
        } catch (Exception e) {
            e.printStackTrace();
        }
        legendCircleInfo = new JLabel(new ImageIcon(iconInfo));
        legendCircleSuccess = new JLabel(new ImageIcon(iconSuccess));
        legendCircleRedirect = new JLabel(new ImageIcon(iconRedirect));
        legendCircleClientError = new JLabel(new ImageIcon(iconClientError));
        legendCircleServerError = new JLabel(new ImageIcon(iconServerError));

        legendInfo = new JLabel("1xx Informational");
        legendSuccess = new JLabel("2xx Success");
        legendRedirect = new JLabel("3xx Redirection");
        legendClientError = new JLabel("4xx Client error");
        legendServerError = new JLabel("5xx Server error");
    }

    private void initSeparators() {
        firstHorizontalSeparator = new JSeparator();
        firstHorizontalSeparator.setOrientation(SwingConstants.HORIZONTAL);
        firstHorizontalSeparator.setMaximumSize(new Dimension(600, firstHorizontalSeparator.getPreferredSize().height));
        firstHorizontalSeparator.setMinimumSize(new Dimension(600, firstHorizontalSeparator.getPreferredSize().height));

        secondHorizontalSeparator = new JSeparator();
        secondHorizontalSeparator.setOrientation(SwingConstants.HORIZONTAL);
        secondHorizontalSeparator.setMaximumSize(new Dimension(600, secondHorizontalSeparator.getPreferredSize().height));
        secondHorizontalSeparator.setMinimumSize(new Dimension(600, secondHorizontalSeparator.getPreferredSize().height));

        thirdHorizontalSeparator = new JSeparator();
        thirdHorizontalSeparator.setOrientation(SwingConstants.HORIZONTAL);
        thirdHorizontalSeparator.setMaximumSize(new Dimension(600, thirdHorizontalSeparator.getPreferredSize().height));
        thirdHorizontalSeparator.setMinimumSize(new Dimension(600, thirdHorizontalSeparator.getPreferredSize().height));

        verticalSeparator = new JSeparator();
        verticalSeparator.setOrientation(SwingConstants.VERTICAL);
        verticalSeparator.setMaximumSize(new Dimension(5, 10000));
        verticalSeparator.setMinimumSize(new Dimension(5, 10000));

        fourthHorizontalSeparator = new JSeparator();
        fourthHorizontalSeparator.setOrientation(SwingConstants.HORIZONTAL);
        fourthHorizontalSeparator.setMaximumSize(new Dimension(600, fourthHorizontalSeparator.getPreferredSize().height));
        fourthHorizontalSeparator.setMinimumSize(new Dimension(600, fourthHorizontalSeparator.getPreferredSize().height));
    }

    private void initTree() {
        tree = new JTree();
        tree.setVisible(false);
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode tempRoot = new DefaultMutableTreeNode(new DiggerNode(null, "", UrlUtils.HttpResponseCodeStatus.SUCCESS));
        model.setRoot(tempRoot);
        CustomIconRenderer customIconRenderer = new CustomIconRenderer();
        tree.setCellRenderer(customIconRenderer);

        wrappedTree.setTree(tree);

        redirectTree = new JTree();
        redirectTree.setRootVisible(false);
        DefaultTreeModel redirectModel = (DefaultTreeModel) redirectTree.getModel();
        DefaultMutableTreeNode redirectRoot = new DefaultMutableTreeNode(new DiggerNode(null, "redirect tree root", UrlUtils.HttpResponseCodeStatus.REDIRECTION));
        redirectModel.setRoot(redirectRoot);
        redirectTree.setCellRenderer(customIconRenderer);

        redirectTreeScrollPane = new JScrollPane(redirectTree);
        redirectTreeScrollPane.setVisible(false);
        redirectTreeScrollPane.setMaximumSize(new Dimension(1000, 1000));
        redirectTreeScrollPane.setMinimumSize(new Dimension(1000, 1000));

        wrappedTree.setRedirectTree(redirectTree);
    }

    private void positionUIComponents() {
        // remainder about GroupLayout https://stackoverflow.com/questions/35252026/aligning-vertical-and-horizontal-sequentialgroup-in-swing
        GroupLayout layout = new GroupLayout(panel);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);
        panel.setLayout(layout);

        layout.setHorizontalGroup(
                layout.createSequentialGroup()

                        // first group of components
                        .addGroup(layout.createParallelGroup()
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(urlLabel)
                                                .addComponent(urlTextField)
                                        )
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(fileExtensionsLabel)
                                                .addComponent(fileExtensionsTextField)
                                        )
                                )

                                .addComponent(firstHorizontalSeparator)

                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(loadFileOfDirsLabel)
                                        .addComponent(browseFiles)
                                )
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(addDirListEntry)
                                        .addComponent(entryTextField)
                                        .addComponent(addEntryButton)
                                )
                                .addComponent(clearDirList)
                                .addComponent(dirScrollPane)

                                .addComponent(secondHorizontalSeparator)

                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(sliderDepthLabel)
                                        .addComponent(depthSlider)
                                )
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(threadNumSliderLabel)
                                        .addComponent(threadNumSlider)
                                )
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(proxyAndPortLabel)
                                        .addComponent(proxyAndPort)
                                )
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(responseCodesLabel)
                                        .addComponent(responseCodes)
                                )

                                .addComponent(followRedirect)
                                .addComponent(seeRedirectTree)

                                .addComponent(thirdHorizontalSeparator)

                                .addComponent(errorMessage)
                                .addComponent(startDigging)
                                .addComponent(loadDigging)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(cancelDigging)
                                                .addComponent(saveDigging)
                                        )
                                )
                                .addComponent(progressBar)

                                .addComponent(fourthHorizontalSeparator)

                                .addComponent(legendHeader)
                                .addGroup(layout.createParallelGroup()
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(legendCircleInfo)
                                                .addComponent(legendInfo)
                                        )
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(legendCircleSuccess)
                                                .addComponent(legendSuccess)
                                        )
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(legendCircleRedirect)
                                                .addComponent(legendRedirect)
                                        )
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(legendCircleClientError)
                                                .addComponent(legendClientError)
                                        )
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(legendCircleServerError)
                                                .addComponent(legendServerError)
                                        )
                                )
                        )

                        .addComponent(verticalSeparator)

                        .addComponent(tree)
                        .addComponent(redirectTreeScrollPane)
        );

        layout.setVerticalGroup(
                layout.createParallelGroup()

                        .addGroup(layout.createSequentialGroup()

                                .addGroup(layout.createParallelGroup()
                                        .addComponent(urlLabel)
                                        .addComponent(urlTextField)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(fileExtensionsLabel)
                                        .addComponent(fileExtensionsTextField)
                                )

                                .addComponent(firstHorizontalSeparator)

                                .addGroup(layout.createParallelGroup()
                                        .addComponent(loadFileOfDirsLabel)
                                        .addComponent(browseFiles)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(addDirListEntry)
                                        .addComponent(entryTextField)
                                        .addComponent(addEntryButton)
                                )
                                .addComponent(clearDirList)
                                .addComponent(dirScrollPane)

                                .addComponent(secondHorizontalSeparator)

                                .addGroup(layout.createParallelGroup()
                                        .addComponent(sliderDepthLabel)
                                        .addComponent(depthSlider)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(threadNumSliderLabel)
                                        .addComponent(threadNumSlider)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(proxyAndPortLabel)
                                        .addComponent(proxyAndPort)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(responseCodesLabel)
                                        .addComponent(responseCodes)
                                )

                                .addComponent(followRedirect)
                                .addComponent(seeRedirectTree)

                                .addComponent(thirdHorizontalSeparator)

                                .addComponent(errorMessage)
                                .addComponent(startDigging)
                                .addComponent(loadDigging)
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(cancelDigging)
                                        .addComponent(saveDigging)
                                )
                                .addComponent(progressBar)

                                .addComponent(fourthHorizontalSeparator)

                                .addComponent(legendHeader)
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(legendCircleInfo)
                                        .addComponent(legendInfo)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(legendCircleSuccess)
                                        .addComponent(legendSuccess)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(legendCircleRedirect)
                                        .addComponent(legendRedirect)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(legendCircleClientError)
                                        .addComponent(legendClientError)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(legendCircleServerError)
                                        .addComponent(legendServerError)
                                )
                        )

                        .addComponent(verticalSeparator)

                        .addComponent(tree)
                        .addComponent(redirectTreeScrollPane)
        );
    }

    // when there is no redirection following
    private void updateUIComponentsPositions() {
        GroupLayout layout = (GroupLayout) panel.getLayout();

        treeScrollPane = new JScrollPane(tree);
        treeScrollPane.setMaximumSize(new Dimension(1000, 1000));
        treeScrollPane.setMinimumSize(new Dimension(1000, 1000));

        layout.setHorizontalGroup(
                layout.createSequentialGroup()

                        // first group of components
                        .addGroup(layout.createParallelGroup()
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(urlLabel)
                                                .addComponent(urlTextField)
                                        )
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(fileExtensionsLabel)
                                                .addComponent(fileExtensionsTextField)
                                        )
                                )

                                .addComponent(firstHorizontalSeparator)

                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(loadFileOfDirsLabel)
                                        .addComponent(browseFiles)
                                )
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(addDirListEntry)
                                        .addComponent(entryTextField)
                                        .addComponent(addEntryButton)
                                )
                                .addComponent(clearDirList)
                                .addComponent(dirScrollPane)

                                .addComponent(secondHorizontalSeparator)

                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(sliderDepthLabel)
                                        .addComponent(depthSlider)
                                )
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(threadNumSliderLabel)
                                        .addComponent(threadNumSlider)
                                )
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(proxyAndPortLabel)
                                        .addComponent(proxyAndPort)
                                )
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(responseCodesLabel)
                                        .addComponent(responseCodes)
                                )

                                .addComponent(followRedirect)
                                .addComponent(seeRedirectTree)

                                .addComponent(thirdHorizontalSeparator)

                                .addComponent(errorMessage)
                                .addComponent(startDigging)
                                .addComponent(loadDigging)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.TRAILING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(cancelDigging)
                                                .addComponent(saveDigging)
                                        )
                                )
                                .addComponent(progressBar)

                                .addComponent(fourthHorizontalSeparator)

                                .addComponent(legendHeader)
                                .addGroup(layout.createParallelGroup()
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(legendCircleInfo)
                                                .addComponent(legendInfo)
                                        )
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(legendCircleSuccess)
                                                .addComponent(legendSuccess)
                                        )
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(legendCircleRedirect)
                                                .addComponent(legendRedirect)
                                        )
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(legendCircleClientError)
                                                .addComponent(legendClientError)
                                        )
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(legendCircleServerError)
                                                .addComponent(legendServerError)
                                        )
                                )
                        )

                        .addComponent(verticalSeparator)

                        .addComponent(treeScrollPane)
        );

        layout.setVerticalGroup(
                layout.createParallelGroup()

                        .addGroup(layout.createSequentialGroup()

                                .addGroup(layout.createParallelGroup()
                                        .addComponent(urlLabel)
                                        .addComponent(urlTextField)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(fileExtensionsLabel)
                                        .addComponent(fileExtensionsTextField)
                                )

                                .addComponent(firstHorizontalSeparator)

                                .addGroup(layout.createParallelGroup()
                                        .addComponent(loadFileOfDirsLabel)
                                        .addComponent(browseFiles)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(addDirListEntry)
                                        .addComponent(entryTextField)
                                        .addComponent(addEntryButton)
                                )
                                .addComponent(clearDirList)
                                .addComponent(dirScrollPane)

                                .addComponent(secondHorizontalSeparator)

                                .addGroup(layout.createParallelGroup()
                                        .addComponent(sliderDepthLabel)
                                        .addComponent(depthSlider)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(threadNumSliderLabel)
                                        .addComponent(threadNumSlider)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(proxyAndPortLabel)
                                        .addComponent(proxyAndPort)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(responseCodesLabel)
                                        .addComponent(responseCodes)
                                )

                                .addComponent(followRedirect)
                                .addComponent(seeRedirectTree)

                                .addComponent(thirdHorizontalSeparator)

                                .addComponent(errorMessage)
                                .addComponent(startDigging)
                                .addComponent(loadDigging)
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(cancelDigging)
                                        .addComponent(saveDigging)
                                )
                                .addComponent(progressBar)

                                .addComponent(fourthHorizontalSeparator)

                                .addComponent(legendHeader)
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(legendCircleInfo)
                                        .addComponent(legendInfo)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(legendCircleSuccess)
                                        .addComponent(legendSuccess)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(legendCircleRedirect)
                                        .addComponent(legendRedirect)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(legendCircleClientError)
                                        .addComponent(legendClientError)
                                )
                                .addGroup(layout.createParallelGroup()
                                        .addComponent(legendCircleServerError)
                                        .addComponent(legendServerError)
                                )
                        )

                        .addComponent(verticalSeparator)

                        .addComponent(treeScrollPane)
        );

        panel.setLayout(layout);
        // Refresh the panel after adding the new component
        panel.revalidate();
        panel.repaint();
    }

    private void initHttpClient(String scheme, String hostname) throws IOReactorException {
        // Create I/O reactor configuration
        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(Runtime.getRuntime().availableProcessors())
                // Connection Timeout – the time to establish the connection with the remote host
                .setConnectTimeout(30000)
                // Socket Timeout – the time waiting for data – after establishing the connection; maximum time of inactivity between two data packets
                .setSoTimeout(30000)
                .build();

        // Create a custom I/O reactor
        ConnectingIOReactor ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);

        // Create a connection manager with custom configuration.
        PoolingNHttpClientConnectionManager connManager = new PoolingNHttpClientConnectionManager(ioReactor);

        // Configure total max or per route limits for persistent connections
        // that can be kept in the pool or leased by the connection manager.
        connManager.setMaxTotal(100);
        connManager.setDefaultMaxPerRoute(100);
//        connManager.setMaxPerRoute(new HttpRoute(new HttpHost("somehost", 80)), 20);

        // Use custom cookie store if necessary.
//        CookieStore cookieStore = new BasicCookieStore();
//        // Use custom credentials provider if necessary.
//        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
//        credentialsProvider.setCredentials(new AuthScope("localhost", 8889), new UsernamePasswordCredentials("squid", "nopassword"));
//        // Create global request configuration
        org.apache.http.client.config.RequestConfig defaultRequestConfig = org.apache.http.client.config.RequestConfig.custom()
         	            .setCookieSpec(CookieSpecs.STANDARD)
//         	            .setExpectContinueEnabled(true)
//         	            .setTargetPreferredAuthSchemes(Arrays.asList(AuthSchemes.NTLM, AuthSchemes.DIGEST))
//         	            .setProxyPreferredAuthSchemes(Arrays.asList(AuthSchemes.BASIC))
         	            .build();

        httpAsyncClient = HttpAsyncClients.custom()
                        .setConnectionManager(connManager)
//         	            .setDefaultCookieStore(cookieStore)
//         	            .setDefaultCredentialsProvider(credentialsProvider)
         	            .setProxy(UrlUtils.loadProxy(proxyAndPort.getText()))
         	            .setDefaultRequestConfig(defaultRequestConfig)
                        .setRedirectStrategy(new CustomRedirectStrategy(scheme, hostname, wrappedTree))
         	            .build();
    }

    private void loadTestDirFile() {
        File file = new File("/home/marko/Desktop/top_100_dirs.txt");

        DefaultListModel<String> listModel = (DefaultListModel<String>) dirsAndFilesList.getModel();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line = reader.readLine();
                while (line != null) {
                    if (!line.startsWith("#") && !line.equals("")) {
                        String entry = line.trim();
                        listModel.addElement(entry);
                        entryList.add(entry);
                    }
                    line = reader.readLine();
                }
            } catch (IOException exception) {
                exception.printStackTrace();
            }
        dirsAndFilesList.setModel(listModel);
    }

    private void disableComponentsWhenStart() {
        followRedirect.setEnabled(false);
        threadNumSlider.setEnabled(false);
        depthSlider.setEnabled(false);
        clearDirList.setEnabled(false);
        browseFiles.setEnabled(false);
        addEntryButton.setEnabled(false);
        loadDigging.setEnabled(false);
        urlTextField.setText("");

        panel.revalidate();
        panel.repaint();
    }

    private void reEnableComponentsWhenCancel() {
        followRedirect.setEnabled(true);
        threadNumSlider.setEnabled(true);
        depthSlider.setEnabled(true);
        clearDirList.setEnabled(true);
        browseFiles.setEnabled(true);
        addEntryButton.setEnabled(true);
        loadDigging.setEnabled(true);

        panel.revalidate();
        panel.repaint();
    }
}
