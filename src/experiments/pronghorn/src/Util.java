package experiments;

import java.util.HashSet;
import java.util.Set;

public class Util
{
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
    
}