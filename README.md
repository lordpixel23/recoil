# recoil

A collection of Java utility code

ThreadLocalExecutor – many applications use ThreadLocals for contextual state. Sometimes you want that state to follow work as it moves between Threads. For the simplest cases InheritableThreadLocal will get the job done. However, if your application uses an ExecutorService thread pool rather than creating its own threads, this utility can help by making sure your key ThreadLocals are set when Runnable and Callable tasks execute in the thread pool.   
