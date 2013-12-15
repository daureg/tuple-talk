package chat;

import tuplespaces.TupleSpace;

public class ChatServer {
	private final int rows;
	private final TupleSpace t;
	private final String[] channelNames;

	public ChatServer(TupleSpace t, int rows, String[] channelNames) {
		this.t = t;

		this.rows = rows;
		t.put("state", String.valueOf(rows));

		final int numChannel = channelNames.length;
		t.put("numChannel", String.valueOf(numChannel));

		String[] channelsInfo = new String[numChannel + 1];
		channelsInfo[0] = "channelNames";
		System.arraycopy(channelNames, 0, channelsInfo, 1, numChannel);
		t.put(channelsInfo);

		for (int i = 0; i < numChannel; i++) {
			t.put(channelNames[i], "0", "true", "false", "0", "1");
		}

		this.channelNames = new String[numChannel];
		System.arraycopy(channelNames, 0, this.channelNames, 0, numChannel);
	}

	public ChatServer(TupleSpace t) {
		this.t = t;
		final String[] state = t.read("state", null);
		this.rows = Integer.parseInt(state[1]);

		final String[] num = t.read("numChannel", null);
		final int numChannel = Integer.parseInt(num[1]);

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
		String[] channelState = t.get(channel, null, null, null, null, null);
		boolean full = !Boolean.valueOf(channelState[2]);
		/*
		 * We get the channel unconditionally. But if it's full, we try to
		 * remove the oldest message, provided that every listeners has already
		 * read it.
		 */
		if (full && !tryToRemoveOldestMessage(t, rows, channelState)) {
			/*
			 * If it's not possible, we put the channel back to allow a listener
			 * to do it instead and wait until it's the case (i.e. the channel
			 * is notFull anymore).
			 */
			t.put(channelState);
			channelState = t.get(channel, null, "true", null, null, null);
		}
		/*
		 * Once we get a valid channel, we just write the message and update the
		 * channel state.
		 */
		int lastWrittenId = Integer.parseInt(channelState[4]);
		final int oldestId = Integer.parseInt(channelState[5]);
		lastWrittenId++;
		t.put(channel + "_msg", String.valueOf(lastWrittenId), message,
				channelState[1]);
		channelState[2] = String.valueOf(lastWrittenId - oldestId + 1 < rows);
		channelState[3] = "true";
		channelState[4] = String.valueOf(lastWrittenId);
		t.put(channelState);
	}

	public ChatListener openConnection(String channel) {
		return new ChatListener(t, channel);
	}

	/*
	 * take the tuple channelState and update it if it managed to remove the
	 * oldest message (return true in that case, false otherwise)
	 */
	public static boolean tryToRemoveOldestMessage(TupleSpace t, int rows,
			String[] channelState) {
		boolean notFull = Boolean.valueOf(channelState[2]);
		boolean empty = !Boolean.valueOf(channelState[3]);
		if (notFull || empty) {
			// nothing to do
			return false;
		}
		final String channel = channelState[0];
		final int lastWrittenId = Integer.parseInt(channelState[4]);
		int oldestId = Integer.parseInt(channelState[5]);
		/* get the oldest and see how many listeners have not yet read it. */
		final String[] msgInfo = t.get(channel + "_msg",
				String.valueOf(oldestId), null, null);
		final int unreadCount = Integer.valueOf(msgInfo[3]);
		if (unreadCount > 0) {
			t.put(msgInfo);
			return false;
		}
		/* 0, we can get rid of it by not putting it back. */
		oldestId++;
		channelState[2] = String.valueOf(lastWrittenId - oldestId + 1 < rows);
		channelState[3] = String.valueOf(lastWrittenId >= oldestId);
		channelState[5] = String.valueOf(oldestId);
		return true;
	}
}