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
import org.hsqldb.HsqlNameManager.SimpleName;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.index.Index;
import org.hsqldb.lib.ArrayListIdentity;
import org.hsqldb.lib.List;
import org.hsqldb.lib.OrderedHashMap;
import org.hsqldb.lib.OrderedHashSet;
import org.hsqldb.lib.Set;
import org.hsqldb.map.ValuePool;
import org.hsqldb.navigator.RangeIterator;
import org.hsqldb.persist.PersistentStore;
import org.hsqldb.types.Type;

/**
 * Implementation of column, variable, parameter, etc. access operations.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 1.9.0
 */
public class ExpressionColumn extends Expression {

    public static final ExpressionColumn[] emptyArray =
        new ExpressionColumn[]{};
    static final SimpleName rownumName = HsqlNameManager.getSimpleName(
        "ROWNUM",
        false);

    //
    public static final OrderedHashMap<String, ColumnSchema> diagnosticsList =
        new OrderedHashMap<>();
    static final String[] diagnosticsVariableTokens = new String[]{
        Tokens.T_NUMBER,
        Tokens.T_MORE, Tokens.T_ROW_COUNT };
    public static final int idx_number    = 0;
    public static final int idx_more      = 1;
    public static final int idx_row_count = 2;

    static {
        for (int i = 0; i < diagnosticsVariableTokens.length; i++) {
            HsqlName name = HsqlNameManager.newSystemObjectName(
                diagnosticsVariableTokens[i],
                SchemaObject.VARIABLE);
            Type type = Type.SQL_INTEGER;

            if (Tokens.T_MORE.equals(diagnosticsVariableTokens[i])) {
                type = Type.SQL_CHAR;
            }

            ColumnSchema col = new ColumnSchema(name, type, false, false, null);

            diagnosticsList.add(diagnosticsVariableTokens[i], col);
        }
    }

    //
    ColumnSchema  column;
    String        schema;
    String        tableName;
    String        columnName;
    RangeVariable rangeVariable;

    //
    int rangePosition = -1;

    //
    NumberSequence sequence;
    boolean        isUpdateColumn;

    //
    boolean isParam;

    //

    /**
     * Creates a OpTypes.COLUMN expression
     */
    ExpressionColumn(String schema, String table, String column) {

        super(OpTypes.COLUMN);

        this.schema     = schema;
        this.tableName  = table;
        this.columnName = column;
    }

    /**
     * Column reference in MySQL INSERT
     */
    ExpressionColumn(String column) {

        super(OpTypes.COLUMN);

        this.columnName     = column;
        this.isUpdateColumn = true;
    }

    ExpressionColumn(ColumnSchema column) {

        super(OpTypes.COLUMN);

        this.column     = column;
        this.dataType   = column.getDataType();
        this.columnName = column.getName().name;
    }

    ExpressionColumn(RangeVariable rangeVar, ColumnSchema column) {

        super(OpTypes.COLUMN);

        this.columnIndex   = rangeVar.findColumn(column.getNameString());
        this.column        = column;
        this.dataType      = column.getDataType();
        this.rangeVariable = rangeVar;
        this.columnName    = column.getName().name;
        this.tableName     = rangeVar.getTableAlias().name;

        rangeVariable.addColumn(columnIndex);
    }

    ExpressionColumn(RangeVariable rangeVar, int index) {

        super(OpTypes.COLUMN);

        this.columnIndex = index;

        setAutoAttributesAsColumn(rangeVar, columnIndex);
    }

    /**
     * Creates a temporary OpTypes.SIMPLE_COLUMN expression
     */
    ExpressionColumn(Expression e, int colIndex, int rangePosition) {

        super(OpTypes.SIMPLE_COLUMN);

        this.dataType      = e.dataType;
        this.columnIndex   = colIndex;
        this.alias         = e.getSimpleName();
        this.rangePosition = rangePosition;
    }

    ExpressionColumn(int type) {

        super(type);

        if (type == OpTypes.ROWNUM) {
            columnName = rownumName.name;
            dataType   = Type.SQL_INTEGER;
        }
    }

    /**
     * For diagnostics vars
     */
    ExpressionColumn(int type, int columnIndex) {

        super(type);

        if (type == OpTypes.DYNAMIC_PARAM) {
            isParam        = true;
            parameterIndex = columnIndex;

            return;
        }

        this.column      = diagnosticsList.get(columnIndex);
        this.columnIndex = columnIndex;
        this.dataType    = column.dataType;
    }

    ExpressionColumn(Expression[] nodes, String name) {

        super(OpTypes.COALESCE);

        this.nodes      = nodes;
        this.columnName = name;
    }

    /**
     * for GROUPING function
     */
    ExpressionColumn(Expression groups) {

        super(OpTypes.GROUPING);

        Expression[] exprs = groups.nodes;

        if (groups.nodes.length == 0) {
            exprs    = new Expression[1];
            exprs[0] = groups;
        }

        this.nodes    = exprs;
        this.dataType = Type.SQL_INTEGER;
    }

    /**
     * Creates an OpCodes.ASTERISK expression
     */
    ExpressionColumn(String schema, String table) {

        super(OpTypes.MULTICOLUMN);

        this.schema    = schema;
        this.tableName = table;
    }

    /**
     * Creates a OpTypes.SEQUENCE expression
     */
    ExpressionColumn(NumberSequence sequence, int opType) {

        super(opType);

        this.sequence = sequence;
        this.dataType = sequence.getDataType();
    }

    void setAutoAttributesAsColumn(RangeVariable range, int i) {

        columnIndex   = i;
        column        = range.getColumn(i);
        dataType      = column.getDataType();
        columnName    = range.getColumnAlias(i).name;
        tableName     = range.getTableAlias().name;
        rangeVariable = range;

        rangeVariable.addColumn(columnIndex);
    }

    void setAttributesAsColumn(RangeVariable range, int i) {

        columnIndex   = i;
        column        = range.getColumn(i);
        dataType      = column.getDataType();
        rangeVariable = range;

        rangeVariable.addColumn(columnIndex);
    }

    public byte getNullability() {

        switch (opType) {

            case OpTypes.COLUMN :
                if (nullability == SchemaObject.Nullability.NULLABLE_UNKNOWN) {
                    return column.getNullability();
                }

                return nullability;

            case OpTypes.COALESCE :
            case OpTypes.SEQUENCE :
            case OpTypes.ROWNUM :
                return SchemaObject.Nullability.NO_NULLS;

            default :
                return SchemaObject.Nullability.NULLABLE_UNKNOWN;
        }
    }

    void setAttributesAsColumn(ColumnSchema column) {
        this.column = column;
        dataType    = column.getDataType();
    }

    SimpleName getSimpleName() {

        if (alias != null) {
            return alias;
        }

        if (rangeVariable != null && rangeVariable.hasColumnAlias()) {
            return rangeVariable.getColumnAlias(columnIndex);
        }

        if (column != null) {
            return column.getName();
        }

        if (opType == OpTypes.COALESCE) {
            return nodes[LEFT].getSimpleName();
        } else if (opType == OpTypes.ROWNUM) {
            return rownumName;
        }

        return null;
    }

    String getAlias() {

        if (alias != null) {
            return alias.name;
        }

        switch (opType) {
            case OpTypes.COLUMN :
            case OpTypes.COALESCE :
            case OpTypes.ROWNUM :
                return columnName;
        }

        return "";
    }

    void collectObjectNames(Set<HsqlName> set) {

        switch (opType) {

            case OpTypes.SEQUENCE :
                HsqlName name = sequence.getName();

                set.add(name);

                return;

            case OpTypes.MULTICOLUMN :
            case OpTypes.DYNAMIC_PARAM :
            case OpTypes.ASTERISK :
            case OpTypes.SIMPLE_COLUMN :
            case OpTypes.COALESCE :
            case OpTypes.PARAMETER :
            case OpTypes.VARIABLE :
                break;

            case OpTypes.COLUMN :
                set.add(column.getName());

                if (column.getName().parent != null) {
                    set.add(column.getName().parent);
                }
        }
    }

    String getColumnName() {

        switch (opType) {

            case OpTypes.COLUMN :
            case OpTypes.PARAMETER :
            case OpTypes.VARIABLE :
                if (column != null) {
                    return column.getName().name;
                }

                if (columnName != null) {
                    return columnName;
                }
        }

        return getAlias();
    }

    public ColumnSchema getColumn() {
        return column;
    }

    String getSchemaName() {
        return schema;
    }

    RangeVariable getRangeVariable() {
        return rangeVariable;
    }

    public List<Expression> resolveColumnReferences(
            Session session,
            RangeGroup rangeGroup,
            int rangeCount,
            RangeGroup[] rangeGroups,
            List<Expression> unresolvedSet,
            boolean acceptsSequences) {

        switch (opType) {

            case OpTypes.SEQUENCE :
                if (!acceptsSequences) {
                    throw Error.error(ErrorCode.X_42598);
                }

                break;

            case OpTypes.MULTICOLUMN :
                throw Error.error(ErrorCode.X_42581, "*");

            case OpTypes.ROWNUM :
            case OpTypes.DYNAMIC_PARAM :
            case OpTypes.ASTERISK :
            case OpTypes.SIMPLE_COLUMN :
            case OpTypes.DIAGNOSTICS_VARIABLE :
                break;

            case OpTypes.GROUPING :
            case OpTypes.COALESCE :
                for (int i = 0; i < nodes.length; i++) {
                    nodes[i].resolveColumnReferences(
                        session,
                        rangeGroup,
                        rangeGroups,
                        unresolvedSet);
                }

                break;

            case OpTypes.COLUMN :
            case OpTypes.PARAMETER :
            case OpTypes.VARIABLE : {
                boolean         resolved      = false;
                RangeVariable[] rangeVarArray = rangeGroup.getRangeVariables();

                if (rangeVariable != null) {
                    return unresolvedSet;
                }

                if (isUpdateColumn) {
                    if (unresolvedSet == null) {
                        unresolvedSet = new ArrayListIdentity<>();
                    }

                    unresolvedSet.add(this);

                    return unresolvedSet;
                }

                for (int i = 0; i < rangeCount; i++) {
                    RangeVariable rangeVar = rangeVarArray[i];

                    if (rangeVar == null) {
                        continue;
                    }

                    if (resolved) {
                        if (session.database.sqlEnforceRefs) {
                            if (resolvesDuplicateColumnReference(rangeVar)) {
                                String message = getColumnName();

                                if (alias != null) {
                                    StringBuilder sb = new StringBuilder(64);

                                    sb.append(message)
                                      .append(' ')
                                      .append(Tokens.T_AS)
                                      .append(' ')
                                      .append(alias.getStatementName());

                                    message = sb.toString();
                                }

                                throw Error.error(ErrorCode.X_42580, message);
                            }
                        }
                    } else {
                        if (resolveColumnReference(rangeVar, false)) {
                            resolved = true;

                            if (!session.database.sqlEnforceRefs) {
                                break;
                            }
                        }
                    }
                }

                if (resolved) {
                    return unresolvedSet;
                }

                if (session.database.sqlSyntaxOra
                        || session.database.sqlSyntaxDb2) {
                    if (acceptsSequences && tableName != null) {
                        if (Tokens.T_CURRVAL.equals(columnName)
                                || Tokens.T_PREVVAL.equals(columnName)) {
                            NumberSequence seq =
                                session.database.schemaManager.findSequence(
                                    session,
                                    tableName,
                                    schema);

                            if (seq != null) {
                                opType     = OpTypes.SEQUENCE_CURRENT;
                                dataType   = seq.getDataType();
                                sequence   = seq;
                                schema     = null;
                                tableName  = null;
                                columnName = null;
                                resolved   = true;
                            }
                        } else if (Tokens.T_NEXTVAL.equals(columnName)) {
                            NumberSequence seq =
                                session.database.schemaManager.findSequence(
                                    session,
                                    tableName,
                                    schema);

                            if (seq != null) {
                                opType     = OpTypes.SEQUENCE;
                                dataType   = seq.getDataType();
                                sequence   = seq;
                                schema     = null;
                                tableName  = null;
                                columnName = null;
                                resolved   = true;
                            }
                        }
                    }
                }

                if (resolved) {
                    return unresolvedSet;
                }

                if (resolveCorrelated(rangeGroup, rangeGroups)) {
                    return unresolvedSet;
                }

                if (unresolvedSet == null) {
                    unresolvedSet = new ArrayListIdentity<>();
                }

                unresolvedSet.add(this);
                break;
            }

            default :
        }

        return unresolvedSet;
    }

    private boolean resolveCorrelated(
            RangeGroup rangeGroup,
            RangeGroup[] rangeGroups) {

        for (int idx = rangeGroups.length - 1; idx >= 0; idx--) {
            RangeVariable[] rangeVarArray =
                rangeGroups[idx].getRangeVariables();

            for (int i = 0; i < rangeVarArray.length; i++) {
                RangeVariable rangeVar = rangeVarArray[i];

                if (rangeVar == null) {
                    continue;
                }

                if (resolveColumnReference(rangeVar, true)) {
                    switch (opType) {

                        case OpTypes.COLUMN :
                        case OpTypes.COALESCE : {
                            rangeGroup.setCorrelated();

                            for (int idxx = rangeGroups.length - 1; idxx > idx;
                                    idxx--) {
                                rangeGroups[idxx].setCorrelated();
                            }
                        }
                    }

                    return true;
                }
            }
        }

        return false;
    }

    boolean resolveColumnReference(RangeVariable rangeVar, boolean outer) {

        if (tableName == null) {
            Expression e = rangeVar.getColumnExpression(columnName);

            if (e != null) {
                opType   = e.opType;
                nodes    = e.nodes;
                dataType = e.dataType;

                return true;
            }
        }

        int colIndex = rangeVar.findColumn(schema, tableName, columnName);

        if (colIndex == -1) {
            return false;
        }

        switch (rangeVar.rangeType) {

            case RangeVariable.PARAMETER_RANGE :
            case RangeVariable.VARIALBE_RANGE : {
                if (tableName != null) {
                    return false;
                }

                ColumnSchema column = rangeVar.getColumn(colIndex);

                if (column.getParameterMode()
                        == SchemaObject.ParameterModes.PARAM_OUT) {
                    return false;
                } else {
                    opType = rangeVar.rangeType == RangeVariable.VARIALBE_RANGE
                             ? OpTypes.VARIABLE
                             : OpTypes.PARAMETER;
                }

                break;
            }

            case RangeVariable.TRANSITION_RANGE : {
                if (tableName == null) {
                    return false;
                }

                if (schema != null) {
                    return false;
                }

                opType = OpTypes.TRANSITION_VARIABLE;
                break;
            }

            default : {
                break;
            }
        }

        setAttributesAsColumn(rangeVar, colIndex);

        return true;
    }

    boolean resolvesDuplicateColumnReference(RangeVariable rangeVar) {

        if (tableName == null) {
            Expression e = rangeVar.getColumnExpression(columnName);

            if (e != null) {
                return false;
            }
        }

        switch (rangeVar.rangeType) {

            case RangeVariable.PARAMETER_RANGE :
            case RangeVariable.VARIALBE_RANGE :
            case RangeVariable.TRANSITION_RANGE :
                return false;

            default :
                int colIndex = rangeVar.findColumn(
                    schema,
                    tableName,
                    columnName);

                return colIndex != -1;
        }
    }

    public void resolveTypes(Session session, Expression parent) {

        switch (opType) {

            case OpTypes.DEFAULT :
                if (parent != null && parent.opType != OpTypes.ROW) {
                    throw Error.error(ErrorCode.X_42544);
                }

                break;

            case OpTypes.COALESCE : {
                Type type = null;

                nullability = SchemaObject.Nullability.NO_NULLS;

                for (int i = 0; i < nodes.length; i++) {
                    type = Type.getAggregateType(nodes[i].dataType, type);
                }

                dataType = type;
                break;
            }

            case OpTypes.COLUMN : {
                if (dataType == null) {
                    dataType = column.getDataType();
                }
            }
        }
    }

    public Object getValue(Session session) {

        switch (opType) {

            case OpTypes.GROUPING :
                if (session.sessionContext.groupSet == null) {
                    return 0;
                }

                return session.sessionContext.groupSet.isGrouped(
                    session.sessionContext.currentGroup,
                    this);

            case OpTypes.DEFAULT :
                return null;

            case OpTypes.DIAGNOSTICS_VARIABLE : {
                return getDiagnosticsVariable(session);
            }

            case OpTypes.VARIABLE : {
                return session.sessionContext.routineVariables[columnIndex];
            }

            case OpTypes.PARAMETER : {
                return session.sessionContext.routineArguments[columnIndex];
            }

            case OpTypes.TRANSITION_VARIABLE : {
                return session.sessionContext.triggerArguments[rangeVariable.rangePosition][columnIndex];
            }

            case OpTypes.COLUMN : {
                RangeIterator[] iterators =
                    session.sessionContext.rangeIterators;
                Object value = iterators[rangeVariable.rangePosition].getField(
                    columnIndex);

                if (dataType != column.dataType) {
                    value = dataType.convertToType(
                        session,
                        value,
                        column.dataType);
                }

                return value;
            }

            case OpTypes.SIMPLE_COLUMN : {
                Object value =
                    session.sessionContext.rangeIterators[rangePosition].getField(
                        columnIndex);

                return value;
            }

            case OpTypes.COALESCE : {
                for (int i = 0; i < nodes.length; i++) {
                    Object value = nodes[i].getValue(session, dataType);

                    if (value != null) {
                        return value;
                    }
                }

                return null;
            }

            case OpTypes.DYNAMIC_PARAM : {
                return session.sessionContext.dynamicArguments[parameterIndex];
            }

            case OpTypes.SEQUENCE : {
                return session.sessionData.getSequenceValue(sequence);
            }

            case OpTypes.SEQUENCE_CURRENT : {
                return session.sessionData.getSequenceCurrent(sequence);
            }

            case OpTypes.ROWNUM : {
                return ValuePool.getInt(session.sessionContext.rownum);
            }

            case OpTypes.ASTERISK :
            case OpTypes.MULTICOLUMN :
            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "ExpressionColumn");
        }
    }

    private Object getDiagnosticsVariable(Session session) {
        return session.sessionContext.diagnosticsVariables[columnIndex];
    }

    public String getSQL() {

        switch (opType) {

            case OpTypes.DEFAULT :
                return Tokens.T_DEFAULT;

            case OpTypes.DYNAMIC_PARAM :
                return Tokens.T_QUESTION;

            case OpTypes.ASTERISK :
                return "*";

            case OpTypes.COALESCE :
                if (alias != null) {
                    return alias.getStatementName();
                } else {
                    return Tokens.T_COALESCE;
                }
            case OpTypes.DIAGNOSTICS_VARIABLE :
            case OpTypes.VARIABLE :
            case OpTypes.PARAMETER :
                return column.getName().statementName;

            case OpTypes.ROWNUM : {
                StringBuilder sb = new StringBuilder();

                sb.append(Tokens.T_ROWNUM).append('(').append(')');

                return sb.toString();
            }

            case OpTypes.COLUMN : {
                if (column == null) {
                    if (alias != null) {
                        return alias.getStatementName();
                    } else {
                        if (tableName == null) {
                            return columnName;
                        }

                        StringBuilder sb = new StringBuilder(64);

                        sb.append(tableName).append('.').append(columnName);

                        return sb.toString();
                    }
                }

                if (rangeVariable.tableAlias == null) {
                    return column.getName().getSchemaQualifiedStatementName();
                } else {
                    StringBuilder sb = new StringBuilder(64);

                    sb.append(rangeVariable.tableAlias.getStatementName())
                      .append('.')
                      .append(column.getName().statementName);

                    return sb.toString();
                }
            }

            case OpTypes.SIMPLE_COLUMN : {
                if (alias != null) {
                    return alias.getStatementName();
                } else {
                    return Tokens.T_COLUMN_NAME;
                }
            }

            case OpTypes.MULTICOLUMN : {
                if (nodes.length == 0) {
                    return "*";
                }

                StringBuilder sb = new StringBuilder();

                for (int i = 0; i < nodes.length; i++) {
                    Expression e = nodes[i];

                    if (i > 0) {
                        sb.append(',');
                    }

                    String s = e.getSQL();

                    sb.append(s);
                }

                return sb.toString();
            }

            case OpTypes.GROUPING : {
                StringBuilder sb = new StringBuilder();

                sb.append("GROUPING(");

                for (int i = 0; i < nodes.length; i++) {
                    Expression e = nodes[i];

                    if (i > 0) {
                        sb.append(',');
                    }

                    String s = e.getSQL();

                    sb.append(s);
                }

                sb.append(")");

                return sb.toString();
            }

            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "ExpressionColumn");
        }
    }

    protected String describe(Session session, int blanks) {

        StringBuilder sb = new StringBuilder(64);

        for (int i = 0; i < blanks; i++) {
            sb.append(' ');
        }

        switch (opType) {

            case OpTypes.DEFAULT :
                sb.append(Tokens.T_DEFAULT);
                break;

            case OpTypes.ASTERISK :
                sb.append("OpTypes.ASTERISK ");
                break;

            case OpTypes.VARIABLE :
                sb.append("VARIABLE: ").append(column.getName().name);
                break;

            case OpTypes.PARAMETER :
                sb.append(Tokens.T_PARAMETER)
                  .append(": ")
                  .append(column.getName().name);
                break;

            case OpTypes.COALESCE :
                sb.append(Tokens.T_COLUMN).append(": ").append(columnName);

                if (alias != null) {
                    sb.append(" AS ").append(alias.name);
                }

                break;

            case OpTypes.COLUMN :
                sb.append(Tokens.T_COLUMN)
                  .append(": ")
                  .append(column.getName().getSchemaQualifiedStatementName());

                if (alias != null) {
                    sb.append(" AS ").append(alias.name);
                }

                break;

            case OpTypes.DYNAMIC_PARAM :
                sb.append("DYNAMIC PARAM: ")
                  .append(", TYPE = ")
                  .append(dataType.getDefinition());
                break;

            case OpTypes.SEQUENCE :
                sb.append(Tokens.T_SEQUENCE)
                  .append(": ")
                  .append(sequence.getName().name);
                break;

            case OpTypes.MULTICOLUMN :

            // shouldn't get here
        }

        sb.append('\n');

        return sb.toString();
    }

    /**
     * Returns the table name used in query
     *
     * @return table name
     */
    String getTableName() {

        if (opType == OpTypes.MULTICOLUMN) {
            return tableName;
        }

        if (opType == OpTypes.COLUMN) {
            if (rangeVariable == null) {
                return tableName;
            } else {
                return rangeVariable.getTable().getName().name;
            }
        }

        return "";
    }

    public static void checkColumnsResolved(List<Expression> set) {

        if (set != null && !set.isEmpty()) {
            Expression e = set.get(0);

            if (e instanceof ExpressionColumn) {
                StringBuilder    sb = new StringBuilder();
                ExpressionColumn c  = (ExpressionColumn) e;

                if (c.schema != null) {
                    sb.append(c.schema).append('.');
                }

                if (c.tableName != null) {
                    sb.append(c.tableName).append('.');
                }

                sb.append(c.getColumnName());

                throw Error.error(ErrorCode.X_42501, sb.toString());
            } else {
                OrderedHashSet<Expression> newSet = new OrderedHashSet<>();

                e.collectAllExpressions(
                    newSet,
                    OpTypes.columnExpressionSet,
                    OpTypes.emptyExpressionSet);

                // throw with column name
                checkColumnsResolved(newSet);

                // throw anyway if not found
                throw Error.error(ErrorCode.X_42501);
            }
        }
    }

    public OrderedHashSet<Expression> getUnkeyedColumns(
            OrderedHashSet<Expression> unresolvedSet) {

        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == null) {
                continue;
            }

            unresolvedSet = nodes[i].getUnkeyedColumns(unresolvedSet);
        }

        if (opType == OpTypes.COLUMN
                && !rangeVariable.hasKeyedColumnInGroupBy) {
            if (unresolvedSet == null) {
                unresolvedSet = new OrderedHashSet<>();
            }

            unresolvedSet.add(this);
        }

        return unresolvedSet;
    }

    /**
     * collects all range variables in expression tree
     */
    OrderedHashSet<RangeVariable> collectRangeVariables(
            OrderedHashSet<RangeVariable> set) {

        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                set = nodes[i].collectRangeVariables(set);
            }
        }

        if (rangeVariable != null) {
            if (set == null) {
                set = new OrderedHashSet<>();
            }

            set.add(rangeVariable);
        }

        return set;
    }

    OrderedHashSet<RangeVariable> collectRangeVariables(
            RangeVariable[] rangeVariables,
            OrderedHashSet<RangeVariable> set) {

        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                set = nodes[i].collectRangeVariables(rangeVariables, set);
            }
        }

        if (rangeVariable != null) {
            for (int i = 0; i < rangeVariables.length; i++) {
                if (rangeVariables[i] == rangeVariable) {
                    if (set == null) {
                        set = new OrderedHashSet<>();
                    }

                    set.add(rangeVariable);
                    break;
                }
            }
        }

        return set;
    }

    Expression replaceAliasInOrderBy(
            Session session,
            List<Expression> columns,
            int length) {

        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == null) {
                continue;
            }

            nodes[i] = nodes[i].replaceAliasInOrderBy(session, columns, length);
        }

        switch (opType) {

            case OpTypes.COALESCE :
            case OpTypes.COLUMN : {
                int matchIndex = -1;

                for (int i = 0; i < length; i++) {
                    Expression e         = columns.get(i);
                    SimpleName aliasName = e.alias;
                    String     alias     = aliasName == null
                                           ? null
                                           : aliasName.name;

                    if (schema == null
                            && tableName == null
                            && columnName.equals(alias)) {
                        if (matchIndex < 0) {
                            matchIndex = i;
                        } else if (session.database.sqlEnforceRefs) {
                            String message = getColumnName();

                            throw Error.error(ErrorCode.X_42580, message);
                        }
                    }
                }

                if (matchIndex >= 0) {
                    return columns.get(matchIndex);
                }

                for (int i = 0; i < length; i++) {
                    Expression e = columns.get(i);

                    if (e instanceof ExpressionColumn) {
                        if (equals(e)) {
                            if (matchIndex < 0) {
                                matchIndex = i;
                            } else if (session.database.sqlEnforceRefs) {
                                String message = getColumnName();

                                throw Error.error(ErrorCode.X_42580, message);
                            }
                        }

                        if (tableName == null
                                && schema == null
                                && columnName.equals(
                                    ((ExpressionColumn) e).columnName)) {
                            if (matchIndex < 0) {
                                matchIndex = i;
                            } else if (session.database.sqlEnforceRefs) {
                                String message = getColumnName();

                                throw Error.error(ErrorCode.X_42580, message);
                            }
                        }
                    }
                }

                if (matchIndex >= 0) {
                    return columns.get(matchIndex);
                }

                break;
            }

            default :
        }

        return this;
    }

    Expression replaceColumnReferences(
            Session session,
            RangeVariable range,
            Expression[] list) {

        if (opType == OpTypes.COLUMN && rangeVariable == range) {
            Expression e = list[columnIndex];

            if (dataType == null || dataType.equals(e.dataType)) {
                return e;
            }

            e = e.duplicate();

            e.setDataType(session, dataType);

            return e;
        }

        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == null) {
                continue;
            }

            nodes[i] = nodes[i].replaceColumnReferences(session, range, list);
        }

        return this;
    }

    /**
     * return true if given RangeVariable is used in expression tree
     */
    boolean hasReference(RangeVariable range) {

        if (range == rangeVariable) {
            return true;
        }

        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] != null) {
                if (nodes[i].hasReference(range)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * SIMPLE_COLUMN expressions are now (2.4.0) always ExpressionColumn
     */
    boolean equals(Expression other) {

        if (other == this) {
            return true;
        }

        if (other == null) {
            return false;
        }

        if (opType != other.opType) {
            return false;
        }

        switch (opType) {

            case OpTypes.SIMPLE_COLUMN :
                return this.columnIndex == other.columnIndex
                       && rangeVariable
                          == ((ExpressionColumn) other).rangeVariable;

            case OpTypes.COALESCE :
                return nodes == other.nodes;

            case OpTypes.DYNAMIC_PARAM :
                return parameterIndex == other.parameterIndex;

            case OpTypes.VARIABLE :
            case OpTypes.PARAMETER :
            case OpTypes.COLUMN :
                return column == other.getColumn()
                       && rangeVariable == other.getRangeVariable();

            default :
                return false;
        }
    }

    void replaceRangeVariables(
            RangeVariable[] ranges,
            RangeVariable[] newRanges) {

        for (int i = 0; i < nodes.length; i++) {
            nodes[i].replaceRangeVariables(ranges, newRanges);
        }

        for (int i = 0; i < ranges.length; i++) {
            if (rangeVariable == ranges[i]) {
                rangeVariable = newRanges[i];
                break;
            }
        }
    }

    void resetColumnReferences() {
        rangeVariable = null;
        columnIndex   = -1;
    }

    public boolean isIndexable(RangeVariable range) {

        if (opType == OpTypes.COLUMN) {
            return rangeVariable == range;
        }

        return false;
    }

    public boolean isUnresolvedParam() {
        return isParam && dataType == null;
    }

    boolean isDynamicParam() {
        return isParam;
    }

    void getJoinRangeVariables(
            RangeVariable[] ranges,
            List<RangeVariable> list) {

        if (opType == OpTypes.COLUMN) {
            for (int i = 0; i < ranges.length; i++) {
                if (ranges[i] == rangeVariable) {
                    list.add(rangeVariable);

                    return;
                }
            }
        }
    }

    /**
     * For normal tables only. We don't want to create an index on
     * each column that is checked.
     */
    double costFactor(Session session, RangeVariable range, int operation) {

        if (range.rangeTable instanceof TableDerived) {
            return 1024;
        }

        PersistentStore store = range.rangeTable.getRowStore(session);
        int indexType = range.rangeTable.indexTypeForColumn(
            session,
            columnIndex);
        double          factor;

        switch (indexType) {

            case Index.INDEX_UNIQUE :
                if (operation == OpTypes.EQUAL) {
                    factor = 1;
                } else {
                    factor = store.elementCount() / 2.0;
                }

                break;

            case Index.INDEX_NON_UNIQUE :
                if (operation == OpTypes.EQUAL) {
                    factor = store.elementCount() / 8.0;

                    if (factor > 1024) {
                        factor = 1024;
                    }
                } else {
                    factor = store.elementCount() / 2.0;
                }

                break;

            case Index.INDEX_NONE :
            default :
                factor = store.elementCount();
                break;
        }

        return Math.max(factor, Index.minimumSelectivity);
    }

    public Expression duplicate() {

        if (opType == OpTypes.PARAMETER) {
            return this;
        }

        return super.duplicate();
    }
}
