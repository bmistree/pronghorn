package pronghorn.port_ops;

import java.util.List;

import pronghorn.PortJava.Port;
import pronghorn.PortJava._InternalPort;
import pronghorn.SwitchDeltaJava._InternalPortDelta;

import pronghorn.ft_ops.RalphInternalValueRemover;

import port_ops.serialized_update.SinglePortUpdateProto.SinglePortUpdate;
import port_ops.serialized_update.PortUpdatesProto.PortUpdates;

public class PortUpdate
{
    public final String local_switch_id;
    public final int local_port_number;
    public final String remote_switch_id;
    public final int remote_port_number;
    public final boolean port_up;
    public final boolean other_end_available;

    public PortUpdate(
        String _local_switch_id, int _local_port_number,
        String _remote_switch_id, int _remote_port_number,
        boolean _port_up, boolean _other_end_available)
    {
        local_switch_id = _local_switch_id;
        local_port_number = _local_port_number;
        remote_switch_id = _remote_switch_id;
        remote_port_number = _remote_port_number;
        port_up = _port_up;
        other_end_available = _other_end_available;
    }

    public SinglePortUpdate.Builder serialize()
    {
        SinglePortUpdate.Builder to_return =
            SinglePortUpdate.newBuilder();

        to_return.setLocalSwitchId(local_switch_id);
        to_return.setLocalPortNumber(local_port_number);
        to_return.setRemoteSwitchId(remote_switch_id);
        to_return.setRemotePortNumber(remote_port_number);
        to_return.setPortUp(port_up);
        to_return.setOtherEndAvailable(other_end_available);
        return to_return;
    }

    public static PortUpdates.Builder serialize_update_list(
        List<PortUpdate> update_list)
    {
        PortUpdates.Builder to_return = PortUpdates.newBuilder();
        for (PortUpdate port_update : update_list)
            to_return.addUpdates(port_update.serialize());
        return to_return;
    }
    
    
    /**
       Generate a port update from a single port update.

       Using static method here instead of constructor because want to
       be able to return null (in case internal_port_update got rolled
       back before actually pushed to switch).
     */
    public static PortUpdate port_update_from_internal(
        _InternalPortDelta internal_port_delta)
    {
        // Common pattern is retrieve, and test for null.  Will get null, for
        // instance, if got backed out while retrieving value.  In these cases,
        // returning null indicates that do not have to log port change.
        
        _InternalPort internal_port =
            RalphInternalValueRemover.<_InternalPort>
            get_internal_from_reference(
                internal_port_delta.entry);

        if (internal_port == null)
            return null;
        
        String local_switch_id = internal_port.local_switch_id.get_val(null);
        if (local_switch_id == null)
            return null;

        Double local_port_number =
            internal_port.local_port_number.get_val(null);
        if (local_port_number == null)
            return null;

        String remote_switch_id = internal_port.remote_switch_id.get_val(null);
        if (remote_switch_id == null)
            return null;

        Double remote_port_number =
            internal_port.remote_port_number.get_val(null);
        if (remote_port_number == null)
            return null;

        Boolean port_up =
            RalphInternalValueRemover.<Boolean>
            get_internal(
                internal_port.port_up);

        if (port_up == null)
            return null;

        Boolean other_end_available =
            RalphInternalValueRemover.<Boolean>
            get_internal(
                internal_port.other_end_available);

        if (other_end_available == null)
            return null;
        

        return new PortUpdate(
            local_switch_id,local_port_number.intValue(),
            remote_switch_id, remote_port_number.intValue(),
            port_up, other_end_available);
    }
}