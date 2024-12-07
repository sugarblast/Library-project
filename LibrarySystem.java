import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
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
    private Map<String, ImageIcon> bookCovers = new HashMap<>(); // To store the cover images

    public LibrarySystem() {
        // Initialize the GUI
        mainFrame = new JFrame("Library System");
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(800, 600);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // Admin Panel
        JPanel adminPanel = createAdminPanel();
        mainPanel.add(adminPanel, "Admin");

        // User Panel
        JPanel userPanel = createUserPanel();
        mainPanel.add(userPanel, "User");

        mainFrame.add(mainPanel);
        mainFrame.setVisible(true);
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

        // Set custom renderer for the book list
        bookList.setCellRenderer(new BookListCellRenderer());

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

        // Add a double-click event to open the book in the reader
        bookList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) { // Double-click detected
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
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("PDF Files", "pdf"));
        int returnValue = fileChooser.showOpenDialog(mainFrame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            try (PDDocument document = Loader.loadPDF(selectedFile)) {
                // Extract the title from the PDF metadata (if available)
                String bookTitle = document.getDocumentInformation().getTitle();
                if (bookTitle == null || bookTitle.trim().isEmpty()) {
                    bookTitle = selectedFile.getName(); // Default to filename if no title in metadata
                }

                // Assign a serial number (s.n.) to the PDF
                int serialNumber = library.size() + 1;
                String serialTitle = serialNumber + ". " + bookTitle;

                // Render the first page as the cover and resize it
                PDFRenderer pdfRenderer = new PDFRenderer(document);
                BufferedImage firstPageImage = pdfRenderer.renderImageWithDPI(0, 100); // First page at 100 DPI

                // Resize the image to a smaller thumbnail size (e.g., 100x150)
                ImageIcon coverIcon = new ImageIcon(firstPageImage.getScaledInstance(100, 150, Image.SCALE_SMOOTH));

                // Store the file and its cover image
                library.put(serialTitle, selectedFile);
                bookCovers.put(serialTitle, coverIcon);

                JOptionPane.showMessageDialog(mainFrame, "Book uploaded successfully!");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(mainFrame, "Error uploading PDF: " + ex.getMessage());
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
            // Adjust the scroll speed
            scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Increase scroll speed
            pdfFrame.add(scrollPane);

            // Add a panel for page navigation controls
            JPanel navPanel = new JPanel();
            JTextField pageNumberField = new JTextField(5); // Input field for page number
            JButton jumpButton = new JButton("Go to Page");

            navPanel.add(new JLabel("Page: "));
            navPanel.add(pageNumberField);
            navPanel.add(jumpButton);

            pdfFrame.add(navPanel, BorderLayout.NORTH);

            // Initialize size variables to track the maximum width/height of the pages
            int maxWidth = 0;
            int maxHeight = 0;

            // Store all pages as image labels
            Map<Integer, JLabel> pageLabels = new HashMap<>();

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 100);
                maxWidth = Math.max(maxWidth, image.getWidth());
                maxHeight = Math.max(maxHeight, image.getHeight());

                JLabel pageLabel = new JLabel(new ImageIcon(image));
                pageLabels.put(page, pageLabel);
                pdfPanel.add(pageLabel);
            }

            // Dynamically adjust the frame size to fit the largest page
            pdfFrame.setSize(maxWidth + 50, maxHeight + 50); // Add some padding to frame size
            pdfFrame.setVisible(true);

            // Add action listener to the "Go to Page" button
            jumpButton.addActionListener(e -> {
                String pageText = pageNumberField.getText().trim();
                try {
                    int targetPage = Integer.parseInt(pageText) - 1; // Convert to zero-indexed
                    if (targetPage >= 0 && targetPage < document.getNumberOfPages()) {
                        // Scroll to the target page
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

    // Custom ListCellRenderer to display the cover image and title
    private class BookListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            String title = (String) value;
            JPanel panel = new JPanel(new BorderLayout());
            JLabel label = new JLabel(title);

            // Set the cover image for the book
            if (bookCovers.containsKey(title)) {
                label.setIcon(bookCovers.get(title));
            }

            panel.add(label, BorderLayout.CENTER);
            return panel;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LibrarySystem::new);
    }
}
