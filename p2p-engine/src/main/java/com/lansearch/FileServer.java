package com.lansearch;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
public class FileServer {
    public void startServer() {
        // Run server on a background thread so it doesn't block the UI
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(8888)) {
                System.out.println("FileServer listening on port 8888...");
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    handleClient(clientSocket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void handleClient(Socket clientSocket) {
    new Thread(() -> {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            // Read the command from the Mac
            String request = in.readLine();
            if (request == null || request.trim().isEmpty()) return;

            if (request.startsWith("SEARCH:")) {
                String keyword = request.substring(7).trim();
                System.out.println("Server received search query: " + keyword);
                
                // Run the local Lucene search on the Windows machine
                FileSearcher searcher = new FileSearcher();
                List<String> results = searcher.search(keyword);
                
                // Send the filenames back to the Mac, separated by newlines
                for (String res : results) {
                    out.write((res + "\n").getBytes());
                }
                out.flush();
                System.out.println("Search results sent.");

            } else if (request.startsWith("DOWNLOAD:")) {
                String filename = request.substring(9).trim();
                System.out.println("Server received download request for: " + filename);

                File fileToSend = new File(FileIndexer.DATA_DIR, filename);
                if (fileToSend.exists() && !fileToSend.isDirectory()) {
                    try (FileInputStream fis = new FileInputStream(fileToSend)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                        out.flush();
                        System.out.println("Transfer complete.");
                    }
                } else {
                    System.out.println("ERROR: File not found.");
                }
            }
        } catch (Exception e) {
            System.out.println("Connection dropped.");
            e.printStackTrace();
        }
    }).start();
    }
}