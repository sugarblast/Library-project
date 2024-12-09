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
    private Map<String, ImageIcon> bookCovers = new HashMap<>(); // To store the cover images

    private static final String DATA_FILE = "library_data.ser"; // File for saving data

    public LibrarySystem() {
        loadLibraryData(); // Load existing library data at startup

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

        // Save data when the window is closed
        mainFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                saveLibraryData();
            }
        });
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

        // Populate the book list with saved books at startup
        populateBookList(bookListModel, "");

        searchButton.addActionListener(e -> {
            String query = searchField.getText().trim();
            populateBookList(bookListModel, query);
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

    private void populateBookList(DefaultListModel<String> bookListModel, String query) {
        bookListModel.clear();
        library.keySet().stream()
            .sorted((a, b) -> {
                int numA = Integer.parseInt(a.split("\\.")[0].trim()); // Extract serial number from title
                int numB = Integer.parseInt(b.split("\\.")[0].trim());
                return Integer.compare(numA, numB);
            })
            .filter(title -> title.toLowerCase().contains(query.toLowerCase()))
            .forEach(bookListModel::addElement);
    
        if (bookListModel.isEmpty()) {
            bookListModel.addElement("No books found!");
        }
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
            scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Increase scroll speed
            pdfFrame.add(scrollPane);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 100);
                JLabel pageLabel = new JLabel(new ImageIcon(image));
                pdfPanel.add(pageLabel);
            }

            pdfFrame.setSize(800, 600);
            pdfFrame.setVisible(true);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(mainFrame, "Error opening PDF: " + ex.getMessage());
        }
    }

    private void saveLibraryData() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(library);
            oos.writeObject(bookCovers);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(mainFrame, "Error saving library data: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadLibraryData() {
        File dataFile = new File(DATA_FILE);
        if (dataFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dataFile))) {
                library = (Map<String, File>) ois.readObject();
                bookCovers = (Map<String, ImageIcon>) ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                JOptionPane.showMessageDialog(null, "Error loading library data: " + e.getMessage());
            }
        }
    }

    private class BookListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            
            String title = (String) value;
    
            // Extract the serial number and title from the string
            String[] parts = title.split("\\. ", 2);
            String serialNumber = parts[0]; // Serial number
            String bookTitle = parts.length > 1 ? parts[1] : title; // Title
    
            JPanel mainPanel = new JPanel();
            mainPanel.setLayout(new BorderLayout());
            mainPanel.setOpaque(true);
            mainPanel.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
    
            // Create a panel for the content (thumbnail + title)
            JPanel contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.X_AXIS));
            contentPanel.setOpaque(false);
    
            // Create label for the serial number
            JLabel serialLabel = new JLabel(serialNumber + ". ");
            serialLabel.setFont(new Font("Arial", Font.BOLD, 14));
            serialLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
    
            // Create label for the thumbnail
            JLabel thumbnailLabel = new JLabel();
            thumbnailLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            if (bookCovers.containsKey(title)) {
                thumbnailLabel.setIcon(bookCovers.get(title));
            } else {
                thumbnailLabel.setPreferredSize(new Dimension(100, 150)); // Placeholder size
                thumbnailLabel.setOpaque(true);
                thumbnailLabel.setBackground(Color.LIGHT_GRAY); // Placeholder color
            }
    
            // Create label for the title
            JLabel titleLabel = new JLabel(bookTitle);
            titleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
            titleLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 5));
    
            // Add components to the content panel
            contentPanel.add(serialLabel);
            contentPanel.add(thumbnailLabel);
            contentPanel.add(Box.createRigidArea(new Dimension(10, 0))); // Add spacing between thumbnail and title
            contentPanel.add(titleLabel);
    
            // Add the content panel to the main panel
            mainPanel.add(contentPanel, BorderLayout.CENTER);
    
            // Add a separator line at the bottom
            if (index < list.getModel().getSize() - 1) { // Avoid adding a separator after the last item
                JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
                separator.setForeground(Color.GRAY);
                mainPanel.add(separator, BorderLayout.SOUTH);
            }
    
            return mainPanel;
        }
    }
    
    
    

    public static void main(String[] args) {
        SwingUtilities.invokeLater(LibrarySystem::new);
    }
}
