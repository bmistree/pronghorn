package pronghorn.ft_ops;

import java.util.List;

import ralph.AtomicValueVariable;
import ralph.AtomicInternalList;
import ralph.RalphObject;
import ralph.AtomicList;
import ralph.AtomicReferenceVariable;
import ralph.IReference;


/**
   Helper class.  Takes a ralph AtomicValueVariable and returns
   either its dirty value, if available, and its non-dirty value
   if unavailable.

   Example:

     Double table_id =
        RalphInternalValueRemover.<Double>get_internal(goto_table.table_id);
*/
public class RalphInternalValueRemover
{
    // This notation allows calling method statically.
    public static <InternalType> InternalType get_internal(
        AtomicValueVariable<InternalType> atomic_variable)
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
    public static <InternalType extends IReference> InternalType get_internal_from_reference(
        AtomicReferenceVariable<InternalType> atomic_variable)
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
    public static <ValueType, ValueDeltaType>
        // return type
        AtomicInternalList<ValueType,ValueDeltaType>
        // method name and arguments
        list_get_internal(
            AtomicList<ValueType,ValueDeltaType> atomic_list)
    {
        AtomicInternalList<ValueType,ValueDeltaType> to_return = null;
        atomic_list._lock();
        if (atomic_list.dirty_val != null)
            to_return = atomic_list.dirty_val.val;
        else
            to_return = atomic_list.val.val;
        atomic_list._unlock();
        return to_return;
    }
    
    // This notation allows calling method statically.
    public static <ValueType, ValueDeltaType>
        // return type
        List<RalphObject<ValueType,ValueDeltaType>>
        // method name and arguments
        internal_list_get_internal(
            AtomicInternalList<ValueType,ValueDeltaType> internal_list)
    {
        List<RalphObject<ValueType,ValueDeltaType>> to_return = null;
        internal_list._lock();

        if (internal_list.dirty_val != null)
        {
            to_return =
                (List<RalphObject<ValueType,ValueDeltaType>>)
                internal_list.dirty_val.val;
        }
        else
        {
            to_return =
                (List<RalphObject<ValueType,ValueDeltaType>>)
                internal_list.val.val;
        }
        internal_list._unlock();
        return to_return;
    }
    
}