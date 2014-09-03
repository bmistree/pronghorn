package pronghorn;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryListener;
import net.floodlightcontroller.linkdiscovery.ILinkDiscovery;

import RalphExceptions.BackoutException;
import ralph.Variables.AtomicTrueFalseVariable;
import ralph.Variables.NonAtomicListVariable;

import pronghorn.PortJava._InternalPort;
import pronghorn.PortJava;

import pronghorn.switch_factory.ISwitchFactory;
import pronghorn.switch_factory.IVersionListenerFactory;
import pronghorn.switch_factory.SwitchFactory;
import pronghorn.InstanceJava.Instance;
import pronghorn.ft_ops.FlowTableToHardware;
import pronghorn.switch_factory.PronghornInternalSwitch;
import pronghorn.ft_ops.IFlowTableToHardwareFactory;
import pronghorn.ft_ops.FloodlightFlowTableToHardware;

import pronghorn.ISwitchStatusHandler;
import pronghorn.IFloodlightShim;


public class SwitchStatusHandler implements ISwitchStatusHandler
{
    protected static final Logger log =
        LoggerFactory.getLogger(SwitchStatusHandler.class);
    
    private Instance prong;
    private ISwitchFactory switch_factory;
    private final static String UNIQUE_SWITCH_FACTORY_PREFIX =
        "something_unique";

    // FIXME: set this to a number that actually has meaning
    private final static double DEFAULT_INITIAL_SWITCH_CAPACITY = 20.0;

    /**
       Used to update statistics for each switch.
     */
    private final StatisticsUpdater stats_updater;
    
    // maps from floodlight switch ids to pronghorn switch ids and
    // vice versa.
    private HashMap<String,String> floodlight_to_pronghorn_ids =
        new HashMap<String,String>();
    private HashMap<String,String> pronghorn_to_floodlight_ids =
        new HashMap<String,String>();
    private IFlowTableToHardwareFactory ftable_to_hardware_factory = null;

    private IFloodlightShim shim = null;
    private final boolean speculate;

    /**
       @param {int} collect_statistics_period_ms.  If period < 0, then
       never collect statistics.
     */
    public SwitchStatusHandler(
        IFloodlightShim shim,
        Instance prong,
        IFlowTableToHardwareFactory ftable_to_hardware_factory,
        boolean speculate,int collect_statistics_period_ms,
        IVersionListenerFactory ft_version_listener_factory,
        IVersionListenerFactory port_version_listener_factory)
    {
        this.shim = shim;
        this.prong = prong;
        this.ftable_to_hardware_factory = ftable_to_hardware_factory;
        this.speculate = speculate;
        stats_updater = new StatisticsUpdater(prong);
        
        switch_factory =
            new SwitchFactory(
                prong.ralph_globals,speculate,collect_statistics_period_ms,
                ft_version_listener_factory,port_version_listener_factory);
        switch_factory.init(UNIQUE_SWITCH_FACTORY_PREFIX);
    }

    /** ILinkDiscoveryListener */
    @Override
    public void linkDiscoveryUpdate(LDUpdate update)
    {
        // FIXME: Inefficient to create a list here and push into
        // linkDiscoveryUpdate.
        List<LDUpdate> update_list = new ArrayList<LDUpdate>();
        update_list.add(update);
        linkDiscoveryUpdate(update_list);
    }

    @Override
    public void linkDiscoveryUpdate(List<LDUpdate> update_list)
    {
        NonAtomicListVariable<_InternalPort,_InternalPort> ralph_update_list =
            new NonAtomicListVariable<_InternalPort,_InternalPort>(
                false,PortJava.STRUCT_LOCKED_MAP_WRAPPER__Port,prong.ralph_globals);
        boolean should_update_ralph = false;
        // only handle link up messages to start with
        for (LDUpdate update : update_list)
        {
            // FIXME: should also process port down.
            if (update.getOperation() != ILinkDiscovery.UpdateOperation.LINK_UPDATED)
                continue;
            
            _InternalPort ralph_port = create_internal_port_from_update(update);
            if (ralph_port != null)
            {
                should_update_ralph = true;
                try
                {
                    ralph_update_list.get_val(null).append(null,ralph_port);
                }
                catch (BackoutException ex)
                {
                    // 1: Should never receive a backout exception on
                    // a non atomic.
                    // 2: Nothing else should no about
                    // ralph_update_list.  Therefore, nothing else
                    // should be contending for it.
                    ex.printStackTrace();
                    assert(false);
                }
            }
        }

        if (should_update_ralph)
        {
            try
            {
                prong.process_port_updates(ralph_update_list.get_val(null));
            }
            catch (Exception ex)
            {
                // FIXME: Is it safe to assume that there will not be
                // application errors in this call?
                ex.printStackTrace();
                assert(false);
            }
        }
    }

    
    /**
       @returns --- Either null or _InternalPort.  Will return null if
       the update is not for an added port.  Will return _InternalPort
       if it is for an addition.
     */
    private _InternalPort create_internal_port_from_update(LDUpdate update)
    {
        if (update.getOperation() == UpdateOperation.LINK_UPDATED)
        {
            String src_floodlight_switch_id =
                HexString.toHexString(update.getSrc());
            String dst_floodlight_switch_id =
                HexString.toHexString(update.getDst());

            String src_ralph_switch_id =
                floodlight_to_pronghorn_ids.get(src_floodlight_switch_id);
            String dst_ralph_switch_id =
                floodlight_to_pronghorn_ids.get(dst_floodlight_switch_id);
            
            int src_port_num = update.getSrcPort();
            int dst_port_num = update.getDstPort();

            _InternalPort to_return = new _InternalPort (prong.ralph_globals);

            // FIXME: assume as soon as a switch gets updated with a port
            // that we can use that port.
            to_return.other_end_available =
                new AtomicTrueFalseVariable(false,true,prong.ralph_globals);
            to_return.port_up =
                new AtomicTrueFalseVariable(false,true,prong.ralph_globals);

            to_return.remote_switch_id.set_val(null,dst_ralph_switch_id);
            to_return.remote_port_number.set_val(
                null,new Double(dst_port_num));

            to_return.local_switch_id.set_val(null,src_ralph_switch_id);
            to_return.local_port_number.set_val(
                null,new Double(src_port_num));

            return to_return;
            
        }
        return null;
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
            prong.remove_switch(pronghorn_switch_id);
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

        // already had switch
        if (floodlight_to_pronghorn_ids.containsKey(floodlight_switch_id))
            return;
        
        FlowTableToHardware ftable_to_hardware =
            ftable_to_hardware_factory.construct(
                shim,floodlight_switch_id);
        PronghornInternalSwitch new_switch =
            switch_factory.construct(
                DEFAULT_INITIAL_SWITCH_CAPACITY,ftable_to_hardware,shim,
                floodlight_switch_id,stats_updater);

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
    public void switchPortChanged(
        long switch_id,ImmutablePort port, IOFSwitch.PortChangeType type)
    {
        // ignoring: using link discovery service instead for port messages.
    }
    @Override
    public void switchChanged(long switchId)
    {
        // ignoring: using link discovery service instead for port messages.
    }
}
