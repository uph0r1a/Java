public class Main {
    // public static class SharedQueue {
    // private int data;
    // private boolean hasData = false;

    // public synchronized int consume() throws InterruptedException {
    // while (!hasData) {
    // System.out.println("Consumer dang cho du lieu");
    // wait();
    // }

    // System.out.println("Consumer lay du lieu: " + data);
    // hasData = false;
    // return data;
    // }

    // public synchronized void produce(int value) throws InterruptedException {
    // while (hasData) {
    // System.out.println("Producer dang cho Consumer lay het du lieu");
    // wait();
    // }

    // this.data = value;
    // hasData = true;
    // System.out.println("Producer dua du lieu: " + value);
    // notify();
    // }
    // }

    // private static final Object lockA = new Object();
    // private static final Object lockB = new Object();

    public static class Chat {
        boolean flag = false;

        public synchronized void Question(String msg) {
            if (flag) {
                try {
                    wait();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println(msg);
            flag = true;
            notify();
        }

        public synchronized void Answer(String msg) {
            if (!flag) {
                try {
                    wait();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            System.out.println(msg);
            flag = false;
            notify();
        }
    }

    public static class Anna implements Runnable {
        Chat m;
        String[] s1 = { "Hi", "How are you?", "Im also doing fine" };

        public Anna(Chat m1) {
            this.m = m1;
            new Thread(this, "Questons").start();
        }

        public void run() {
            for (int i = 0; i < s1.length; i++) {
                m.Answer("Anna: " + s1[i]);
            }
        }
    }

    public static class Michael implements Runnable {
        Chat m;
        String[] s2 = { "Hi", "Im good, what abt u", "Great" };

        public Michael(Chat m2) {
            this.m = m2;
            new Thread(this, "Answer").start();
        }

        public void run() {
            for (int i = 0; i < s2.length; i++) {
                m.Answer("Michael: " + s2[i]);
            }
        }
    }

    public static void main(String[] args) {
        // SharedQueue queue = new SharedQueue();

        // Thread producer = new Thread(() -> {
        // try {
        // for (int i = 1; i <= 5; i++) {
        // queue.produce(i * 10);
        // Thread.sleep(500);
        // }
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
        // });

        // Thread consumer = new Thread(() -> {
        // try {
        // for (int i = 1; i <= 5; i++) {
        // queue.consume();
        // Thread.sleep(800);
        // }
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
        // });

        // producer.start();
        // consumer.start();

        // Thread t2 = new Thread(() ->{
        // synchronized (lockB){
        // System.out.println("Thread 2 dang giu lock A");
        // try {
        // Thread.sleep(200);
        // } catch (Exception e) {
        // e.printStackTrace();
        // }
        // System.out.println("Thread 2 dang cho lock A");
        // synchronized(lockA){
        // System.out.println("Thread lay duoc ca 2 lock");
        // }
        // }
        // });
        Chat m = new Chat();
        new Anna(m);
        new Michael(m);
    }
}
