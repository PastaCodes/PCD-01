package pcd.pooolTaskOriented.model;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class SimulationCoordinator extends Thread {

    private final Board board;
    private final GameState gameState;
    private final List<BoardObserver> observers;
    private final ExecutorService exec;

    public SimulationCoordinator(
            Board board,
            List<BoardObserver> observers,
            int poolSize
    ) {
        this.board = board;
        this.gameState = board.getState();
        this.observers = List.copyOf(observers);
        this.exec = Executors.newFixedThreadPool(poolSize);
    }

    @Override
    public void run() {
        long nTicks = 0;
        long t0 = System.currentTimeMillis();
        long lastUpdateTime = System.currentTimeMillis();
        long tickPerSec = 0;
        while (!gameState.isGameOver()) {
            long elapsed = System.currentTimeMillis() - lastUpdateTime;
            lastUpdateTime = System.currentTimeMillis();

            this.updateState(elapsed);

            nTicks++;
            tickPerSec = 0;
            long dt = (System.currentTimeMillis() - t0);
            if (dt > 0) {
                tickPerSec = nTicks*1000/dt;
            }
            notifyObservers(tickPerSec);
        }
        for (var o : observers) {
            o.gameOver(board.getBoardViewInfo(), gameState.getGameStateViewInfo(), tickPerSec, gameState.getGameResult());
        }
        exec.shutdown();
    }

    private void updateState(long dt) {
        var allBalls = gameState.getAllBalls();

        withTasks(tasks -> {
            for (var ball : allBalls) {
                tasks.accept(new SimulationTasks.PositionUpdate(ball, dt, board));
            }
        });

        withTasks(tasks -> {
            for (var ball : allBalls) {
                for (var hole : board.getHoles()) {
                    tasks.accept(new SimulationTasks.HoleResolution(ball, hole, gameState));
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

        withTasks(tasks -> {
            for (int i = 0; i < allBallsAfter.size() - 1; i ++) {
                tasks.accept(new SimulationTasks.CollisionResolution(allBallsAfter, i));
            }
        });
    }

    private void withTasks(Consumer<Consumer<Runnable>> taskCreator) {
        var subtasks = new ArrayList<Callable<Void>>();
        taskCreator.accept(newTask -> subtasks.add(() -> {
			newTask.run();
			return null;
		}));
		try {
			exec.invokeAll(subtasks);
	    } catch (InterruptedException ignored) {}
	}

    private void setEndGame() {
        int humanScore = gameState.getHumanScore();
        int botScore = gameState.getBotScore();
        String gameResult = humanScore > botScore ? "Human wins! " + humanScore + " - " + botScore
            : botScore > humanScore ? "Bot wins! " + botScore + " - " + humanScore
            : "Draw! " + humanScore + " - " + botScore;
        gameState.endGame(gameResult);
    }

    private void notifyObservers(long tickPerSec) {
        for (var o: observers) {
            o.modelUpdated(board.getBoardViewInfo(), gameState.getGameStateViewInfo(), tickPerSec);
        }
    }

}
