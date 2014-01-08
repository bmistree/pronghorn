package single_host;
import pronghorn.SwitchStatusHandler;
import single_host.JavaPronghornInstance.PronghornInstance;
import pronghorn.SwitchFactory;
import pronghorn.RoutingTableToHardware;
import pronghorn.SwitchFactory.PronghornInternalSwitch;
import java.util.HashMap;
import pronghorn.FloodlightRoutingTableToHardware;
import pronghorn.ShimInterface;


public class SingleHostSwitchStatusHandler implements SwitchStatusHandler
{
    private PronghornInstance prong;
    private SwitchFactory switch_factory;
    private final static String UNIQUE_SWITCH_FACTORY_PREFIX =
        "something_unique";

    // FIXME: set this to a number that actually has meaning
    private final static double DEFAULT_INITIAL_SWITCH_CAPACITY = 20.0;

    // maps from floodlight switch ids to pronghorn switch ids and
    // vice versa.
    private HashMap<String,String> floodlight_to_pronghorn_ids =
        new HashMap<String,String>();
    private HashMap<String,String> pronghorn_to_floodlight_ids =
        new HashMap<String,String>();

    private ShimInterface shim = null;
    
    public SingleHostSwitchStatusHandler(
        PronghornInstance _prong,ShimInterface _shim)
    {
        prong = _prong;
        shim = _shim;
        switch_factory = new SwitchFactory();
        switch_factory.init(UNIQUE_SWITCH_FACTORY_PREFIX);
    }

    @Override
    public void new_switch(String floodlight_switch_id)
    {
        FloodlightRoutingTableToHardware rtable_to_hardware =
            new FloodlightRoutingTableToHardware(shim,floodlight_switch_id);
        
        /// FIXME: Should overload RoutingTableToHardware object to
        /// actually push changes to hardware.
        PronghornInternalSwitch new_switch =
            switch_factory.construct(
                DEFAULT_INITIAL_SWITCH_CAPACITY,rtable_to_hardware);
        
        
        String pronghorn_switch_id = new_switch.ralph_internal_switch_id;
        floodlight_to_pronghorn_ids.put(
            floodlight_switch_id,pronghorn_switch_id);
        pronghorn_to_floodlight_ids.put(
            pronghorn_switch_id,floodlight_switch_id);

        try {
            prong.add_switch(new_switch);
        } catch (Exception _ex)
        {
            _ex.printStackTrace();
            System.out.println(
                "\nError: Should not generate exception from adding switch\n");
        }
    }
    @Override
    public void removed_switch(String floodlight_switch_id)
    {
        String pronghorn_switch_id =
            floodlight_to_pronghorn_ids.remove(floodlight_switch_id);

        /// FIXME: create library-wide assertion module
        if (pronghorn_switch_id == null)
        {
            System.out.println(
                "Asked to remove a switch that does not exist");
            assert(false);
        }

        pronghorn_to_floodlight_ids.remove(pronghorn_switch_id);
        try {
            prong.switch_failure(pronghorn_switch_id);
        } catch(Exception _ex)
        {
            _ex.printStackTrace();
            System.out.println(
                "\nError: Should not generate exception from removing switch\n");
        }
    }
}
