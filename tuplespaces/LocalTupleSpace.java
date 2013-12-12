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

	public String[] find(boolean remove, String... pattern) {
		String[] matched = null;
		int[] hash = new int[pattern.length];
		for (int i = 0; i < pattern.length; i++) {
			hash[i] = pattern[i] == null ? -1
					: (pattern[i].hashCode() & 0x7FFFFFFF);
		}
		synchronized (tuples) {
			do {
				Iterator<int[]> it_hash = hashes.iterator();
				for (Iterator<String[]> it_tuple = tuples.iterator(); it_tuple
						.hasNext();) {
					String[] tuple = (String[]) it_tuple.next();
					int[] tuple_hash = (int[]) it_hash.next();
//					if (match(tuple_hash, hash)) {
					if (match(tuple_hash, hash) && matchStr(tuple, pattern)) {
						// if (matchStr(tuple, pattern)) {
						matched = tuple.clone();
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
					// Restore the interrupted status
					// http://www.ibm.com/developerworks/java/library/j-jtp05236/
					Thread.currentThread().interrupt();
//					e.printStackTrace();
				}
			} while (matched == null);
		}
		return matched;
	}

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

	private boolean match(int[] tuple, int[] pattern) {
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
		synchronized (tuples) {
			int[] hash = new int[tuple.length];
			for (int i = 0; i < tuple.length; i++) {
				hash[i] = tuple[i] == null ? -1
						: (tuple[i].hashCode() & 0x7FFFFFFF);
			}
			tuples.add(tuple.clone());
			hashes.add(hash.clone());
			hash = null;
			tuples.notifyAll();
		}
	}
}
