package pronghorn;


public interface SwitchStatusHandler
{
    public void new_switch(ShimInterface shim, String switch_id);
    public void removed_switch(ShimInterface shim, String switch_id);
}
