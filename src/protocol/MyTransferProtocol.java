package protocol;

import client.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyTransferProtocol implements IRDTProtocol {
    public static final int PACKET_SIZE = 128;

    public static boolean brake = false;
    NetworkLayer networkLayer;

    private Role role = Role.Receiver;
    //private Role role = Role.Receiver;

    @Override
    public void run() {

        /**
         *
         * Send mode
         *
         */
        if (this.role == Role.Sender) {
            System.out.println("Sending...");

            // set packetSize
            int packetSize = 128;

            // read from the input file
            Integer[] fileContents = Utils.getFileContents();

            // keep track of where we are in the data
            int filePointer = 0;
            
            //Map with the index of a packet as Key and the value of that packet as content.
            Map<Integer, Integer[]> packets = Packets.makePackets(fileContents);

            boolean connected = false;
            while (!connected) {
                networkLayer.sendPacket(packets.get(0));
                System.out.println("Trying to connect");
                Integer[] packet = networkLayer.receivePacket();
                Utils.Timeout.Start();
                Utils.Timeout.SetTimeout(500L, this, null);
                while(packet == null && brake) {
                    packet = networkLayer.receivePacket();
                }
                brake = false;
                Utils.Timeout.Stop();
                if (packet.length == 0) {
                    System.out.println("Connected");
                    connected = true;
                }
            }
            
            for (int i = 1; i < packets.size(); i++) {
                networkLayer.sendPacket(packets.get(i));
            }
            
            boolean done = false;
            while (!done){
                Integer[] packet = networkLayer.receivePacket();
                Utils.Timeout.Start();
                Utils.Timeout.SetTimeout(500L, this, null);
                while(packet == null && brake) {
                    packet = networkLayer.receivePacket();
                }
                brake = false;
                Utils.Timeout.Stop();
                if (packet.length == 0){
                    done = true;
                    System.out.println("Finished the transmission");
                } else {
                    int checksum = packet[0];
                    int xor = packet[1];
                    int[] numbers = new int[(packet.length - 2) / 2];
                    for(int i = 0; i < numbers.length; i++){
                        numbers[i] = packet[i * 2] * 256 + packet[i * 2 + 1];
                    }
                    if (Packets.checkPacket(packet)) {
                        networkLayer.sendPacket(new Integer[0]);
                    } else {
                        System.out.println("Resending the missing packets");
                        for (int i : numbers) {
                            networkLayer.sendPacket(packets.get(i));
                        }
                    }
                }
            }

            // finally, send an empty packet to signal end-of-file.
            // There is a good chance this will not arrive, and the receiver will never finish.
            networkLayer.sendPacket(new Integer[0]);
        }

        /**
         *
         * Receive mode
         *
         */
        else if (this.role == Role.Receiver) {
            List<Integer> corruptPackets;

            System.out.println("Receiving...");

            // create the array that will contain the file contents
            Integer[] fileContents = new Integer[0];
            Map<Integer, Integer[]> packets = new HashMap<Integer, Integer[]>();
            boolean connected = false;
            int total = 0;

            while (!connected) {
                System.out.println("Trying to connect");
                Integer[] packet = networkLayer.receivePacket();
                Utils.Timeout.Start();
                Utils.Timeout.SetTimeout(500L, this, null);
                while(packet == null && brake) {
                    packet = networkLayer.receivePacket();
                }
                brake = false;
                Utils.Timeout.Stop();
                if (Packets.checkPacket(packet)) {
                    total = packet[2] * 256 + packet[3];
                    networkLayer.sendPacket(new Integer[0]);
                    connected = true;
                    System.out.println("Connected!");
                } else {
                    Integer[] b = new Integer[1];
                    b[0] = 0;
                    networkLayer.sendPacket(b);
                }
            }

            System.out.println("Receiving data");
            for (int i = 0; i < total; i++) {
                Integer[] packet = networkLayer.receivePacket();
                Utils.Timeout.Start();
                Utils.Timeout.SetTimeout(500L, this, null);
                while(packet == null && brake) {
                    packet = networkLayer.receivePacket();
                }
                brake = false;
                Utils.Timeout.Stop();
                if (Packets.checkPacket(packet)) {
                    packets.put(Packets.getIndex(packet), packet);
                } else {
                    System.out.println("Dropped packet");
                }
            }


            System.out.println("Asking for unfinished data");
            // loop until we are done receiving the file
            boolean stop = false;
            while (!stop) {
                List<Integer> missing = new ArrayList<Integer>();
                for (int i = 0; i < total; i++) {
                    if (!packets.containsKey((Integer) i + 1)) {
                        missing.add(i);
                    }
                }
                
                System.out.println("missing these parts: " + missing.toString());

                Integer[] packet = new Integer[2 + missing.size() * 2];
                int checksum = 0;
                int xor = 0;

                for (int i = 0; i < missing.size(); i++) {
                    int j = missing.get(i);
                    packet[j * 2 + 2] = j & 255;
                    packet[j * 2 + 3] = j >>> 8;
                    checksum += j & 255;
                    xor ^= j & 255;
                    checksum += j >>> 8;
                    xor ^= j >>> 8;
                }
                checksum += xor;

                packet[0] = checksum;
                packet[1] = xor;

                networkLayer.sendPacket(packet);

                // try to receive a packet from the network layer
                packet = networkLayer.receivePacket();
                Utils.Timeout.Start();
                Utils.Timeout.SetTimeout(500L, this, null);
                while(packet == null && brake) {
                    packet = networkLayer.receivePacket();
                }
                brake = false;
                Utils.Timeout.Stop();

                // if we reached the end of file, stop receiving
                if (packet.length == 0) {
                    System.out.println("Reached end-of-file. Done receiving.");
                    stop = true;
                }

                // if we haven't reached the end of file yet
                else {
                    if (Packets.checkPacket(packet)) {
                        packets.put(Packets.getIndex(packet), packet);
                    }
                }
            }

            System.out.println("Starting building the file");
            for (int i = 0; i < total; i++) {
                Integer[] ar = packets.get(i);
                
                Integer[] arr = new Integer[ar.length - 4];
                System.arraycopy(ar, 4, arr, 0, ar.length - 4);
                
                Integer[] newFileContents = new Integer[fileContents.length
                        + arr.length];
                System.arraycopy(fileContents, 0, newFileContents, 0,
                        fileContents.length);
                System.arraycopy(arr, 0, newFileContents,
                        fileContents.length, arr.length);

                // and assign it as the new fileContents
                fileContents = newFileContents;

                // write to the output file
                Utils.setFileContents(fileContents);
            }
        }
    }

    @Override
    public void setNetworkLayer(NetworkLayer networkLayer) {
        this.networkLayer = networkLayer;
    }

    @Override
    public void TimeoutElapsed(Object tag) {
        this.brake = true;
    }

    public void senderConnect(){
        
        
    }
    
    public void senderSend(){
        
        
    }
    
    public void senderSend(Integer[] b){
        
        
    }
    
    public void senderQuit(){
        
        
    }
    
    public void receiverConnect(){
        
        
    }
    
    public void receiverReceive(){
        
        
    }
    
    public void receiverFix(){
        
        
    }
    
    public void receiverQuit(){
        
        
    }
    
    public enum Role {
        Sender, Receiver
    }
}
