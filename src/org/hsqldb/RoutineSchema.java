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
import org.hsqldb.lib.ArrayUtil;
import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.OrderedHashSet;
import org.hsqldb.rights.Grantee;
import org.hsqldb.types.NumberType;
import org.hsqldb.types.Type;

/**
 * Implementation of SQL procedure and functions
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 *
 * @version 2.7.3
 * @since 1.9.0
 */
public class RoutineSchema implements SchemaObject {

    static RoutineSchema[] emptyArray = new RoutineSchema[]{};

    //
    Routine[]        routines = Routine.emptyArray;
    int              routineType;
    private HsqlName name;

    public RoutineSchema(int type, HsqlName name) {
        routineType = type;
        this.name   = name;
    }

    public int getType() {
        return routineType;
    }

    public HsqlName getName() {
        return name;
    }

    public HsqlName getSchemaName() {
        return name.schema;
    }

    public HsqlName getCatalogName() {
        return name.schema.schema;
    }

    public Grantee getOwner() {
        return name.schema.owner;
    }

    public OrderedHashSet<HsqlName> getReferences() {

        OrderedHashSet<HsqlName> set = new OrderedHashSet<>();

        for (int i = 0; i < routines.length; i++) {
            set.addAll(routines[i].getReferences());
        }

        return set;
    }

    public OrderedHashSet<SchemaObject> getComponents() {

        OrderedHashSet<SchemaObject> set = new OrderedHashSet<>();

        set.addAll(routines);

        return set;
    }

    public String getSQL() {
        return null;
    }

    public long getChangeTimestamp() {
        return 0;
    }

    public String[] getSQLArray() {

        HsqlArrayList<String> list = new HsqlArrayList<>();

        for (int i = 0; i < routines.length; i++) {
            list.add(routines[i].getSQL());
        }

        String[] array = new String[list.size()];

        list.toArray(array);

        return array;
    }

    public void addSpecificRoutine(
            HsqlNameManager nameManager,
            Routine routine,
            boolean replace) {

        int    signature     = routine.getParameterSignature();
        Type[] types         = routine.getParameterTypes();
        int    matchPosition = routines.length;

        for (int i = 0; i < this.routines.length; i++) {
            if (routines[i].parameterTypes.length == types.length) {
                if (routineType == SchemaObject.PROCEDURE) {
                    if (!replace) {
                        throw Error.error(ErrorCode.X_42605);
                    }
                }

                if (routines[i].isAggregate() != routine.isAggregate()) {
                    throw Error.error(ErrorCode.X_42605);
                }

                boolean match = true;

                for (int j = 0; j < types.length; j++) {
                    if (!routines[i].parameterTypes[j].equals(types[j])) {
                        match = false;
                        break;
                    }
                }

                if (match) {
                    if (replace) {
                        routine.setSpecificName(routines[i].getSpecificName());

                        matchPosition = i;
                        break;
                    } else {
                        throw Error.error(ErrorCode.X_42605);
                    }
                }
            }
        }

        if (routine.getSpecificName() == null) {
            HsqlName specificName = nameManager.newSpecificRoutineName(name);

            routine.setSpecificName(specificName);
        } else {
            routine.getSpecificName().parent = name;
            routine.getSpecificName().schema = name.schema;
        }

        routine.setName(name);

        routine.routineSchema = this;

        if (matchPosition == routines.length) {
            routines = (Routine[]) ArrayUtil.resizeArray(
                routines,
                routines.length + 1);
        }

        routines[matchPosition] = routine;
    }

    public void removeSpecificRoutine(Routine routine) {

        for (int i = 0; i < this.routines.length; i++) {
            if (routines[i] == routine) {
                routines = (Routine[]) ArrayUtil.toAdjustedArray(
                    routines,
                    null,
                    i,
                    -1);
                break;
            }
        }
    }

    public Routine[] getSpecificRoutines() {
        return routines;
    }

    public Routine getSpecificRoutine(Type[] types) {

        Routine routine = findSpecificRoutine(types);

        if (routine == null) {
            StringBuilder sb = new StringBuilder();

            sb.append(name.getSchemaQualifiedStatementName())
              .append(Tokens.T_OPENBRACKET);

            for (int i = 0; i < types.length; i++) {
                if (i != 0) {
                    sb.append(Tokens.T_COMMA);
                }

                if (types[i] == null) {
                    sb.append("null");
                } else {
                    sb.append(types[i].getNameString());
                }
            }

            sb.append(Tokens.T_CLOSEBRACKET);

            throw Error.error(ErrorCode.X_42609, sb.toString());
        }

        return routine;
    }

    public Routine findSpecificRoutine(Type[] types) {

        int matchIndex = -1;

        outerLoop:
        for (int i = 0; i < this.routines.length; i++) {
            int matchCount = 0;

            if (routines[i].isAggregate()) {
                if (types.length == 1) {
                    if (types[0] == null) {
                        return routines[i];
                    }

                    int typeDifference = types[0].precedenceDegree(
                        routines[i].parameterTypes[0]);

                    if (typeDifference < -NumberType.DOUBLE_WIDTH) {
                        if (matchIndex == -1) {
                            continue outerLoop;
                        }

                        int oldDiff = types[0].precedenceDegree(
                            routines[matchIndex].parameterTypes[0]);
                        int newDiff = types[0].precedenceDegree(
                            routines[i].parameterTypes[0]);

                        if (oldDiff == newDiff) {
                            continue outerLoop;
                        }

                        if (newDiff < oldDiff) {
                            matchIndex = i;
                        }

                        continue outerLoop;
                    } else if (typeDifference == 0) {
                        return routines[i];
                    } else {
                        matchIndex = i;
                        continue outerLoop;
                    }
                }

                // treat routine as non-aggregate
            }

            if (routines[i].parameterTypes.length != types.length) {
                continue;
            }

            if (types.length == 0) {
                return this.routines[i];
            }

            for (int j = 0; j < types.length; j++) {
                int typeDifference;

                // parameters
                if (types[j] == null) {
                    continue;
                }

                typeDifference = types[j].precedenceDegree(
                    routines[i].parameterTypes[j]);

                if (typeDifference < -NumberType.DOUBLE_WIDTH) {

                    // accept numeric type narrowing
                    continue outerLoop;
                } else if (typeDifference == 0) {
                    if (matchCount == j) {
                        matchCount = j + 1;
                    }
                }
            }

            if (matchCount == types.length) {
                return routines[i];
            }

            if (matchIndex == -1) {
                matchIndex = i;
                continue;
            }

            for (int j = 0; j < types.length; j++) {
                if (types[j] == null) {
                    continue;
                }

                int oldDiff = types[j].precedenceDegree(
                    routines[matchIndex].parameterTypes[j]);
                int newDiff = types[j].precedenceDegree(
                    routines[i].parameterTypes[j]);

                if (oldDiff == newDiff) {
                    continue;
                }

                if (newDiff < oldDiff) {
                    matchIndex = i;
                }

                continue outerLoop;
            }
        }

        return matchIndex < 0
               ? null
               : routines[matchIndex];
    }

    public Routine getSpecificRoutine(int paramCount) {

        for (int i = 0; i < this.routines.length; i++) {
            if (routines[i].parameterTypes.length == paramCount) {
                return routines[i];
            }
        }

        throw Error.error(ErrorCode.X_42501);
    }

    public boolean isAggregate() {
        return routines[0].isAggregate;
    }
}
