package tuplespaces;

import java.util.ArrayList;
import java.util.Iterator;

public class LocalTupleSpace implements TupleSpace {
	private final ArrayList<String[]> tuples;
	private final ArrayList<int[]> hashes;

	public LocalTupleSpace() {
		tuples = new ArrayList<String[]>(100);
		hashes = new ArrayList<int[]>(100);
	}

	/*
	 * Return of an array of hashCode of every non-null element of pattern (and
	 * -1 otherwise)
	 */
	private int[] buildHash(String[] pattern) {
		int[] hash = new int[pattern.length];
		for (int i = 0; i < pattern.length; i++) {
			hash[i] = pattern[i] == null ? -1
					: (pattern[i].hashCode() & 0x7FFFFFFF);
		}
		return hash;
	}

	public String[] find(boolean remove, String... pattern) {
		String[] matched = null;
		int[] hash = buildHash(pattern);
		synchronized (tuples) {
			do {
				/* We iterate simultaneously on tuples and their hashes. */
				Iterator<int[]> it_hash = hashes.iterator();
				for (Iterator<String[]> it_tuple = tuples.iterator(); it_tuple
						.hasNext();) {
					String[] tuple = (String[]) it_tuple.next();
					int[] tuple_hash = (int[]) it_hash.next();
					/*
					 * if hashes match, we also check string themselves to avoid
					 * collision
					 */
					if (matchHash(tuple_hash, hash) && matchStr(tuple, pattern)) {
						matched = tuple.clone();
						/*
						 * the only difference between read and get is that in
						 * later case, we remove the tuple and its hash.
						 */
						if (remove) {
							it_tuple.remove();
							it_hash.remove();
						}
						break;
					}
				}
				try {
					if (matched == null)
						tuples.wait();
				} catch (InterruptedException e) {
					/*
					 * In ChatListener.closeConnection(), it would be convenient
					 * to interrupt the thread and for instance return null here
					 * to avoid being block in a get call during getNextMessage.
					 * But according to the spec, we must only return valid
					 * tuple.
					 */
					// return null;
				}
			} while (matched == null);
		}
		return matched;
	}

	/* true if tuple[] and pattern[] are equal in non-null position of pattern */
	private boolean matchStr(String[] tuple, String[] pattern) {
		if (tuple.length != pattern.length) {
			return false;
		}
		for (int i = 0; i < pattern.length; i++) {
			if (pattern[i] != null && pattern[i].compareTo(tuple[i]) != 0) {
				return false;
			}
		}
		return true;
	}

	/* Same as above but for hashCode and -1 instead of null */
	private boolean matchHash(int[] tuple, int[] pattern) {
		if (tuple.length != pattern.length) {
			return false;
		}
		for (int i = 0; i < pattern.length; i++) {
			if (pattern[i] >= 0 && pattern[i] != tuple[i]) {
				return false;
			}
		}
		return true;
	}

	public String[] get(String... pattern) {
		return find(true, pattern);
	}

	public String[] read(String... pattern) {
		return find(false, pattern);
	}

	public void put(String... tuple) {
		/*
		 * There's no explicit synchronization over hashes as it's always use in
		 * conjunction with tuples.
		 */
		synchronized (tuples) {
			int[] hash = buildHash(tuple);
			tuples.add(tuple.clone());
			hashes.add(hash.clone());
			hash = null;
			tuples.notifyAll();
		}
	}
}

/*
package tuplespaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class AltSpace implements TupleSpace {
	private final HashMap<Integer, Integer> idToIndices;
	private final ArrayList<String[]> tuples;
	private final ArrayList<HashMap<String, ArrayList<Integer>>> fieldsIndex;
	private final ArrayList<TupleLock> locks;
	private Integer currentId = 0;

	public AltSpace() {
		tuples = new ArrayList<String[]>(100);
		idToIndices = new HashMap<Integer, Integer>(100);
		fieldsIndex = new ArrayList<HashMap<String, ArrayList<Integer>>>(6);
		for (int i = 0; i < 6; i++) {
			ArrayList<Integer> tmp = new ArrayList<Integer>();
			HashMap<String, ArrayList<Integer>> hi = new HashMap<String, ArrayList<Integer>>();
			hi.put(null, tmp);
			fieldsIndex.add(hi);
		}
		locks = new ArrayList<AltSpace.TupleLock>(100);
	}

	@Override
	public void put(String... tuple) {
		synchronized (this) {
			currentId++;
		}
		synchronized (tuples) {
			final int id = tuples.size();
			tuples.add(tuple.clone());
			synchronized (idToIndices) {
				idToIndices.put(currentId, id);
			}
		}
		synchronized (fieldsIndex) {
			int i = 0;
			for (i = fieldsIndex.size(); i < tuple.length; i++) {
				fieldsIndex.add(new HashMap<String, ArrayList<Integer>>());
			}
			for (i = 0; i < tuple.length; i++) {
				String value = tuple[i];
				ArrayList<Integer> previousMsg = fieldsIndex.get(i).get(value);
				if (previousMsg != null) {
					previousMsg.add(currentId);
				} else {
					ArrayList<Integer> msgWithThisValue = new ArrayList<Integer>();
					msgWithThisValue.add(currentId);
					fieldsIndex.get(i).put(value, msgWithThisValue);
				}
				fieldsIndex.get(i).get(null).add(currentId);
			}

		}
		TupleLock lock = findMatchingLock(tuple);
		if (lock != null) {
			synchronized (lock) {
				lock.notify();
			}
		}
	}

	public Integer match(String... pattern) {
		synchronized (fieldsIndex) {
			List<Integer> first = fieldsIndex.get(0).get(pattern[0]);
			if (first == null) return null;
			HashSet<Integer> candidate = new HashSet<Integer>(first);
			int i = 0;
			while (candidate.size() > 0 && i < pattern.length) {
				ArrayList<Integer> matchingThisField = fieldsIndex.get(i).get(
						pattern[i]);
				if (matchingThisField != null) {
					candidate.retainAll(matchingThisField);
				} else {
					return null;
				}
				i++;
			}
			for (i = pattern.length; i < fieldsIndex.size(); i++) {
				ArrayList<Integer> largerMsg = fieldsIndex.get(i).get(null);
				candidate.removeAll(largerMsg);
			}
			if (candidate.size() > 0) {
				return candidate.iterator().next();
			}
		}
		return null;
	}

	private String[] find(boolean remove, String... pattern) {
		TupleLock lock = getMatchingLock(pattern);
		Integer msgId = null;
		Integer msgIdx = -1;
		synchronized (lock) {
			while (msgId == null) {
				msgId = match(pattern);
				if (msgId != null)
					break;
				try {
					lock.wait();
				} catch (InterruptedException e) {
//					e.printStackTrace();
				}
			}
			if (!remove) {
				synchronized (idToIndices) {
					msgIdx = idToIndices.get(msgId);
				}
				synchronized (tuples) {
					return tuples.get(msgIdx).clone();
				}
			} else {
				synchronized (fieldsIndex) {
					for (int i = 0; i < pattern.length; i++) {
						String val = pattern[i];
						fieldsIndex.get(i).get(val).remove(msgId);
						fieldsIndex.get(i).get(null).remove(msgId);
					}
				}
				synchronized (idToIndices) {
					Integer k = 0;
					Integer v = 0;
					for (Map.Entry<Integer, Integer> entry : idToIndices
							.entrySet()) {
						k = entry.getKey();
						v = entry.getValue();
						if (k.equals(msgId)) {
							msgIdx = v;
						}
						if (k.compareTo(msgId) > 0) {
							entry.setValue(v - 1);
						}
					}
				}
				synchronized (tuples) {
					return tuples.remove(msgIdx.intValue()).clone();
				}
			}
		}
	}

	@Override
	public String[] get(String... pattern) {
		return find(true, pattern);
	}

	@Override
	public String[] read(String... pattern) {
		return find(false, pattern);
	}

	private TupleLock findMatchingLock(String[] pattern) {
		synchronized (locks) {
			for (TupleLock lock : locks) {
				if (lock.match(pattern)) {
					return lock;
				}
			}
		}
		return null;
	}

	private TupleLock getMatchingLock(String[] pattern) {
		synchronized (locks) {
			TupleLock l = findMatchingLock(pattern);
			if (l != null) {
				l.newUser();
			} else {
				l = new TupleLock(pattern);
				locks.add(l);
			}
			return l;
		}
	}

	public static void main(String[] args) {
		AltSpace a = new AltSpace();
		a.put("zork", null, "1", "hell", null);
		a.put("zork", null, "2", "hell");
		a.put("zork", null, "3", null, null);
		a.put("zork", null, "4", "hell");
		String[] res = a.get("zork", null, null, "hell");
		for (String v : res) {
			System.out.print(v + "\t");
		}
		a = null;
	}

	private class TupleLock {
		private int usersCount;
		private final String[] pattern;
		private final int[] hPattern;

		TupleLock(String[] pattern) {
			this.pattern = pattern.clone();
			hPattern = buildHash(pattern);
			usersCount = 0;
		}

		public boolean match(String[] pattern) {
			return matchHash(hPattern, buildHash(pattern))
					&& matchStr(this.pattern, pattern);
		}

		public synchronized void newUser() {
			usersCount = usersCount + 1;
		}
	}

	private int[] buildHash(String[] pattern) {
		int[] hash = new int[pattern.length];
		for (int i = 0; i < pattern.length; i++) {
			hash[i] = pattern[i] == null ? -1
					: (pattern[i].hashCode() & 0x7FFFFFFF);
		}
		return hash;
	}

	public static boolean matchStr(String[] tuple, String[] pattern) {
		if (tuple.length != pattern.length) {
			return false;
		}
		for (int i = 0; i < pattern.length; i++) {
			if (pattern[i] != null && pattern[i].compareTo(tuple[i]) != 0) {
				return false;
			}
		}
		return true;
	}

	public static boolean matchHash(int[] tuple, int[] pattern) {
		if (tuple.length != pattern.length) {
			return false;
		}
		for (int i = 0; i < pattern.length; i++) {
			if (pattern[i] >= 0 && pattern[i] != tuple[i]) {
				return false;
			}
		}
		return true;
	}
}
*/