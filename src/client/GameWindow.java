package client;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.net.Socket;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.JPanel;

import server.Direction;

public class GameWindow extends JPanel implements KeyListener {

	private int numRows, numCols;

	private JFrame frame;

	private String gameData;

	private String player;

	private Client client;

	private boolean gameRunning;

	private int countdown;

	private boolean countingDown;

	public GameWindow(Client client, String setUpInstructions, Socket player) {

		frame = new JFrame("Multiplayer Snake");

		frame.add(this);

		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		frame.addKeyListener(this);

		setUp(client, setUpInstructions, player);
	}

	public void setCountdown(int time) {

		System.out.println("Setting countdown to: " + time);

		this.countdown = time;
		countingDown = true;
	}

	public void countdown(boolean countdown) {
		this.countingDown = countdown;
	}

	public void setUp(Client client, String setUpInstructions, Socket player) {

		this.client = client;

		this.gameRunning = false;

		this.player = player.toString();

		gameData = "";

		setUpInstructions = setUpInstructions.substring(setUpInstructions.indexOf(":") + 1);
		String[] size = setUpInstructions.split(Pattern.quote(","));

		System.out.println("Set up instructions: " + setUpInstructions);

		numRows = Integer.parseInt(size[0]);
		numCols = Integer.parseInt(size[1]);

		System.out.println("Got num of rows: " + numRows);
		System.out.println("Got num of cols: " + numCols);

		this.setPreferredSize(new Dimension(numCols * 20, numRows * 20));

		frame.setResizable(true);

		frame.pack();

		frame.setVisible(true);
	}

	public void setGameData(String s) {
		gameData = s.substring(s.indexOf(":") + 1);
	}

	public void paintComponent(Graphics g) {

		if (gameRunning) {

			if (gameData.length() > 0) {

				g.setColor(Color.BLACK);
				g.fillRect(0, 0, getWidth(), getHeight());

				System.out.println("GAMEDATA: " + gameData);

				int tileWidth = (int) ((double) getWidth() / numCols);
				int tileHeight = (int) ((double) getHeight() / numRows);

				int tileSize = Math.min(tileWidth, tileHeight);

				String[] playerAndPillData = gameData.split(Pattern.quote("$^$"));

				System.out.println("Player data: " + playerAndPillData[0]);
				System.out.println("Pill data: " + playerAndPillData[1]);

				if (!playerAndPillData[0].isEmpty()) {

					String[] perPlayerData = playerAndPillData[0].split(Pattern.quote("$@$"));
					
					for (String curPlayer : perPlayerData) {

						String[] playerAndSnake = curPlayer.split(Pattern.quote("$:$"));

						System.out.println("P&S[0]: " + playerAndSnake[0]);
						System.out.println("P&S[1]: " + playerAndSnake[1]);

						System.out.println("Me: " + player);
						System.out.println("Sn: " + playerAndSnake[0]);
						
						boolean isMe = false;

						if (isSameConnection(player, playerAndSnake[0])) {
							isMe = true;
						}

						String[] snakeParts = playerAndSnake[1].split(Pattern.quote("$%$"));

						boolean head = true;
						
						for (String snakePart : snakeParts) {
							String[] rowCol = snakePart.split(Pattern.quote("$#$"));

							int row = Integer.parseInt(rowCol[0]);
							int col = Integer.parseInt(rowCol[1]);
							
							if (isMe) {
								if (head) {
									g.setColor(Color.BLUE.brighter().brighter());
								} else {
									g.setColor(Color.BLUE);
								}
							} else {
								g.setColor(Color.RED);
							}

							g.fillRect(col * tileSize, row * tileSize, tileSize, tileSize);
							head = false;
						}
					}

					g.setColor(Color.ORANGE);

					for (String perPillData : playerAndPillData[1].split(Pattern.quote("$%$"))) {
						String[] pillRowCol = perPillData.split(Pattern.quote("$#$"));

						int row = Integer.parseInt(pillRowCol[0]);
						int col = Integer.parseInt(pillRowCol[1]);

						g.fillOval(col * tileSize, row * tileSize, tileSize, tileSize);
					}

				}
				
				g.setColor(Color.WHITE);
				g.drawRect(0, 0, tileSize * numCols, tileSize * numRows);
				
			}
		} else {
			g.setColor(Color.GRAY);
			g.fillRect(0, 0, getWidth(), getHeight());

			String middleWindowString = "";

			if (countingDown) {
				middleWindowString = "A New Game Will Start In " + countdown + " Seconds";
			} else {
				middleWindowString = "There Are Not Enough Players To Start A New Game";
			}

			g.setColor(Color.RED);

			Font oldFont = g.getFont();

			g.setFont(new Font(oldFont.getFontName(), oldFont.getStyle(), 30));

			FontMetrics metrics = g.getFontMetrics();

			int stringWidth = metrics.stringWidth(middleWindowString);

			g.drawString(middleWindowString, (getWidth() / 2) - (stringWidth) / 2, getHeight() / 2);
		}
	}

	private boolean isSameConnection(String str1, String str2) {

		str1 = str1.substring(str1.indexOf(",") + 1);
		str2 = str2.substring(str2.indexOf(",") + 1);

		String port1 = str1.substring(str1.indexOf("=") + 1, str1.indexOf(","));
		String localport1 = str1.substring(str1.lastIndexOf("=") + 1, str1.lastIndexOf("]"));

		String port2 = str2.substring(str2.indexOf("=") + 1, str2.indexOf(","));
		String localport2 = str2.substring(str2.lastIndexOf("=") + 1, str2.lastIndexOf("]"));

		if (port1.equals(localport2) && port2.equals(localport1)) {
			return true;
		}
		return false;
	}

	@Override
	public void keyPressed(KeyEvent e) {
		if (e.getKeyChar() == 'w') {
			client.setNextDir(Direction.UP);
		}

		if (e.getKeyChar() == 's') {
			client.setNextDir(Direction.DOWN);
		}

		if (e.getKeyChar() == 'a') {
			client.setNextDir(Direction.LEFT);
		}

		if (e.getKeyChar() == 'd') {
			client.setNextDir(Direction.RIGHT);
		}
	}

	@Override
	public void keyReleased(KeyEvent e) {
	}

	@Override
	public void keyTyped(KeyEvent e) {
	}

	public void setGameRunning(boolean gameRunning) {
		this.gameRunning = gameRunning;
	}
}
