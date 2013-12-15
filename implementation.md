# Tuple space

I first use the simplest solution: keeping a copy of every tuples in a
`ListArray`, whose access is synchronized for the whole duration of the three
operations, ensuring safety. At the end of `put` we notify all threads
waiting. For `get` and `read`, we iterate over the `ListArray` until we find a
tuple matching the given pattern. In `get`, we remove it and in both cases, we
give up the lock over the `ListArray` and return the found tuple. Because it
is faster to compare integer than string, I also stored a hash of each tuple
in a parallel array and did the matching on it. But for small tuple, there was
no noticeable speed improvement. Therefore I tried another method described in
the appendix but it did not yield any conclusive results.

# Chat server

The chat server (and all its synchronization) is almost completely defined by
the tuples it uses. The first is `('state', row, channel_name_1, …,
channel_name_n)`. Then each channel --- which is basically a bounded buffer
whose elements are removed once all of its consumers have consumed it --- is
represented by `(channel_name, listeners, notFull, notEmpty, lastWritten,
oldest)` where `listeners` is the current number of connect listeners,
`lastWritten` is the id of the last written message and `oldest` the id of the
oldest one that is still accessible. Finally, I choose to put all messages of
a channel in a separate tuple, to avoid contention, namely:
`(channel_name_msg, id, content, unreadBy)` where `unreadBy` is the count of
listeners that still have to read it.

`ChatServer` constructors either populate a tuple space with provided
arguments or copy a given tuple space to a local one. The other non trivial
method is `writeMessage`, that first `get` the corresponding channel (by doing
so, no one else could write, read, join or leave). If it is `notFull`, `put`
the new message (with the next sequential id obtained from the channel) and
`put` the updated channel. If the channel is full, it tries to remove the
oldest message itself and if it is not possible (because the message is still
unread by some) wait for the last listener to do so.

When creating a `ChatListener`, it `get` the channel to increment its
`listeners` and marked all reachable message as unread by itself (it does the
reverse in `closeConnection()`). In order to `getNextMessage()`, it `get` the
next message relative to its last read id, decrement its `unreadBy` count and
`put` it back. If it was the last reader of this message and the channel is
full, it tries to remove the `oldest` message.

The fact that for each channel, each message is written once and assigned a
strictly increasing id should ensure that they are always ordered. And because
listeners read them one by one in that order, they should not be duplicated
either. Finally, by ensuring that enough listeners have read them before
removing them, none of them should be lost.

# Testing and issues

I mainly use the provided test cases, which revealed several problems I will
describe. I tested once the `chatUI`, which also showed some minor issues in the
constructor that connect to an existing `ChatServer`. My first problem was
that I did not think at first that message could be written without listeners
and that why I allowed `writeMessage()` to remove message by itself.  Then
there was a deadlock when both writer and listeners try to remove the same old
message which I solved by moving the order in which they remove tuple.
Finally, there was also a problem with listeners leaving channel too fast,
without executing `getNextMessage()` at least once. A issue I did not manage
to solve is that after `closeConnection()`, the thread can still be blocked on
a call to the TupleSpace in `getNextMessage()`.

# Appendix

The alternative tuple storage goes like this. We can assign an id to every
tuple and keep a map $R$ between these ids and the index of the corresponding
tuple in the `ListArray` $T$. Additionally, we maintain an array $H$ of
`HashMap<String, List<Integer>>` $\{H_i\}_{i=1,\dots,k}$ where $k$ is the
maximum number of fields of a tuple. $H_i[v_i]$ is then the list of ids of all
tuple whose value of field $i$ is $v_i$ (and $H_i[\textrm{null}]$ is the list
of all tuples that have at least length $i$). To match a pattern $(p_1, \dots,
p_k)$, we iterate over $H$ to build $M_p = \bigcap_{i=1}^k H_i[p_i]$ and
return the first id, or null if $M_p$ is empty. It takes $O(k)$ operations
(but we expect $k\leq n$) and it needs to get a lock over $H$ first.

To `put` a new tuple, we first add it to the $T$ (with proper
synchronization), update $R$ (also with synchronization) and finally get a
lock over $H$ to update it. Then we notify corresponding threads, as described
later. To `read` or `get`, first we acquired a lock customized to the pattern
considered. Then we perform the match operation. In the case of `get`, we
remove the found tuple from the $T$, get a lock over $H$, remove it from all
the lists in which it appeared and finally update $R$ (as all subsequent
messages will have their index in $L$ shifted to the left by one).

To avoid using `notifyAll()`, we create a specific Object (`TupleLock`) for
each pattern passed to `read` or `get` (that simply consist of a copy of the
string array and a counter of threads using it) and store it in a list $L$.
At the beginning of `read` and `get`, and at the end of `put`, we try to find
a appropriate lock in $L$ (this time by doing a linear search, as $L$ will be
periodically cleaned of locks with 0 user) or otherwise create it.
Unfortunately, this more sophisticated approach took to long so I did not had
time to test it properly. It is included in `LocalTupleSpace.java` as
comments.
