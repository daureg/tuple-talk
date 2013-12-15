package chat;

import tuplespaces.TupleSpace;

public class ChatListener {
	private int lastRead;
	private boolean stillListening;
	private final int rows;
	private final String channel;
	private final TupleSpace t;

	public ChatListener(TupleSpace t, String channel) {
		this.channel = channel;
		this.t = t;
		final String[] state = t.read("state", null);
		rows = Integer.parseInt(state[1]);
		lastRead = -1;
		stillListening = true;
		/*
		 * We get to the channel to add an unread user to every reachable
		 * message at the point where we join.
		 */
		String[] channelState = t.get(channel, null, null, null, null, null);
		final int lastWrittenId = Integer.parseInt(channelState[4]);
		final int oldestId = Integer.parseInt(channelState[5]);
		for (int i = oldestId; i <= lastWrittenId; i++) {
			String[] msgInfo = t.get(channel + "_msg", String.valueOf(i), null,
					null);
			msgInfo[3] = String.valueOf(Integer.valueOf(msgInfo[3]) + 1);
			t.put(msgInfo);
		}
		channelState[1] = String.valueOf(Integer.valueOf(channelState[1]) + 1);
		t.put(channelState);
	}

	public String getNextMessage() {
		String[] request = new String[] { channel, null, null, "true", null,
				null };
		String[] channelState = new String[6];
		/*
		 * If it's our first time, we need to know where to start reading (it's
		 * done here because maybe it has changed since we connected (in the
		 * constructor)).
		 */
		if (lastRead == -1) {
			channelState = t.read(request);
			lastRead = Integer.parseInt(channelState[5]) - 1;
		}
		/* Now we get the next message */
		final int msgId = lastRead + 1;
		String[] msgInfo = t.get(channel + "_msg", String.valueOf(msgId), null,
				null);
		if (!stillListening) {
			t.put(msgInfo);
			return "";
		}
		final String msg = msgInfo[2];
		int unreadCount = Integer.valueOf(msgInfo[3]);
		unreadCount--;
		/* Acknowledge that we read it and put it back */
		msgInfo[3] = String.valueOf(unreadCount);
		t.put(msgInfo);
		if (unreadCount <= 0) {
			/* Let's see if we can do more, i.e remove a message */
			channelState = t.get(request);
			int oldestId = Integer.parseInt(channelState[5]);
			final int lastWrittenId = Integer.parseInt(channelState[4]);
			final boolean notFull = Boolean.valueOf(channelState[2]);
			final String[] oldestMsgRequest = new String[] { channel + "_msg",
					String.valueOf(oldestId), null, null };
			if (notFull) {
				/* no need to do so. */
				t.put(channelState);
			} else {
				if (msgId != oldestId) {
					/* get the oldest message */
					msgInfo = t.get(oldestMsgRequest);
					unreadCount = Integer.valueOf(msgInfo[3]);
					if (unreadCount > 0) {
						/* if not every listeners has read it, we put it back */
						t.put(msgInfo);
					} else {
						/* hopefully it's not the case so we have removed it */
						oldestId++;
					}
				} else {
					/*
					 * The current message was the oldestOne so we already know
					 * it have been read by everybody.
					 */
					msgInfo = t.get(oldestMsgRequest);
					oldestId++;
				}
				/* update channel state after potential removal */
				channelState[2] = String
						.valueOf(lastWrittenId - oldestId + 1 < rows);
				channelState[3] = String.valueOf(lastWrittenId >= oldestId);
				channelState[5] = String.valueOf(oldestId);
				t.put(channelState);
			}
		}
		lastRead = msgId;
		return msg;
	}

	public void closeConnection() {
		stillListening = false;
		String[] channelState = t.get(channel, null, null, null, null, null);
		final int lastWrittenId = Integer.parseInt(channelState[4]);
		/*
		 * max is kind of hack for the case when we leave without reading any
		 * message: there, lastRead+1=0 but there is non such message.
		 */
		for (int i = Math.max(lastRead + 1, 1); i <= lastWrittenId; i++) {
			String[] msgInfo = t.get(channel + "_msg", String.valueOf(i), null,
					null);
			msgInfo[3] = String.valueOf(Integer.valueOf(msgInfo[3]) - 1);
			t.put(msgInfo);
		}
		channelState[1] = String.valueOf(Integer.valueOf(channelState[1]) - 1);
		t.put(channelState);
		/*
		 * A way to stop a listener blocking in getNextMessage quickly would be
		 * to write a message saying "%d has left the channel". But it's not
		 * mentioned in the spec and it would use one message out the rows
		 * available in the buffer for nothing. As said in LocalTupleSpace.find,
		 * we could also interrupt it.
		 */
		// Thread.currentThread().interrupt();
	}
}