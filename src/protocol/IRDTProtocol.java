package protocol;

import client.ITimeoutEventHandler;
import client.NetworkLayer;

/**
 * 
 * @author Jaco ter Braak, Twente University
 * @version 03-01-2015
 *
 * Specifies a data transfer protocol.
 *
 * DO NOT EDIT
 */
public interface IRDTProtocol extends ITimeoutEventHandler {
	
	/**
	 * Run the protocol. Called from the framework
	 */
	public void run();
	
	/**
	 * Sets the network layer implementation. This network layer is used for transmitting and receiving packets.
	 * @param networkLayer
	 */
	public void setNetworkLayer(NetworkLayer networkLayer);
}
