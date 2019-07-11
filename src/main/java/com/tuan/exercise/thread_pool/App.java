package com.tuan.exercise.thread_pool;

public class App {

    static int corePoolSize = 3;
    static int maxPoolSize = 6;
    static int taskNum = 50;

    public static void main(String[] args) {
        CustomThreadPool customThreadPool = new CustomThreadPool(corePoolSize, maxPoolSize);

        for (int i = 1; i <= taskNum; i++) {
            Task task = new Task("Task " + i);

            customThreadPool.execute(task);
        }
        
        customThreadPool.shutdown();
    }
}

class Task implements Runnable {
    private String mName;

    public Task(String name) {
        this.mName = name;
    }

    public String getName() {
        return mName;
    }

    @Override
    public void run() {
        try {
            System.out.println(Thread.currentThread().getName() + " is executing: " + mName);
            Thread.sleep(2000);
            System.out.println(Thread.currentThread().getName() + " finished: " + mName);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
