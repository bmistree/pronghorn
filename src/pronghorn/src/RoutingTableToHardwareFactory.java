package pronghorn;

public interface RoutingTableToHardwareFactory
{
    public RoutingTableToHardware construct(
        ShimInterface shim, String internal_switch_id);
}