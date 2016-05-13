package server;

import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class Game {

	private HashMap<Socket, ArrayList<int[]>> snakes;

	private ArrayList<int[]> pillPos;

	public final int ROWS, COLS;

	final private Server server;

	public Game(final Server server, final int rows, final int cols, final int numOfPills) {
		this.server = server;

		this.ROWS = rows;
		this.COLS = cols;

		snakes = new HashMap<Socket, ArrayList<int[]>>();
		pillPos = new ArrayList<int[]>();

		for (int i = 0; i < numOfPills; i++) {
			placePill();
		}
	}

	public void addPlayer(final Socket player) {

		System.out.println("Adding snake parts for player: " + player);

		ArrayList<int[]> newPlayer = new ArrayList<int[]>();

		boolean okPos = false;

		Direction newDir = null;

		while (!okPos) {

			okPos = true;

			int[] newHeadPos = new int[] { (int) (Math.random() * (ROWS - 14)) + 7,
					(int) (Math.random() * (COLS - 14)) + 7 };
			newDir = Direction.values()[(int) (Math.random() * Direction.values().length)];

			for (int i = 0; i < 5; i++) {
				if (newDir == Direction.DOWN) {
					newPlayer.add(new int[] { newHeadPos[0] - i, newHeadPos[1] });
					System.out.println("Added snake part: Row: " + (newHeadPos[0] - i) + " Col: " + newHeadPos[1]);
				}
				if (newDir == Direction.UP) {
					newPlayer.add(new int[] { newHeadPos[0] + i, newHeadPos[1] });
					System.out.println("Added snake part: Row: " + (newHeadPos[0] + i) + " Col: " + newHeadPos[1]);
				}
				if (newDir == Direction.LEFT) {
					newPlayer.add(new int[] { newHeadPos[0], newHeadPos[1] + i });
					System.out.println("Added snake part: Row: " + newHeadPos[0] + " Col: " + (newHeadPos[1] + i));
				}
				if (newDir == Direction.RIGHT) {
					newPlayer.add(new int[] { newHeadPos[0], newHeadPos[1] - i });
					System.out.println("Added snake part: Row: " + newHeadPos[0] + " Col: " + (newHeadPos[1] - i));
				}
			}

			for (int[] snakePartPos : newPlayer) {
				if (isNearSnake(snakePartPos)) {
					okPos = false;
					newPlayer.clear();
					break;
				}
			}
		}
		server.sendMessage(player, "START DIR:" + newDir.toString());
		snakes.put(player, newPlayer);
	}

	public void move(final Socket player, final Direction dir) {
		ArrayList<int[]> snake = snakes.get(player);

		int oldHeadRow = snake.get(0)[0];
		int oldHeadCol = snake.get(0)[1];

		switch (dir) {
		case UP:
			snake.add(0, new int[] { oldHeadRow - 1, oldHeadCol });
			break;
		case DOWN:
			snake.add(0, new int[] { oldHeadRow + 1, oldHeadCol });
			break;
		case LEFT:
			snake.add(0, new int[] { oldHeadRow, oldHeadCol - 1 });
			break;
		case RIGHT:
			snake.add(0, new int[] { oldHeadRow, oldHeadCol + 1 });
			break;
		}

		int[] snakeHead = snake.get(0);

		boolean atePill = false;

		for (int i = 0; i < pillPos.size(); i++) {

			int[] curPillPos = pillPos.get(i);

			if (snakeHead[0] == curPillPos[0] && snakeHead[1] == curPillPos[1]) {
				atePill = true;
				pillPos.remove(i);
				i--;
				break;
			}
		}

		if (!atePill) {
			snake.remove(snake.size() - 1);
		} else {
			placePill();
		}
	}

	private void placePill() {
		boolean okPos = false;

		int[] newPillPos = null;

		while (!okPos) {

			int randRow = (int) (Math.random() * ROWS);
			int randCol = (int) (Math.random() * COLS);

			newPillPos = new int[] { randRow, randCol };

			if (!isNearSnake(newPillPos)) {
				okPos = true;
			}

			for (int[] otherPills : pillPos) {
				if (newPillPos[0] == otherPills[0] && newPillPos[1] == otherPills[1]) {
					okPos = false;
					break;
				}
			}
		}

		pillPos.add(newPillPos);
	}

	private boolean isNearSnake(int[] pos) {

		for (ArrayList<int[]> curSnake : snakes.values()) {

			for (int[] curTile : curSnake) {

				if (Math.abs(curTile[0] - pos[0]) <= 1) {
					return true;
				}

				if (Math.abs(curTile[1] - pos[1]) <= 1) {
					return true;
				}
			}
		}
		return false;
	}

	public void removeLosers() {

		Collection<ArrayList<int[]>> localSnakes = snakes.values();

		ArrayList<Socket> playersToRemove = new ArrayList<Socket>();

		System.out.println("REMOVING LOSERS");

		for (ArrayList<int[]> curSnake : localSnakes) {

			System.out.println("TESTING LOSS OF SNAKE: " + curSnake);

			int headRow = curSnake.get(0)[0];
			int headCol = curSnake.get(0)[1];

			if (headRow < 0 || headRow >= ROWS) {
				for (Socket curPlayer : snakes.keySet()) {
					if (snakes.get(curPlayer).equals(curSnake)) {
						System.out.println("REMOVE SNAKE FOR (ROWS): " + curPlayer);
						playersToRemove.add(curPlayer);
					}
				}
			}

			if (headCol < 0 || headCol >= COLS) {
				for (Socket curPlayer : snakes.keySet()) {
					if (snakes.get(curPlayer).equals(curSnake)) {
						System.out.println("REMOVE SNAKE FOR (COLS): " + curPlayer);
						playersToRemove.add(curPlayer);
					}
				}
			}

			for (ArrayList<int[]> otherSnake : localSnakes) {

				boolean curTileIsOtherHead = true;

				for (int[] curTile : otherSnake) {
					if (headRow == curTile[0] && headCol == curTile[1]) {
						if (!curSnake.equals(otherSnake) || (curSnake.equals(otherSnake) && !curTileIsOtherHead)) {
							System.out.println("LOSS TRIGGERED ON SNAKE: " + curSnake + " Other Snake: " + otherSnake);
							for (Socket curPlayer : snakes.keySet()) {
								if (snakes.get(curPlayer).equals(curSnake)) {
									System.out.println("REMOVE SNAKE FOR: " + curPlayer);
									playersToRemove.add(curPlayer);
								}
							}
						}
					}
					curTileIsOtherHead = false;
				}
			}
		}

		for (Socket s : playersToRemove) {
			removePlayer(s);
		}
	}

	public String getGameState() {

		String gameState = "";

		for (Socket player : snakes.keySet()) {

			gameState += player + "$:$";

			for (int[] bodyPart : snakes.get(player)) {

				gameState += bodyPart[0] + "$#$" + bodyPart[1] + "$%$";
			}

			gameState = gameState.substring(0, gameState.length() - 3);

			gameState += "$@$";

		}

		if (gameState.length() != 0) {
			gameState = gameState.substring(0, gameState.length() - 3);
		}

		gameState += "$^$";

		for (int[] pill : pillPos) {
			gameState += pill[0] + "$#$" + pill[1] + "$%$";
		}

		if (gameState.length() != 0) {
			gameState = gameState.substring(0, gameState.length() - 3);
		}

		return gameState;
	}

	public void removePlayer(final Socket player) {
		snakes.remove(player);
	}

	public boolean playerAlive(final Socket player) {
		return snakes.containsKey(player);
	}

	public boolean isGameOver() {

		int minSnakes = 1;

		if (server.clientsConnected() == 1) {
			minSnakes = 0;
		}

		return snakes.size() <= minSnakes;
	}
}
