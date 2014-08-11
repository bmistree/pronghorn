package pronghorn.ft_ops;

import ralph.AtomicValueVariable;
import ralph.AtomicInternalList;

/**
   Helper class.  Takes a ralph AtomicValueVariable and returns
   either its dirty value, if available, and its non-dirty value
   if unavailable.

   Example:

     Double table_id =
        RalphInternalValueRemover.<Double>get_internal(goto_table.table_id);
*/
class RalphInternalValueRemover
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


    // This notation allows calling method statically.
    public static <InternalType1,InternalType2> InternalType1 list_get_internal(
        AtomicValueVariable<InternalType1,InternalType2> atomic_variable)
    {
        InternalType1 to_return = null;
        atomic_variable._lock();
        if (atomic_variable.dirty_val != null)
            to_return = atomic_variable.dirty_val.val;
        else
            to_return = atomic_variable.val.val;
        atomic_variable._unlock();
        return to_return;
    }
    
    // This notation allows calling method statically.
    public static <InternalType1>
        InternalType1 internal_list_get_internal(
            AtomicInternalList internal_list)
    {
        InternalType1 to_return = null;
        internal_list._lock();

        if (internal_list.dirty_val != null)
            to_return = (InternalType1) internal_list.dirty_val.val;
        else
            to_return = (InternalType1) internal_list.val.val;
        internal_list._unlock();
        
        return to_return;
    }
    
}