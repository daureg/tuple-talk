package tuplespaces;

public interface TupleSpace {
	public void put(String... tuple);

	public String[] get(String... pattern);

	public String[] read(String... pattern);
}
