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

import java.sql.Connection;

import org.hsqldb.HsqlNameManager.HsqlName;
import org.hsqldb.ParserDQL.CompileContext;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.OrderedHashSet;
import org.hsqldb.map.ValuePool;
import org.hsqldb.result.Result;
import org.hsqldb.result.ResultMetaData;
import org.hsqldb.types.Type;

/**
 * Implementation of Statement for callable procedures.<p>
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 1.9.0
 */
public class StatementProcedure extends StatementDMQL {

    /** Expression to evaluate */
    Expression expression;

    /** Routine to execute */
    Routine procedure;

    /** arguments to Routine */
    Expression[]   arguments = Expression.emptyArray;
    ResultMetaData resultMetaData;

    /**
     * Constructor for CALL statements for expressions.
     */
    StatementProcedure(
            Session session,
            Expression expression,
            CompileContext compileContext) {

        super(
            StatementTypes.CALL,
            StatementTypes.X_SQL_DATA,
            session.getCurrentSchemaHsqlName());

        statementReturnType = StatementTypes.RETURN_RESULT;

        if (expression.opType == OpTypes.FUNCTION) {
            FunctionSQLInvoked f = (FunctionSQLInvoked) expression;

            if (f.routine.returnsTable) {
                this.procedure = f.routine;
                this.arguments = f.nodes;
            } else {
                this.expression = expression;
            }
        } else {
            this.expression = expression;
        }

        setDatabaseObjects(session, compileContext);
        checkAccessRights(session);

        if (procedure != null) {
            session.getGrantee().checkAccess(procedure);
        }

        isTransactionStatement = readTableNames.length > 0
                                 || writeTableNames.length > 0;
    }

    /**
     * Constructor for CALL statements for procedures.
     */
    StatementProcedure(
            Session session,
            Routine procedure,
            Expression[] arguments,
            CompileContext compileContext) {

        super(
            StatementTypes.CALL,
            StatementTypes.X_SQL_DATA,
            session.getCurrentSchemaHsqlName());

        if (procedure.maxDynamicResults > 0) {
            statementReturnType = StatementTypes.RETURN_ANY;
        }

        this.procedure = procedure;
        this.arguments = arguments;

        setDatabaseObjects(session, compileContext);
        checkAccessRights(session);
        session.getGrantee().checkAccess(procedure);

        if (procedure.isPSM()) {
            isTransactionStatement = readTableNames.length > 0
                                     || writeTableNames.length > 0;
        }
    }

    Result getResult(Session session) {

        Result result = expression == null
                        ? getProcedureResult(session)
                        : getExpressionResult(session);

        result.setStatementType(statementReturnType);

        return result;
    }

    Result getProcedureResult(Session session) {

        Object[] data = getArgsArray();

        for (int i = 0; i < arguments.length; i++) {
            Expression e = arguments[i];

            if (e != null) {
                Type   targetType = procedure.getParameter(i).getDataType();
                Object value      = e.getValue(session);

                data[i] = targetType.convertToType(
                    session,
                    value,
                    e.getDataType());
            }
        }

        session.sessionContext.pushRoutineInvocation();

        Result   result = Result.updateZeroResult;
        Object[] callArguments;

        try {
            session.sessionContext.routineArguments = data;
            session.sessionContext.routineVariables =
                ValuePool.emptyObjectArray;

            if (procedure.isPSM()) {
                result = executePSMProcedure(session);
            } else {
                Connection connection = session.getInternalConnection();

                result = executeJavaProcedure(session, connection);
            }

            callArguments = session.sessionContext.routineArguments;
        } finally {
            session.sessionContext.popRoutineInvocation();

            if (!procedure.isPSM()) {
                session.releaseInternalConnection();
            }
        }

        if (result.isError()) {
            return result;
        }

        for (int i = 0; i < procedure.getParameterCount(); i++) {
            ColumnSchema param = procedure.getParameter(i);
            int          mode  = param.getParameterMode();

            if (mode != SchemaObject.ParameterModes.PARAM_IN) {
                if (arguments[i].isDynamicParam()) {
                    int paramIndex = arguments[i].parameterIndex;

                    session.sessionContext.dynamicArguments[paramIndex] =
                        callArguments[i];
                } else {
                    int varIndex = arguments[i].getColumnIndex();

                    session.sessionContext.routineVariables[varIndex] =
                        callArguments[i];
                }
            }
        }

        Result r = result;

        result = Result.newCallResponse(
            getParametersMetaData().getParameterTypes(),
            id,
            session.sessionContext.dynamicArguments);

        if (procedure.returnsTable()) {
            result.addChainedResult(r);
        } else if (callArguments.length > arguments.length) {
            r = (Result) callArguments[arguments.length];

            result.addChainedResult(r);
        }

        return result;
    }

    private Object[] getArgsArray() {

        Object[] data = ValuePool.emptyObjectArray;
        int      argLength;

        if (procedure.isPSM()) {
            argLength = arguments.length;

            if (procedure.getMaxDynamicResults() > 0) {
                argLength++;
            }
        } else {
            argLength = procedure.javaMethod.getParameterTypes().length;

            if (procedure.javaMethodWithConnection) {
                argLength--;
            }
        }

        if (argLength > 0) {
            data = new Object[argLength];
        }

        return data;
    }

    Result executePSMProcedure(Session session) {

        int variableCount = procedure.getVariableCount();
        int cursorCount   = procedure.getCursorCount();

        session.sessionContext.routineVariables = new Object[variableCount];
        session.sessionContext.routineCursors   = new Result[cursorCount];

        Result result = procedure.statement.execute(session);

        if (result.isError()) {
            return result;
        }

        return result;
    }

    Result executeJavaProcedure(Session session, Connection connection) {

        Result   result        = Result.updateZeroResult;
        Object[] callArguments = session.sessionContext.routineArguments;
        Object[] data = procedure.convertArgsToJava(session, callArguments);

        if (procedure.javaMethodWithConnection) {
            data[0] = connection;
        }

        result = procedure.invokeJavaMethod(session, data);

        procedure.convertArgsToSQL(session, callArguments, data);

        return result;
    }

    Result getExpressionResult(Session session) {

        Object o;    // expression return value
        Result r;

        session.sessionData.startRowProcessing();

        o = expression.getValue(session);

        if (resultMetaData == null) {
            getResultMetaData();
        }

        r = Result.newSingleColumnResult(resultMetaData);

        Object[] row;

        if (expression.getDataType().isArrayType()) {
            row    = new Object[1];
            row[0] = o;
        } else if (o instanceof Object[]) {
            row = (Object[]) o;
        } else {
            row    = new Object[1];
            row[0] = o;
        }

        r.getNavigator().add(row);

        return r;
    }

    TableDerived[] getSubqueries(Session session) {

        OrderedHashSet<TableDerived> subQueries = null;

        if (expression != null) {
            subQueries = expression.collectAllSubqueries(null);
        }

        for (int i = 0; i < arguments.length; i++) {
            subQueries = arguments[i].collectAllSubqueries(subQueries);
        }

        if (subQueries == null || subQueries.isEmpty()) {
            return TableDerived.emptyArray;
        }

        TableDerived[] subQueryArray = new TableDerived[subQueries.size()];

        subQueries.toArray(subQueryArray);

        for (int i = 0; i < subqueries.length; i++) {
            subQueryArray[i].prepareTable(session);
        }

        return subQueryArray;
    }

    public ResultMetaData getResultMetaData() {

        if (resultMetaData != null) {
            return resultMetaData;
        }

        switch (type) {

            case StatementTypes.CALL : {
                if (expression == null) {
                    return ResultMetaData.emptyResultMetaData;
                }

                // TODO:
                //
                // 1.) standard to register metadata for columns of
                // the primary result set, if any, generated by call
                //
                // 2.) Represent the return value, if any (which is
                // not, in truth, a result set), as an OUT parameter
                //
                // For now, I've reverted a bunch of code I had in place
                // and instead simply reflect things as the are, describing
                // a single column result set that communicates
                // the return value.  If the expression generating the
                // return value has a void return type, a result set
                // is described whose single column is of type NULL
                ResultMetaData md = ResultMetaData.newResultMetaData(1);
                ColumnBase column = new ColumnBase(
                    null,
                    null,
                    null,
                    StatementDMQL.RETURN_COLUMN_NAME);

                column.setType(expression.getDataType());

                md.columns[0] = column;

                md.prepareData();

                resultMetaData = md;

                return md;
            }

            default :
                throw Error.runtimeError(
                    ErrorCode.U_S0500,
                    "StatementProcedure");
        }
    }

    /**
     * Returns the metadata for the placeholder parameters.
     */
    public ResultMetaData getParametersMetaData() {

        ResultMetaData meta = super.getParametersMetaData();

        for (int i = 0; i < meta.columnLabels.length; i++) {
            ColumnSchema param = parameters[i].getColumn();

            if (param != null && param.getName() != null) {
                meta.columnLabels[i] = param.getNameString();
            }
        }

        return meta;
    }

    void collectTableNamesForRead(OrderedHashSet<HsqlName> set) {

        if (expression == null) {
            set.addAll(procedure.getTableNamesForRead());
        } else {
            for (int i = 0; i < subqueries.length; i++) {
                if (subqueries[i].queryExpression != null) {
                    subqueries[i].queryExpression.getBaseTableNames(set);
                }
            }

            for (int i = 0; i < routines.length; i++) {
                set.addAll(routines[i].getTableNamesForRead());
            }
        }
    }

    void collectTableNamesForWrite(OrderedHashSet<HsqlName> set) {
        if (expression == null) {
            set.addAll(procedure.getTableNamesForWrite());
        }
    }
}
