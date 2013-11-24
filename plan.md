# Tuple space

In my opinion, the simplest solution would be to keep a copy of every tuples
in a `ListArray`, whose access will be synchronized for the whole duration of
the three operations. At the end of `put` we notify all threads waiting. For
`get` and `read`, we iterate over the `ListArray` until we find a tuple
matching the given pattern. In `get`, we remove it and in both cases, we give
up the lock over the `ListArray` and return the found tuple. But if time
allows it, I'd like to try another approach.

Indeed, this one can be slow as the matching is an $O(n)$ operation and all
threads are wake up at every insertion. Instead, we can assign an id to every
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
periodically cleaned of locks with 0 user) or otherwise create it. Hopefully
this added complexity will be beneficial to the performance, although it
remained to be seen in tests.

# Chat server

The chat server (and all its synchronization) is almost completely defined by
the tuples it will use. The first is `('state', row, next_id, channel_name_1,
…, channel_name_n)`, where `next_id` is an integer to be assigned to next
listener that will connect to the server. Then each channel --- which is
basically a bounded buffer whose elements are removed once all of its consumers
have consumed it --- will be represented by `(channel_name, listeners, notFull,
notEmpty, lastWritten, oldest)` where `listeners` is comma separated list of
listener's id, `lastWritten` is the id of the last written message and
`oldest` the id of the oldest one that is still accessible. Finally, I choose
to put all messages of a channel in a separate tuple, to avoid contention,
namely: `(channel_name_msg, id, content, readBy)` where `readBy` is again a
list of user's id.

`ChatServer`'s constructors either populate a tuple space with provided
arguments or copy a given tuple space to a local one. The other non trivial
method is `writeMessage`, that will `get` the corresponding channel if it is
`notFull` (by doing so, no one else could write, read, join or leave), `put`
the new message and `put` the updated channel.

When creating a `ChatListener`, it will first `get` the `state` to obtain an
id and a local copy of `row` and then `put` it back with incremented
`next_id`. Then it `get` the channel to add itself to its `listeners` (and do
the reverse in `closeConnection()`). It also initialize `last` read message
to -1. In order to `getNextMessage()`, it first `get` the channel tuple if it
is `notEmpty` (again preventing any other operation on it), sets `msg_id` to
`last+1` (or `oldest` if `last == -1`), `get` the corresponding message, add
itself to `readBy`. If `listeners` is not included in `readBy`, some connected
people have not yet seen it so we `put` everything back as it was. Otherwise,
we also `put` the message back but if the channel was full, we remove the
`oldest` message, increment it, and `put` the updated channel.
