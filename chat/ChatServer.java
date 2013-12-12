package chat;

import java.util.HashMap;
import java.util.HashSet;

import tuplespaces.TupleSpace;

public class ChatServer {
	private final int rows;
	// private final int nextId;
	private final TupleSpace t;
	private final String[] channelNames;
//	private final HashMap<String, Cleaner> cleaners;

	public ChatServer(TupleSpace t, int rows, String[] channelNames) {
		this.t = t;
		this.rows = rows;
		// this.nextId = 0;
		t.put(new String[] { "state", String.valueOf(rows), "0" });
		int numChannel = channelNames.length;
		t.put(new String[] { "numChannel", String.valueOf(numChannel) });
		String[] channelsInfo = new String[channelNames.length + 1];
		channelsInfo[0] = "channelNames";
		System.arraycopy(channelNames, 0, channelsInfo, 1, numChannel);
		this.channelNames = new String[numChannel];
		System.arraycopy(channelNames, 0, this.channelNames, 0, numChannel);
		t.put(channelsInfo);
//		cleaners = new HashMap<String, Cleaner>(numChannel);
		for (int i = 0; i < numChannel; i++) {
			t.put(new String[] { channelNames[i], "", "true", "false", "0", "1" });
			t.put(new String[] { channelNames[i] + "_msg", "0", "", "" });
//			cleaners.put(channelNames[i], new Cleaner(t, channelNames[i]));
//			cleaners.get(channelNames[i]).start();
		}
	}

	public ChatServer(TupleSpace t) {
		System.out.println("clone CS");
		this.t = t;
		String[] state = t.read(new String[] { "state", null, null });
		this.rows = Integer.parseInt(state[1]);
		// this.nextId = Integer.parseInt(state[2]);
		String[] tmp = t.read(new String[] { "numChannel", null });
		final int numChannel = Integer.parseInt(tmp[1]);
		channelNames = new String[numChannel];
		String[] pattern = new String[numChannel + 1];
		pattern[0] = "channelNames";
		for (int i = 0; i < numChannel; i++) {
			pattern[i + 1] = null;
		}
		System.arraycopy(t.read(pattern), 1, channelNames, 0, numChannel);
//		channelNames = t.read(pattern);
//		cleaners = new HashMap<String, Cleaner>(numChannel);
//		for (int i = 0; i < numChannel; i++) {
//			cleaners.put(channelNames[i], new Cleaner(t, channelNames[i]));
//			cleaners.get(channelNames[i]).start();
//		}
	}

	public String[] getChannels() {
		return channelNames;
	}

	public void writeMessage(String channel, String message) {
//		System.out.format("Want to write '%s' to channel '%s'\n", message,
//				channel);
//		Object cleaner = cleaners.get(channel).callMeMaybe;
		// synchronized (cleaner) {
		// cleaner.notify();
		// }
		String[] channelState = t.get(new String[] { channel, null, null, null,
				null, null });
		boolean full = !Boolean.valueOf(channelState[2]);
		if (full) {
			t.put(channelState); // TODO: avoid this
			tryToRemoveOldestMessage(t, rows, channel);
			// TODO: and that
			channelState = t.get(new String[] { channel, null, "true", null,
					null, null });
		}
		// channelState(channelState);
		int lastWrittenId = Integer.parseInt(channelState[4]);
		final int oldestId = Integer.parseInt(channelState[5]);
		lastWrittenId++;
		t.put(new String[] { channel + "_msg", String.valueOf(lastWrittenId),
				message, "" });
		channelState[2] = String.valueOf(lastWrittenId - oldestId + 1 < rows);
		channelState[3] = "true";
		channelState[4] = String.valueOf(lastWrittenId);
		t.put(channelState);
//		System.out.format("Wrote '%s' to channel '%s':\n", message, channel);
//		channelState(channelState);
//		System.out.format("Full: %b and notFull: %s\n", lastWrittenId
//				- oldestId + 1 == rows, channelState[2]);
	}

	public ChatListener openConnection(String channel) {
		return new ChatListener(t, channel);
	}

	public static void channelState(String[] s) {
		System.out.format(
				"'%s': notFull: %s\tnotEmpty: %s\t msg from %s to %s\t%s\n",
				s[0], s[2], s[3], s[5], s[4], s[1]);
	}

	/**
	 * @param t
	 * @param rows
	 * @param channel
	 * @return true if it has removed something
	 */
	public static boolean tryToRemoveOldestMessage(TupleSpace t, int rows,
			String channel) {
		boolean removed = false;
		String[] channelState = t.get(new String[] { channel, null, null, null,
				null, null });
		Boolean notFull = Boolean.valueOf(channelState[2]);
		Boolean empty = !Boolean.valueOf(channelState[3]);
		if (notFull || empty) {
			t.put(channelState);
			return removed;
		}
		final int lastWrittenId = Integer.parseInt(channelState[4]);
		int oldestId = Integer.parseInt(channelState[5]);
		final HashSet<Integer> listeners = ChatListener
				.stringListToIntSet(channelState[1]);
		final String[] msgInfo = t.get(new String[] { channel + "_msg",
				String.valueOf(oldestId), null, null });
		final HashSet<Integer> readBy = ChatListener
				.stringListToIntSet(msgInfo[3]);
		if (!readBy.containsAll(listeners)) {
			t.put(msgInfo);
		} else {
			removed = true;
			oldestId++;
		}
		channelState[2] = String.valueOf(lastWrittenId - oldestId  + 1 < rows);
		channelState[3] = String.valueOf(lastWrittenId >= oldestId);
		channelState[5] = String.valueOf(oldestId);
		t.put(channelState);
		return removed;
	}

	class Cleaner extends Thread {
		private final TupleSpace t;
		private final String channel;
		public final Object callMeMaybe = new Object();

		Cleaner(TupleSpace t, String channel) {
			super("Cleaner");
			this.t = t;
			this.channel = channel;
		}

		@Override
		public void run() {
			String[] channelState = new String[6];
			while (true) {
				boolean notFull = false;
				synchronized (callMeMaybe) {
					while (!notFull) {
						try {
							callMeMaybe.wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							// e.printStackTrace();
							continue;
						}
						channelState = t.read(new String[] { channel, null,
								"false", null, null, null });
						notFull = Boolean.valueOf(channelState[2]);
					}
					ChatServer.tryToRemoveOldestMessage(t, rows, channel);
				}
			}
		}
	}
}
