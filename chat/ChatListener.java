package chat;

import tuplespaces.TupleSpace;

public class ChatListener {
	private int lastRead;
	private boolean stillListening;
	private final int rows;
	private final int myID;
	private final String channel;
	private final TupleSpace t;
	private int totalRead;

	public ChatListener(TupleSpace t, String channel) {
		this.channel = channel;
		this.t = t;
		String[] state = t.get("state", null, null);
		lastRead = -1;
		stillListening = true;
		rows = Integer.parseInt(state[1]);
		// TODO: don't need id (except for debug)
		myID = Integer.parseInt(state[2]);
		state[2] = String.valueOf(myID + 1);
		t.put(state);
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
		System.out.format("%d is now listening to '%s':\n", myID, channel);
		// ChatServer.channelState(channelState);
		t.put(channelState);
		totalRead = 0;
	}

	public String getNextMessage() {
		String[] request = new String[] { channel, null, null, "true", null,
				null };
		/*
		 * individual for every listeners. => write msg could post
		 * (channel_notification, listener_id, new_msg_id) The nice thing about
		 * tuple tailored to each listener is that it would be a good way to
		 * tell them to stop listening. A hack could be to post a message with
		 * the desired id but an invalid readBy (not very good because it's hard
		 * to ignore for other listeners. Maybe I could add a 'fake' last field
		 * in message (exactly the same)
		 */
		/* TODO: when we close connection, find a way to stop get calls */
		System.out
				.format("listener %d want to read %d from channel '%s' (after %d msg read):\n",
						myID, lastRead + 1, channel, totalRead);
		String[] channelState = new String[6];
		if (lastRead == -1) {
			channelState = t.get(request);
			lastRead = Integer.parseInt(channelState[5]) - 1;
			t.put(channelState);
		}
		final int msgId = lastRead + 1;
		String[] msgInfo = t.get(channel + "_msg", String.valueOf(msgId), null,
				null);
		if (!stillListening) {
			t.put(msgInfo);
			return "";
		}
		String msg = msgInfo[2];
		int unreadCount = Integer.valueOf(msgInfo[3]);
		unreadCount--;
		msgInfo[3] = String.valueOf(unreadCount);
		totalRead++;
		System.out.format(
				"listener %d just read (%d, '%s') out of channel '%s':\n",
				myID, msgId, msg, channel);
		t.put(msgInfo);
		if (unreadCount <= 0) {
			channelState = t.get(request);
			int oldestId = Integer.parseInt(channelState[5]);
			final int lastWrittenId = Integer.parseInt(channelState[4]);
			final boolean notFull = Boolean.valueOf(channelState[2]);
			final String[] oldestMsgRequest = new String[] { channel + "_msg",
					String.valueOf(oldestId), null, null };
			System.out.format(
					"everybody has read %d but is channel '%s' full: %b	\n",
					msgId, channel, !notFull);
			if (notFull) {
				/* Not much has changed, except that myId has read this msg. */
				t.put(channelState);
			} else {
				System.out
						.format("after reading %d, listener %d try to remove %d because channel '%s' is full\n",
								msgId, myID, oldestId, channel);
				/*
				 * Every current listeners has read this message and we need to
				 * make room. So it seems appropriate to not put it back. But
				 * what if it's not the oldest one? This would create a gap
				 * whereas we rely on msg id to be sequential. Thus I think we
				 * can either put it back and wait for someone to be the last
				 * reader of the oldest or maybe, to be faster, look at the
				 * oldest to see if it can be removed (actually that's better
				 * because then, we don't need to wait for another listener to
				 * join). Maybe we can continue to look at older message until
				 * we cannot remove them anymore (would be nice to prove that
				 * reader count is monotonically decreasing with msg id). In
				 * fact not, because we need to keep rows message if possible.
				 * So if we are full, we can remove only one at a time, right?
				 */
				if (msgId != oldestId) {
					// get the oldest one
					msgInfo = t.get(oldestMsgRequest);
					unreadCount = Integer.valueOf(msgInfo[3]);
					if (unreadCount > 0) {
						/*
						 * if not every listeners has read it, we put it back
						 * (so should we just t.read instead?)
						 */
						t.put(msgInfo);
					} else {
						/* hopefully it's not the case so we have removed it */
						System.out.format(
								"listener %d removed %d from channel '%s'\n",
								myID, oldestId, channel);
						oldestId++;
					}
				} else {
					/*
					 * TODO: the only difference with the if part is that I know
					 * that the oldest message has already been read by everyone
					 * so maybe they should merge.
					 */
					// we just remove the oldest one
					msgInfo = t.get(oldestMsgRequest);
					oldestId++;
					System.out.format(
							"listener %d removed %d from channel '%s'\n", myID,
							oldestId, channel);
				}
				// update channel state after potential removal
				channelState[2] = String
						.valueOf(lastWrittenId - oldestId + 1 < rows);
				/*
				 * TODO: we were full before (ie rows messages) and we remove at
				 * most one so can we really be empty now? (yes if rows==1)
				 */
				channelState[3] = String.valueOf(lastWrittenId >= oldestId);
				channelState[5] = String.valueOf(oldestId);
				t.put(channelState);
			}
		}
		lastRead = msgId;
		System.out.format(
				"%d: listener %d got (%d, '%s') out of channel '%s':\n",
				System.nanoTime(), myID, msgId, msg, channel);
		return msg;
	}

	public void closeConnection() {
		stillListening = false;
		/*
		 * a way to stop the listener almost immediately would be to write a
		 * message saying "%d has left the channel". But it's not mention in the
		 * spec and it would use one message out the row available in the buffer
		 * for nothing.
		 */
		String[] channelState = t.get(channel, null, null, null, null, null);
		final int lastWrittenId = Integer.parseInt(channelState[4]);
		for (int i = lastRead + 1; i <= lastWrittenId; i++) {
			/*
			 * TODO: maybe I'll decrement lastRead+1 twice if getNextMessage
			 * just did it but didn't yet update lastRead (at worst one listener
			 * will miss one message)
			 */
			String[] msgInfo = t.get(channel + "_msg", String.valueOf(i), null,
					null);
			msgInfo[3] = String.valueOf(Integer.valueOf(msgInfo[3]) - 1);
			t.put(msgInfo);
		}

		channelState[1] = String.valueOf(Integer.valueOf(channelState[1]) - 1);
		t.put(channelState);
		System.out.format("listener %d is leaving '%s' after reading %d\n",
				myID, channel, lastRead);
		ChatServer.channelState(channelState);
		// Thread.currentThread().interrupt();
	}
}
