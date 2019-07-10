package com.tuan.exercise.thread_pool;

public interface ICustomeThreadPool {

    public void execute(Runnable task);
    
    public void shutdown();
    
    public int getActiveThread();
    
    public int getAssignedThread();
    
    public int getCorePoolSize();
    
    public int getMaxPoolSize();
}
