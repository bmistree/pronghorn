
package test_package;

import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import test_package.HardwareTest.PronghornInstance;
import pronghorn.SwitchFactory;
import pronghorn.SwitchFactory.PronghornInternalSwitch;
import java.util.concurrent.atomic.AtomicInteger;
import ralph.Variables.AtomicTextVariable;

public class SingleTest
{
    public static AtomicInteger atomic_switch_id = new AtomicInteger(0);
    public final static Double initial_switch_capacity = new Double(20.0);
    
    public static void main (String[] args)
    {
	if (SingleTest.static_run())
            System.out.println("\nSUCCESS in test SingleTest\n");
        else
            System.out.println("\nFAILURE in test SingleTest\n");
    }

    public static String add_switch(PronghornInstance prong,SwitchFactory sf)
        throws Exception
    {
        PronghornInternalSwitch to_add = sf.construct(20);
        prong.add_switch(to_add);
        return to_add.ralph_internal_switch_id;
    }
    
    public static boolean static_run()
    {
        try
        {
            SwitchFactory switch_factory = new SwitchFactory();
            switch_factory.init("unique_factory_id");
            String dummy_host_uuid = "dummy_host_uuid";
            
            PronghornInstance prong = new PronghornInstance(
                new RalphGlobals(),
                dummy_host_uuid,
                new SingleSideConnection());

            String switch_id = add_switch(prong,switch_factory);

            
            prong.add_rtable_entry(switch_id,true,"ipa","ipb",1.);

            if (!prong.rtable_size(switch_id).equals(1.0))
                return false;
            
        }
        catch(Exception _ex)
        {
            _ex.printStackTrace();
            return false;
        }
        
        
        return true;
    }
}