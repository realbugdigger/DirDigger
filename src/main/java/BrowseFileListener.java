import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class BrowseFileListener implements ActionListener {

    private JList<String> dirsAndFilesList;

    public BrowseFileListener(JList<String> dirsAndFilesList) {
        this.dirsAndFilesList = dirsAndFilesList;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser(FileSystemView.getFileSystemView().getHomeDirectory());
        fileChooser.setMultiSelectionEnabled(true);

        int r = fileChooser.showOpenDialog(null);

        if (r == JFileChooser.APPROVE_OPTION) {
            File files[] = fileChooser.getSelectedFiles();

            DefaultListModel<String> listModel = (DefaultListModel<String>) dirsAndFilesList.getModel();
            for (File file : files) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line = reader.readLine();
                    while (line != null) {
                        if (!line.startsWith("#"))
                            listModel.addElement(line.trim());
                        line = reader.readLine();
                    }
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            }
            dirsAndFilesList.setModel(listModel);
        }
    }
}
