package protocol;

import client.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyTransferProtocol implements IRDTProtocol {
    public static final int PACKET_SIZE = 128;

    NetworkLayer networkLayer;

    private Role role = Role.Sender;
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
                Integer[] resp = networkLayer.receivePacket();
                if (resp.length == 0) {
                    connected = true;
                }
            }
            
            for (int i = 1; i < packets.size(); i++) {
                networkLayer.sendPacket(packets.get(i));
            }
            
            boolean done = false;
            while (!done){
                Integer[] response = networkLayer.receivePacket();
                if (response.length == 0){
                    done = true;
                } else {
                    int checksum = response[0];
                    int xor = response[1];
                    int[] numbers = new int[(response.length - 2) / 2];
                    for(int i = 0; i < numbers.length; i++){
                        checksum -= response[i * 2];
                        checksum -= response[i * 2 + 1];
                        xor ^= response[i * 2];
                        xor ^= response[i * 2 + 1];
                        numbers[i] = response[i * 2] * 256 + response[i * 2 + 1];
                    }
                    if (checksum != 0 || xor != 0) {
                        networkLayer.sendPacket(new Integer[0]);
                    } else {
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
                Integer[] firstPacket = networkLayer.receivePacket();
                if (Packets.checkPacket(firstPacket)) {
                    connected = true;
                }
            }
            
            while(packets.size() < total){
                Integer[] packet = networkLayer.receivePacket();
                int checksum = packet[0];
                int xor = packet[1];
                int index = packet[2] * 255 + packet[3];
                
            }
            
            // loop until we are done receiving the file
            boolean stop = false;
            while (!stop) {
                corruptPackets = new ArrayList<Integer>();

                // try to receive a packet from the network layer
                Integer[] packet = networkLayer.receivePacket();

                // if we indeed received a packet
                if (packet != null) {

                    // if we reached the end of file, stop receiving
                    if (packet.length == 0) {
                        System.out.println("Reached end-of-file. Done receiving.");
                        stop = true;
                    }



                    // if we haven't reached the end of file yet
                    else {
                        // make a new integer array which contains fileContents
                        // + packet
                        Integer[] newFileContents = new Integer[fileContents.length
                                + packet.length];
                        System.arraycopy(fileContents, 0, newFileContents, 0,
                                fileContents.length);
                        System.arraycopy(packet, 0, newFileContents,
                                fileContents.length, packet.length);

                        // and assign it as the new fileContents
                        fileContents = newFileContents;
                    }
                }else{
                    // wait ~10ms (or however long the OS makes us wait) before trying again
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        stop = true;
                    }
                }
            }

            // write to the output file
            Utils.setFileContents(fileContents);
        }
    }

    @Override
    public void setNetworkLayer(NetworkLayer networkLayer) {
        this.networkLayer = networkLayer;
    }

    @Override
    public void TimeoutElapsed(Object tag) {

    }

    public enum Role {
        Sender, Receiver
    }
}
