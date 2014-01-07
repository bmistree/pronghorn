
package test_package;

import RalphConnObj.SingleSideConnection;
import ralph.RalphGlobals;
import test_package.HardwareTest.PronghornInstance;
import pronghorn.SwitchFactory;
import pronghorn.SwitchFactory.PronghornInternalSwitch;
import java.util.concurrent.atomic.AtomicInteger;
import ralph.Variables.AtomicTextVariable;
import RalphDataWrappers.ListTypeDataWrapper;
import pronghorn.RTable._InternalRoutingTableEntry;

/**
   Does not actually push changes to hardware, but checks to ensure
   that RoutingTableToHardware object we provide gets called when we
   make changes to a router's routing list.
 */
public class SimulatedExtendedTest
{
    public static AtomicInteger atomic_switch_id = new AtomicInteger(0);
    public final static Double initial_switch_capacity = new Double(20.0);

    public static int num_times_pushed_to_hardware = 0;
    
    public static void main (String[] args)
    {
	if (SimulatedExtendedTest.static_run())
            System.out.println("\nSUCCESS in test SimulatedExtendedTest\n");
        else
            System.out.println("\nFAILURE in test SimulatedExtendedTest\n");
    }


    public static class TestRoutingTableToHardware
        extends SwitchFactory.RoutingTableToHardware
    {
        @Override
        public boolean apply_changes_to_hardware(
            ListTypeDataWrapper<
                _InternalRoutingTableEntry,_InternalRoutingTableEntry> dirty)
        {
            num_times_pushed_to_hardware += 1;
            return true;
        }
        @Override
        public void undo_dirty_changes_to_hardware(
            ListTypeDataWrapper<_InternalRoutingTableEntry,_InternalRoutingTableEntry> to_undo)
        { }
    }
    
    public static String add_switch(PronghornInstance prong,SwitchFactory sf)
        throws Exception
    {
        TestRoutingTableToHardware rtable_to_hardware_obj =
            new TestRoutingTableToHardware();
        
        PronghornInternalSwitch to_add =
            sf.construct(20,rtable_to_hardware_obj);
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
            int num_times_to_add_entry = 5;
            for (int i =0; i < num_times_to_add_entry; ++i)
                prong.add_rtable_entry(switch_id,true,"ipa","ipb",1.);

            if (num_times_pushed_to_hardware != num_times_to_add_entry)
                return false;
            
            if (!prong.rtable_size(switch_id).equals(new Double(num_times_to_add_entry)))
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