package chat;

import java.util.HashSet;

import tuplespaces.TupleSpace;

public class ChatServer {
	private final int rows;
	private final TupleSpace t;
	private final String[] channelNames;

	public ChatServer(TupleSpace t, int rows, String[] channelNames) {
		this.t = t;
		this.rows = rows;
		t.put(new String[] { "state", String.valueOf(rows), "0" });
		final int numChannel = channelNames.length;
		t.put(new String[] { "numChannel", String.valueOf(numChannel) });
		String[] channelsInfo = new String[channelNames.length + 1];
		channelsInfo[0] = "channelNames";
		System.arraycopy(channelNames, 0, channelsInfo, 1, numChannel);
		this.channelNames = new String[numChannel];
		System.arraycopy(channelNames, 0, this.channelNames, 0, numChannel);
		t.put(channelsInfo);
		for (int i = 0; i < numChannel; i++) {
			t.put(new String[] { channelNames[i], "", "true", "false", "0", "1" });
			t.put(new String[] { channelNames[i] + "_msg", "0", "", "" });
		}
	}

	public ChatServer(TupleSpace t) {
		this.t = t;
		String[] state = t.read(new String[] { "state", null, null });
		this.rows = Integer.parseInt(state[1]);
		String[] tmp = t.read(new String[] { "numChannel", null });
		final int numChannel = Integer.parseInt(tmp[1]);
		channelNames = new String[numChannel];
		String[] pattern = new String[numChannel + 1];
		pattern[0] = "channelNames";
		for (int i = 0; i < numChannel; i++) {
			pattern[i + 1] = null;
		}
		System.arraycopy(t.read(pattern), 1, channelNames, 0, numChannel);
	}

	public String[] getChannels() {
		return channelNames;
	}

	public void writeMessage(String channel, String message) {
		System.out.format("Want to write '%s' to channel '%s'\n", message,
				channel);
		String[] channelState = t.get(new String[] { channel, null, null, null,
				null, null });
		boolean full = !Boolean.valueOf(channelState[2]);
		if (full && !tryToRemoveOldestMessage(t, rows, channelState)) {
			t.put(channelState);
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
		System.out.format("%d:Wrote (%d, '%s') to channel '%s':\n",
				System.nanoTime(), lastWrittenId, message, channel);
		channelState(channelState);
		// System.out.format("Full: %b and notFull: %s\n", lastWrittenId
		// - oldestId + 1 == rows, channelState[2]);
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
	 * @param channelState
	 * @return true if it has removed something and modify channelState in place
	 */
	// public static boolean tryToRemoveOldestMessage(TupleSpace t, int rows,
	// String channel) {
	public static boolean tryToRemoveOldestMessage(TupleSpace t, int rows,
			String[] channelState) {
		// boolean removed = false;
		// String[] channelState = t.get(new String[] { channel, null, null,
		// null,
		// null, null });
		Boolean notFull = Boolean.valueOf(channelState[2]);
		Boolean empty = !Boolean.valueOf(channelState[3]);
		if (notFull || empty) {
			// t.put(channelState);
			return false;
		}
		final String channel = channelState[0];
		final int lastWrittenId = Integer.parseInt(channelState[4]);
		int oldestId = Integer.parseInt(channelState[5]);
		System.out.println("try to remove " + oldestId);
		final HashSet<Integer> listeners = ChatListener
				.stringListToIntSet(channelState[1]);
		final String[] msgInfo = t.get(new String[] { channel + "_msg",
				String.valueOf(oldestId), null, null });
		final HashSet<Integer> readBy = ChatListener
				.stringListToIntSet(msgInfo[3]);
		System.out.println("which have been read by " + msgInfo[3]);
		if (!readBy.containsAll(listeners)) {
			t.put(msgInfo);
			return false;
		}
		// else {
		// removed = true;
		System.out.println("A writer removed msg id: " + oldestId);
		oldestId++;
		// }
		channelState[2] = String.valueOf(lastWrittenId - oldestId + 1 < rows);
		channelState[3] = String.valueOf(lastWrittenId >= oldestId);
		channelState[5] = String.valueOf(oldestId);
		// t.put(channelState);
		return true;
	}
}
