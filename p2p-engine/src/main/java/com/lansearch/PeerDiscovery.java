package com.lansearch;

import java.net.*;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class PeerDiscovery {
    private static final String MULTICAST_GROUP = "230.0.0.0";
    private static final int PORT = 4446;
    
    // Thread-safe set to store the IPs of everyone we find
    private Set<String> activePeers = new CopyOnWriteArraySet<>();

    public void startListening() {
        new Thread(() -> {
            try {
                MulticastSocket socket = new MulticastSocket(PORT);
                InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                socket.joinGroup(group);

                byte[] buf = new byte[256];
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    
                    String senderIP = packet.getAddress().getHostAddress();
                    
                    // Add IP to our list. If it's new, print it.
                    if (activePeers.add(senderIP)) {
                        System.out.println("Found a peer! IP: " + senderIP);
                    }
                }
            } catch (Exception e) {
                System.out.println("Listener error: " + e.getMessage());
            }
        }).start();
    }

    public void broadcastPresence() {
        // Shout out to the network every 3 seconds
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
                String msg = "HELLO_LAN_SEARCH";
                
                while (true) {
                    DatagramPacket packet = new DatagramPacket(msg.getBytes(), msg.length(), group, PORT);
                    socket.send(packet);
                    Thread.sleep(3000); // Wait 3 seconds before shouting again
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // SearchApp will call this to get the targets
    public Set<String> getActivePeers() {
        return activePeers;
    }
}