package protocol;

import client.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Lars on 12-2-2015.
 */
public class Packets {
    public static Map<Integer, Integer[]> makePackets(Integer[] contents) {
        // read from the input file
        Integer[] fileContents = Utils.getFileContents();

        // keep track of where we are in the data
        int filePointer = 0;

        // Map with the index of the packet as Key and the contents of the packet as Value.
        Map<Integer, Integer[]> packets = new HashMap<Integer, Integer[]>();

        for (int i = 1; filePointer < fileContents.length; i++) {
            Integer checksum = i;
            Integer xor = i;
            Integer[] packetContents = new Integer[Math.min(MyTransferProtocol.PACKET_SIZE, fileContents.length - filePointer) + 3];
            packetContents[2] = i;
            for(int j = 0; j < MyTransferProtocol.PACKET_SIZE && filePointer < fileContents.length; j++){
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
        Integer[] firstPacket = new Integer[4];
        firstPacket[2] = 0;
        firstPacket[3] = packets.size();
        firstPacket[1] = packets.size();
        firstPacket[0] = packets.size() + packets.size();
        packets.put(0, firstPacket);

        return packets;
    }

    public static boolean checkPacket(Integer[] contents) {
        //calculate the checkSum of the received packet
        int sum = 0;
        for (int i = 1; i < contents.length; i ++) {
            sum += contents[i];
        }

        //calculate the xor of the received packet
        int xor = contents[2];
        for (int i = 3; i < contents.length; i++) {
            xor = xor ^ contents[i];
        }

        //check if the calculated checks of the received packet match the given ones calculated by the sender
        return sum != contents[0] && xor != contents[1];
    }

    public static List<Integer> checkPackets(Map<Integer, Integer[]> packet) {
        List<Integer> corruptPackets = new ArrayList<Integer>();

        for (Map.Entry<Integer, Integer[]> entry : packet.entrySet()) {
            if(!checkPacket(entry.getValue())) {
                corruptPackets.add(entry.getKey());
            }
        }

        return corruptPackets;
    }
}
