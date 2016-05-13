package server;

import java.awt.HeadlessException;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JOptionPane;

public class Server {

	private int MIN_PLAYERS;
	private int MAX_PLAYERS;
	private int NUM_ROWS;
	private int NUM_COLS;
	private int NUM_OF_PILLS;
	private int REFRESH_RATE;

	private final String[] gameSpeeds = new String[] { "Very Slow", "Slow", "Normal", "Fast", "Super Fast" };

	private String serverVersion = "v1.2.2";

	private ServerSocket serverSocket;

	private ArrayList<Socket> sockets;

	private ArrayList<DataInputStream> inputStreams;
	private ArrayList<DataOutputStream> outputStreams;

	private boolean gameRunning;

	private Timer gameStartTimer;

	private Game game;

	private Server() {

		String gameSpeed;

		try {

			MIN_PLAYERS = Integer.parseInt(
					JOptionPane.showInputDialog("Enter Minumium Amount Of Players Required To Start A Game:"));
			MAX_PLAYERS = Integer
					.parseInt(JOptionPane.showInputDialog("Enter Maximum Amount Of Players Allowed To Join Server:"));
			NUM_ROWS = Integer.parseInt(JOptionPane.showInputDialog("Enter Amount Of Rows:"));
			NUM_COLS = Integer.parseInt(JOptionPane.showInputDialog("Enter Amount Of Columns:"));

			NUM_ROWS = Math.max(NUM_ROWS, 25);
			NUM_COLS = Math.max(NUM_COLS, 25);

			gameSpeed = (String) JOptionPane.showInputDialog(null, "Choose A Game Speed:", "Game Speed",
					JOptionPane.QUESTION_MESSAGE, null, gameSpeeds, "Normal");

			REFRESH_RATE = 1500;

			for (String curSpeed : gameSpeeds) {
				if (gameSpeed.equals(curSpeed)) {
					break;
				}
				REFRESH_RATE += -250;
			}

			NUM_OF_PILLS = Integer.parseInt(JOptionPane.showInputDialog("Enter Amount Of Pills In Game:"));
		} catch (NumberFormatException e) {
			JOptionPane.showMessageDialog(null, "Failed to set value\nClosing Server");
			return;
		}

		sockets = new ArrayList<Socket>();

		inputStreams = new ArrayList<DataInputStream>();
		outputStreams = new ArrayList<DataOutputStream>();

		gameRunning = false;

		try {
			serverSocket = new ServerSocket(10);
			serverSocket.setSoTimeout(100);

			System.out.println("Server Started on: " + InetAddress.getLocalHost());

			new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						//@formatter:off
						JOptionPane.showMessageDialog(null, "Server Sucessfully Started" + 
					"\nHost: " + InetAddress.getLocalHost() + 
					"\nMinimum Players: " + MIN_PLAYERS +
					"\nMaximum Players: " + MAX_PLAYERS +
					"\nNumber Of Rows: " + NUM_ROWS +
					"\nNumber Of Columns: " + NUM_COLS +
					"\nNumber Of Pickups: " + NUM_OF_PILLS + 
					"\nGame Speed: " + gameSpeed);
						//@formatter:on

						JOptionPane.showMessageDialog(null, "Server Running...\nClick Ok To Stop");
						System.exit(0);
					} catch (HeadlessException | UnknownHostException e) {
						e.printStackTrace();
					}
				}
			}).start();

			startListeningForConnections();
		} catch (

		Exception e) {
			e.printStackTrace();
		}
	}

	private void startListeningForConnections() {

		new Thread(new Runnable() {

			@Override
			public void run() {

				System.out.println("Started Listening for Connections on Port: " + serverSocket.getLocalPort());

				while (true) {

					// System.out.println("Checking for lost connections");

					for (int i = 0; i < sockets.size(); i++) {

						if (sockets.get(i).isClosed()) {
							System.out.println("Lost connection with: " + sockets.get(i));
							removeClosedSocket(sockets.get(i));
							i--;
						}
					}

					try {
						Socket newSocket = serverSocket.accept();

						newSocket.setSoTimeout(REFRESH_RATE);

						sockets.add(newSocket);
						inputStreams.add(new DataInputStream(new BufferedInputStream(newSocket.getInputStream())));
						outputStreams.add(new DataOutputStream(new BufferedOutputStream(newSocket.getOutputStream())));

						String newConnect = inputStreams.get(inputStreams.size() - 1).readUTF();

						System.out.println("New Connection String = " + newConnect);

						if (newConnect == null) {
							gracefulDisconnect(newSocket);
							System.out.println(newSocket + " Did not Confirm Connection: null Recieved");
							continue;
						} else if (newConnect.contains("CONFIRM CONNECT:") && !newConnect.endsWith(serverVersion)) {
							outputStreams.get(outputStreams.size() - 1).writeUTF("BAD VERSION");
							outputStreams.get(outputStreams.size() - 1).flush();
							gracefulDisconnect(newSocket);
							System.out.println(newSocket + " Did not Confirm Connection: Bad Version");
							continue;
						} else if (sockets.size() > MAX_PLAYERS) {
							outputStreams.get(outputStreams.size() - 1).writeUTF("SERVER FULL");
							outputStreams.get(outputStreams.size() - 1).flush();
							gracefulDisconnect(newSocket);
							System.out.println(newSocket + " Did not Confirm Connection: Server Full");
							continue;
						}

						System.out.println(newSocket + " Confirmed Connection, sending setup data");

						String responceTag = "SETUP";

						if (gameRunning) {
							responceTag += " RUNNING";
						}

						responceTag += ":";

						outputStreams.get(outputStreams.size() - 1).writeUTF(responceTag + NUM_ROWS + "," + NUM_COLS);
						outputStreams.get(outputStreams.size() - 1).flush();

						System.out.println("Connecton completed for: " + newSocket);

						System.out.println("There are now " + sockets.size() + " people connected");
					} catch (SocketTimeoutException e) {

					} catch (IOException e) {
						e.printStackTrace();
					}

					if (sockets.size() >= MIN_PLAYERS && gameStartTimer == null && !gameRunning) {

						System.out.println("Enough players, starting game...");

						gameStartTimer = new Timer(true);
						gameStartTimer.scheduleAtFixedRate(new TimerTask() {

							private int countdown = 10;

							@Override
							public void run() {

								if (gameRunning) {
									cancel();
									gameStartTimer = null;
									System.out.println("False gamestart, stopping early");
								}

								countdown--;

								sendToAll("COUNTDOWN:" + countdown, true);

								if (countdown == 0) {
									cancel();
									gameStartTimer = null;

									if (!gameRunning) {
										startGame();
									} else {
										System.out.println("False gamestart, failed launch");
									}
								}
							}
						}, 0, 1000);
					} else if (sockets.size() < MIN_PLAYERS && gameStartTimer != null) {
						gameStartTimer.cancel();
						gameStartTimer.purge();
						gameStartTimer = null;
						sendToAll("ABORT COUNTDOWN", true);
						System.out.println("Not enough players, canceling game start");
					}

					try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();

	}

	private void sendToAll(final String message, final boolean sendToDead) {

		System.out.println("Sending: " + message + " to " + sockets.size() + " clients");

		for (int i = 0; i < sockets.size(); i++) {

			if (!sendToDead && !game.playerAlive(sockets.get(i))) {
				continue;
			}

			System.out.println("Attempting to send to socket: " + sockets.get(i));

			try {

				if (sockets.get(i).isClosed()) {
					System.out.println("Lost connection with: " + sockets.get(i));
					removeClosedSocket(sockets.get(i));
					i--;
					continue;
				}

				System.out.println("Sending " + message + " to " + sockets.get(i));

				outputStreams.get(i).writeUTF(message);
				outputStreams.get(i).flush();

				System.out.println("Sent");
			} catch (SocketException e) {
				System.out.println("Lost connection with: " + sockets.get(i));
				removeClosedSocket(sockets.get(i));
				i--;
				continue;
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void startGame() {

		System.out.println("Starting game!");

		gameRunning = true;

		game = new Game(this, NUM_ROWS, NUM_COLS, NUM_OF_PILLS);

		for (Socket s : sockets) {
			game.addPlayer(s);
		}

		System.out.println("Sent START to clients");

		sendToAll("START GAME", true);

		new Timer(true).scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {

				sendToAll("SEND KEY", false);

				System.out.println("Requested keys");

				try {
					Thread.sleep(25);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				calculateGame();
				updateClients();

				System.out.println("Is game over: " + game.isGameOver());

				if (game.isGameOver()) {
					cancel();
					gameRunning = false;
					sendToAll("STOP GAME", true);
				}
			}

		}, 0, REFRESH_RATE);
	}

	private void calculateGame() {

		for (int i = 0; i < sockets.size(); i++) {

			if (!game.playerAlive(sockets.get(i))) {
				continue;
			}

			Direction direction = null;
			try {
				direction = Direction.valueOf(inputStreams.get(i).readUTF());
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (direction == null) {
				System.out.println("Forceably Disconnected: " + sockets.get(i));
				System.out.println("*Did not provide a direction in time*");
				System.out.println("Provided direction: " + direction);
				gracefulDisconnect(sockets.get(i));
				i--;
				continue;
			}

			game.move(sockets.get(i), direction);

		}

		game.removeLosers();
	}

	private void gracefulDisconnect(Socket sok) {

		System.out.println("Gracefully closing socket: " + sok);

		if (game != null) {
			game.removePlayer(sok);
		}

		try {
			sok.shutdownOutput();
			while (inputStreams.get(sockets.indexOf(sok)).read() >= 0)
				;
			sok.close();
			removeClosedSocket(sok);
		} catch (SocketException e) {
			try {
				sok.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			removeClosedSocket(sok);
		} catch (SocketTimeoutException e) {

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void updateClients() {
		sendToAll("UPDATE:" + game.getGameState(), true);
	}

	private void removeClosedSocket(final Socket s) {

		if (game != null) {
			game.removePlayer(s);
		}

		int pos = sockets.indexOf(s);

		if (pos != -1) {
			sockets.remove(pos);
			inputStreams.remove(pos);
			outputStreams.remove(pos);
			System.out.println("Sucessfully removed socket: " + s);
		} else {
			System.out.println("Tried to remove socket: " + s + " but it didn't exist");
		}

		System.out.println("There are now " + sockets.size() + " people connected");
	}

	public static void main(String as[]) {
		new Server();
	}

	public void sendMessage(Socket player, String string) {

		System.out.println("Sending: " + string + " to: " + player);

		final int pos = sockets.indexOf(player);

		try {
			outputStreams.get(pos).writeUTF(string);
			outputStreams.get(pos).flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int clientsConnected() {
		return sockets.size();
	}
}