public class Main {
    // public static class MyThread extends Thread {
    // public MyThread(String name) {
    // super(name);
    // }

    // @Override
    // public void run() {
    // for (int i = 1; i < 5; i++) {
    // System.out.println(getName() + " running round " + i);
    // try {
    // Thread.sleep(500);
    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // }
    // System.out.println(getName() + " ended");
    // }
    // }

    // public static class MyTask implements Runnable {
    // private String name;

    // public MyTask(String name) {
    // this.name = name;
    // }

    // @Override
    // public void run() {
    // for (int i = 1; i < 5; i++) {
    // System.out.println(name + " running round " + i);
    // try {
    // Thread.sleep(500);
    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // }
    // System.out.println(name + " ended");
    // }
    // }

    // public static class LifeCycleThread extends Thread {

    // @Override
    // public void run() {
    // System.out.println(">>>Thread running<<<");
    // try {
    // Thread.sleep(2000);
    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // System.out.println(">>> Thread about to end");
    // }
    // }

    // public static class Worker extends Thread {
    // private String name;

    // public Worker(String name) {
    // this.name = name
    // }

    // @Override
    // public void run() {
    // for (int i = 1; i <= 4; i++) {
    // System.out.println(name + " running round " + i);
    // try {
    // Thread.sleep(800);
    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // }
    // System.out.println(name + " ended");
    // }
    // }

    // public static class PriorityThread extends Thread {

    // public PriorityThread(String name) {
    // super(name);
    // }

    // @Override
    // public void run() {
    // for (int i = 1; i <= 4; i++) {
    // System.out.println(getName() + " | Priority = " + getPriority() + "| time " +
    // i);
    // try {
    // Thread.sleep(800);
    // } catch (Exception e) {
    // e.printStackTrace();
    // }
    // }
    // System.out.println(getName() + " ended");
    // }
    // }

    public static class PrintDemo {
        public void printCount() {
            try {
                for (int i = 5; i > 0; i--) {
                    System.out.println("Counter: " + i);
                }
            } catch (Exception e) {
                System.out.println("Stack interuppted");
            }
        }
    }

    public static class ThreadDemo extends Thread {
        private Thread t;
        private String threadName;
        PrintDemo pd;

        public ThreadDemo(String threadName, Main.PrintDemo pd) {
            this.threadName = threadName;
            this.pd = pd;
        }

        public void run() {
            synchronized (pd) {
                pd.printCount();
            }
            System.out.println("Thread " + threadName + " exitting");
        }

        public void start() {
            System.out.println("Starting: " + threadName);
            if (t == null) {
                t = new Thread(this, threadName);
                t.start();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // MyThread t1 = new MyThread("Thread Extends");
        // t1.start();
        // Thread t2 = new Thread(new MyTask("Thread Runnable"));
        // t2.start();
        // LifeCycleThread t = new LifeCycleThread();
        // System.out.println("1. Before start(): isAlive = "+t.isAlive());
        // t.start();
        // System.out.println("2. After start(): isAlive = "+t.isAlive());
        // t.join();
        // System.out.println("3. After thread end: isAlive = "+ t.isAlive());

        // Worker t1 = new Worker("Thread 1");
        // Worker t2 = new Worker("Thread 2");

        // t1.start();
        // t1.join();
        // System.out.println(">>>Thread 1 is done, start Thread 2");
        // t2.start();
        // t2.join();
        // System.out.println(">>>All is done");

        // PriorityThread t1 = new PriorityThread("Low Priority");
        // PriorityThread t2 = new PriorityThread("Normal Priority");
        // PriorityThread t3 = new PriorityThread("High Priority");
        // t1.setPriority(Thread.MIN_PRIORITY);
        // t2.setPriority(Thread.NORM_PRIORITY);
        // t3.setPriority(Thread.MAX_PRIORITY);

        PrintDemo pd = new PrintDemo();
        ThreadDemo t1 = new ThreadDemo("Thread 1", pd);
        ThreadDemo t2 = new ThreadDemo("Thread 2", pd);
        t1.start();
        t2.start();
        try {
            t1.join();
            t2.join();
        } catch (Exception e) {
            System.out.println("Interuppted");
        }
    }
}
