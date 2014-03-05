package experiments;

import java.util.HashSet;
import java.util.Set;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.io.IOException;

import java.util.List;
import java.util.ArrayList;

import ralph.NonAtomicInternalList;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.OffOnApplicationJava.OffOnApplication;

public class Util
{
    final static int TIME_TO_WAIT_AFTER_FIRST_SWITCH_MS = 12000;
    final static int FIRST_SWITCH_POLL_PERIOD_MS = 250;
    
    public static class HostPortPair
    {
        public String host = null;
        public int port = -1;
        /**
           @param {String} str_host_port_pair --- <hostname/ip>:<port>
         */
        public HostPortPair(String str_host_port_pair)
        {
            String [] split = str_host_port_pair.split(":");
            host = split[0];
            port = Integer.parseInt(split[1]);
        }
    }

    /**
       @param {String} csv_host_port_pairs --- Should have format:
       <hostname/ip>:<port>,<hostname/ip>:<port>,<hostname/ip>:<port>
     */
    public static Set<HostPortPair> parse_csv_host_port_pairs(
        String csv_host_port_pairs)
    {
        String [] pairs = csv_host_port_pairs.split(",");

        Set<HostPortPair> to_return = new HashSet<HostPortPair>();

        for (String pair : pairs)
            to_return.add(new HostPortPair(pair));
        return to_return;
    }


    public static Set<Integer> parse_csv_ports(
        String csv_ports)
    {
        String [] ports = csv_ports.split(",");
        Set<Integer> to_return = new HashSet<Integer>();
        for (String port : ports)
            to_return.add(Integer.parseInt(port));
        return to_return;
    }

    /**
       Force pronghorn+floodlight to shutdown.  This is not clean: it
       forces a bunch of exceptions to be thrown.  It would be nice to
       figure out a clean way of shutting down.
     */
    public static void force_shutdown()
    {
        System.exit(0);
    }
    

    public static void write_results_to_file(
        String filename, String to_write)
    {
        Writer w;
        try
        {
            w = new PrintWriter(new FileWriter(filename));
            w.write(to_write);
            w.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            assert(false);
        }
    }

    public static void wait_on_switches(GetNumberSwitches num_switches_app)
    {
        Double num_switches = null;
        while (true)
        {
            try
            {
                num_switches = num_switches_app.num_switches();
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                assert(false);
            }

            if (num_switches.doubleValue() != 0.)
                break;

            try
            {
                Thread.sleep(FIRST_SWITCH_POLL_PERIOD_MS);
            }
            catch (InterruptedException ex)
            {
                ex.printStackTrace();
                assert(false);
            }
        }

        // how long to wait to begin after see first switch.

        try
        {
            Thread.sleep(TIME_TO_WAIT_AFTER_FIRST_SWITCH_MS);
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
            assert(false);
        }
    }

    public static List<String> get_switch_id_list (GetNumberSwitches num_switches_app)
    {
        List<String> to_return = new ArrayList<String>();
        try
        {
            NonAtomicInternalList<String,String> switch_list =
                num_switches_app.switch_id_list();

            int num_switches = switch_list.get_len(null);
            if (num_switches == 0)
            {
                System.out.println(
                    "No switches attached to pronghorn: error");
                assert(false);
            }

            for (int i = 0; i < num_switches; ++i)
            {
                Double index_to_get_from = new Double(i);
                String switch_id =
                    switch_list.get_val_on_key(null,index_to_get_from);
                to_return.add(switch_id);
            }
        }
        catch (Exception _ex)
        {
            _ex.printStackTrace();
            assert(false);
        }

        return to_return;
    }

    public static String first_connected_switch_id (
        GetNumberSwitches num_switches_app)
    {
        return get_switch_id_list(num_switches_app).get(0);
    }

    
    public static class LatencyThread extends Thread
    {
        public List <Long> all_times = new ArrayList<Long>();
        
        private OffOnApplication off_on_app = null;
        private String switch_id = null;
        private int num_ops_to_run = -1;
        private boolean read_only = false;
        private boolean distributed = false;
        
        public LatencyThread(
            OffOnApplication off_on_app, String switch_id,
            int num_ops_to_run,  boolean read_only)
        {
            this.off_on_app = off_on_app;
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
            this.read_only = read_only;
        }

        public LatencyThread(
            OffOnApplication off_on_app, String switch_id, int num_ops_to_run,
            boolean read_only, boolean distributed)
        {
            this.off_on_app = off_on_app;
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
            this.read_only = read_only;
            this.distributed = distributed;
        }
        
        
        public LatencyThread(
            OffOnApplication off_on_app, String switch_id, int num_ops_to_run)
        {
            this.off_on_app = off_on_app;
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
        }

        public void run()
        {
            /* perform all operations and determine how long they take */
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                long start_time = System.nanoTime();
                try
                {
                    if (read_only)
                        off_on_app.read_first_instance_flow_table();
                    else if (distributed)
                        off_on_app.single_op_and_ask_children_for_single_op();
                    else
                        off_on_app.single_op(switch_id);
                }
                catch (Exception _ex)
                {
                    _ex.printStackTrace();
                    assert(false);
                }
                long total_time = System.nanoTime() - start_time;
                all_times.add(total_time);
            }
        }

        /**
           Write the latencies that each operation took as a csv
         */
        public void write_times(StringBuffer buffer)
        {
            for (Long latency : all_times)
                buffer.append(latency.toString()).append(",");
            
            if (! all_times.isEmpty())
                buffer.append("\n");
        }
        
    }
    
}