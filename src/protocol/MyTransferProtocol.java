package protocol;

import client.*;

import java.util.HashMap;
import java.util.Map;

public class MyTransferProtocol implements IRDTProtocol {

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

            Map<Integer, Integer[]> packets = new HashMap<Integer, Integer[]>();

            boolean stop = false;
            
            for(int i = 1; filePointer < fileContents.length; i++){
                Integer checksum = i;
                Integer xor = i;
                Integer[] packetContents = new Integer[Math.min(packetSize, fileContents.length - filePointer) + 3];
                packetContents[2] = i;
                for(int j = 0; j < packetSize && filePointer < fileContents.length; j++){
                    packetContents[i + 3] = fileContents[filePointer];
                    checksum += fileContents[filePointer];
                    xor = xor ^ fileContents[filePointer];
                    filePointer++;
                }
                checksum += xor;
                packetContents[0] = checksum;
                packetContents[1] = xor;
                packets.put(i, packetContents);
            }
            
            // loop until we are done transmitting the file
            stop = false;
            while (!stop) {
                // create a new packet
                // with size packetSize
                // or the remaining file size if less than packetSize
                Integer[] packetToSend = new Integer[Math.min(packetSize,
                        fileContents.length - filePointer)];

                // read (packetToSend.length) bytes and store them in the packet
                for (int i = 0; i < packetSize && filePointer < fileContents.length; i++) {
                    packetToSend[i] = fileContents[filePointer];
                    filePointer++;
                }

                // send the packet to the network layer
                networkLayer.sendPacket(packetToSend);

                // if we reached the end of the file
                if (filePointer >= fileContents.length) {
                    System.out.println("Reached end-of-file. Done sending.");
                    stop = true;
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
            System.out.println("Receiving...");

            // create the array that will contain the file contents
            Integer[] fileContents = new Integer[0];

            // loop until we are done receiving the file
            boolean stop = false;
            while (!stop) {

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
