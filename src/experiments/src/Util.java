package experiments;

import java.lang.Process;
import java.lang.Runtime;

import java.util.HashSet;
import java.util.Set;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.util.List;
import java.util.ArrayList;

import ralph.NonAtomicInternalList;
import experiments.GetNumberSwitchesJava.GetNumberSwitches;
import experiments.OffOnApplicationJava.OffOnApplication;
import experiments.MultiControllerOffOnJava.MultiControllerOffOn;
import experiments.ReadOnlyJava.ReadOnly;
import experiments.MultiControllerTunnelsJava.MultiControllerTunnelsApp;

public class Util
{
    //final static int TIME_TO_WAIT_AFTER_FIRST_SWITCH_MS = 12000;
    final static int TIME_TO_WAIT_AFTER_FIRST_SWITCH_MS = 40000;
    //final static int TIME_TO_WAIT_AFTER_FIRST_SWITCH_MS = 100;
    final static int FIRST_SWITCH_POLL_PERIOD_MS = 250;
    /**
       For ovs commands, require a mininet-specific switch name.  In
       general, these seem to have the form system@s1, system@s2, etc.
    */
    final static String OVS_SWITCH_NAME_PREFIX = "system@s";

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

    /**
       For ovs commands, require a mininet-specific switch name.  In
       general, these seem to have the form system@s1, system@s2, etc.

       This returns a list of these switch names.
     */
    public static List<String> produce_ovs_switch_names(int num_switches)
    {
        List<String> to_return = new ArrayList<String>();
        for (int i = 1; i <= num_switches; ++i)
            to_return.add(OVS_SWITCH_NAME_PREFIX + i);
        return to_return;
    }

    /**
       @param {String} switch_id --- The name of the switch to
       query for its flow table entries. If using mininet, this is
       likely something like: "system@s1" or "system@s2".

       @returns {int} --- The number of flow table entries.
     */
    public static int ovs_hardware_flow_table_size(String switch_id)
    {
        try
        {
            Runtime r = Runtime.getRuntime();
            Process p = r.exec("ovs-ofctl dump-flows " + switch_id +
                               " --protocols=OpenFlow13");
            p.waitFor();
            BufferedReader b =
                new BufferedReader(new InputStreamReader(p.getInputStream()));

            // command returns stdout like the following if it has
            // two flow table entries:

            // NXST_FLOW reply (xid=0x4):
            // cookie=0x0, duration=116.601s, table=0, ...
            // cookie=0x0, duration=4.777s, table=0, ...

            //ie, should subtract 1 from number of distinct lines to
            //get number of flow table entries.

            String line = "";
            int number_lines = 0;
            while ((line = b.readLine()) != null)
                ++number_lines;
            return number_lines - 1;
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            assert(false);
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
            assert(false);
        }
        return -1;
    }

    public static StringBuffer produce_result_string(
        ConcurrentHashMap<String,List<Long>> results)
    {
        StringBuffer string_buffer = new StringBuffer();
        for (String results_index : results.keySet())
        {
            List<Long> times = results.get(results_index);
            StringBuffer buff_line = new StringBuffer();
            for (Long time : times)
                buff_line.append(time.toString()).append(",");

            String line = buff_line.toString();
            if (line != "") {
                // trim off trailing comma
                line = line.substring(0, line.length() - 1);
            }
            string_buffer.append(line).append("\n");
        }
        return string_buffer;
    }

    public static void print_throughput_results(
        int num_switches,int threads_per_switch,int num_ops_to_run,
        long elapsedNano)
    {
        double throughputPerS =
            ((double) (num_switches * threads_per_switch * num_ops_to_run)) /
            ((double)elapsedNano/1000000000);
        System.out.println(
            "Switches: " + num_switches + " Throughput(op/s): " +
            throughputPerS);
    }


    /**
       @param {String} ovs_switch_id --- generated by
       produce_ovs_switch_names.

       Sends a message down to switch to drop all its flow table
       entries.
     */
    public static void ovs_clear_flows_hardware (String ovs_switch_id)
    {
        try
        {
            Runtime r = Runtime.getRuntime();
            Process p = r.exec("ovs-ofctl del-flows " + ovs_switch_id);
            p.waitFor();
        }
        catch (IOException ex)
        {
            ex.printStackTrace();
            assert(false);
        }
        catch (InterruptedException ex)
        {
            ex.printStackTrace();
            assert(false);
        }
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

        try {
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
        private MultiControllerOffOn mc_off_on_app = null;
        private ReadOnly read_only_app = null;
        private MultiControllerTunnelsApp tunnels_app = null;
        private List<String> switch_names = null;
        private String switch_id = null;
        private int num_ops_to_run = -1;

        private boolean no_read_first = false;

        private static Random rand = new Random();


        // For locals
        public LatencyThread(
            OffOnApplication off_on_app, String switch_id, int num_ops_to_run,
            boolean no_read_first)
        {
            this.off_on_app = off_on_app;
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
            this.no_read_first = no_read_first;
        }

        // For multiple controllers
        public LatencyThread(
            MultiControllerOffOn mc_off_on_app, String switch_id,
            int num_ops_to_run)
        {
            this.mc_off_on_app = mc_off_on_app;
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
        }

        // For read only
        public LatencyThread(
            ReadOnly read_only_app, String switch_id,
            int num_ops_to_run)
        {
            this.read_only_app = read_only_app;
            this.switch_id = switch_id;
            this.num_ops_to_run = num_ops_to_run;
        }

        // For read only
        public LatencyThread(
            MultiControllerTunnelsApp tunnels_app,
            int num_ops_to_run,List<String> switch_names)
        {
            this.tunnels_app = tunnels_app;
            this.num_ops_to_run = num_ops_to_run;
            this.switch_names = switch_names;
        }


        private String select_one_switch(List <String> switch_names)
        {
            if (switch_names.size() == 0)
            {
                System.out.println(
                    "Cannot select an element from empty list.");
                assert(false);
            }
            int index = rand.nextInt(switch_names.size());
            return switch_names.get(index);
        }
        private String select_one_switch(
            List<String> switch_names, String switch_to_skip)
        {
            if (switch_names.size() <= 1)
            {
                System.out.println(
                    "Cannot select an element from list with 1 element.");
                assert(false);
            }

            String to_return = null;
            while (true)
            {
                to_return = select_one_switch(switch_names);
                if (! to_return.equals(switch_to_skip))
                    break;
            }
            return to_return;
        }


        public void run()
        {
            /* perform all operations and determine how long they take */
            for (int i = 0; i < num_ops_to_run; ++i)
            {
                long start_time = System.nanoTime();
                try
                {
                    if (read_only_app != null)
                        read_only_app.read_first_instance_flow_table();
                    else if (mc_off_on_app != null)
                        mc_off_on_app.single_op_and_ask_children_for_single_op();
                    else if (tunnels_app != null)
                    {
                        String switch_id_1 = select_one_switch(switch_names);
                        String switch_id_2 = select_one_switch(switch_names,switch_id_1);
                        tunnels_app.install_shortest_path(switch_id_1,switch_id_2);
                    }
                    else
                    {
                        if (no_read_first)
                        {
                            // alternate adding and removing switches
                            // without reading first
                            if ((i % 2) == 0)
                                off_on_app.add_entry_switch(switch_id);
                            else
                                off_on_app.remove_entry_switch(switch_id);
                        }
                        else
                            off_on_app.single_op(switch_id);
                    }
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