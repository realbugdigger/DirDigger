package core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PausableThreadPoolExecutor extends ThreadPoolExecutor {

    private static final Logger log = LoggerFactory.getLogger(PausableThreadPoolExecutor.class);

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
        log.debug("[BEFORE-THREAD-EXEC] Num of threads in a pool: {}, num of tasks in queue: {}", getPoolSize(), getQueue().size());
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
