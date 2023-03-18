import burp.IBurpExtenderCallbacks;
import burp.IExtensionHelpers;
import utils.Globals;

import javax.swing.*;
import javax.swing.border.LineBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Hashtable;
import java.util.concurrent.*;

public class DirDigger {

    private static final int MAX_WORKER_THREADS = 10;

    private JPanel panel;
    private JLabel urlOrDomainLabel;
    private JTextField urlOrDomainTextField;
    private JLabel fileExtensionsLabel;
    private JButton browseFiles;
    private JList<String> dirsAndFilesList;
    private JLabel errorMessage;
    private JLabel loadFileOfDirsLabel;
    private JTextField extensionsTextField;
    private JButton startDigging;
    private JTree tree;
    private JProgressBar progressBar;
    private JSlider depthSlider;
    private JLabel sliderDepthLabel;
    private JSlider threadNumSlider;
    private JLabel threadNumSliderLabel;

    private final IBurpExtenderCallbacks callbacks;
    private final IExtensionHelpers helpers;

    private ExecutorService executorService;

    public DirDigger(IBurpExtenderCallbacks callbacks, IExtensionHelpers helpers) {

        createUIComponents();
        initThreadPool();
        this.callbacks = callbacks;
        this.helpers = helpers;

        BrowseFileListener browseFileListener = new BrowseFileListener(dirsAndFilesList);
        browseFiles.addActionListener(browseFileListener);

        startDigging.addActionListener(e -> {
            if (urlOrDomainTextField.getText() != null && dirsAndFilesList.getModel().getSize() > 0) {
                errorMessage.setVisible(false);
                progressBar.setVisible(true);
                tree.setVisible(true);

                String url = urlOrDomainTextField.getText();
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
                DefaultMutableTreeNode child = new DefaultMutableTreeNode(url);
                model.setRoot(child);
                tree.scrollPathToVisible(new TreePath(child.getPath()));

                DiggerWorker diggerWorker = new DiggerWorker.DiggerWorkerBuilder(url, 0)
                        .threadPool(executorService)
                        .directoryList(dirsAndFilesList)
                        .maxDepth(depthSlider.getValue())
                        .callbacks(callbacks)
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
        browseFiles = new JButton("Browse");

        panel = new JPanel();
        panel.setName(Globals.APP_NAME);

        urlOrDomainLabel = new JLabel("Target Url");

        urlOrDomainTextField = new JFormattedTextField();
        urlOrDomainTextField.setToolTipText("eg. https://example.org or example.org");
        urlOrDomainTextField.setMaximumSize(new Dimension(500, urlOrDomainTextField.getPreferredSize().height));
        urlOrDomainTextField.setMinimumSize(new Dimension(500, urlOrDomainTextField.getPreferredSize().height));

        fileExtensionsLabel = new JLabel("File Extensions");

        extensionsTextField = new JFormattedTextField();
        extensionsTextField.setToolTipText("php html sh asp txt");
        extensionsTextField.setMaximumSize(new Dimension(500, extensionsTextField.getPreferredSize().height));
        extensionsTextField.setMinimumSize(new Dimension(500, extensionsTextField.getPreferredSize().height));

        JSeparator firstHorizontalSeparator = new JSeparator();
        firstHorizontalSeparator.setOrientation(SwingConstants.HORIZONTAL);
        firstHorizontalSeparator.setMaximumSize(new Dimension(600, firstHorizontalSeparator.getPreferredSize().height));
        firstHorizontalSeparator.setMinimumSize(new Dimension(600, firstHorizontalSeparator.getPreferredSize().height));

        DefaultListModel<String> listModel = new DefaultListModel<>();
        dirsAndFilesList = new JList<>(listModel);
        dirsAndFilesList.setMaximumSize(new Dimension(600, 500));
        dirsAndFilesList.setMinimumSize(new Dimension(600, 500));
        dirsAndFilesList.setBorder(new LineBorder(new Color(0,0,0), 1));
        JScrollPane scrollPane = new JScrollPane(dirsAndFilesList);
        scrollPane.setMaximumSize(new Dimension(600, 500));
        scrollPane.setMinimumSize(new Dimension(600, 500));

        JSeparator secondHorizontalSeparator = new JSeparator();
        secondHorizontalSeparator.setOrientation(SwingConstants.HORIZONTAL);
        secondHorizontalSeparator.setMaximumSize(new Dimension(600, secondHorizontalSeparator.getPreferredSize().height));
        secondHorizontalSeparator.setMinimumSize(new Dimension(600, secondHorizontalSeparator.getPreferredSize().height));

        errorMessage = new JLabel();
        errorMessage.setVisible(false);

        loadFileOfDirsLabel = new JLabel("Load file of dirs and files");

        startDigging = new JButton("Start Digging");
        startDigging.setMaximumSize(new Dimension(600, startDigging.getPreferredSize().height));
        startDigging.setMinimumSize(new Dimension(600, startDigging.getPreferredSize().height));

        progressBar = new JProgressBar(0, 300);
        progressBar.setMaximumSize(new Dimension(600, progressBar.getPreferredSize().height));
        progressBar.setMinimumSize(new Dimension(600, progressBar.getPreferredSize().height));
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);

        JSeparator thirdHorizontalSeparator = new JSeparator();
        thirdHorizontalSeparator.setOrientation(SwingConstants.HORIZONTAL);
        thirdHorizontalSeparator.setMaximumSize(new Dimension(600, thirdHorizontalSeparator.getPreferredSize().height));
        thirdHorizontalSeparator.setMinimumSize(new Dimension(600, thirdHorizontalSeparator.getPreferredSize().height));

        depthSlider = new JSlider(1, 7);
        // Paint the track and label
        depthSlider.setPaintTrack(true);
        depthSlider.setPaintTicks(true);
        depthSlider.setPaintLabels(true);
        // Set the spacing
        depthSlider.setMajorTickSpacing(20);
        depthSlider.setMinorTickSpacing(5);
        depthSlider.setMinimumSize(new Dimension(400, depthSlider.getPreferredSize().height));
        depthSlider.setMaximumSize(new Dimension(400, depthSlider.getPreferredSize().height));
        // set slider labels
        Hashtable<Integer, JLabel> labels = new Hashtable<>();
        labels.put(1, new JLabel("1"));
        labels.put(2, new JLabel("2"));
        labels.put(3, new JLabel("3"));
        labels.put(4, new JLabel("4"));
        labels.put(5, new JLabel("5"));
        labels.put(6, new JLabel("6"));
        labels.put(7, new JLabel("7"));
        depthSlider.setLabelTable(labels);

        sliderDepthLabel = new JLabel("Depth of directories");

        threadNumSlider = new JSlider(1, 100);
        // Paint the track and label
        threadNumSlider.setPaintTrack(true);
        threadNumSlider.setPaintTicks(true);
        threadNumSlider.setPaintLabels(true);
        // Set the spacing
        threadNumSlider.setMajorTickSpacing(20);
        threadNumSlider.setMinorTickSpacing(5);
        threadNumSlider.setMinimumSize(new Dimension(400, threadNumSlider.getPreferredSize().height));
        threadNumSlider.setMaximumSize(new Dimension(400, threadNumSlider.getPreferredSize().height));
        // set slider labels
        Hashtable<Integer, JLabel> labelsTHSlider = new Hashtable<>();
        for (int i = 0; i <= 100; i=i+5) {
            if (i % 10 == 0)
                labelsTHSlider.put(i, new JLabel(String.format("%d", i)));
            else
                labelsTHSlider.put(i, new JLabel(""));
        }
        threadNumSlider.setLabelTable(labelsTHSlider);

        threadNumSliderLabel = new JLabel("Number of threads");

        JSeparator verticalSeparator = new JSeparator();
        verticalSeparator.setOrientation(SwingConstants.VERTICAL);
        verticalSeparator.setMaximumSize(new Dimension(5, 10000));
        verticalSeparator.setMinimumSize(new Dimension(5, 10000));

        tree = new JTree();
        tree.setVisible(false);
        JScrollPane treeScrollPane = new JScrollPane(tree);

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
                                                .addComponent(urlOrDomainLabel)
                                                .addComponent(urlOrDomainTextField)
                                        )
                                        .addGroup(layout.createSequentialGroup()
                                                .addComponent(fileExtensionsLabel)
                                                .addComponent(extensionsTextField)
                                        )
                                )
                                .addComponent(firstHorizontalSeparator)
                                .addGroup(layout.createSequentialGroup()
                                        .addComponent(loadFileOfDirsLabel)
                                        .addComponent(browseFiles)
                                )
                                .addComponent(scrollPane)
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
                        )

                        .addComponent(verticalSeparator)

                        .addComponent(treeScrollPane)
        );

        layout.setVerticalGroup(
                layout.createParallelGroup()

                        .addGroup(layout.createSequentialGroup()
                                        .addGroup(layout.createParallelGroup()
                                                        .addComponent(urlOrDomainLabel)
                                                        .addComponent(urlOrDomainTextField)
                                        )
                                        .addGroup(layout.createParallelGroup()
                                                .addComponent(fileExtensionsLabel)
                                                .addComponent(extensionsTextField)
                                        )
                                        .addComponent(firstHorizontalSeparator)
                                        .addGroup(layout.createParallelGroup()
                                                .addComponent(loadFileOfDirsLabel)
                                                .addComponent(browseFiles)
                                        )
                                        .addComponent(scrollPane)
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
                        )

                        .addComponent(verticalSeparator)

                        .addComponent(treeScrollPane)
        );

    }
}
