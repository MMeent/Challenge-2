package client;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.management.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Helper utilities. Supplied for convenience.
 * 
 * @author Jaco ter Braak, Twente University
 * @version 11-01-2014
 */
public class Utils {
	private Utils() {
	}

	/**
	 * Helper method to get the current process ID
	 * 
	 * @author Jaco ter Braak, Twente University
	 * @version 04-01-2015
	 * @return process id
	 */
	public static int getProcessId() {
		final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
		final int index = jvmName.indexOf('@');

		if (index < 1) {
			return 0;
		}

		try {
			return Integer.parseInt(jvmName.substring(0, index));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * @return the array of integers, representing the contents of the file to
	 *         transmit
	 */
	public static Integer[] getFileContents() {
		File fileToTransmit = new File("rdtcInput.jpg");
		FileInputStream fileStream = null;
		try {
			fileStream = new FileInputStream(fileToTransmit);
			Integer[] fileContents = new Integer[(int) fileToTransmit.length()];

			for (int i = 0; i < fileContents.length; i++) {
				int nextByte = fileStream.read();
				if (nextByte == -1) {
					throw new Exception("File size is smaller than reported");
				}

				fileContents[i] = nextByte;
			}
			return fileContents;
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.err.println(e.getStackTrace());
			return null;
		} finally {
			try {
				fileStream.close();
			} catch (IOException e) {
				System.err.println(e.getMessage());
				System.err.println(e.getStackTrace());
			}
		}
	}

	/**
	 * Writes the contents of the fileContents array to the file
	 * 
	 * @param fileContents
	 */
	public static void setFileContents(Integer[] fileContents) {
		File fileToWrite = new File("rdtcOutput." + Utils.getProcessId()
				+ ".jpg");
		FileOutputStream fileStream = null;
		try {
			fileStream = new FileOutputStream(fileToWrite);
			for (int i = 0; i < fileContents.length; i++) {
				fileStream.write(fileContents[i]);
			}
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.err.println(e.getStackTrace());
		} finally {
			try {
				fileStream.close();
			} catch (IOException e) {
				System.err.println(e.getMessage());
				System.err.println(e.getStackTrace());
			}
		}
	}

	/**
	 * Helper class for setting timeouts. Supplied for convenience.
	 * 
	 * @author Jaco ter Braak, Twente University
	 * @version 11-01-2014
	 */
	public static class Timeout implements Runnable {
		private static Map<Date, Map<ITimeoutEventHandler, List<Object>>> eventHandlers = new HashMap<Date, Map<ITimeoutEventHandler, List<Object>>>();
		private static Thread eventTriggerThread;
		private static boolean started = false;
		private static ReentrantLock lock = new ReentrantLock();

		/**
		 * Starts the helper thread
		 */
		public static void Start() {
			if (started)
				throw new IllegalStateException("Already started");
			started = true;
			eventTriggerThread = new Thread(new Timeout());
			eventTriggerThread.start();
		}

		/**
		 * Stops the helper thread
		 */
		public static void Stop() throws IllegalStateException{
			if (!started)
				throw new IllegalStateException(
						"Not started or already stopped");
			eventTriggerThread.interrupt();
			try {
				eventTriggerThread.join();
			} catch (InterruptedException e) {
			}
			started = false;
		}

		/**
		 * Set a timeout
		 * 
		 * @param millisecondsTimeout
		 *            the timeout interval, starting now
		 * @param handler
		 *            the event handler that is called once the timeout elapses
		 */
		public static void SetTimeout(long millisecondsTimeout,
				ITimeoutEventHandler handler, Object tag) {
			Date elapsedMoment = new Date();
			elapsedMoment
					.setTime(elapsedMoment.getTime() + millisecondsTimeout);

			lock.lock();
			if (!eventHandlers.containsKey(elapsedMoment)) {
				eventHandlers.put(elapsedMoment,
						new HashMap<ITimeoutEventHandler, List<Object>>());
			}
			if (!eventHandlers.get(elapsedMoment).containsKey(handler)) {
				eventHandlers.get(elapsedMoment).put(handler,
						new ArrayList<Object>());
			}
			eventHandlers.get(elapsedMoment).get(handler).add(tag);
			lock.unlock();
		}

		/**
		 * Do not call this
		 */
		@Override
		public void run() {
			boolean runThread = true;
			ArrayList<Date> datesToRemove = new ArrayList<Date>();
			HashMap<ITimeoutEventHandler, List<Object>> handlersToInvoke = new HashMap<ITimeoutEventHandler, List<Object>>();
			Date now;

			while (runThread) {
				try {
					now = new Date();

					// If any timeouts have elapsed, trigger their handlers
					lock.lock();

					for (Date date : eventHandlers.keySet()) {
						if (date.before(now)) {
							datesToRemove.add(date);
							for (ITimeoutEventHandler handler : eventHandlers
									.get(date).keySet()) {
								if (!handlersToInvoke.containsKey(handler)) {
									handlersToInvoke.put(handler,
											new ArrayList<Object>());
								}
								for (Object tag : eventHandlers.get(date).get(
										handler)) {
									handlersToInvoke.get(handler).add(tag);
								}
							}
						}
					}

					// Remove elapsed events
					for (Date date : datesToRemove) {
						eventHandlers.remove(date);
					}
					datesToRemove.clear();

					lock.unlock();

					// Invoke the event handlers outside of the lock, to prevent
					// deadlocks
					for (ITimeoutEventHandler handler : handlersToInvoke
							.keySet()) {
						for (Object tag : handlersToInvoke.get(handler)) {
							handler.TimeoutElapsed(tag);
						}
					}
					handlersToInvoke.clear();

					Thread.sleep(1);
				} catch (InterruptedException e) {
					runThread = false;
				}
			}

		}
	}
}
