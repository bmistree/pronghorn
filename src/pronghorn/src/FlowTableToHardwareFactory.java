package pronghorn;

public interface FlowTableToHardwareFactory
{
    public FlowTableToHardware construct(
        IFloodlightShim shim, String internal_switch_id);
}