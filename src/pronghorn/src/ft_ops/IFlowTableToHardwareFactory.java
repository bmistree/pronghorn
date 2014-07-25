package pronghorn.ft_ops;

import pronghorn.IFloodlightShim;

public interface IFlowTableToHardwareFactory
{
    public FlowTableToHardware construct(
        IFloodlightShim shim, String internal_switch_id);
}