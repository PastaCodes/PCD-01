package pcd.pooolTaskOriented.model;

import java.util.List;

public final class SimulationTasks {

	private SimulationTasks() {}

	public record PositionUpdate(Ball ball, long dt, Board board) implements Runnable {

		@Override
		public void run() {
			ball.updateState(dt, board);
		}

	}

	public record HoleResolution(Ball ball, Hole hole, GameState gameState) implements Runnable {

		@Override
		public void run() {
			Ball.resolveHole(ball, hole, gameState);
		}

	}

	public record CollisionResolution(List<Ball> allBalls, int i) implements Runnable {

		@Override
		public void run() {
			for (int j = i + 1; j < allBalls.size(); j++) {
				Ball.resolveCollision(allBalls.get(i), allBalls.get(j));
			}
		}

	}

}
