package pronghorn;

public class RTableUpdate
{
    public enum Operation {
        INSERT, REMOVE
    }
    
    public Operation op;

    // data shared by insertions and removals
    public String entry_name;
    
    // data for insertions
    public String src_ip;
    public String dst_ip;
    public String action;
    
    public static RTableUpdate create_insert_update(
        String _entry_name, String _src_ip, String _dst_ip,
        String _action)
    {
        RTableUpdate to_return = new RTableUpdate();
        to_return.op = Operation.INSERT;
        to_return.entry_name = _entry_name;

        to_return.src_ip = _src_ip;
        to_return.dst_ip = _dst_ip;
        to_return.action = _action;
        return to_return;
    }

    public static RTableUpdate create_remove_update(String _entry_name)
    {
        RTableUpdate to_return = new RTableUpdate();
        to_return.op = Operation.REMOVE;
        to_return.entry_name = _entry_name;
        return to_return;
    }
    
    /**
       Private constructor: use static methods to construct insertion
       and remove updates.
     */
    private RTableUpdate()
    {}
}