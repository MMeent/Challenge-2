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
                while (resp == null){
                    resp = networkLayer.receivePacket();
                }
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
                while (response == null) {
                    response = networkLayer.receivePacket();
                }
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
                while(firstPacket == null) {
                    firstPacket = networkLayer.receivePacket();
                }
                if (Packets.checkPacket(firstPacket)) {
                    total = firstPacket[2] * 256 + firstPacket[3];
                    networkLayer.sendPacket(new Integer[0]);
                    connected = true;
                } else {
                    Integer[] b = new Integer[1];
                    b[0] = 0;
                    networkLayer.sendPacket(b);
                }
            }

            for (int i = 0; i < total; i++) {
                Integer[] packet = networkLayer.receivePacket();
                while (packet == null) {
                    packet = networkLayer.receivePacket();
                }
                if (Packets.checkPacket(packet)) {
                    packets.put(Packets.getIndex(packet), packet);
                }
            }

            // loop until we are done receiving the file
            boolean stop = false;
            while (!stop) {
                List<Integer> missing = new ArrayList<Integer>();
                for (int i = 0; i < total; i++) {
                    if (!packets.containsKey((Integer) i + 1)) {
                        missing.add(i);
                    }
                }

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
                while (packet == null) {
                    packet = networkLayer.receivePacket();
                }

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

    }

    public enum Role {
        Sender, Receiver
    }
}
