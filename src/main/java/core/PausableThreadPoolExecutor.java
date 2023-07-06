package core;

import burp.api.montoya.logging.Logging;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PausableThreadPoolExecutor extends ThreadPoolExecutor {

    private Logging logging;

    private final AtomicBoolean isPaused;
    private final CyclicBarrier barrier;

    public PausableThreadPoolExecutor(
        int corePoolSize,
        int maximumPoolSize,
        long keepAliveTime,
        TimeUnit unit,
        BlockingQueue<Runnable> workQueue,
        ThreadFactory threadFactory,
        int numberOfThreadsToPause,
        Logging logging) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
            this.isPaused = new AtomicBoolean(false);
            this.barrier = new CyclicBarrier(numberOfThreadsToPause);

            this.logging = logging;
    }

    public void pause() {
        isPaused.set(true);
    }

    public void resume() {
        isPaused.set(false);
        barrier.reset();
    }

    public boolean isPaused() {
        return isPaused.get();
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        logging.logToOutput("[BEFORE-THREAD-EXEC] Num of threads in a pool: " + getPoolSize() + ", num of tasks in queue: " + getQueue().size());
        super.beforeExecute(t, r);
        if (isPaused.get()) {
            try {
                barrier.await();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
