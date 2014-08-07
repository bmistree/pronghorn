package pronghorn.ft_ops;

import ralph.AtomicValueVariable;

/**
   Helper class.  Takes a ralph AtomicValueVariable and returns
   either its dirty value, if available, and its non-dirty value
   if unavailable.

   Example:

     Double table_id =
        RalphInternalValueRemover.<Double>get_internal(goto_table.table_id);
*/
class RalphInternalValueRemover<InternalType>
{
    // This notation allows calling method statically.
    public static <InternalType> InternalType get_internal(
        AtomicValueVariable<InternalType,InternalType> atomic_variable)
    {
        InternalType to_return = null;
        atomic_variable._lock();
        if (atomic_variable.dirty_val != null)
            to_return = atomic_variable.dirty_val.val;
        else
            to_return = atomic_variable.val.val;
        atomic_variable._unlock();
        return to_return;
    }
}