package com.ibm.wala.ipa.callgraph.multithread.analyses;

import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.multithread.statements.CallSiteLabel;
import com.ibm.wala.ipa.callgraph.multithread.statements.AllocSiteNodeFactory.AllocSiteNode;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;

public class ContextInsensitive extends HeapAbstractionFactory {

    public static final Context EMPTY_CONTEXT = new Context() {
        @Override
        public ContextItem get(ContextKey name) {
            // There are no context items
            return null;
        }

        @Override
        public String toString() {
            return "[]";
        }
    };

    @Override
    public InstanceKey record(AllocSiteNode allocationSite, Context context) {
        return AllocationName.create(context, allocationSite);
    }

    @Override
    public Context merge(CallSiteLabel callSite, InstanceKey receiver, Context callerContext) {
        return initialContext();
    }

    @Override
    public Context initialContext() {
        return EMPTY_CONTEXT;
    }

    @Override
    public String toString() {
        return "ContextInsensitive";
    }
}
