package pronghorn;

import pronghorn.InstanceJava.Instance;
import pronghorn.PortStatsJava._InternalPortStats;

public class StatisticsUpdater
{
    Instance prong = null;
    
    public StatisticsUpdater(Instance _prong)
    {
        prong = _prong;
    }

    public void update_port_stats(
        String ralph_switch_id,Double port_num,
        _InternalPortStats new_port_stats)
    {
        try
        {
            prong.update_port_stats(ralph_switch_id,port_num,new_port_stats);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
            System.out.println(
                "Should never get error when updating port statistics");
            assert(false);
        }
    }
}