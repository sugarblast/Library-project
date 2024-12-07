import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

public class LibrarySystem {
    private JFrame mainFrame;
    private JPanel mainPanel;
    private CardLayout cardLayout;
    private Map<String, File> library = new HashMap<>();
    private final File libraryFolder = new File("library_books");
    private final File metadataFile = new File("library_metadata.txt");

    public LibrarySystem() {
        mainFrame = new JFrame("Library System");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(800, 600);
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);
        JPanel adminPanel = createAdminPanel();
        mainPanel.add(adminPanel, "Admin");
        JPanel userPanel = createUserPanel();
        mainPanel.add(userPanel, "User");
        mainFrame.add(mainPanel);
        mainFrame.setVisible(true);
        loadBooks();
    }

    private JPanel createAdminPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JButton uploadButton = new JButton("Upload PDF Book");
        JButton switchToUser = new JButton("Switch to User View");
        uploadButton.addActionListener(e -> uploadPDF());
        switchToUser.addActionListener(e -> cardLayout.show(mainPanel, "User"));
        panel.add(uploadButton, BorderLayout.CENTER);
        panel.add(switchToUser, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createUserPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        JTextField searchField = new JTextField();
        JButton searchButton = new JButton("Search");
        JButton switchToAdmin = new JButton("Switch to Admin View");
        DefaultListModel<String> bookListModel = new DefaultListModel<>();
        JList<String> bookList = new JList<>(bookListModel);
        JScrollPane resultScroll = new JScrollPane(bookList);
        JPanel searchPanel = new JPanel();
        searchPanel.add(new JLabel("Search Book: "));
        searchPanel.add(searchField);
        searchPanel.add(searchButton);
        searchButton.addActionListener(e -> {
            String query = searchField.getText().trim();
            bookListModel.clear();
            for (String title : library.keySet()) {
                if (title.toLowerCase().contains(query.toLowerCase())) {
                    bookListModel.addElement(title);
                }
            }
            if (bookListModel.isEmpty()) {
                bookListModel.addElement("No books found!");
            }
        });
        switchToAdmin.addActionListener(e -> cardLayout.show(mainPanel, "Admin"));
        bookList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    String selectedBook = bookList.getSelectedValue();
                    if (selectedBook != null && library.containsKey(selectedBook)) {
                        openPDF(library.get(selectedBook));
                    }
                }
            }
        });
        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(resultScroll, BorderLayout.CENTER);
        panel.add(switchToAdmin, BorderLayout.SOUTH);
        return panel;
    }
    private void uploadPDF() {
        if (!libraryFolder.exists()) {
            libraryFolder.mkdir();
        }
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Files", "pdf"));
        int returnValue = fileChooser.showOpenDialog(mainFrame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String bookTitle = JOptionPane.showInputDialog("Enter Book Title:");
            if (bookTitle != null && !bookTitle.trim().isEmpty()) {
                File destinationFile = new File(libraryFolder, selectedFile.getName());
                try {
                    copyFile(selectedFile, destinationFile);
                    library.put(bookTitle, destinationFile);
                    saveMetadata();
                    JOptionPane.showMessageDialog(mainFrame, "Book uploaded successfully!");
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(mainFrame, "Error uploading book: " + e.getMessage());
                }
            } else {
                JOptionPane.showMessageDialog(mainFrame, "Book title cannot be empty!");
            }
        }
    }

    private void copyFile(File source, File destination) throws IOException {
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    private void saveMetadata() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(metadataFile))) {
            for (Map.Entry<String, File> entry : library.entrySet()) {
                String relativePath = libraryFolder.toPath().relativize(entry.getValue().toPath()).toString();
                writer.write(entry.getKey() + " | " + relativePath);
                writer.newLine();
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainFrame, "Error saving metadata: " + e.getMessage());
        }
    }

    private void loadBooks() {
        if (!libraryFolder.exists()) {
            libraryFolder.mkdir();
        }
        if (metadataFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(metadataFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(" \\| ");
                    if (parts.length == 2) {
                        String title = parts[0];
                        File bookFile = new File(libraryFolder, parts[1]);
                        if (bookFile.exists()) {
                            library.put(title, bookFile);
                        }
                    }
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(mainFrame, "Error loading metadata: " + e.getMessage());
            }
        }
    }

    private void openPDF(File pdfFile) {
        JFrame pdfFrame = new JFrame("Reading: " + pdfFile.getName());
        pdfFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            JPanel pdfPanel = new JPanel();
            pdfPanel.setLayout(new BoxLayout(pdfPanel, BoxLayout.Y_AXIS));
            JScrollPane scrollPane = new JScrollPane(pdfPanel);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            pdfFrame.add(scrollPane);
            JPanel navPanel = new JPanel();
            JTextField pageNumberField = new JTextField(5);
            JButton jumpButton = new JButton("Go to Page");
            navPanel.add(new JLabel("Page: "));
            navPanel.add(pageNumberField);
            navPanel.add(jumpButton);
            pdfFrame.add(navPanel, BorderLayout.NORTH);
            int maxWidth = 0;
            int maxHeight = 0;
            Map<Integer, JLabel> pageLabels = new HashMap<>();
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 100);
                maxWidth = Math.max(maxWidth, image.getWidth());
                maxHeight = Math.max(maxHeight, image.getHeight());
                JLabel pageLabel = new JLabel(new ImageIcon(image));
                pageLabels.put(page, pageLabel);
                pdfPanel.add(pageLabel);
            }
            pdfFrame.setSize(maxWidth + 50, maxHeight + 50);
            pdfFrame.setVisible(true);
            jumpButton.addActionListener(e -> {
                String pageText = pageNumberField.getText().trim();
                try {
                    int targetPage = Integer.parseInt(pageText) - 1;
                    if (targetPage >= 0 && targetPage < document.getNumberOfPages()) {
                        JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
                        verticalScrollBar.setValue(pageLabels.get(targetPage).getY());
                    } else {
                        JOptionPane.showMessageDialog(pdfFrame, "Invalid page number.");
                    }
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(pdfFrame, "Please enter a valid page number.");
                }
            });
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame, "Error opening PDF: " + ex.getMessage());
        }
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(LibrarySystem::new);
    }
}
