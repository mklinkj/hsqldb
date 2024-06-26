/* Copyright (c) 2001-2024, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.hsqldb;

import org.hsqldb.HsqlNameManager.HsqlName;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.ArrayListIdentity;
import org.hsqldb.lib.List;
import org.hsqldb.lib.Set;
import org.hsqldb.map.ValuePool;
import org.hsqldb.result.Result;
import org.hsqldb.types.Type;

/**
 * Implementation of SQL-invoked user-defined function calls - PSM and JRT
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 1.9.0
 */
public class FunctionSQLInvoked extends Expression {

    RoutineSchema routineSchema;
    Routine       routine;
    Expression    condition = Expression.EXPR_TRUE;    // needed for equals() method

    FunctionSQLInvoked(RoutineSchema routineSchema) {

        super(routineSchema.isAggregate()
              ? OpTypes.USER_AGGREGATE
              : OpTypes.FUNCTION);

        this.routineSchema = routineSchema;
    }

    public void setArguments(Expression[] newNodes) {
        this.nodes = newNodes;
    }

    public List<Expression> resolveColumnReferences(
            Session session,
            RangeGroup rangeGroup,
            int rangeCount,
            RangeGroup[] rangeGroups,
            List<Expression> unresolvedSet,
            boolean acceptsSequences) {

        List<Expression> conditionSet = condition.resolveColumnReferences(
            session,
            rangeGroup,
            rangeCount,
            rangeGroups,
            null,
            false);

        ExpressionColumn.checkColumnsResolved(conditionSet);

        if (isSelfAggregate()) {
            if (unresolvedSet == null) {
                unresolvedSet = new ArrayListIdentity<>();
            }

            unresolvedSet.add(this);

            return unresolvedSet;
        } else {
            return super.resolveColumnReferences(
                session,
                rangeGroup,
                rangeCount,
                rangeGroups,
                unresolvedSet,
                acceptsSequences);
        }
    }

    public void resolveTypes(Session session, Expression parent) {

        Type[] types = new Type[nodes.length];

        for (int i = 0; i < nodes.length; i++) {
            Expression e = nodes[i];

            e.resolveTypes(session, this);

            types[i] = e.dataType;
        }

        routine = routineSchema.getSpecificRoutine(types);

        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i].dataType == null) {
                nodes[i].dataType = routine.getParameterTypes()[i];
            }
        }

        dataType = routine.getReturnType();

        condition.resolveTypes(session, null);
    }

    private Object getValueInternal(Session session, Object[] aggregateData) {

        boolean  isValue = false;
        Result   result;
        int      extraArg = routine.javaMethodWithConnection
                            ? 1
                            : 0;
        Object[] data     = ValuePool.emptyObjectArray;
        boolean  push     = true;

        if (extraArg + nodes.length > 0) {
            if (opType == OpTypes.USER_AGGREGATE) {
                data = new Object[routine.getParameterCount()];

                System.arraycopy(
                    aggregateData,
                    0,
                    data,
                    1,
                    aggregateData.length);
            } else {
                data = new Object[nodes.length + extraArg];
            }

            if (!routine.isPSM()) {
                Object connection = session.getInternalConnection();

                if (extraArg > 0) {
                    data[0] = connection;
                }
            }
        }

        Type[] dataTypes = routine.getParameterTypes();

        for (int i = 0; i < nodes.length; i++) {
            Expression e     = nodes[i];
            Object     value = e.getValue(session, dataTypes[i]);

            if (value == null) {
                if (routine.isNullInputOutput()) {
                    return null;
                }

                if (!routine.getParameter(i).isNullable()) {
                    return Result.newErrorResult(
                        Error.error(ErrorCode.X_39004));
                }
            }

            if (routine.isPSM()) {
                data[i] = value;
            } else {
                data[i + extraArg] = e.dataType.convertSQLToJava(
                    session,
                    value);
            }
        }

        result = routine.invoke(session, data, aggregateData, push);

        session.releaseInternalConnection();

        if (result.isError()) {
            throw result.getException();
        }

        if (isValue) {
            return result.valueData;
        } else {
            return result;
        }
    }

    public Object getValue(Session session) {

        Object returnValue = getValueInternal(session, null);

        if (returnValue instanceof Result) {
            Result result = (Result) returnValue;

            if (result.isError()) {
                throw result.getException();
            } else if (result.isSimpleValue()) {
                returnValue = result.getValueObject();
            } else if (result.isData()) {
                returnValue = result;
            } else {
                throw Error.error(ErrorCode.X_2F005, routine.getName().name);
            }
        }

        return returnValue;
    }

    public Result getResult(Session session) {

        Object value = getValueInternal(session, null);

        if (value instanceof Result) {
            return (Result) value;
        }

        return Result.newPSMResult(value);
    }

    void collectObjectNames(Set<HsqlName> set) {
        set.add(routine.getSpecificName());
    }

    public String getSQL() {

        StringBuilder sb = new StringBuilder();

        sb.append(routineSchema.getName().getSchemaQualifiedStatementName())
          .append('(');

        int nodeCount = nodes.length;

        if (opType == OpTypes.USER_AGGREGATE) {
            nodeCount = 1;
        }

        for (int i = 0; i < nodeCount; i++) {
            if (i != 0) {
                sb.append(',');
            }

            sb.append(nodes[i].getSQL());
        }

        sb.append(')');

        return sb.toString();
    }

    public String describe(Session session, int blanks) {
        return super.describe(session, blanks);
    }

    public boolean isSelfAggregate() {
        return routineSchema.isAggregate();
    }

    public boolean isDeterministic() {
        return routine.isDeterministic();
    }

    boolean equals(Expression other) {

        if (other instanceof FunctionSQLInvoked) {
            FunctionSQLInvoked o = (FunctionSQLInvoked) other;

            return super.equals(other)
                   && opType == other.opType
                   && routineSchema == o.routineSchema
                   && routine == o.routine
                   && condition.equals(o.condition);
        }

        return false;
    }

    public SetFunction updateAggregatingValue(
            Session session,
            SetFunction currValue) {

        if (!condition.testCondition(session)) {
            return currValue;
        }

        if (currValue == null) {
            currValue = new SetFunctionValueUser();
        }

        Object[] array = ((SetFunctionValueUser) currValue).list;

        array[0] = Boolean.FALSE;

        getValueInternal(session, array);

        return currValue;
    }

    public Object getAggregatedValue(Session session, SetFunction currValue) {

        if (currValue == null) {
            currValue = new SetFunctionValueUser();
        }

        Object[] array = ((SetFunctionValueUser) currValue).list;

        array[0] = Boolean.TRUE;

        Result result = (Result) getValueInternal(session, array);

        if (result.isError()) {
            throw result.getException();
        } else {
            return result.getValueObject();
        }
    }

    public Expression getCondition() {
        return condition;
    }

    public boolean hasCondition() {
        return condition != null && !condition.isTrue();
    }

    public void setCondition(Expression e) {
        condition = e;
    }
}
