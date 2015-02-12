package protocol;

import client.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MyTransferProtocol implements IRDTProtocol {
    public static final int PACKET_SIZE = 128;

    private int state = 0;
    
    private int amount = 0;
    private boolean brake;
    NetworkLayer networkLayer;

    private Role role = Role.Receiver;
    
    private Map<Integer, Integer[]> packets;

    @Override
    public void run() {
        try {
            Utils.Timeout.Stop();
        } catch (Exception e) {
            
        }
        if (this.role == Role.Receiver) {
            this.receiverConnect();
        }
        if (this.role == Role.Sender) {
            this.senderConnect();
        }
    }
/////////////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void setNetworkLayer(NetworkLayer networkLayer) {
        this.networkLayer = networkLayer;
    }

    @Override
    public void TimeoutElapsed(Object tag) {
        this.brake = true;
        try {
            Utils.Timeout.Stop();
        } catch (Exception e) {
        }
        if(this.role == Role.Receiver){
            switch(state){
                case 1: this.receiverConnect(); break;
                case 2: this.receiverFix(); break;
                case 3: this.receiverFix(); break;
                case 4: this.receiverQuit(); break;
                default: this.receiverQuit(); break;
            }
        } else if (this.role == Role.Sender) {
            switch(state){
                case 1: this.senderConnect(); break;
                case 2: this.senderWaitConfirm(); break;
                case 3: this.senderWaitConfirm(); break;
                case 4: this.senderQuit(); break;
            }
        }
    }

    public void senderConnect(){
        this.state = 1;
        System.out.println("Connecting.......");
        
        boolean connected = false;
        this.packets = Packets.makePackets(Utils.getFileContents());
        
        Utils.Timeout.SetTimeout(500L, this, null);
        Utils.Timeout.Start();
        Integer[] packet = new Integer[4];
        int size = this.packets.size();
        int xor = size ^ (size >>> 8);
        int sum = size + (size >>> 8) + xor;
        packet[0] = sum;
        packet[1] = xor;
        packet[2] = size >>> 8;
        packet[3] = size;
        networkLayer.sendPacket(packet);
        System.out.println("Sent handshake");
        while (!connected && !brake) {
            packet = networkLayer.receivePacket();
            if (packet != null) {
                connected = true;
            }
        }
        try {Utils.Timeout.Stop();} catch (Exception e){}
        if(!brake) senderSend();
    }
    
    public void senderSend(){
        this.state = 2;
        for(Integer i: this.packets.keySet()) {
            networkLayer.sendPacket(this.packets.get(i));
        }
        this.senderWaitConfirm();
    }
    
    public void senderSend(Integer[] b){
        this.state = 2;
        System.out.println("Sending...");
        for (Integer i: b){
            networkLayer.sendPacket(this.packets.get(i));
        }
        System.out.println("Finished Sending");
        this.senderWaitConfirm();
    }
    
    public void senderWaitConfirm(){
        this.state = 2;
        Utils.Timeout.SetTimeout(1500L, this, null);
        Utils.Timeout.Start();
        Integer[] packet = networkLayer.receivePacket();
        while(!brake && packet == null){
            networkLayer.receivePacket();
        }
        if (packet == null) {
            return;
        }
        if (packet.length == 0) {
            this.senderQuit();
        } else if (Packets.checkPacket(packet)) {
            Integer[] lostPackets = new Integer[(packet.length / 2) - 1];
            for (int i = 0; i < (packet.length / 2) - 1; i++) {
                lostPackets[i] = packet[i * 2 + 2] * 256 + packet[i * 2 + 3];
            }
            this.senderSend(lostPackets);
        }
    }
    
    public void senderQuit(){
        this.state = 4;
        networkLayer.sendPacket(new Integer[0]);
    }
    
    public void receiverConnect(){
        this.state = 1;
        System.out.println("Start connection");
        boolean connected = false;
        Utils.Timeout.SetTimeout(500L, this, null);
        Utils.Timeout.Start();
        while(!connected && !brake){
            Integer[] packet = networkLayer.receivePacket();
            if(packet != null && Packets.checkPacket(packet)){
                this.amount = Packets.getIndex(packet);
                networkLayer.sendPacket(new Integer[0]);
                connected = true;
            }
        }
        if(!brake) {
            try {Utils.Timeout.Stop();} catch (Exception e){}
            this.receiverReceive();
        }
    }
    
    public void receiverReceive(){
        this.state = 2;
        if (this.packets.size() == amount){
            this.receiverQuit();
        }
        System.out.println("Start receiving");
        Utils.Timeout.SetTimeout(500L, this, null);
        Utils.Timeout.Start();
        while (!brake) {
            Integer[] packet = networkLayer.receivePacket();
            if (packet == null) {
                continue;
            }
            if (packet.length == 0) {
                this.receiverReceive();
            }
            if (!Packets.checkPacket(packet)) {
                continue;
            }
            this.packets.put(Packets.getIndex(packet), packet);
        }
    }
    
    public void receiverFix(){
        List<Integer> missing = new ArrayList<Integer>();
        for (int i = 0; i < amount; i++) {
            if (!packets.containsKey(i)) {
                missing.add(i);
            }
        }
        Integer[] sending = new Integer[missing.size() * 2 + 2];
        int sum = 0;
        int xor = 0;
        for (Integer i: missing) {
            sum += i + i >>> 8;
            xor ^= i + i >>> 8;
            sending[i * 2 + 2] = i;
            sending[i * 2 + 3] = i >>> 8;
        }
        sum += xor;
        sending[0] = sum;
        sending[1] = xor;
        networkLayer.sendPacket(sending);
        receiverReceive();
    }
    
    public void receiverQuit(){
        this.state = 4;
        Utils.Timeout.SetTimeout(500L, this, null);
        Utils.Timeout.Start();
        networkLayer.sendPacket(new Integer[0]);
        while(!brake){
            Integer[] packet = networkLayer.receivePacket();
            if (packet != null && packet.length == 0) {
                this.receiverFinish();
            }
        }
    }
    
    public void receiverFinish(){
        Integer[] fileContents = new Integer[0];
        for (int i = 0; i < amount; i++) {
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
    
    public enum Role {
        Sender, Receiver
    }
}
