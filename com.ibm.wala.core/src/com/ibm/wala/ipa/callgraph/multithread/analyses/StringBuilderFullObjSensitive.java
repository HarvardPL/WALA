package com.ibm.wala.ipa.callgraph.multithread.analyses;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.multithread.statements.AllocSiteNodeFactory.AllocSiteNode;
import com.ibm.wala.ipa.callgraph.multithread.statements.CallSiteLabel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;

/**
 * A full object sensitive analysis, as described in "Pick Your Contexts Well: Understanding Object-Sensitivity" by
 * Smaragdakis, Bravenboer, and Lhotak, POPL 2011.
 * <p>
 * Not that this analysis is imprecise for static calls: the context for the static call is the context of the caller.
 * It is recommended that one combine this with another abstraction to recover precision for static calls (e.g.
 * StaticCallStiteSensitive)
 */
public class StringBuilderFullObjSensitive extends HeapAbstractionFactory {

    /**
     * Object sensitivity parameter
     */
    private final int n;

    /**
     * default is 2 Object sensitive + 1H.
     */
    private static final int DEFAULT_DEPTH = 2;

    /**
     * Create an n-object-sensitive analysis
     *
     * @param n
     *            depth of calling context stack
     */
    public StringBuilderFullObjSensitive(int n) {
        this.n = n;
    }

    /**
     * Create a full object sensitive abstraction factory with the default parameters
     */
    public StringBuilderFullObjSensitive() {
        this(DEFAULT_DEPTH);
    }

    /**
     * Is the given class is a string builder
     *
     * @param c class to check
     * @return true if <code>c</code> is a string builder
     */
    private static boolean isStringBuilder(IClass c) {
        TypeReference abs = TypeReference.findOrCreate(ClassLoaderReference.Primordial,
                                                       "Ljava/lang/AbstractStringBuilder");
        return c.getReference() == TypeReference.JavaLangStringBuffer
                || c.getReference() == TypeReference.JavaLangStringBuilder || c.getReference() == abs;
    }

    @SuppressWarnings("unchecked")
    @Override
    public AllocationNameContext merge(CallSiteLabel callSite,
            InstanceKey receiver, Context callerContext) {
        if (!callSite.isStatic() && isStringBuilder(receiver.getConcreteType())) {
            AllocationName<ContextStack<AllocSiteNode>> rec = (AllocationName<ContextStack<AllocSiteNode>>) receiver;
            return AllocationNameContext.create(rec);
        }
        return initialContext();
    }

    @Override
    public AllocationName<ContextStack<AllocSiteNode>> record(AllocSiteNode allocationSite, Context context) {
        if (isStringBuilder(allocationSite.getAllocatedClass()) || isStringBuilder(allocationSite.getAllocatingClass())) {
            AllocationNameContext c = (AllocationNameContext) context;
            AllocationName<ContextStack<AllocSiteNode>> an = c.allocationName();
            ContextStack<AllocSiteNode> stack;
            if (an == null) {
                stack = ContextStack.emptyStack();
            }
            else {
                stack = an.getContext().push(an.getAllocationSite(), n);
            }
            return AllocationName.create(stack, allocationSite);
        }
        ContextStack<AllocSiteNode> stack = ContextStack.emptyStack();
        return AllocationName.create(stack, allocationSite);
    }

    @Override
    public AllocationNameContext initialContext() {
        return AllocationNameContext.create(null);
    }

    @Override
    public String toString() {
        return n + "StringBuilderFullObjSens+1H";
    }

    public static class AllocationNameContext implements Context {
        private final AllocationName<ContextStack<AllocSiteNode>> an;

        AllocationNameContext(AllocationName<ContextStack<AllocSiteNode>> an) {
            this.an = an;
        }

        public static AllocationNameContext create(
                AllocationName<ContextStack<AllocSiteNode>> an) {
            // XXX ANDREW: maybe make this memoize. Steve: Yes, in the meantime ensure we have equality defined.

            return new AllocationNameContext(an);
        }

        @Override
        public ContextItem get(ContextKey name) {
            return null;
        }

        public AllocationName<ContextStack<AllocSiteNode>> allocationName() {
            return an;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (an == null ? 0 : an.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof AllocationNameContext)) {
                return false;
            }
            AllocationNameContext other = (AllocationNameContext) obj;
            if (an == null) {
                if (other.an != null) {
                    return false;
                }
            }
            else if (!an.equals(other.an)) {
                return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return String.valueOf(an);
        }
    }
}
