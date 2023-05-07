package core;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PausableThreadPoolExecutor extends ThreadPoolExecutor {

    private final AtomicBoolean isPaused;
    private final CyclicBarrier barrier;

    public PausableThreadPoolExecutor(
        int corePoolSize,
        int maximumPoolSize,
        long keepAliveTime,
        TimeUnit unit,
        BlockingQueue<Runnable> workQueue,
        ThreadFactory threadFactory,
        int numberOfThreadsToPause) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
            this.isPaused = new AtomicBoolean(false);
            this.barrier = new CyclicBarrier(numberOfThreadsToPause);
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
