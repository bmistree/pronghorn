package pronghorn;

public interface FlowTableToHardwareFactory
{
    public FlowTableToHardware construct(
        ShimInterface shim, String internal_switch_id);
}