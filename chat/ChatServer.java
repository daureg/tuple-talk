package chat;

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
			t.put(new String[] { channelNames[i], "0", "true", "false", "0",
					"1" });
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
		System.out.format("Want to write '%s' to channel '%s' and got it:\n", message,
				channel);
		channelState(channelState);
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
				message, channelState[1] });
		channelState[2] = String.valueOf(lastWrittenId - oldestId + 1 < rows);
		channelState[3] = "true";
		channelState[4] = String.valueOf(lastWrittenId);
		t.put(channelState);
		System.out.format("%d:Wrote (%d, '%s') to channel '%s':\n",
				System.nanoTime(), lastWrittenId, message, channel);
		channelState(channelState);
	}

	public ChatListener openConnection(String channel) {
		return new ChatListener(t, channel);
	}

	public static void channelState(String[] s) {
		System.out
				.format("'%s': notFull: %s\tnotEmpty: %s\t msg from %s to %s\tlisten by: %s\n",
						s[0], s[2], s[3], s[5], s[4], s[1]);
	}

	/**
	 * @param t
	 * @param rows
	 * @param channelState
	 * @return true if it has removed something and modify channelState in place
	 */
	public static boolean tryToRemoveOldestMessage(TupleSpace t, int rows,
			String[] channelState) {
		boolean notFull = Boolean.valueOf(channelState[2]);
		boolean empty = !Boolean.valueOf(channelState[3]);
		if (notFull || empty) {
			return false;
		}
		final String channel = channelState[0];
		final int lastWrittenId = Integer.parseInt(channelState[4]);
		int oldestId = Integer.parseInt(channelState[5]);
		System.out.format("writer try to remove %d from '%s'\n", oldestId, channel);
		final String[] msgInfo = t.get(new String[] { channel + "_msg",
				String.valueOf(oldestId), null, null });
		final int unreadCount = Integer.valueOf(msgInfo[3]);
		System.out.println("which have not been read by " + msgInfo[3]);
		if (unreadCount > 0) {
			t.put(msgInfo);
			return false;
		}
		System.out.println("A writer removed msg id: " + oldestId);
		oldestId++;
		channelState[2] = String.valueOf(lastWrittenId - oldestId + 1 < rows);
		channelState[3] = String.valueOf(lastWrittenId >= oldestId);
		channelState[5] = String.valueOf(oldestId);
		return true;
	}
}
