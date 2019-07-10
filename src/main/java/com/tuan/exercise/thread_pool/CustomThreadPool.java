package com.tuan.exercise.thread_pool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadPool implements ICustomeThreadPool {

    private int mCorePoolSize;
    private int mMaxPoolSize;
    private AtomicInteger mActiveThreads;
    private AtomicInteger mAssignedThreads;
    private List<WorkerThread> mWorkers;
    private Queue<Runnable> mAssignPoint;

    private final ManagementThread mManager;

    public CustomThreadPool(int corePoolSize, int maxPoolSize) {
        mCorePoolSize = corePoolSize;
        mMaxPoolSize = maxPoolSize;
        mWorkers = new ArrayList<>();
        mAssignPoint = new LinkedList<>();

        mAssignedThreads = new AtomicInteger(0);
        for (int i = 0; i < corePoolSize; i++) {
            mWorkers.add(new WorkerThread("Thread-" + (i + 1), true));
            mWorkers.get(i).start();
        }
        mActiveThreads = new AtomicInteger(mWorkers.size());

        // start management thread
        mManager = new ManagementThread();
        mManager.start();
    }

    @Override
    public void execute(Runnable task) {
        try {
            if (mAssignedThreads.get() >= mMaxPoolSize) {
                throw new RejectedExecutionException("FULL");
            }

            synchronized (mAssignPoint) {
                if (!mAssignPoint.isEmpty()) {
                    try {
                        mAssignPoint.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mAssignPoint.offer(task);
                mAssignPoint.notifyAll();
            }
        } catch (RejectedExecutionException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void shutdown() {
        // put worker threads in closable state
        for (WorkerThread worker : mWorkers) {
            worker.close();
        }
    }

    @Override
    public int getActiveThread() {
        return mActiveThreads.get();
    }

    @Override
    public int getAssignedThread() {
        return mAssignedThreads.get();
    }

    @Override
    public int getCorePoolSize() {
        return mCorePoolSize;
    }

    @Override
    public int getMaxPoolSize() {
        return mMaxPoolSize;
    }

    private class ManagementThread extends Thread {

        @Override
        public void run() {
            while (true) {
                if (mWorkers.isEmpty()) {
                    break;
                }

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                synchronized (mAssignPoint) {
                    if (!mAssignPoint.isEmpty()) {
                        if (mAssignedThreads.get() >= mCorePoolSize && mActiveThreads.get() < mMaxPoolSize) {
                            // add extra thread when all core threads are assigned
                            WorkerThread workerThread = new WorkerThread("Thread-" + mActiveThreads.getAndIncrement(),
                                    false);
                            mWorkers.add(workerThread);
                            workerThread.start();
                        }
                    }
                }

                for (Iterator<WorkerThread> it = mWorkers.iterator(); it.hasNext();) {
                    if (!it.next().isAlive()) {
                        it.remove();
                        System.out.println("REMAIN: " + mActiveThreads.get() + ", WORKERS: " + mWorkers.size());
                    }
                }

            }
        }
    }

    private class WorkerThread extends Thread {

        private AtomicBoolean mActive;
        private boolean mIsCore;

        public WorkerThread(String name, boolean isCore) {
            super(name);
            mActive = new AtomicBoolean(true);
            mIsCore = isCore;
        }

        public void run() {
            while (mActive.get()) {
                Runnable task;
                synchronized (mAssignPoint) {
                    task = mAssignPoint.poll();
                    mAssignPoint.notifyAll();
                }

                if (task != null) {
                    mAssignedThreads.incrementAndGet();
                    task.run();
                    mAssignedThreads.decrementAndGet();
                } else {
                    if (mAssignedThreads.get() < mCorePoolSize && !mIsCore) {
                        mActiveThreads.decrementAndGet();
                        System.out.println(getName() + " is not needed no more");
                        break;
                    }

                    synchronized (mAssignPoint) {
                        try {
                            mAssignPoint.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        public void close() {
            mActive.set(false);
        }
    }

}
