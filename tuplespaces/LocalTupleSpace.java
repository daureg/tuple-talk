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