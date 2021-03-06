package server;

import java.util.ArrayList;
import java.io.*;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Random;
import java.util.Scanner;

public class Room implements AutoCloseable {
	private static SocketServer server;// used to refer to accessible server functions
	private String name;
	private final static Logger log = Logger.getLogger(Room.class.getName());

	// Commands
	private final static String COMMAND_TRIGGER = "/";
	private final static String CREATE_ROOM = "createroom";
	private final static String JOIN_ROOM = "joinroom";
	private final static String FLIP = "flip";
	private final static String ROLL = "roll";
	private final static String MUTE = "mute";
	private final static String UNMUTE = "unmute";
	private final static String WHISPER = "whisper";
	private final static String COLOR = "color";
	private final static String EXPORT = "export";
	private Random rng = new Random();

	public Room(String name) {
		this.name = name;
	}

	public static void setServer(SocketServer server) {
		Room.server = server;
	}

	public String getName() {
		return name;
	}

	private List<ServerThread> clients = new ArrayList<ServerThread>();

	protected synchronized void addClient(ServerThread client) {
		client.setCurrentRoom(this);
		if (clients.indexOf(client) > -1) {

			log.log(Level.INFO, "Attempting to add a client that already exists");

		} else {
			String newUser;
			clients.add(client);
			if (client.getClientName() != null) {

				client.sendClearList();
				sendConnectionStatus(client, true, "joined the room " + getName());
				updateClientList(client);
				File file = new File(client.getClientName() + ".txt");
				if (file.exists()) {
					ArrayList<String> tempMuted = new ArrayList<String>();
					try (Scanner reader = new Scanner(file)) {
						while (reader.hasNextLine()) {
							newUser = reader.nextLine();
							tempMuted.add(newUser);
							sendMessage(client, "/color " + newUser + " gray");
							
						}
						client.setMuted(tempMuted);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
						return;
					} catch (Exception e2) {
						e2.printStackTrace();
						return;
					}
				}
			}
		}
	}

	private void updateClientList(ServerThread client) {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread c = iter.next();
			if (c != client) {
				boolean messageSent = client.sendConnectionStatus(c.getClientName(), true, null);

			}
		}
	}

	protected synchronized void removeClient(ServerThread client) {
		clients.remove(client);
		if (clients.size() > 0) {

			// sendMessage(client, "left the room");
			sendConnectionStatus(client, false, "left the room " + getName());

		} else {
			cleanupEmptyRoom();
		}
	}

	private void cleanupEmptyRoom() {
		// If name is null it's already been closed. And don't close the Lobby
		if (name == null || name.equalsIgnoreCase(SocketServer.LOBBY)) {
			return;
		}
		try {

			log.log(Level.INFO, "Closing empty room: " + name);

			close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	protected void joinRoom(String room, ServerThread client) {
		server.joinRoom(room, client);
	}

	protected void joinLobby(ServerThread client) {
		server.joinLobby(client);
	}

	/***
	 * Helper function to process messages to trigger different functionality.
	 * 
	 * @param message The original message being sent
	 * @param client  The sender of the message (since they'll be the ones
	 *                triggering the actions)
	 */
	private boolean processCommands(String message, ServerThread client) {
		boolean wasCommand = false;
		try {
			if (message.indexOf(COMMAND_TRIGGER) > -1) {
				String[] comm = message.split(COMMAND_TRIGGER);

				log.log(Level.INFO, message);

				String part1 = comm[1];
				String[] comm2 = part1.split(" ");
				String command = comm2[0];
				if (command != null) {
					command = command.toLowerCase();
				}
				String roomName;
				switch (command) {
				case CREATE_ROOM:
					roomName = comm2[1];
					if (server.createNewRoom(roomName)) {
						joinRoom(roomName, client);
					}
					wasCommand = true;
					break;
				case JOIN_ROOM:
					roomName = comm2[1];
					joinRoom(roomName, client);
					wasCommand = true;
					break;
				case FLIP:
					boolean flipBool = false;
					int flipInt = rng.nextInt(2);
					if (flipInt == 1) {
						flipBool = true;
					}
					Iterator<ServerThread> iter = clients.iterator();
					if (flipBool) {
						message = client.getClientName() + " flipped &Heads!&";
					} else {
						message = client.getClientName() + " flipped &Tails!&";
					}
					while (iter.hasNext()) {
						ServerThread clientList = iter.next();
						boolean messageSent = clientList.send("Server", message);
					}
					wasCommand = true;
					break;
				case ROLL:
					int rollInt = rng.nextInt(6) + 1;
					message = client.getClientName() + " rolled a &" + rollInt + "& ";
					Iterator<ServerThread> iter1 = clients.iterator();
					while (iter1.hasNext()) {
						ServerThread clientList = iter1.next();
						boolean messageSent = clientList.send("Server", message);
					}
					wasCommand = true;
					break;
				case MUTE:
					message = message.replace("/mute ", "");
					if (!client.checkMuted(message)) {
						client.addMuted(message);
						muteMessage(client, message);
						File file = new File(client.getClientName() + ".txt");
						ArrayList<String> tempMuted = new ArrayList<String>();
						try (FileWriter fw = new FileWriter(client.getClientName() + ".txt")) {
							tempMuted = client.getMuted();
							for (int i = 0; i < tempMuted.size(); i++) {
								fw.write(tempMuted.get(i) + "\n");
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					wasCommand = true;
					break;
				case UNMUTE:
					message = message.replace("/unmute ", "");
					if (client.checkMuted(message)) {
						client.removeMuted(message);
						unmuteMessage(client, message);
						File file = new File(client.getClientName() + ".txt");
						ArrayList<String> tempMuted = new ArrayList<String>();
						try (FileWriter fw = new FileWriter(client.getClientName() + ".txt")) {
							tempMuted = client.getMuted();
							for (int i = 0; i < tempMuted.size(); i++) {
								fw.write(tempMuted.get(i) + "\n");
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
					wasCommand = true;
					break;
				case WHISPER:
					sendPrivateMessage(client, message);
					wasCommand = true;
					break;
				case COLOR:
					message = message.replace("/color ", "~");
					colorMessage(client, message);
					wasCommand = true;
					break;
				case EXPORT:
					message = message.replace("/export ", "`");
					client.send(client.getClientName(), message);
					wasCommand = true;
					break;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return wasCommand;
	}

	// TODO changed from string to ServerThread
	protected void sendConnectionStatus(ServerThread client, boolean isConnect, String message) {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread c = iter.next();
			boolean messageSent = c.sendConnectionStatus(client.getClientName(), isConnect, message);
			if (!messageSent) {
				iter.remove();
				log.log(Level.INFO, "Removed client " + c.getId());

			}
		}
	}

	/***
	 * Takes a sender and a message and broadcasts the message to all clients in
	 * this room. Client is mostly passed for command purposes but we can also use
	 * it to extract other client info.
	 * 
	 * @param sender  The client sending the message
	 * @param message The message to broadcast inside the room
	 */
	protected void sendMessage(ServerThread sender, String message) {

		log.log(Level.INFO, getName() + ": Sending message to " + clients.size() + " clients");

		if (processCommands(message, sender)) {
			// it was a command, don't broadcast
			return;
		}
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread client = iter.next();
			if (client.checkMuted(sender.getClientName()) == false) {
				boolean messageSent = client.send(sender.getClientName(), message);
				// client.send(sender.getClientName(), "~" + sender.getClientName() + "
				// lightgray");
				if (!messageSent) {
					iter.remove();

					log.log(Level.INFO, "Removed client " + client.getId());

				}
			}
		}
	}

	protected void sendPrivateMessage(ServerThread sender, String message) {

		log.log(Level.INFO, getName() + ": Sending message to " + clients.size() + " clients");
		ArrayList<String> dmed = new ArrayList<String>();
		dmed.add(sender.getClientName());
		message = message + " ";
		if (message.indexOf("@") == -1) {

			return;
		}
		String tempString = "";
		boolean hasAt = false;
		String tempString2 = " ";
		for (int i = message.indexOf("@"); i < message.length(); i++) {
			tempString2 = Character.toString(message.charAt(i));
			if (hasAt == true) {
				if (tempString2.equals(" ")) {
					dmed.add(tempString);
					tempString = "";
					hasAt = false;
				} else {
					tempString = tempString + tempString2;
				}
			} else {
				if (tempString2.equals("@")) {
					hasAt = true;
				}
			}
		}

		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			boolean inWhisper = false;
			ServerThread client = iter.next();
			if (client.checkMuted(sender.getClientName()) == false) {
				for (int i = 0; i < dmed.size(); i++) {
					if (client.getClientName().equals(dmed.get(i))) {
						inWhisper = true;
					}
				}
				if (inWhisper) {
					boolean messageSent = client.send(sender.getClientName(), message);
					if (!messageSent) {
						iter.remove();

						log.log(Level.INFO, "Removed client " + client.getId());

					}
				}
			}
		}
	}

	/***
	 * Will attempt to migrate any remaining clients to the Lobby room. Will then
	 * set references to null and should be eligible for garbage collection
	 */
	@Override
	public void close() throws Exception {
		int clientCount = clients.size();
		if (clientCount > 0) {

			log.log(Level.INFO, "Migrating " + clients.size() + " to Lobby");

			Iterator<ServerThread> iter = clients.iterator();
			Room lobby = server.getLobby();
			while (iter.hasNext()) {
				ServerThread client = iter.next();
				lobby.addClient(client);
				iter.remove();
			}

			log.log(Level.INFO, "Done Migrating " + clients.size() + " to Lobby");

		}
		server.cleanupRoom(this);
		name = null;
		// should be eligible for garbage collection now
	}

	public void muteMessage(ServerThread sender, String message) {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread client = iter.next();
			if (client.getClientName().equals(message)) {
				boolean messageSent = client.send("Server", sender.getClientName() + " has muted you.");
				sendMessage(sender, "/color " + client.getClientName() + " gray");
				if (!messageSent) {
					iter.remove();

					log.log(Level.INFO, "Removed client " + client.getId());

				}
			}
		}
	}

	public void unmuteMessage(ServerThread sender, String message) {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread client = iter.next();
			if (client.getClientName().equals(message)) {
				boolean messageSent = client.send("Server", sender.getClientName() + " has unmuted you.");
				sendMessage(sender, "/color " + client.getClientName() + " white");
				if (!messageSent) {
					iter.remove();

					log.log(Level.INFO, "Removed client " + client.getId());

				}
			}
		}
	}

	public void colorMessage(ServerThread sender, String message) {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread client = iter.next();
			if (client.getClientName().equals(sender.getClientName())) {
				boolean messageSent = client.send(sender.getClientName(), message);
				if (!messageSent) {
					iter.remove();

					log.log(Level.INFO, "Removed client " + client.getId());

				}
			}
		}
	}
	
	public void disconnectRequest(String dis) {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread client = iter.next();
			if (client.getClientName().equals(dis)) {
				client.send("Server", "You have been forcefully diconnected");
				removeClient(client);
				client.kill();
			}
		}
	}
	
	public String returnUserList() {
		String returnString = "";
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread client = iter.next();
			returnString = returnString + client.getClientName() + ", ";
		}
		return returnString;
	}
	
	public void closeMessage() {
		Iterator<ServerThread> iter = clients.iterator();
		while (iter.hasNext()) {
			ServerThread client = iter.next();
			client.send("Server", "Server has closed. You may exit.");
		}
	}

}