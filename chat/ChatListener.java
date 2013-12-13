package chat;

import java.util.HashSet;

import tuplespaces.TupleSpace;

public class ChatListener {
	private int lastRead;
	private int newestSeen;
	private boolean stillListening;
	private final int rows;
	private final int myID;
	private final String channel;
	private final TupleSpace t;

	public ChatListener(TupleSpace t, String channel) {
		this.channel = channel;
		this.t = t;
		String[] state = t.get(new String[] { "state", null, null });
		lastRead = -1;
		newestSeen = 0;
		stillListening = true;
		rows = Integer.parseInt(state[1]);
		myID = Integer.parseInt(state[2]);
		state[2] = String.valueOf(myID + 1);
		t.put(state);
		String[] channelState = t.get(new String[] { channel, null, null, null,
				null, null });
		channelState[1] += String.valueOf(myID) + ";";
		System.out.format("%d is now listening to '%s':\n", myID, channel);
		// ChatServer.channelState(channelState);
		t.put(channelState);
	}

	public String getNextMessage() {
		String[] request = new String[] { channel, null, null, "true", null,
				null };
		/*
		 * here i could read state to get a more recent value of newestSeen, but
		 * still, if more than two msg are posted before I go to the next get,
		 * I'm stuck forever (I would to wait on a lastW > lastRead condition
		 * but it would have to be individual for every listeners. => write msg
		 * could post (channel_notification, listener_id, new_msg_id) and I
		 * could wait on that one alone. Then i could wait to get the channel
		 * and be sure that there is something for me. Or just read the correct
		 * msg directly. Actually it would be great to get the channelState the
		 * possible. The nice thing about tuple tailored to each listener is
		 * that it would be a good way to tell them to stop listening. A hack
		 * could be to post a message with the desired id but an invalid readBy
		 * (not very good because it's hard to ignore for other listeners. Maybe
		 * I could add a 'fake' last field in message (exactly the same)
		 */
		if (lastRead == newestSeen) {
			System.out
					.format("last time, listener %d has read msg %d out '%s' and the more recent she saw was %d so waiting for a new one %d\n",
							myID, lastRead, channel, newestSeen, lastRead + 1);
			// System.out.format("a old one could be: ");
			// String[] omsg = t.read(new String[] { channel + "_msg", "2",
			// null,
			// null });
			// System.out.format("(%d, '%s'), read by %s\n", 2, omsg[2],
			// omsg[3]);
			/* if we are already up to date, we wait until a new message appear */
			request[4] = String.valueOf(lastRead + 1);
		}
		/* TODO: when we close connection, find a way to stop this call */
		String[] channelState = t.get(request);
		System.out.format(
				"listener %d want to read something from channel '%s':\n",
				myID, channel);
		final int lastWrittenId = Integer.parseInt(channelState[4]);
		newestSeen = lastWrittenId; // equivalent to newestSeen++
		int oldestId = Integer.parseInt(channelState[5]);
		if (lastRead == -1)
			lastRead = oldestId - 1;
		final int msgId = lastRead + 1;
		HashSet<Integer> listeners = stringListToIntSet(channelState[1]);
		String[] msgInfo = t.get(new String[] { channel + "_msg",
				String.valueOf(msgId), null, null });
		if (!stillListening) {
			t.put(msgInfo);
			return "";
		}
		String msg = msgInfo[2];
		HashSet<Integer> readBy = stringListToIntSet(msgInfo[3]);
		readBy.add(myID);
		msgInfo[3] = intSetToStringList(readBy);
		if (readBy.containsAll(listeners)) {
			boolean notFull = Boolean.valueOf(channelState[2]);
			System.out.format(
					"everybody has read %d but is channel '%s' full: %b	\n",
					msgId, channel, !notFull);
			if (notFull) {
				/* Not much has changed, except that myId has read this msg. */
				t.put(msgInfo);
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
					// not the oldest one so put it back just in case
					t.put(msgInfo);
					// get the oldest one
					msgInfo = t.get(new String[] { channel + "_msg",
							String.valueOf(oldestId), null, null });
					readBy = stringListToIntSet(msgInfo[3]);
					if (!readBy.containsAll(listeners)) {
						/*
						 * if not every listeners has read it, we put it back
						 * (so should we just t.read instead?)
						 */
						t.put(msgInfo);
					} else {
						/* hopefully it's not the case so we have removed it */
						oldestId++;
					}
				} else {
					// we just remove the oldest one, maybe we can look at the
					// next one
					oldestId++;
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

		} else {
			/* Not much has changed, except that myId has read this msg. */
			t.put(msgInfo);
			t.put(channelState);
		}
		lastRead = msgId;
		System.out.format(
				"%d: listener %d got (%d, '%s') out of channel '%s':\n",
				System.nanoTime(), myID, msgId, msg, channel);
		return msg;
	}

	public static HashSet<Integer> stringListToIntSet(String list) {
		if (list.length() == 0)
			return new HashSet<Integer>(0);
		String[] tmp = list.split(";");
		HashSet<Integer> set = new HashSet<Integer>(tmp.length);
		for (String id : tmp) {
			set.add(Integer.valueOf(id));
		}
		return set;
	}

	public static String intSetToStringList(HashSet<Integer> set) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (Integer id : set) {
			if (first)
				first = false;
			else
				sb.append(";");
			sb.append(String.valueOf(id));
		}
		return sb.toString();
	}

	public void closeConnection() {
		stillListening = false;
		/*
		 * a way to stop the listener almost immediately would be to write a
		 * message saying "%d has left the channel". But it's not mention in the
		 * spec and it would use one message out the row available in the buffer
		 * for nothing.
		 */
		String[] channelState = t.get(new String[] { channel, null, null, null,
				null, null });
		channelState[1] = removeMeFromListeners(channelState[1]);
		t.put(channelState);
	}

	// Come'on JDK! http://stackoverflow.com/q/1751844
	private String removeMeFromListeners(String list) {
		String[] listeners = list.split(";");
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String item : listeners) {
			if (first)
				first = false;
			else
				sb.append(";");
			if (Integer.parseInt(item) != myID)
				sb.append(item);
		}
		return sb.toString();
	}
}
