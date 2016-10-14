package com.ibm.wala.ipa.callgraph.multithread.statements;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.multithread.analyses.HeapAbstractionFactory;
import com.ibm.wala.ipa.callgraph.multithread.engine.PointsToAnalysis.StmtAndContext;
import com.ibm.wala.ipa.callgraph.multithread.graph.GraphDelta;
import com.ibm.wala.ipa.callgraph.multithread.graph.ObjectField;
import com.ibm.wala.ipa.callgraph.multithread.graph.PointsToGraph;
import com.ibm.wala.ipa.callgraph.multithread.graph.PointsToGraphNode;
import com.ibm.wala.ipa.callgraph.multithread.graph.ReferenceVariableReplica;
import com.ibm.wala.ipa.callgraph.multithread.registrar.StatementRegistrar;
import com.ibm.wala.ipa.callgraph.multithread.registrar.ReferenceVariableFactory.ReferenceVariable;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.types.FieldReference;

/**
 * Points-to statement for an assignment into a field, o.f = v
 */
public class LocalToFieldStatement extends PointsToStatement {
    /**
     * Field assigned into
     */
    private final FieldReference field;

    /**
     * receiver for field access
     */
    private ReferenceVariable receiver;
    /**
     * Value assigned into field
     */
    private ReferenceVariable localVar;

    /**
     * Statement for an assignment into a field, o.f = v
     *
     * @param o
     *            points-to graph node for receiver of field access
     * @param f
     *            field assigned to
     * @param v
     *            points-to graph node for value assigned
     * @param m
     *            method the points-to statement came from
     */
    public LocalToFieldStatement(ReferenceVariable o, FieldReference f,
                                 ReferenceVariable v, IMethod m) {
        super(m);
        this.field = f;
        this.receiver = o;
        this.localVar = v;
    }

    @Override
    public GraphDelta process(Context context, HeapAbstractionFactory haf,
                              PointsToGraph g, GraphDelta delta, StatementRegistrar registrar, StmtAndContext originator) {
        PointsToGraphNode rec = new ReferenceVariableReplica(context, this.receiver, haf);
        PointsToGraphNode local = new ReferenceVariableReplica(context, this.localVar, haf);

        GraphDelta changed = new GraphDelta(g);

        if (delta == null) {
            // no delta, let's do some simple processing
            for (Iterator<InstanceKey> iter = g.pointsToIterator(rec, originator); iter.hasNext();) {
                InstanceKey recHeapContext = iter.next();

                ObjectField f = new ObjectField(recHeapContext, this.field);
                // o.f can point to anything that local can.
                GraphDelta d1 = g.copyEdges(local, f);

                changed = changed.combine(d1);
            }
        }
        else {
            // We check if o has changed what it points to. If it has, we need to make the new object fields
            // point to everything that the RHS can.
            for (Iterator<InstanceKey> iter = delta.pointsToIterator(rec); iter.hasNext();) {
                InstanceKey recHeapContext = iter.next();
                ObjectField contents = new ObjectField(recHeapContext, this.field);
                GraphDelta d1 = g.copyEdges(local, contents);
                changed = changed.combine(d1);
            }
        }

        return changed;
    }

    @Override
    public String toString() {
        return this.receiver
                + "."
                + (this.field != null
                ? this.field.getName() : PointsToGraph.ARRAY_CONTENTS)
                + " = " + this.localVar;
    }

    @Override
    public void replaceUse(int useNumber, ReferenceVariable newVariable) {
        assert useNumber == 0 || useNumber == 1;
        if (useNumber == 0) {
            this.receiver = newVariable;
            return;
        }
        this.localVar = newVariable;
    }

    @Override
    public List<ReferenceVariable> getUses() {
        List<ReferenceVariable> uses = new ArrayList<>(2);
        uses.add(this.receiver);
        uses.add(this.localVar);
        return uses;
    }

    @Override
    public ReferenceVariable getDef() {
        return null;
    }

    /**
     * Get the field assigned into
     *
     * @return field assigned to
     */
    protected FieldReference getField() {
        return field;
    }
}
