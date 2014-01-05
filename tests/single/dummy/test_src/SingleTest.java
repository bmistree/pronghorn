
package test_package;

import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import test_package.HardwareTest.PronghornInstance;
import test_package.HardwareTest._InternalSwitch;
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

    public static _InternalSwitch produce_internal_switch()
    {
        Integer new_switch_id = atomic_switch_id.addAndGet(1);

        // Overwrite internal values of internal switch
        _InternalSwitch internal_switch = new _InternalSwitch();
        internal_switch.switch_id =
            new AtomicTextVariable (
                "",false,new_switch_id.toString());

        return internal_switch;
    }

    public static void add_switch(PronghornInstance prong) throws Exception
    {
        _InternalSwitch to_add = produce_internal_switch();
        prong.add_switch(to_add);
    }
    
    public static boolean static_run()
    {
        try
        {
            String dummy_host_uuid = "dummy_host_uuid";
            
            PronghornInstance prong = new PronghornInstance(
                new RalphGlobals(),
                dummy_host_uuid,
                new SingleSideConnection());

            add_switch(prong);
        }
        catch(Exception _ex)
        {
            _ex.printStackTrace();
            return false;
        }
        
        
        return true;
    }
}