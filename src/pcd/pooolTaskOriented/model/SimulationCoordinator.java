package pcd.pooolTaskOriented.model;

import java.util.List;
import java.util.concurrent.*;

public class SimulationCoordinator extends Thread {

    private final Board board;
    private final GameState gameState;
    private final List<BoardObserver> observers;
    private final ForkJoinPool exec;

    public SimulationCoordinator(
            Board board,
            List<BoardObserver> observers
    ) {
        this.board = board;
        this.gameState = board.getState();
        this.observers = List.copyOf(observers);
        this.exec = new ForkJoinPool();
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
        exec.invoke(new SimulationTask(board, gameState, dt));
    }

    private void notifyObservers(long tickPerSec) {
        for (var o: observers) {
            o.modelUpdated(board.getBoardViewInfo(), gameState.getGameStateViewInfo(), tickPerSec);
        }
    }

}
