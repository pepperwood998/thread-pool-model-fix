package com.tuan.exercise.thread_pool;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class CustomThreadPool implements ICustomeThreadPool {

    private int mCorePoolSize;
    private int mMaxPoolSize;
    private AtomicInteger mAssignedThreadNum;
    private int mRejectedTaskNum;
    private List<WorkerThread> mWorkers;
    private RunnableEntry mEntry;

    private final ManagementThread mManager;

    public CustomThreadPool(int corePoolSize, int maxPoolSize) {
        mCorePoolSize = corePoolSize;
        mMaxPoolSize = maxPoolSize;
        mWorkers = new ArrayList<>();
        mEntry = new RunnableEntry();
        mRejectedTaskNum = 0;

        mAssignedThreadNum = new AtomicInteger(0);
        for (int i = 0; i < corePoolSize; i++) {
            mWorkers.add(new WorkerThread("Thread-" + (i + 1), true));
            mWorkers.get(i).start();
        }

        // start management thread
        mManager = new ManagementThread();
        mManager.start();
    }

    @Override
    public void execute(Runnable task) {
        try {
            if (mAssignedThreadNum.get() >= mMaxPoolSize) {
                throw new RejectedExecutionException("# Rejected Tasks: " + ++mRejectedTaskNum);
            }

            synchronized (mEntry) {
                // if a task has not been assigned to a worker-thread
                if (mEntry.mAssignPoint != null) {
                    try {
                        mEntry.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mEntry.mAssignPoint = task;
                // notify for waiting threads to get new task
                mEntry.notifyAll();
            }
        } catch (RejectedExecutionException e) {
            System.out.println(e.getMessage());
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
        return mWorkers.size();
    }

    @Override
    public int getAssignedThread() {
        return mAssignedThreadNum.get();
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
                if (mWorkers.isEmpty())
                    break;

                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                synchronized (mEntry) {
                    if (mEntry.mAssignPoint != null) {
                        if (mAssignedThreadNum.get() >= mCorePoolSize && mWorkers.size() < mMaxPoolSize) {
                            // add extra thread when all core threads are assigned
                            WorkerThread workerThread = new WorkerThread("Thread-" + (mWorkers.size() + 1), false);
                            mWorkers.add(workerThread);
                            workerThread.start();
                        }
                    }
                }

                // remove the worker thread, if its loop ended
                for (Iterator<WorkerThread> it = mWorkers.iterator(); it.hasNext();) {
                    WorkerThread worker = it.next();
                    if (!worker.isAlive()) {
                        it.remove();
                        System.out.println("Worker " + worker.getName() + " is removed");
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
                synchronized (mEntry) {
                    task = mEntry.mAssignPoint;
                    mEntry.mAssignPoint = null;
                    // notify that the task is assigned, new task can come into the assign-point
                    mEntry.notifyAll();
                }

                if (task != null) {
                    mAssignedThreadNum.incrementAndGet();
                    task.run();
                    mAssignedThreadNum.decrementAndGet();
                } else {
                    if (mAssignedThreadNum.get() < mCorePoolSize && !mIsCore) {
                        System.out.println("Extra worker " + getName() + " is not needed no more");
                        break;
                    }

                    // if this thread is not assigned, wait for incoming task
                    synchronized (mEntry) {
                        try {
                            mEntry.wait();
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
