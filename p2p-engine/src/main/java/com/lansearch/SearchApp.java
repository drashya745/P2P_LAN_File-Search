package com.lansearch;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SearchApp {
    
    // NEW: The app's "memory". It remembers which IP has which file so you don't have to!
    private static final Map<String, String> fileLocations = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        // Ensure directories exist
        new File(FileIndexer.DATA_DIR).mkdirs();
        new File(FileIndexer.INDEX_DIR).mkdirs();

        // Boot up background services
        new com.lansearch.FileServer().startServer();
        com.lansearch.PeerDiscovery discovery = new com.lansearch.PeerDiscovery();
        discovery.startListening();
        discovery.broadcastPresence();

        // Launch UI
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("LAN P2P Search");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(750, 450);
            frame.setLayout(new BorderLayout());

            JTextField searchField = new JTextField();
            JButton searchButton = new JButton("Search Network");
            JButton indexButton = new JButton("Rebuild Index");
            JButton downloadButton = new JButton("Download File");

            JTextArea resultsArea = new JTextArea();
            resultsArea.setEditable(false);

            JPanel topPanel = new JPanel(new BorderLayout());
            
            // The manual IP field is completely GONE!
            topPanel.add(searchField, BorderLayout.CENTER);

            JPanel btnPanel = new JPanel();
            btnPanel.add(searchButton);
            btnPanel.add(downloadButton);
            btnPanel.add(indexButton);
            topPanel.add(btnPanel, BorderLayout.EAST);

            frame.add(topPanel, BorderLayout.NORTH);
            frame.add(new JScrollPane(resultsArea), BorderLayout.CENTER);

            FileIndexer indexer = new FileIndexer();
            startFolderWatcher(resultsArea, indexer);

            indexButton.addActionListener(e -> {
                resultsArea.setText("Indexing files in " + FileIndexer.DATA_DIR + "...\n");
                new Thread(() -> {
                    indexer.indexFiles();
                    SwingUtilities.invokeLater(() -> resultsArea.append("Index rebuilt successfully.\n"));
                }).start();
            });

            searchButton.addActionListener(e -> {
                String query = searchField.getText();
                
                if (!query.trim().isEmpty()) {
                    if (discovery.getActivePeers().isEmpty()) {
                        resultsArea.setText("No peers discovered yet. Wait a few seconds for the network shout...\n");
                        return;
                    }

                    resultsArea.setText("Broadcasting search for: " + query + "\n");
                    fileLocations.clear(); // Clear old memory on new search
                    
                    // Run the network search on a background thread
                    new Thread(() -> {
                        boolean foundAny = false;
                        
                        // NEW: Loop through every discovered peer automatically
                        for (String targetIp : discovery.getActivePeers()) {
                            resultsArea.append("Asking peer: " + targetIp + "...\n");
                            List<String> results = searchOverNetwork(targetIp, query);
                            
                            for (String res : results) {
                                if (!res.startsWith("[Error]")) {
                                    foundAny = true;
                                    fileLocations.put(res, targetIp); // Save this file's location!
                                    
                                    SwingUtilities.invokeLater(() -> 
                                        resultsArea.append("Found: " + res + " (on " + targetIp + ")\n")
                                    );
                                }
                            }
                        }
                        
                        if (!foundAny) {
                            SwingUtilities.invokeLater(() -> resultsArea.append("No files found on the network.\n"));
                        }
                    }).start();
                }
            });

            downloadButton.addActionListener(e -> {
                String sourceFilename = JOptionPane.showInputDialog(frame, "1. Enter the exact filename:");
                if (sourceFilename != null && !sourceFilename.trim().isEmpty()) {
                    
                    // NEW: Automagically grab the IP from our memory map!
                    String targetIp = fileLocations.get(sourceFilename.trim());
                    
                    if (targetIp == null) {
                        JOptionPane.showMessageDialog(frame, "File location unknown! Please Search for it first so I know who has it.");
                        return;
                    }

                    String saveFilename = JOptionPane.showInputDialog(frame, "2. Save this file as (Enter your custom name):");
                    if (saveFilename != null && !saveFilename.trim().isEmpty()) {
                        resultsArea.append("\nAutomagically requesting: " + sourceFilename + " from " + targetIp + "...\n");
                        resultsArea.append("Saving as: " + saveFilename + "...\n");

                        new Thread(() -> {
                            new com.lansearch.FileClient().downloadFile(targetIp, 8888, sourceFilename.trim(), saveFilename.trim());
                            SwingUtilities.invokeLater(() -> resultsArea.append("Download complete! Check your P2P shared folder.\n"));
                        }).start();
                    }
                }
            });

            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static List<String> searchOverNetwork(String ipAddress, String keyword) {
        List<String> results = new ArrayList<>();
        try (Socket socket = new Socket(ipAddress, 8888);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("SEARCH:" + keyword);

            String line;
            while ((line = in.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    results.add(line);
                }
            }
        } catch (Exception e) {
            results.add("[Error] Failed to connect to " + ipAddress);
        }
        return results;
    }

    private static void startFolderWatcher(JTextArea resultsArea, FileIndexer indexer) {
        new Thread(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                Path folderToWatch = Paths.get(FileIndexer.DATA_DIR);

                folderToWatch.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                resultsArea.append("Auto-indexer is actively watching your folder...\n");

                while (true) {
                    WatchKey key = watchService.take(); 
                    for (WatchEvent<?> event : key.pollEvents()) {
                        String changedFile = event.context().toString();
                        if (!changedFile.startsWith(".")) {
                            SwingUtilities.invokeLater(() -> resultsArea.append("\n[Auto] Change detected (" + changedFile + "). Rebuilding index...\n"));
                            indexer.indexFiles();
                            SwingUtilities.invokeLater(() -> resultsArea.append("[Auto] Index completely updated!\n"));
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}