package experiments;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.io.IOException;

import experiments.IFairnessApplicationJava.IFairnessApplication;

public class FairnessUtil
{
    /**
       how many ms to wait before requesting b to dump its tasks after
       a has started its tasks
    */
    final static int A_HEADSTART_TIME_MS = 4;

    /**
       extra debugging flag: something for us to watch out for in case
       we had an exception.
    */
    final public static AtomicBoolean had_exception =
        new AtomicBoolean(false);

    /**
       These identifiers are associated with each endpoint and appear
       (in order) in the threadsafe queue.
    */
    final static String PRINCIPAL_A_IDENTIFIER = "1";
    final static String PRINCIPAL_B_IDENTIFIER = "0";

    
    /**
       Dump num_external_calls operations into system using app_a.
       Pause to ensure that side_a registers all of them.  Then dump
       num_external_calls into system using app_b.
     */
    public static void run_operations(
        IFairnessApplication app_a, IFairnessApplication app_b,String switch_id,
        int num_external_calls,ConcurrentLinkedQueue<String> tsafe_queue)
    {
        PrincipalTask task_a =
            new PrincipalTask(
                app_a,PRINCIPAL_A_IDENTIFIER,switch_id,tsafe_queue);
        PrincipalTask task_b =
            new PrincipalTask(
                app_b,PRINCIPAL_B_IDENTIFIER,switch_id,tsafe_queue);

        ExecutorService executor_a = create_executor();
        ExecutorService executor_b = create_executor();
        
        // put a bunch of tasks rooted at A into system.
        for (int i = 0; i < num_external_calls; ++i)
            executor_a.execute(task_a);

        // give those tasks a head start to get started before b can
        // start its tasks
        try {
            Thread.sleep(A_HEADSTART_TIME_MS);
        } catch (InterruptedException _ex) {
            _ex.printStackTrace();
            had_exception.set(true);
            assert(false);
            return;
        }
        
        // put a bunch of tasks rooted at B into system
        for (int i = 0; i < num_external_calls; ++i)
            executor_b.execute(task_b);
        
        // join on executor services
        executor_a.shutdown();
        executor_b.shutdown();
        while (!(executor_a.isTerminated() && executor_b.isTerminated()))
        {
            try
            {
                Thread.sleep(50);
            }
            catch (InterruptedException _ex)
            {
                _ex.printStackTrace();
                had_exception.set(true);
                assert(false);
                break;
            }
        }
    }

    public static void write_results(
        String result_filename,ConcurrentLinkedQueue<String> tsafe_queue)
    {
        String to_write = "";
        for (String endpoint_id : tsafe_queue)
            to_write += endpoint_id + ",";

        if (had_exception.get())
            to_write += " **** THERE WAS AN EXCEPTION *** \n";
        Util.write_results_to_file(result_filename,to_write);
    }

    public static void print_usage()
    {
        String usage_string = "";

        // USE_WOUND_WAIT_ARG_INDEX
        usage_string +=
            "\n\t<boolean>: true if should run using wound-wait; " +
            "false if should use ralph scheduler.\n";
        
        // NUMBER_OPS_TO_RUN_ARG_INDEX
        usage_string +=
            "\n\t<int>: Number ops to run per experiment\n";

        // COLLECT_STATISTICS_ARG_INDEX
        usage_string +=
            "\n\t<int> : period for collecting individual switch stastics " +
            "in ms.  < 0 if should not collect any statistics\n";

        // OUTPUT_FILENAME_ARG_INDEX
        usage_string += "\n\t<String> : output filename\n";

        System.out.println(usage_string);
    }

    
    private static class PrincipalTask implements Runnable
    {
        private final IFairnessApplication app;
        private final String principal_id;
        private final String switch_id;
        private final ConcurrentLinkedQueue<String> tsafe_queue;

        private final static AtomicInteger unique_subnet_int =
            new AtomicInteger(0);
        
        public PrincipalTask(
            IFairnessApplication _app, String _principal_id, String _switch_id,
            ConcurrentLinkedQueue<String> _tsafe_queue)
        {
            app = _app;
            principal_id = _principal_id;
            switch_id = _switch_id;
            tsafe_queue = _tsafe_queue;
        }

        public void run ()
        {
            Integer unique_subnet = unique_subnet_int.getAndDecrement();
            int c_subnet = unique_subnet /256;
            int d_subnet = unique_subnet % 256;
            String ip_addr = "18.18." + c_subnet + "." + d_subnet;
            try
            {
                app.single_add(ip_addr);
                tsafe_queue.add(principal_id + "|" + System.nanoTime());
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
                had_exception.set(true);
            }
        }
    }
        

    private static ExecutorService create_executor()
    {
        ExecutorService executor = Executors.newCachedThreadPool(
            new ThreadFactory()
            {
                // each thread created is a daemon
                public Thread newThread(Runnable r)
                {
                    Thread t=new Thread(r);
                    t.setDaemon(true);
                    return t;
                }
            });
        return executor;
    }    
}