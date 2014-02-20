package single_host;

import single_host.JavaPronghornInstance.PronghornInstance;
import pronghorn.SwitchFactory;
import pronghorn.RoutingTableToHardware;
import pronghorn.RoutingTableToHardwareFactory;
import pronghorn.SwitchFactory.PronghornInternalSwitch;
import java.util.HashMap;
import pronghorn.FloodlightRoutingTableToHardware;
import pronghorn.ShimInterface;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.IOFSwitch;

import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleHostSwitchStatusHandler implements IOFSwitchListener
{
    protected static final Logger log =
        LoggerFactory.getLogger(SingleHostSwitchStatusHandler.class);
    
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
    private RoutingTableToHardwareFactory rtable_to_hardware_factory = null;

    private ShimInterface shim = null;
    
    
    public SingleHostSwitchStatusHandler(
        ShimInterface shim,
        PronghornInstance prong,
        RoutingTableToHardwareFactory rtable_to_hardware_factory)
    {
        this.shim = shim;
        this.prong = prong;
        switch_factory = new SwitchFactory();
        switch_factory.init(UNIQUE_SWITCH_FACTORY_PREFIX);
        this.rtable_to_hardware_factory = rtable_to_hardware_factory;
    }


    /** IOFSwitchListener */
    @Override
    public void switchAdded(long switchId)
    {
        log.info("Ignoring switch added message: waiting on activated.");
    }
    @Override
    public void switchRemoved(long switch_id)
    {
        String floodlight_switch_id = HexString.toHexString(switch_id);
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
    @Override
    public void switchActivated(long switch_id)
    {
        String floodlight_switch_id = HexString.toHexString(switch_id);
        
        RoutingTableToHardware rtable_to_hardware =
            rtable_to_hardware_factory.construct(shim,floodlight_switch_id);
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
    public void switchPortChanged(long switchId,
                                  ImmutablePort port,
                                  IOFSwitch.PortChangeType type)
    {
        log.error(
            "Still not handling switchPortChangedMessage " +
            "in switch status handler.");
    }
    @Override
    public void switchChanged(long switchId)
    {
        log.error(
            "Still not handling switchChangedMessage " +
            "in switch status handler.");
    }
}
