import burp.api.montoya.logging.Logging;
import utils.Globals;
import utils.UrlUtils;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.*;

public class DirDigger {

    private static final int MAX_WORKER_THREADS = 10;

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
    private JProgressBar progressBar;

    private JLabel sliderDepthLabel;
    private JSlider depthSlider;
    private JLabel threadNumSliderLabel;
    private JSlider threadNumSlider;

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

    private JSeparator verticalSeparator;
    private JSeparator firstHorizontalSeparator;
    private JSeparator secondHorizontalSeparator;
    private JSeparator thirdHorizontalSeparator;
    private JSeparator fourthHorizontalSeparator;

    private ExecutorService executorService;

    public DirDigger(Logging logging) {

        this.logging = logging;

        createUIComponents();
        initThreadPool();

        BrowseFileListener browseFileListener = new BrowseFileListener(dirsAndFilesList);
        browseFiles.addActionListener(browseFileListener);

        startDigging.addActionListener(e -> {
            if (urlTextField.getText() != null && dirsAndFilesList.getModel().getSize() > 0) {
                errorMessage.setVisible(false);
                progressBar.setVisible(true);
                tree.setVisible(true);

                List<String> fileExtensions = null;
                if (fileExtensionsTextField.getText() != null && !fileExtensionsTextField.getText().equals("")) {
                    fileExtensions = List.of(fileExtensionsTextField.getText().split(
                            fileExtensionsTextField.getText().contains(",") ? "," : " "
                    ));
                }

                String url = urlTextField.getText();
//                try {
//                    URI uri = new URI(url);
//                    URL url1 = new URL()
//                } catch (URISyntaxException ex) {
//                    throw new RuntimeException(ex);
//                }

//                String rootDomain = url.replaceFirst("^(http[s]?://www\\.|http[s]?://|www\\.)","");
                if (!url.endsWith("/")) {
                    url = url + "/";
                }

                DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(new DiggerNode(null, url, UrlUtils.HttpResponseCodeStatus.SUCCESS));
                model.setRoot(child);
                tree.scrollPathToVisible(new TreePath(child.getPath()));

                DiggerWorker diggerWorker = new DiggerWorker.DiggerWorkerBuilder(url, 0)
                        .fileExtensions(fileExtensions)
                        .threadPool(executorService)
                        .directoryList(dirsAndFilesList)
                        .maxDepth(depthSlider.getValue())
                        .tree(tree)
                        .progressBar(progressBar)
                        .build();
                diggerWorker.addPropertyChangeListener(evt -> {
                    if ("progress".equals(evt.getPropertyName())) {
                        int progress = (Integer) evt.getNewValue();
                        progressBar.setValue(progress);
                    }
                });

                diggerWorker.execute();
            } else {
                errorMessage.setText("Url not provided or list hasn't been loaded");
                errorMessage.setForeground(Color.RED);
                errorMessage.setVisible(true);
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

        executorService = new ThreadPoolExecutor(MAX_WORKER_THREADS, MAX_WORKER_THREADS,
                10L, TimeUnit.MINUTES,
                new LinkedBlockingQueue<>(),
                threadFactory);
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
    }

    private void initStartAndProgressSection() {
        errorMessage = new JLabel();
        errorMessage.setVisible(false);

        startDigging = new JButton("Start Digging");
        startDigging.setMaximumSize(new Dimension(600, startDigging.getPreferredSize().height));
        startDigging.setMinimumSize(new Dimension(600, startDigging.getPreferredSize().height));

        progressBar = new JProgressBar(0, 300);
        progressBar.setMaximumSize(new Dimension(600, progressBar.getPreferredSize().height));
        progressBar.setMinimumSize(new Dimension(600, progressBar.getPreferredSize().height));
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
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
            iconInfo = ImageIO.read((DirDigger.class.getResource("images/info-circle.png")));
            iconSuccess = ImageIO.read((DirDigger.class.getResource("images/success-circle.png")));
            iconRedirect = ImageIO.read((DirDigger.class.getResource("images/redirect-circle.png")));
            iconClientError = ImageIO.read((DirDigger.class.getResource("images/client_error-circle.png")));
            iconServerError = ImageIO.read((DirDigger.class.getResource("images/server_error-circle.png")));
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
//        tree.setRootVisible(false);
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        DefaultMutableTreeNode tempRoot = new DefaultMutableTreeNode(new DiggerNode(null, "", UrlUtils.HttpResponseCodeStatus.SUCCESS));
        model.setRoot(tempRoot);
        CustomIconRenderer customIconRenderer = new CustomIconRenderer();
        tree.setCellRenderer(customIconRenderer);

        treeScrollPane = new JScrollPane(tree);
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

                                .addComponent(thirdHorizontalSeparator)

                                .addComponent(errorMessage)
                                .addComponent(startDigging)
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

                                .addComponent(thirdHorizontalSeparator)

                                .addComponent(errorMessage)
                                .addComponent(startDigging)
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
        );
    }
}
