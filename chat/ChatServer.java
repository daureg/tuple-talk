package chat;

import tuplespaces.TupleSpace;

public class ChatServer {
	// Add stuff here.
	
	public ChatServer(TupleSpace t, int rows, String[] channelNames) {
		// TODO: Implement ChatServer(TupleSpace, int, String[]);
	}

	public ChatServer(TupleSpace t) {
		// TODO: Implement ChatServer(TupleSpace);
	}

	public String[] getChannels() {
		throw new UnsupportedOperationException();
		// TODO: Implement ChatServer.getChannels();
	}

	public void writeMessage(String channel, String message) {
		// TODO: Implement ChatServer.writeMessage(String, String);
	}

	public ChatListener openConnection(String channel) {
		throw new UnsupportedOperationException(); // Implement this.
		// TODO: Implement ChatServer.openConnection(String);
	}
}
