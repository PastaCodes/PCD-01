package pcd.pooolTaskOriented.model;

import pcd.pooolTaskOriented.util.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SimulationCoordinator extends Thread {

    private final Board board;
    private final GameState gameState;
    private final List<BoardObserver> observers;
    private final ExecutorService exec;
    private final int nTasks;

    public SimulationCoordinator(
            Board board,
            List<BoardObserver> observers,
            int nWorker,
            int nTasks
    ) {
        this.board = board;
        this.gameState = board.getState();
        this.observers = List.copyOf(observers);
        this.exec = Executors.newFixedThreadPool(nWorker);
        this.nTasks = nTasks;
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
        distributeLinearWork(allBalls, ball -> ball.updateState(dt, board));

        distributeLinearWork(allBalls, ball -> {
            for (var hole : board.getHoles()) {
                Ball.resolveHole(ball, hole, gameState);
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
        int nActualTasks = Math.min(nTasks, allBallsAfter.size());

        distributeWork(taskIndex -> {
            for (int i = taskIndex; i < allBallsAfter.size() - 1; i += nActualTasks) {
                for (int j = i + 1; j < allBallsAfter.size(); j++) {
                    Ball.resolveCollision(allBallsAfter.get(i), allBallsAfter.get(j));
                }
            }
        }, nActualTasks);
    }

    public void distributeWork(Consumer<Integer> action, int nActualTasks) {
        var tasks = new ArrayList<Callable<Void>>();
        for (int i = 0; i < nActualTasks; i++) {
            int taskIndex = i;
            tasks.add(() -> {
                action.accept(taskIndex);
                return null;
            });
        }
        try {
            exec.invokeAll(tasks);
        } catch (Exception ignored) {}
    }

    public void distributeLinearWork(List<Ball> balls, Consumer<Ball> action) {
        int totalSize = balls.size();

        int nActualTasks = Math.min(nTasks, totalSize);
        int workAmount = totalSize / nActualTasks;

        distributeWork(taskIndex -> {
            int start = taskIndex * workAmount;
            // L'ultima task prende tutti gli elementi fino alla fine della lista,
            // includendo il resto della divisione
            int end = (taskIndex == nActualTasks - 1) ? totalSize : start + workAmount;
            for (int j = start; j < end; j++) {
                action.accept(balls.get(j));
            }
        }, nActualTasks);
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
