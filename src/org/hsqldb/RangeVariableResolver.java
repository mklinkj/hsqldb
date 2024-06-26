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

import org.hsqldb.ParserDQL.CompileContext;
import org.hsqldb.RangeVariable.RangeVariableConditions;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.index.Index;
import org.hsqldb.index.Index.IndexUse;
import org.hsqldb.lib.ArrayUtil;
import org.hsqldb.lib.HashMap;
import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.IntKeyIntValueHashMap;
import org.hsqldb.lib.Iterator;
import org.hsqldb.lib.List;
import org.hsqldb.lib.MultiValueHashMap;
import org.hsqldb.lib.OrderedHashSet;
import org.hsqldb.lib.OrderedIntHashSet;
import org.hsqldb.persist.PersistentStore;

/**
 * Determines how JOIN and WHERE expressions are used in query
 * processing and which indexes are used for table access.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 1.9.0
 */
public class RangeVariableResolver {

    Session                       session;
    QuerySpecification            select;
    RangeVariable[]               rangeVariables;
    Expression                    conditions;
    OrderedHashSet<RangeVariable> rangeVarSet = new OrderedHashSet<>();
    CompileContext                compileContext;
    SortAndSlice                  sortAndSlice = SortAndSlice.noSort;
    boolean                       reorder;

    //
    HsqlArrayList<Expression>[] tempJoinExpressions;
    HsqlArrayList<Expression>[] joinExpressions;
    HsqlArrayList<Expression>[] whereExpressions;
    HsqlArrayList<Expression>   queryConditions = new HsqlArrayList<>();

    //
    Expression[] inExpressions;
    boolean[]    inInJoin;
    int          inExpressionCount  = 0;
    boolean      expandInExpression = true;

    //
    int firstLeftJoinIndex;
    int firstRightJoinIndex;
    int lastRightJoinIndex;
    int firstLateralJoinIndex;
    int firstOuterJoinIndex;
    int lastOuterJoinIndex;

    //
    OrderedIntHashSet colIndexSetEqual = new OrderedIntHashSet();
    IntKeyIntValueHashMap colIndexSetOther = new IntKeyIntValueHashMap();
    OrderedHashSet<Expression> tempExprSet = new OrderedHashSet<>();
    OrderedHashSet<RangeVariable> tempRangeSet = new OrderedHashSet<>();
    HashMap<ColumnSchema, Expression> tempMap = new HashMap<>();
    MultiValueHashMap<ColumnSchema, Expression> tempMultiMap =
        new MultiValueHashMap<>();

    RangeVariableResolver(Session session, QuerySpecification select) {

        this.session        = session;
        this.select         = select;
        this.rangeVariables = select.rangeVariables;
        this.conditions     = select.queryCondition;
        this.compileContext = select.compileContext;
        this.sortAndSlice   = select.sortAndSlice;
        this.reorder        = true;

//        this.expandInExpression = select.checkQueryCondition == null;
        initialise();
    }

    RangeVariableResolver(
            Session session,
            RangeVariable[] rangeVariables,
            Expression conditions,
            CompileContext compileContext,
            boolean reorder) {

        this.session        = session;
        this.rangeVariables = rangeVariables;
        this.conditions     = conditions;
        this.compileContext = compileContext;
        this.reorder        = reorder;

        initialise();
    }

    private void initialise() {

        firstLeftJoinIndex    = rangeVariables.length;
        firstRightJoinIndex   = rangeVariables.length;
        firstLateralJoinIndex = rangeVariables.length;
        firstOuterJoinIndex   = rangeVariables.length;
        inExpressions         = new Expression[rangeVariables.length];
        inInJoin              = new boolean[rangeVariables.length];
        tempJoinExpressions   = new HsqlArrayList[rangeVariables.length];

        for (int i = 0; i < rangeVariables.length; i++) {
            tempJoinExpressions[i] = new HsqlArrayList<>();
        }

        joinExpressions = new HsqlArrayList[rangeVariables.length];

        for (int i = 0; i < rangeVariables.length; i++) {
            joinExpressions[i] = new HsqlArrayList<>();
        }

        whereExpressions = new HsqlArrayList[rangeVariables.length];

        for (int i = 0; i < rangeVariables.length; i++) {
            whereExpressions[i] = new HsqlArrayList<>();
        }

        queryConditions.clear();
    }

    void processConditions() {

        if (session.sessionOptimization < 8) {
            reorder = false;
        }

        decomposeAndConditions(session, conditions, queryConditions);

        for (int i = 0; i < rangeVariables.length; i++) {
            rangeVarSet.add(rangeVariables[i]);

            if (rangeVariables[i].joinCondition == null) {
                continue;
            }

            decomposeAndConditions(
                session,
                rangeVariables[i].joinCondition,
                tempJoinExpressions[i]);
        }

        for (int j = 0; j < queryConditions.size(); j++) {
            Expression e = queryConditions.get(j);

            if (e.isTrue()) {
                continue;
            }

            if (e.isSingleColumnEqual || e.isColumnCondition) {
                RangeVariable range = e.getLeftNode().getRangeVariable();

                if (e.getLeftNode().opType == OpTypes.COLUMN && range != null) {
                    int index = rangeVarSet.getIndex(range);

                    if (index > 0) {
                        rangeVariables[index].isLeftJoin = false;
                    }
                }

                range = e.getRightNode().getRangeVariable();

                if (e.getRightNode().opType == OpTypes.COLUMN
                        && range != null) {
                    int index = rangeVarSet.getIndex(range);

                    if (index > 0) {
                        rangeVariables[index].isLeftJoin = false;
                    }
                }
            }
        }

        for (int i = 0; i < rangeVariables.length; i++) {
            RangeVariable range   = rangeVariables[i];
            boolean       isOuter = false;

            if (range.isLeftJoin) {
                if (firstLeftJoinIndex == rangeVariables.length) {
                    firstLeftJoinIndex = i;
                }

                isOuter = true;
            }

            if (range.isRightJoin) {
                if (firstRightJoinIndex == rangeVariables.length) {
                    firstRightJoinIndex = i;
                }

                lastRightJoinIndex = i;
                isOuter            = true;
            }

            if (range.isLateral) {
                if (firstLateralJoinIndex == rangeVariables.length) {
                    firstLateralJoinIndex = i;
                }

                isOuter = true;
            }

            if (isOuter) {
                if (firstOuterJoinIndex == rangeVariables.length) {
                    firstOuterJoinIndex = i;
                }

                lastOuterJoinIndex = i;
            }
        }

        expandConditions();

        conditions = null;

        reorder();
        assignToLists();
        assignToRangeVariables();

        // rangePositionInJoin and the two bounds are used only together, regardless of any IN ranges added
        if (select != null) {
            select.startInnerRange = 0;
            select.endInnerRange   = rangeVariables.length;

            if (firstRightJoinIndex < rangeVariables.length) {
                select.startInnerRange = firstRightJoinIndex;
            }

            if (firstLeftJoinIndex < rangeVariables.length) {
                select.endInnerRange = firstLeftJoinIndex;
            }
        }

        for (int i = 0; i < rangeVariables.length; i++) {
            rangeVariables[i].rangePositionInJoin = i;
        }

        if (expandInExpression && inExpressionCount != 0) {
            setInConditionsAsTables();
        }
    }

    /**
     * Divides AND and OR conditions and assigns
     */
    static Expression decomposeAndConditions(
            Session session,
            Expression e,
            List<Expression> conditions) {

        if (e == null) {
            return Expression.EXPR_TRUE;
        }

        Expression arg1 = e.getLeftNode();
        Expression arg2 = e.getRightNode();
        int        type = e.getType();

        if (type == OpTypes.AND) {
            arg1 = decomposeAndConditions(session, arg1, conditions);
            arg2 = decomposeAndConditions(session, arg2, conditions);

            if (arg1.isTrue()) {
                return arg2;
            }

            if (arg2.isTrue()) {
                return arg1;
            }

            e.setLeftNode(arg1);
            e.setRightNode(arg2);

            return e;
        } else if (type == OpTypes.EQUAL) {
            if (arg1.getType() == OpTypes.ROW
                    && arg2.getType() == OpTypes.ROW) {
                for (int i = 0; i < arg1.nodes.length; i++) {
                    Expression part = new ExpressionLogical(
                        arg1.nodes[i],
                        arg2.nodes[i]);

                    part.resolveTypes(session, null);
                    conditions.add(part);
                }

                return Expression.EXPR_TRUE;
            }
        }

        if (!e.isTrue()) {
            conditions.add(e);
        }

        return Expression.EXPR_TRUE;
    }

    /**
     * Divides AND and OR conditions and adds to list
     */
    static Expression decomposeOrConditions(
            Expression e,
            List<Expression> conditions) {

        if (e == null) {
            return Expression.EXPR_FALSE;
        }

        Expression arg1 = e.getLeftNode();
        Expression arg2 = e.getRightNode();
        int        type = e.getType();

        if (type == OpTypes.OR) {
            arg1 = decomposeOrConditions(arg1, conditions);
            arg2 = decomposeOrConditions(arg2, conditions);

            if (arg1.isFalse()) {
                return arg2;
            }

            if (arg2.isFalse()) {
                return arg1;
            }

            e = new ExpressionLogical(OpTypes.OR, arg1, arg2);

            return e;
        }

        if (!e.isFalse()) {
            conditions.add(e);
        }

        return Expression.EXPR_FALSE;
    }

    void expandConditions() {

        HsqlArrayList<Expression>[] lists = tempJoinExpressions;

        if (firstRightJoinIndex == rangeVariables.length) {
            moveConditions(lists, 0, firstOuterJoinIndex, queryConditions, -1);
        }

        if (firstOuterJoinIndex < 2) {
            return;
        }

        for (int i = 0; i < firstOuterJoinIndex; i++) {
            moveConditions(lists, 0, firstOuterJoinIndex, lists[i], i);
        }

        if (firstOuterJoinIndex < 3) {
            return;
        }

        for (int i = 0; i < firstOuterJoinIndex; i++) {
            HsqlArrayList<Expression> list = lists[i];

            tempMultiMap.clear();
            tempExprSet.clear();
            tempMap.clear();

            boolean hasValEqual = false;
            boolean hasColEqual = false;
            boolean hasChain    = false;

            for (int j = 0; j < list.size(); j++) {
                Expression e = list.get(j);

                if (e.isTrue()) {
                    continue;
                }

                if (e.isSingleColumnEqual) {
                    hasValEqual = true;

                    if (e.getLeftNode().opType == OpTypes.COLUMN) {
                        tempMap.put(
                            e.getLeftNode().getColumn(),
                            e.getRightNode());
                    } else if (e.getRightNode().opType == OpTypes.COLUMN) {
                        tempMap.put(
                            e.getRightNode().getColumn(),
                            e.getLeftNode());
                    }

                    continue;
                }

                if (!e.isColumnEqual) {
                    continue;
                }

                if (e.getLeftNode().getRangeVariable()
                        == e.getRightNode().getRangeVariable()) {
                    continue;
                }

                if (e.getLeftNode().getRangeVariable() == null
                        || e.getRightNode().getRangeVariable() == null) {
                    continue;
                }

                int idx = rangeVarSet.getIndex(
                    e.getLeftNode().getRangeVariable());

                if (idx < 0) {
                    e.isSingleColumnEqual     = true;
                    e.isSingleColumnCondition = true;

                    tempMap.put(e.getRightNode().getColumn(), e.getLeftNode());
                    continue;
                }

                if (idx >= firstOuterJoinIndex) {
                    continue;
                }

                idx = rangeVarSet.getIndex(e.getRightNode().getRangeVariable());

                if (idx < 0) {
                    e.isSingleColumnEqual     = true;
                    e.isSingleColumnCondition = true;

                    tempMap.put(e.getRightNode().getColumn(), e.getLeftNode());
                    continue;
                }

                if (idx >= firstOuterJoinIndex) {
                    continue;
                }

                hasColEqual = true;

                if (e.getLeftNode().getRangeVariable() == rangeVariables[i]) {
                    ColumnSchema column = e.getLeftNode().getColumn();

                    tempMultiMap.put(column, e.getRightNode());

                    if (tempMultiMap.valueCount(column) > 1) {
                        hasChain = true;
                    }
                } else if (e.getRightNode().getRangeVariable()
                           == rangeVariables[i]) {
                    ColumnSchema column = e.getRightNode().getColumn();

                    tempMultiMap.put(column, e.getLeftNode());

                    if (tempMultiMap.valueCount(column) > 1) {
                        hasChain = true;
                    }
                }
            }

            if (hasChain) {
                Iterator<ColumnSchema> keyIt = tempMultiMap.keySet().iterator();

                while (keyIt.hasNext()) {
                    ColumnSchema key = keyIt.next();
                    Iterator<Expression> it = tempMultiMap.getValuesIterator(
                        key);

                    tempExprSet.clear();

                    while (it.hasNext()) {
                        tempExprSet.add(it.next());
                    }

                    while (tempExprSet.size() > 1) {
                        Expression e1 = tempExprSet.remove(
                            tempExprSet.size() - 1);

                        for (int j = 0; j < tempExprSet.size(); j++) {
                            Expression e2 = tempExprSet.get(j);

                            closeJoinChain(lists, e1, e2);
                        }
                    }
                }
            }

            if (hasColEqual && hasValEqual) {
                Iterator<ColumnSchema> keyIt = tempMultiMap.keySet().iterator();

                while (keyIt.hasNext()) {
                    ColumnSchema key = keyIt.next();
                    Expression   e1  = tempMap.get(key);

                    if (e1 != null) {
                        Iterator<Expression> it =
                            tempMultiMap.getValuesIterator(
                                key);

                        while (it.hasNext()) {
                            Expression e2 = it.next();
                            Expression e  = new ExpressionLogical(e1, e2);
                            int index = rangeVarSet.getIndex(
                                e2.getRangeVariable());

                            lists[index].add(e);
                        }
                    }
                }
            }
        }
    }

    void moveConditions(
            List<Expression>[] lists,
            int rangeStart,
            int rangeLimit,
            List<Expression> list,
            int listIndex) {

        for (int j = 0; j < list.size(); j++) {
            Expression e = list.get(j);

            tempRangeSet.clear();
            e.collectRangeVariables(rangeVariables, tempRangeSet);

            int index = rangeVarSet.getSmallestIndex(tempRangeSet);

            if (index < rangeStart) {
                continue;
            }

            index = rangeVarSet.getLargestIndex(tempRangeSet);

            if (index >= rangeLimit) {
                continue;
            }

            if (index != listIndex) {
                list.remove(j);
                lists[index].add(e);

                j--;
            }
        }
    }

    void closeJoinChain(
            List<Expression>[] lists,
            Expression e1,
            Expression e2) {

        int idx1  = rangeVarSet.getIndex(e1.getRangeVariable());
        int idx2  = rangeVarSet.getIndex(e2.getRangeVariable());
        int index = idx1 > idx2
                    ? idx1
                    : idx2;

        if (idx1 == -1 || idx2 == -1) {
            return;
        }

        Expression e = new ExpressionLogical(e1, e2);

        for (int i = 0; i < lists[index].size(); i++) {
            if (e.equals(lists[index].get(i))) {
                return;
            }
        }

        lists[index].add(e);
    }

    void reorder() {

        if (!reorder) {
            return;
        }

        if (rangeVariables.length == 1
                || firstRightJoinIndex != rangeVariables.length) {
            return;
        }

        if (firstLeftJoinIndex == 1) {
            return;
        }

        if (firstLateralJoinIndex != rangeVariables.length) {
            return;
        }

        if (sortAndSlice.usingIndex && sortAndSlice.primaryTableIndex != null) {
            return;
        }

        HsqlArrayList<Expression> joins  = new HsqlArrayList<>();
        HsqlArrayList<Expression> starts = new HsqlArrayList<>();

        for (int i = 0; i < firstLeftJoinIndex; i++) {
            HsqlArrayList<Expression> tempJoins = tempJoinExpressions[i];

            for (int j = 0; j < tempJoins.size(); j++) {
                Expression e = tempJoins.get(j);

                if (e.isColumnEqual) {
                    joins.add(e);
                } else if (e.isSingleColumnCondition) {
                    starts.add(e);
                }
            }
        }

        reorderRanges(starts, joins);
    }

    void reorderRanges(
            HsqlArrayList<Expression> starts,
            HsqlArrayList<Expression> joins) {

        if (starts.isEmpty()) {
            return;
        }

        int           position = -1;
        RangeVariable range    = null;
        double        cost     = 1024;

        for (int i = 0; i < firstLeftJoinIndex; i++) {
            Table table = rangeVariables[i].rangeTable;

            if (table instanceof TableDerived) {
                continue;
            }

            collectIndexableColumns(rangeVariables[i], starts);

            IndexUse[] indexes = table.getIndexForColumns(
                session,
                colIndexSetEqual,
                OpTypes.EQUAL,
                false);
            Index index = null;

            for (int j = 0; j < indexes.length; j++) {
                index = indexes[j].index;

                double currentCost = searchCost(
                    session,
                    table,
                    index,
                    indexes[j].columnCount,
                    OpTypes.EQUAL);

                if (currentCost < cost) {
                    cost     = currentCost;
                    position = i;
                }
            }

            if (index == null) {
                Iterator<Integer> it = colIndexSetOther.keySet().iterator();

                while (it.hasNext()) {
                    int colIndex = it.nextInt();

                    index = table.getIndexForColumn(session, colIndex);

                    if (index != null) {
                        cost = table.getRowStore(session).elementCount() / 2.0;

                        if (colIndexSetOther.get(colIndex, 0) > 1) {
                            cost /= 2;
                        }

                        break;
                    }
                }
            }

            if (index == null) {
                continue;
            }

            if (i == 0) {
                position = 0;
                break;
            }
        }

        if (position < 0) {
            return;
        }

        if (position == 0 && firstLeftJoinIndex == 2) {
            return;
        }

        RangeVariable[] newRanges = new RangeVariable[rangeVariables.length];

        ArrayUtil.copyArray(rangeVariables, newRanges, rangeVariables.length);

        range               = newRanges[position];
        newRanges[position] = newRanges[0];
        newRanges[0]        = range;
        position            = 1;

        for (; position < firstLeftJoinIndex; position++) {
            boolean found = false;

            for (int i = 0; i < joins.size(); i++) {
                Expression e = joins.get(i);

                if (e == null) {
                    continue;
                }

                int newPosition = getJoinedRangePosition(
                    e,
                    position,
                    newRanges);

                if (newPosition >= position) {
                    range                  = newRanges[position];
                    newRanges[position]    = newRanges[newPosition];
                    newRanges[newPosition] = range;

                    joins.set(i, null);

                    found = true;
                    break;
                }
            }

            if (found) {
                continue;
            }

            for (int i = 0; i < newRanges.length; i++) {
                Table table = newRanges[i].rangeTable;

                collectIndexableColumns(newRanges[i], starts);

                IndexUse[] indexes = table.getIndexForColumns(
                    session,
                    colIndexSetEqual,
                    OpTypes.EQUAL,
                    false);

                if (indexes.length > 0) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                break;
            }
        }

        if (position != firstLeftJoinIndex) {
            return;
        }

        ArrayUtil.copyArray(newRanges, rangeVariables, rangeVariables.length);
        joins.clear();

        for (int i = 0; i < firstLeftJoinIndex; i++) {
            HsqlArrayList<Expression> tempJoins = tempJoinExpressions[i];

            joins.addAll(tempJoins);
            tempJoins.clear();
        }

        tempJoinExpressions[firstLeftJoinIndex - 1].addAll(joins);
        rangeVarSet.clear();

        for (int i = 0; i < rangeVariables.length; i++) {
            rangeVarSet.add(rangeVariables[i]);
        }
    }

    int getJoinedRangePosition(
            Expression e,
            int position,
            RangeVariable[] currentRanges) {

        int found = -1;

        tempRangeSet.clear();
        e.getJoinRangeVariables(currentRanges, tempRangeSet);

        for (int i = 0; i < tempRangeSet.size(); i++) {
            for (int j = 0; j < currentRanges.length; j++) {
                if (tempRangeSet.get(i) == currentRanges[j]) {
                    if (j >= position) {
                        if (found > 0) {
                            return -1;
                        } else {
                            found = j;
                        }
                    }
                }
            }
        }

        return found;
    }

    /**
     * Assigns the conditions to separate lists
     */
    void assignToLists() {

        int lastOuterIndex = -1;

        for (int i = 0; i < rangeVariables.length; i++) {
            if (rangeVariables[i].isLeftJoin) {
                lastOuterIndex = i;
            }

            if (rangeVariables[i].isRightJoin) {
                lastOuterIndex = i;
            }

            if (lastOuterIndex == i) {
                joinExpressions[i].addAll(tempJoinExpressions[i]);
            } else {
                int start = lastOuterIndex + 1;

                for (int j = 0; j < tempJoinExpressions[i].size(); j++) {
                    Expression e = tempJoinExpressions[i].get(j);

                    assignToJoinLists(e, joinExpressions, start);
                }
            }
        }

        for (int i = 0; i < queryConditions.size(); i++) {
            assignToJoinLists(
                queryConditions.get(i),
                whereExpressions,
                lastRightJoinIndex);
        }
    }

    /**
     * Assigns a single condition to the relevant list of conditions
     *
     * Parameter first indicates the first range variable to which condition
     * can be assigned
     */
    void assignToJoinLists(
            Expression e,
            List<Expression>[] expressionLists,
            int first) {

        if (e == null) {
            return;
        }

        tempRangeSet.clear();
        e.collectRangeVariables(rangeVariables, tempRangeSet);

        int index = rangeVarSet.getLargestIndex(tempRangeSet);

        if (index == -1) {
            index = 0;
        }

        if (index < first) {
            index = first;
        }

        if (e instanceof ExpressionLogical) {
            if (((ExpressionLogical) e).isTerminal) {
                index = expressionLists.length - 1;
            }
        }

        expressionLists[index].add(e);
    }

    /**
     * Assigns conditions to range variables and converts suitable IN conditions
     * to table lookup.
     */
    void assignToRangeVariables() {

        for (int i = 0; i < rangeVariables.length; i++) {
            boolean                 hasIndex = false;
            RangeVariableConditions conditions;

            if (i < firstLeftJoinIndex
                    && firstRightJoinIndex == rangeVariables.length) {
                conditions = rangeVariables[i].joinConditions[0];

                joinExpressions[i].addAll(whereExpressions[i]);
                setIndexConditions(conditions, joinExpressions[i], i, true);
                assignToRangeVariable(conditions, joinExpressions[i]);
            } else {
                conditions = rangeVariables[i].joinConditions[0];

                setIndexConditions(conditions, joinExpressions[i], i, true);

                conditions = rangeVariables[i].joinConditions[0];

                if (conditions.hasIndex()) {
                    hasIndex = true;
                }

                assignToRangeVariable(conditions, joinExpressions[i]);

                conditions = rangeVariables[i].whereConditions[0];

                for (int j = i + 1; j < rangeVariables.length; j++) {
                    if (rangeVariables[j].isRightJoin) {
                        assignToRangeVariable(
                            rangeVariables[j].whereConditions[0],
                            whereExpressions[i]);
                    }
                }

                if (!hasIndex) {
                    setIndexConditions(
                        conditions,
                        whereExpressions[i],
                        i,
                        true);
                }

                assignToRangeVariable(conditions, whereExpressions[i]);
            }
        }
    }

    void assignToRangeVariable(
            RangeVariableConditions conditions,
            List<Expression> exprList) {

        for (int j = 0, size = exprList.size(); j < size; j++) {
            Expression e = exprList.get(j);

            conditions.addCondition(e);
        }
    }

    private void collectIndexableColumns(
            RangeVariable range,
            List<Expression> exprList) {

        colIndexSetEqual.clear();
        colIndexSetOther.clear();

        for (int j = 0, size = exprList.size(); j < size; j++) {
            Expression e = exprList.get(j);

            if (!e.isSingleColumnCondition) {
                continue;
            }

            int idx;

            if (e.getLeftNode().getRangeVariable() == range) {
                idx = e.getLeftNode().getColumnIndex();
            } else if (e.getRightNode().getRangeVariable() == range) {
                idx = e.getRightNode().getColumnIndex();
            } else {
                continue;
            }

            if (e.isSingleColumnEqual) {
                colIndexSetEqual.add(idx);
            } else {
                int count = colIndexSetOther.get(idx, 0);

                colIndexSetOther.put(idx, count + 1);
            }
        }
    }

    /**
     * Assigns a set of conditions to a range variable.
     */
    private void setIndexConditions(
            RangeVariableConditions conditions,
            List<Expression> exprList,
            int rangeVarIndex,
            boolean includeOr) {

        boolean hasIndex;

        if (exprList.isEmpty()) {
            return;
        }

        colIndexSetEqual.clear();
        colIndexSetOther.clear();

        for (int j = 0, size = exprList.size(); j < size; j++) {
            Expression e = exprList.get(j);

            if (e == null) {
                continue;
            }

            if (!e.isIndexable(conditions.rangeVar)) {
                continue;
            }

            int type = e.getType();

            switch (type) {

                case OpTypes.OR : {
                    continue;
                }

                case OpTypes.COLUMN : {
                    continue;
                }

                case OpTypes.EQUAL : {
                    if (e.getSubType() == OpTypes.ANY_QUANTIFIED
                            || e.getSubType() == OpTypes.ALL_QUANTIFIED) {
                        continue;
                    }

                    if (e.getLeftNode().getRangeVariable()
                            != conditions.rangeVar) {
                        continue;
                    }

                    int colIndex = e.getLeftNode().getColumnIndex();

                    colIndexSetEqual.add(colIndex);
                    break;
                }

                case OpTypes.IS_NULL : {
                    if (e.getLeftNode().getRangeVariable()
                            != conditions.rangeVar) {
                        continue;
                    }

                    if (conditions.rangeVar.isLeftJoin) {
                        continue;
                    }

                    int colIndex = e.getLeftNode().getColumnIndex();

                    colIndexSetEqual.add(colIndex);
                    break;
                }

                case OpTypes.NOT : {
                    if (e.getLeftNode().getLeftNode().getRangeVariable()
                            != conditions.rangeVar) {
                        continue;
                    }

                    if (conditions.rangeVar.isLeftJoin) {
                        continue;
                    }

                    int colIndex = e.getLeftNode()
                                    .getLeftNode()
                                    .getColumnIndex();
                    int count = colIndexSetOther.get(colIndex, 0);

                    colIndexSetOther.put(colIndex, count + 1);
                    break;
                }

                case OpTypes.SMALLER :
                case OpTypes.SMALLER_EQUAL :
                case OpTypes.GREATER :
                case OpTypes.GREATER_EQUAL :
                case OpTypes.GREATER_EQUAL_PRE : {
                    if (e.getLeftNode().getRangeVariable()
                            != conditions.rangeVar) {
                        continue;
                    }

                    int colIndex = e.getLeftNode().getColumnIndex();
                    int count    = colIndexSetOther.get(colIndex, 0);

                    colIndexSetOther.put(colIndex, count + 1);
                    break;
                }

                default : {
                    throw Error.runtimeError(
                        ErrorCode.U_S0500,
                        "RangeVariableResolver");
                }
            }
        }

        setEqualityConditions(conditions, exprList, rangeVarIndex);

        hasIndex = conditions.hasIndex();

        if (!hasIndex) {
            setNonEqualityConditions(conditions, exprList, rangeVarIndex);

            hasIndex = conditions.hasIndex();
        }

        if (rangeVarIndex == 0 && sortAndSlice.usingIndex) {
            hasIndex = true;
        }

        boolean isOR = false;

        if (!hasIndex && includeOr) {
            for (int j = 0, size = exprList.size(); j < size; j++) {
                Expression e = exprList.get(j);

                if (e == null) {
                    continue;
                }

                if (e.getType() == OpTypes.OR) {
                    hasIndex = setOrConditions(
                        conditions,
                        (ExpressionLogical) e,
                        rangeVarIndex);

                    if (hasIndex) {
                        exprList.set(j, null);

                        isOR = true;
                        break;
                    }
                } else if (e.getType() == OpTypes.EQUAL
                           && e.exprSubType == OpTypes.ANY_QUANTIFIED) {
                    if (rangeVarIndex >= firstLeftJoinIndex
                            || firstRightJoinIndex != rangeVariables.length) {
                        continue;
                    }

                    if (e.getRightNode().isCorrelated()) {
                        continue;
                    }

                    OrderedIntHashSet set = new OrderedIntHashSet();

                    ((ExpressionLogical) e).addLeftColumnsForAllAny(
                        conditions.rangeVar,
                        set);

                    IndexUse[] indexes =
                        conditions.rangeVar.rangeTable.getIndexForColumns(
                            session,
                            set,
                            OpTypes.EQUAL,
                            false);

                    // code to disable IN optimisation
                    // index = null;
                    if (indexes.length != 0
                            && inExpressions[rangeVarIndex] == null) {
                        inExpressions[rangeVarIndex] = e;
                        inInJoin[rangeVarIndex]      = conditions.isJoin;

                        inExpressionCount++;

                        exprList.set(j, null);
                        break;
                    }
                }
            }
        }

        for (int i = 0, size = exprList.size(); i < size; i++) {
            Expression e = exprList.get(i);

            if (e == null) {
                continue;
            }

            if (isOR) {
                for (int j = 0; j < conditions.rangeVar.joinConditions.length;
                        j++) {
                    if (conditions.isJoin) {
                        conditions.rangeVar.joinConditions[j].nonIndexCondition =
                            ExpressionLogical.andExpressions(
                                e,
                                conditions.rangeVar.joinConditions[j].nonIndexCondition);
                    } else {
                        conditions.rangeVar.whereConditions[j].nonIndexCondition =
                            ExpressionLogical.andExpressions(
                                e,
                                conditions.rangeVar.whereConditions[j].nonIndexCondition);
                    }
                }
            } else {
                conditions.addCondition(e);
                exprList.set(i, null);
            }
        }
    }

    private boolean setOrConditions(
            RangeVariableConditions conditions,
            ExpressionLogical orExpression,
            int rangeVarIndex) {

        HsqlArrayList<Expression> orExprList = new HsqlArrayList<Expression>();

        decomposeOrConditions(orExpression, orExprList);

        RangeVariableConditions[] conditionsArray =
            new RangeVariableConditions[orExprList.size()];

        for (int i = 0; i < orExprList.size(); i++) {
            HsqlArrayList<Expression> exprList = new HsqlArrayList<>();
            Expression                e        = orExprList.get(i);

            decomposeAndConditions(session, e, exprList);

            RangeVariableConditions c = new RangeVariableConditions(conditions);

            setIndexConditions(c, exprList, rangeVarIndex, false);

            conditionsArray[i] = c;

            if (!c.hasIndex()) {

                // deep OR
                return false;
            }
        }

        Expression exclude = null;

        for (int i = 0; i < conditionsArray.length; i++) {
            RangeVariableConditions c = conditionsArray[i];

            conditionsArray[i].excludeConditions = exclude;

            if (i == conditionsArray.length - 1) {
                break;
            }

            Expression e = null;

            if (c.indexCond != null) {
                for (int k = 0; k < c.indexedColumnCount; k++) {
                    e = ExpressionLogical.andExpressions(e, c.indexCond[k]);
                }
            }

            e       = ExpressionLogical.andExpressions(e, c.indexEndCondition);
            e       = ExpressionLogical.andExpressions(e, c.nonIndexCondition);
            exclude = ExpressionLogical.orExpressions(e, exclude);
        }

        if (exclude != null) {

//            return false;
        }

        if (conditions.isJoin) {
            conditions.rangeVar.joinConditions = conditionsArray;
            conditionsArray = new RangeVariableConditions[orExprList.size()];

            ArrayUtil.fillArray(
                conditionsArray,
                conditions.rangeVar.whereConditions[0]);

            conditions.rangeVar.whereConditions = conditionsArray;
        } else {
            conditions.rangeVar.whereConditions = conditionsArray;
            conditionsArray = new RangeVariableConditions[orExprList.size()];

            ArrayUtil.fillArray(
                conditionsArray,
                conditions.rangeVar.joinConditions[0]);

            conditions.rangeVar.joinConditions = conditionsArray;
        }

        return true;
    }

    private void setEqualityConditions(
            RangeVariableConditions conditions,
            List<Expression> exprList,
            int rangeVarIndex) {

        Index index = null;

        if (rangeVarIndex == 0 && sortAndSlice.usingIndex) {
            index = sortAndSlice.primaryTableIndex;

            if (index != null) {
                conditions.rangeIndex = index;
            }
        }

        if (index == null) {
            IndexUse[] indexes =
                conditions.rangeVar.rangeTable.getIndexForColumns(
                    session,
                    colIndexSetEqual,
                    OpTypes.EQUAL,
                    false);

            if (indexes.length == 0) {
                return;
            }

            index = indexes[0].index;

            double cost = Double.MAX_VALUE;

            if (indexes.length > 1) {
                for (int i = 0; i < indexes.length; i++) {
                    PersistentStore store =
                        conditions.rangeVar.rangeTable.getRowStore(
                            session);
                    double currentCost = store.searchCost(
                        session,
                        indexes[i].index,
                        indexes[i].columnCount,
                        OpTypes.EQUAL);

                    if (currentCost < cost) {
                        cost  = currentCost;
                        index = indexes[i].index;
                    }
                }
            }
        }

        int[]        cols                = index.getColumns();
        int          colCount            = cols.length;
        Expression[] firstRowExpressions = new Expression[cols.length];

        for (int j = 0; j < exprList.size(); j++) {
            Expression e = exprList.get(j);

            if (e == null) {
                continue;
            }

            int type = e.getType();

            if (type == OpTypes.EQUAL || type == OpTypes.IS_NULL) {
                if (e.getLeftNode().getRangeVariable() != conditions.rangeVar) {
                    continue;
                }

                if (!e.isIndexable(conditions.rangeVar)) {
                    continue;
                }

                int offset = ArrayUtil.find(
                    cols,
                    e.getLeftNode().getColumnIndex());

                if (offset != -1 && firstRowExpressions[offset] == null) {
                    firstRowExpressions[offset] = e;

                    exprList.set(j, null);
                }
            }
        }

        boolean hasNull = false;

        for (int i = 0; i < firstRowExpressions.length; i++) {
            Expression e = firstRowExpressions[i];

            if (e == null) {
                if (colCount == cols.length) {
                    colCount = i;
                }

                hasNull = true;
                continue;
            }

            if (hasNull) {
                exprList.add(e);

                firstRowExpressions[i] = null;
            }
        }

        if (colCount > 0) {
            conditions.addIndexCondition(firstRowExpressions, index, colCount);
        }
    }

    private void setNonEqualityConditions(
            RangeVariableConditions conditions,
            List<Expression> exprList,
            int rangeVarIndex) {

        if (colIndexSetOther.isEmpty()) {
            return;
        }

        int               currentCount = 0;
        Index             index        = null;
        Iterator<Integer> it;

        if (rangeVarIndex == 0 && sortAndSlice.usingIndex) {
            index = sortAndSlice.primaryTableIndex;
        }

        if (index == null) {
            it = colIndexSetOther.keySet().iterator();

            while (it.hasNext()) {
                int colIndex = it.nextInt();
                int colCount = colIndexSetOther.get(colIndex, 0);

                if (colCount > currentCount) {
                    Index currentIndex =
                        conditions.rangeVar.rangeTable.getIndexForColumn(
                            session,
                            colIndex);

                    if (currentIndex != null) {
                        index        = currentIndex;
                        currentCount = colCount;
                    }
                }
            }
        }

        if (index == null) {
            return;
        }

        int[] cols = index.getColumns();

        for (int j = 0; j < exprList.size(); j++) {
            Expression e = exprList.get(j);

            if (e == null) {
                continue;
            }

            boolean isIndexed = false;

            switch (e.getType()) {

                case OpTypes.NOT : {
                    if (e.getLeftNode().getType() == OpTypes.IS_NULL
                            && cols[0]
                            == e.getLeftNode().getLeftNode().getColumnIndex()) {
                        isIndexed = true;
                    }

                    break;
                }

                case OpTypes.SMALLER :
                case OpTypes.SMALLER_EQUAL :
                case OpTypes.GREATER :
                case OpTypes.GREATER_EQUAL :
                case OpTypes.GREATER_EQUAL_PRE : {
                    if (cols[0] == e.getLeftNode().getColumnIndex()) {
                        if (e.getRightNode() != null
                                && !e.getRightNode().isCorrelated()) {
                            isIndexed = true;
                        }
                    }

                    break;
                }
            }

            if (isIndexed) {
                Expression[] firstRowExpressions =
                    new Expression[index.getColumnCount()];

                firstRowExpressions[0] = e;

                conditions.addIndexCondition(firstRowExpressions, index, 1);
                exprList.set(j, null);
                break;
            }
        }
    }

    /**
     * Converts an IN conditions into a JOIN
     */
    void setInConditionsAsTables() {

        for (int i = rangeVariables.length - 1; i >= 0; i--) {
            RangeVariable     rangeVar = rangeVariables[i];
            ExpressionLogical in       = (ExpressionLogical) inExpressions[i];

            if (in != null) {
                OrderedIntHashSet set = new OrderedIntHashSet();

                in.addLeftColumnsForAllAny(rangeVar, set);

                IndexUse[] indexes = rangeVar.rangeTable.getIndexForColumns(
                    session,
                    set,
                    OpTypes.EQUAL,
                    false);
                Index index           = indexes[0].index;
                int   indexedColCount = 0;

                for (int j = 0; j < index.getColumnCount(); j++) {
                    if (set.contains(index.getColumns()[j])) {
                        indexedColCount++;
                    } else {
                        break;
                    }
                }

                RangeVariable newRangeVar = new RangeVariable(
                    in.getRightNode().getTable(),
                    null,
                    null,
                    null,
                    compileContext);

                newRangeVar.isGenerated = true;

                RangeVariable[] newList =
                    new RangeVariable[rangeVariables.length + 1];

                ArrayUtil.copyAdjustArray(
                    rangeVariables,
                    newList,
                    newRangeVar,
                    i,
                    1);

                rangeVariables = newList;

                // make two columns as arg
                Expression[] firstRowExpressions =
                    new Expression[index.getColumnCount()];

                for (int j = 0; j < indexedColCount; j++) {
                    int leftIndex  = index.getColumns()[j];
                    int rightIndex = set.getIndex(leftIndex);
                    Expression e = new ExpressionLogical(
                        rangeVar,
                        leftIndex,
                        newRangeVar,
                        rightIndex);

                    firstRowExpressions[j] = e;
                }

                boolean isOuter = rangeVariables[i].isLeftJoin
                                  || rangeVariables[i].isRightJoin;
                RangeVariableConditions conditions = !inInJoin[i] && isOuter
                        ? rangeVar.whereConditions[0]
                        : rangeVar.joinConditions[0];

                conditions.addIndexCondition(
                    firstRowExpressions,
                    index,
                    indexedColCount);

                for (int j = 0; j < set.size(); j++) {
                    int leftIndex  = set.get(j);
                    int rightIndex = j;
                    Expression e = new ExpressionLogical(
                        rangeVar,
                        leftIndex,
                        newRangeVar,
                        rightIndex);

                    conditions.addCondition(e);
                }
            }
        }
    }

    private double searchCost(
            Session session,
            Table table,
            Index index,
            int count,
            int opType) {

        if (table instanceof TableDerived) {
            return 1000;
        } else {
            return table.getRowStore(session)
                        .searchCost(session, index, count, opType);
        }
    }
}
