package com.lansearch;

import java.io.*;
import java.net.Socket;

public class FileClient {
    public void downloadFile(String serverIp, int port, String filename, String saveAs) {
        try (Socket socket = new Socket(serverIp, port);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             InputStream in = socket.getInputStream()) {

            // Tell the server we specifically want to DOWNLOAD
            out.println("DOWNLOAD:" + filename);

            File saveFile = new File(FileIndexer.DATA_DIR, saveAs);
            try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            System.out.println("File downloaded successfully to: " + saveFile.getAbsolutePath());

        } catch (Exception e) {
            System.out.println("Error downloading file from " + serverIp);
            e.printStackTrace();
        }
    }
}