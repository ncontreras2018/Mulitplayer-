package client;

import java.io.*;
import java.net.*;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JOptionPane;

import server.Direction;

public class Client {
	private Socket socket;
	private DataInputStream dataIn;
	private DataOutputStream dataOut;

	private GameWindow gameWindow;

	private Direction nextDir;

	private final String version = "v1.2.2";

	private boolean gameRunning;

	public Client() {

		setupSocket();

		String result = connectToServer();

		if (result != null) {

			gameWindow = new GameWindow(this, result, socket);

			if (result.contains("RUNNING")) {
				gameRunning = true;
				gameWindow.setGameRunning(true);
			}

			new Thread(new Runnable() {
				@Override
				public void run() {
					while (true) {

						if (gameRunning) {

							String serverRequest = waitForData();

							if (serverRequest != null) {
								if (serverRequest.equals("SEND KEY")) {
									System.out.println("Requested to send key");
									sendData(nextDir + "");
									System.out.println("Sent key");
								} else if (serverRequest.equals("STOP GAME")) {
									System.out.println("Requested game stop");
									gameWindow.setGameRunning(false);
									gameRunning = false;
									gameWindow.setCountdown(10);
									gameWindow.countdown(false);
								} else if (serverRequest.startsWith("UPDATE")) {
									gameWindow.setGameData(serverRequest);
									gameWindow.repaint();
								}
							}
						} else {
							waitForGameStart();
						}
					}
				}
			}).start();
		} else {
			disconnectFromServer();
		}
	}

	private void setupSocket() {
		String serverIP = JOptionPane.showInputDialog("Enter Server IP");

		if (serverIP == null || serverIP == "") {
			System.exit(0);
		}

		try {
			socket = new Socket(serverIP, 10);
			socket.setSoTimeout(5000);
			System.out.println(socket);
			dataIn = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
			dataOut = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String connectToServer() {
		gameRunning = false;

		System.out.println("Confirming Connection...");

		String result = handshakeWithServer("CONFIRM CONNECT:" + version);

		System.out.println("Handshake result = " + result);

		if (result != null && result.equals("BAD VERSION")) {
			System.out.println("Pop up, bad version");
			JOptionPane.showMessageDialog(null, "Failed to Connect:\nIncompatable Version");
			gracefulDisconnect(socket);
		}

		if (result != null && result.equals("SERVER FULL")) {
			System.out.println("Pop up, server full");
			JOptionPane.showMessageDialog(null, "Failed to Connect:\nThe Server Is Full");
			gracefulDisconnect(socket);
		}

		if (result == null || !result.startsWith("SETUP")) {
			System.out.println("Handshake failed, closing: " + socket);
			gracefulDisconnect(socket);
			return null;
		}

		System.out.println("Handshake sucessful");

		return result;
	}

	public void waitForGameStart() {

		System.out.println("Waiting for game to start...");

		String startKey = null;

		while (startKey == null || !startKey.equals("START GAME")) {
			startKey = waitForData();

			if (startKey != null) {
				if (startKey.equals("ABORT COUNTDOWN")) {
					gameWindow.setCountdown(10);
					gameWindow.countdown(false);
				} else if (startKey.contains("COUNTDOWN")) {
					System.out.println("Countdown recieved: " + startKey);
					gameWindow.setCountdown(Integer.parseInt(startKey.substring(startKey.indexOf(":") + 1)));
				} else if (startKey.startsWith("START DIR")) {
					nextDir = Direction.valueOf(startKey.substring(startKey.indexOf(":") + 1));
					System.out.println("Got Dir From Server -> " + nextDir);
				}
			}
			gameWindow.repaint();
		}

		System.out.println("Game started!");

		gameWindow.setGameRunning(true);
		gameRunning = true;
	}

	public void setNextDir(Direction dir) {
		nextDir = dir;
	}

	public String handshakeWithServer(final String message) {
		sendData(message);

		System.out.println("Waiting");

		return waitForData();

	}

	public void sendData(final String data) {
		try {
			dataOut.writeUTF(data);
			dataOut.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public String waitForData() {

		String recieved = null;

		try {
			recieved = dataIn.readUTF();
		} catch (EOFException e) {
		} catch (SocketTimeoutException e) {
		} catch (SocketException e) {
			System.out.println("Client lost connection with server");
			disconnectFromServer();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return recieved;
	}

	private void disconnectFromServer() {
		gracefulDisconnect(socket);

		gameRunning = false;

		if (gameWindow != null) {
			gameWindow.setGameRunning(false);
		}

		setupSocket();

		String result = connectToServer();

		if (gameWindow != null) {
			gameWindow.setUp(this, result, socket);
		}
	}

	private void gracefulDisconnect(Socket sok) {

		System.out.println("Attempting to gracefully close socket: " + sok);

		InputStream is;
		try {
			is = sok.getInputStream();
			sok.shutdownOutput();
			while (is.read() >= 0)
				;
			sok.close();
		} catch (SocketException e) {
			try {
				sok.close();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String as[]) {
		new Client();
	}
}