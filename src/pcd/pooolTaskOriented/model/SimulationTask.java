package pcd.pooolTaskOriented.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.function.Consumer;

public class SimulationTask extends RecursiveAction {

	private final Board board;
	private final GameState gameState;
	private final long dt;

	public SimulationTask(Board board, GameState gameState, long dt) {
		this.board = board;
		this.gameState = gameState;
		this.dt = dt;
	}

	private class PositionUpdateSubtask extends RecursiveAction {

		private final Ball ball;

		private PositionUpdateSubtask(Ball ball) {
			this.ball = ball;
		}

		@Override
		protected void compute() {
			ball.updateState(dt, board);
		}

	}

	private class HoleResolutionSubtask extends RecursiveAction {

		private final Ball ball;
		private final Hole hole;

		private HoleResolutionSubtask(Ball ball, Hole hole) {
			this.ball = ball;
			this.hole = hole;
		}

		@Override
		protected void compute() {
			Ball.resolveHole(ball, hole, gameState);
		}

	}

	private static class CollisionResolutionSubtask extends RecursiveAction {

		private final List<Ball> allBalls;
		private final int i;

		private CollisionResolutionSubtask(List<Ball> allBalls, int i) {
			this.allBalls = allBalls;
			this.i = i;
		}

		@Override
		protected void compute() {
			for (int j = i + 1; j < allBalls.size(); j++) {
				Ball.resolveCollision(allBalls.get(i), allBalls.get(j));
			}
		}

	}

	@Override
	protected void compute() {
		assert ForkJoinTask.inForkJoinPool();

		var allBalls = gameState.getAllBalls();

		withSubtasks(tasks -> {
			for (var ball : allBalls) {
				tasks.accept(new PositionUpdateSubtask(ball));
			}
		});

		withSubtasks(tasks -> {
			for (var ball : allBalls) {
				for (var hole : board.getHoles()) {
					tasks.accept(new HoleResolutionSubtask(ball, hole));
				}
			}
		});

		if (gameState.isGameOver())
			return;

		var smallBalls = gameState.getSmallBalls();
		if (smallBalls.isEmpty()) {
			setEndGame();
			return;
		}

		var allBallsAfter = gameState.getAllBalls();

		withSubtasks(tasks -> {
			for (int i = 0; i < allBallsAfter.size() - 1; i ++) {
				tasks.accept(new CollisionResolutionSubtask(allBallsAfter, i));
			}
		});
	}

	private void withSubtasks(Consumer<Consumer<RecursiveAction>> subtaskCreator) {
		var subtasks = new ArrayList<RecursiveAction>();
		subtaskCreator.accept(subtasks::add);
		invokeAll(subtasks);
	}

	private void setEndGame() {
        int humanScore = gameState.getHumanScore();
        int botScore = gameState.getBotScore();
        String gameResult = humanScore > botScore ? "Human wins! " + humanScore + " - " + botScore
                : botScore > humanScore ? "Bot wins! " + botScore + " - " + humanScore
                : "Draw! " + humanScore + " - " + botScore;
        gameState.endGame(gameResult);
    }

}
