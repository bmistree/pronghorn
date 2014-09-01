package experiments;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

import ralph.RalphGlobals;

import experiments.Util.HostPortPair;

/**
   Measures and reports time it takes to connect to, query, and
   receive responses from a command-line specified list of
   controllers.
 */
public class TimeToQueryAll
{
    final private static int HOST_PORT_PAIRS_ARG_INDEX = 0;
    final private static int OUTPUT_FILENAME_ARG_INDEX = 1;
    
    public static void main(String [] args)
    {
        if (args.length != 2)
        {
            print_usage();
            return;
        }
        
        String host_port_argument_string = args[HOST_PORT_PAIRS_ARG_INDEX];
        Set<HostPortPair> hosts_to_contact =
            Util.parse_csv_host_port_pairs(host_port_argument_string);

        String output_filename = args[OUTPUT_FILENAME_ARG_INDEX];

        // If use the default parameters, will not be able to run
        // multiple queriers on the same machine because will try to
        // bind to default tcp ports already listening on.  Get around
        // that by binding to different, unused ports.  Hopefully,
        // just adding 5 to existing tcp port does not cause conflict.
        RalphGlobals.Parameters non_conflicting_params =
            new RalphGlobals.Parameters();
        non_conflicting_params.tcp_port_to_listen_for_connections_on += 5;
        RalphGlobals ralph_globals = new RalphGlobals(non_conflicting_params);

        List<VersionQuerier> queriers = new ArrayList<VersionQuerier>();
        List<FutureVersionQueryResult> results =
            new ArrayList<FutureVersionQueryResult>();
        
        // want to time how long it takes to connect, query, and
        // receive full histories from all controllers
        long begin = System.nanoTime();
        for (HostPortPair hpp : hosts_to_contact)
        {
            VersionQuerier querier =
                new VersionQuerier(ralph_globals,hpp.host,hpp.port);
            queriers.add(querier);
            results.add(querier.get_all_versions());
        }

        // block until receive responses from each.
        for (FutureVersionQueryResult fqr : results)
            fqr.get();

        long end = System.nanoTime();

        String time_delta_ns = Long.toString(end-begin);
        Util.write_results_to_file(output_filename,time_delta_ns);
        
    }

    public static void print_usage()
    {
        String usage_string = "";
        usage_string += "\n\t<csv>: Controllers to contact host port csv.  ";
        usage_string += "Format host:port,host:port\n";

        usage_string += "\n\t<string>: Ouput filename.  Saves a single ";
        usage_string += "number: the time, in ns, it took to query all ";
        usage_string += "controllers.\n";
        
        System.out.println(usage_string);
    }
}