package client;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32;

import javax.xml.bind.*;

/*
 * 
 * DO NOT EDIT
 * 
 */
/**
 * 
 * Class for maintainging communications with the challenge server
 * 
 * @author Jaco ter Braak, Twente University
 * @version 23-01-2014
 * 
 *          DO NOT EDIT
 */
public class DRDTChallengeClient implements Runnable {
	private static String protocolString = "RDTCHALLENGE/2.0";

	// server address
	private String host;

	// server port
	private int port;

	// student group ID
	private int groupId;

	// student group password
	private String password;

	// thread for handling server messages
	private Thread eventLoopThread;

	// server socket
	private Socket socket;

	// scanner over socket input stream
	private Scanner inputScanner;

	// socket output stream
	private PrintStream outputStream;

	// currently pending control message from server
	private String currentControlMessage = null;

	// whether the simulation was started
	private boolean simulationStarted = false;

	// whether the simulation is finished
	private boolean simulationFinished = false;

	// input packet buffer
	private List<Integer[]> inputPacketBuffer = new ArrayList<Integer[]>();
	private final ReentrantLock inputPacketBufferLock = new ReentrantLock();

	// output packet buffer
	private List<Integer[]> outputPacketBuffer = new ArrayList<Integer[]>();
	private final ReentrantLock outputPacketBufferLock = new ReentrantLock();

	// challenge string
	private byte[] challenge;

	/**
	 * Constructs the client and connects to the server.
	 * 
	 * @param groupId
	 *            The group Id
	 * @param password
	 *            Password for the group
	 * @throws IOException
	 *             if the connection failed
	 * @throws InterruptedException
	 *             if the operation was interrupted
	 */
	public DRDTChallengeClient(String serverAddress, int serverPort,
			int groupId, String password) throws IOException,
			InterruptedException {
		Utils.Timeout.Start();

		if (password == "changeme") {
			throw new IllegalArgumentException(
					"Please change the default password");
		}

		this.host = serverAddress;
		this.port = serverPort;
		this.groupId = groupId;
		this.password = password;

		eventLoopThread = new Thread(this, "Event Loop Thread");

		// connect to the server. Throws IOException if failure
		connect();
	}

	/**
	 * Connects to the challenge server
	 * 
	 * @throws IOException
	 *             if the connection failed
	 */
	private void connect() throws IOException, InterruptedException {
		try {
			// Open comms
			socket = new Socket(host, port);
			inputScanner = new Scanner(new BufferedInputStream(
					socket.getInputStream()));
			outputStream = new PrintStream(new BufferedOutputStream(
					socket.getOutputStream()));

			if (!getControlMessageBlocking().equals("REGISTER")) {
				throw new ProtocolException(
						"Did not get expected hello from server");
			}
			clearControlMessage();

			// register
			sendControlMessage("REGISTER " + this.groupId + " " + this.password);

			String reply = getControlMessageBlocking();
			if (!reply.equals("OK")) {
				String reason = reply.substring(reply.indexOf(' ') + 1);
				throw new ProtocolException("Could not register with server: "
						+ reason);
			}
			clearControlMessage();

			// start handling messages
			eventLoopThread.start();

		} catch (IOException e) {
			throw e;
		} catch (InterruptedException e) {
			throw e;
		}
	}

	/**
	 * Reqests a simulation start from the server
	 */
	public void requestStart() {
		if (!simulationStarted) {
			sendControlMessage("START");
		}
	}

	/**
	 * Starts the simulation
	 */
	public void start() {
		if (!simulationStarted) {
			simulationStarted = true;
		}
	}

	/**
	 * @return whether the simulation has been started
	 */
	public boolean isSimulationStarted() {
		return simulationStarted;
	}

	/**
	 * @return whether the simulation has finished
	 */
	public boolean isSimulationFinished() {
		return simulationFinished;
	}

	/**
	 * @return whether the output buffer is empty
	 */
	public boolean isOutputBufferEmpty() {
		return this.outputPacketBuffer.size() == 0;
	}

	/**
	 * Stops the client, and disconnects it from the server.
	 */
	public void stop() {
		Utils.Timeout.Stop();

		try {
			socket.setTcpNoDelay(true);

			// upload file checksum
			FileInputStream checksumInput = null;
			long checksumInputLength = 0;
			String fileType = "";

			File inputFile = new File("rdtcInput.jpg");
			File outputFile = new File("rdtcOutput." + Utils.getProcessId()
					+ ".jpg");
			if (outputFile.exists()) {
				try {
					checksumInput = new FileInputStream(outputFile);
					fileType = "OUT";
				} catch (FileNotFoundException e) {
				}
				checksumInputLength = outputFile.length();
			} else if (inputFile.exists()) {
				try {
					checksumInput = new FileInputStream(inputFile);
					fileType = "IN";
				} catch (FileNotFoundException e) {
				}
				checksumInputLength = inputFile.length();
			}

			if (checksumInput != null) {
				byte[] checksumInputContent = new byte[(int) checksumInputLength
						+ this.challenge.length];
				try {
					checksumInput.read(checksumInputContent,
							this.challenge.length, (int) checksumInputLength);

				} catch (IOException e) {
					e.printStackTrace();
				}
				System.arraycopy(this.challenge, 0, checksumInputContent, 0,
						this.challenge.length);

				CRC32 crc = new CRC32();
				crc.update(checksumInputContent);
				long checksum = crc.getValue();
				this.sendControlMessage("CHECKSUM " + fileType + " " + checksum);
			}

			// stop simulation
			simulationStarted = false;
			simulationFinished = true;

			// stop the message loop
			eventLoopThread.interrupt();
			try {
				eventLoopThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// close comms
			sendControlMessage("CLOSED");
			socket.getOutputStream().flush();
			Thread.sleep(1000);
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Handles communication between the server and the protocol implementation
	 */
	public void run() {
		boolean stopThread = false;
		while (!stopThread && !simulationFinished) {
			try {
				String message = getControlMessageBlocking();
				String[] splitMessage = message.split(" ");

				if (splitMessage.length > 0
						&& splitMessage[0].startsWith("FAIL")) {
					if (message.split(" ").length > 1) {
						System.err.println("Failure: "
								+ message.substring(message.indexOf(' ') + 1));
					}
					clearControlMessage();
					stopThread = true;
					simulationStarted = false;
					simulationFinished = true;

				} else if (splitMessage.length > 1
						&& splitMessage[0].startsWith("START")) {
					// start the simulation
					simulationStarted = true;
					this.challenge = DatatypeConverter
							.parseBase64Binary(splitMessage[1]);

				} else if (splitMessage.length > 0
						&& splitMessage[0].startsWith("SLOT")) {
					// We got offered a slot by the server to send a packet in.
					boolean transmitted = false;
					if (simulationStarted) {
						if (this.outputPacketBuffer.size() > 0) {
							try {
								outputPacketBufferLock.lock();
								if (this.outputPacketBuffer.size() > 0) {
									// if there are packets available, send one
									Integer[] packetContentsInIntegers = this.outputPacketBuffer
											.remove(0);
									// convert 32-bit integers to 8-bit bytes
									byte[] packetContents = new byte[packetContentsInIntegers.length];
									for (int i = 0; i < packetContentsInIntegers.length; i++) {
										packetContents[i] = (byte) ((packetContentsInIntegers[i] & 0x000000ff));
									}
									this.sendControlMessage("TRANSMIT "
											+ DatatypeConverter
													.printBase64Binary(packetContents));
									transmitted = true;
								}
							} finally {
								outputPacketBufferLock.unlock();
							}
						}
					}
					
					if (!transmitted) {
						this.sendControlMessage("NOTRANSMIT");
					}

				} else if (splitMessage.length > 0
						&& splitMessage[0].startsWith("PACKET")) {
					// We received a packet from the server
					if (simulationStarted) {
						Integer[] packetContentsInIntegers;
						if (splitMessage.length > 1) {
							try {
								// convert base64 string to bytes
								byte[] packetContents = DatatypeConverter
										.parseBase64Binary(splitMessage[1]);
								// convert 8-bit bytes to 32-bit integers
								packetContentsInIntegers = new Integer[packetContents.length];
								for (int i = 0; i < packetContents.length; i++) {
									packetContentsInIntegers[i] = (packetContents[i] & 0x000000ff);
								}
							} catch (IllegalArgumentException e) {
								e.printStackTrace();
								packetContentsInIntegers = new Integer[0];
							}
						} else {
							packetContentsInIntegers = new Integer[0];
						}

						try {
							this.inputPacketBufferLock.lock();
							this.inputPacketBuffer
									.add(packetContentsInIntegers);
						} finally {
							this.inputPacketBufferLock.unlock();
						}
					}

				} else if (message.startsWith("CLOSED")) {
					simulationStarted = false;
					simulationFinished = true;

					System.err.println("Simulation aborted!");
					if (splitMessage.length > 1) {
						System.err.println("Reason: "
								+ message.substring(message.indexOf(' ') + 1));
					}
					Utils.Timeout.Stop();
				} else if (message.startsWith("FINISH")) {
					simulationStarted = false;
					simulationFinished = true;

					System.out
							.println("Simulation finished! Check your performance on the server web interface.");
					Utils.Timeout.Stop();
				}

				clearControlMessage();

				Thread.sleep(1);
			} catch (ProtocolException e) {
			} catch (InterruptedException e) {
				stopThread = true;
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Waits for a control message from the server
	 * 
	 * @return the message
	 * @throws ProtocolException
	 *             if a corrupt message was received
	 */
	private String getControlMessageBlocking() throws ProtocolException,
			InterruptedException, IOException {
		try {
			// Block while waiting for message
			String controlMessage = getControlMessage();
			while (controlMessage == null) {
				Thread.sleep(10);
				controlMessage = getControlMessage();
			}

			return controlMessage;
		} catch (Exception e) {
			throw e;
		}

	}

	public Integer[] receivePacket() {
		if (this.inputPacketBuffer.size() > 0) {
			try {
				this.inputPacketBufferLock.lock();
				if (this.inputPacketBuffer.size() > 0) {
					return inputPacketBuffer.remove(0);
				}
			} finally {
				this.inputPacketBufferLock.unlock();
			}
		}
		return null;
	}

	public void sendPacket(Integer[] packet) throws IllegalArgumentException {
		if (packet == null) {
			throw new IllegalArgumentException("packet == null");
		}
		for (int i = 0; i < packet.length; i++) {
			if (packet[i] == null) {
				throw new IllegalArgumentException("packet[" + i + "] == null");
			}
		}

		try {
			this.outputPacketBufferLock.lock();
			this.outputPacketBuffer.add(packet);
		} finally {
			this.outputPacketBufferLock.unlock();
		}
	}

	/**
	 * Removes the first message from the queue Call this when you have
	 * processed a message
	 */
	private void clearControlMessage() {
		this.currentControlMessage = null;
	}

	/**
	 * Obtains a message from the server, if any exists.
	 * 
	 * @return the message, null if no message was present
	 * @throws IOException
	 */
	private synchronized String getControlMessage() throws IOException {
		if (!simulationFinished) {
			if (this.currentControlMessage == null
					&& inputScanner.hasNextLine()) {
				String line = inputScanner.nextLine();
				if (line.startsWith(protocolString)) {
					this.currentControlMessage = line.substring(protocolString
							.length() + 1);
				} else {
					throw new ProtocolException("Protocol mismatch with server");
				}
			}
		}
		return this.currentControlMessage;
	}

	/**
	 * Sends a message to the server
	 * 
	 * @param message
	 */
	private void sendControlMessage(String message) {
		outputStream.print(protocolString + " " + message + "\n");
		outputStream.flush();
	}
}
