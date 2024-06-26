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
import org.hsqldb.lib.OrderedHashSet;
import org.hsqldb.rights.Grantee;

/**
 * SQL schema object interface
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 1.9.0
 */
public interface SchemaObject {

    int DATABASE         = 0;
    int CATALOG          = 1;
    int SCHEMA           = 2;
    int TABLE            = 3;
    int VIEW             = 4;
    int CONSTRAINT       = 5;
    int ASSERTION        = 6;
    int SEQUENCE         = 7;
    int TRIGGER          = 8;
    int COLUMN           = 9;
    int TRANSITION       = 10;
    int GRANTEE          = 11;
    int TYPE             = 12;
    int DOMAIN           = 13;
    int CHARSET          = 14;
    int COLLATION        = 15;
    int FUNCTION         = 16;
    int PROCEDURE        = 17;
    int ROUTINE          = 18;
    int CURSOR           = 19;
    int INDEX            = 20;
    int LABEL            = 21;
    int VARIABLE         = 22;
    int PARAMETER        = 23;
    int SPECIFIC_ROUTINE = 24;
    int WRAPPER          = 25;
    int SERVER           = 26;
    int SUBQUERY         = 27;
    int SEARCH           = 28;
    int REFERENCE        = 29;
    int PERIOD           = 30;
    int MODULE           = 31;
    int EXCEPTION        = 32;

    //
    SchemaObject[] emptyArray = new SchemaObject[]{};

    int getType();

    HsqlName getName();

    HsqlName getSchemaName();

    HsqlName getCatalogName();

    Grantee getOwner();

    default OrderedHashSet<HsqlName> getReferences() {
        return new OrderedHashSet<>();
    }

    default OrderedHashSet<SchemaObject> getComponents() {
        return new OrderedHashSet<>();
    }

    default void compile(Session session, SchemaObject parentObject) {}

    String getSQL();

    long getChangeTimestamp();

    interface ConstraintTypes {

        int FOREIGN_KEY = 0;
        int MAIN        = 1;
        int UNIQUE      = 2;
        int CHECK       = 3;
        int PRIMARY_KEY = 4;
        int TEMP        = 5;
    }

    /*
     SQL CLI codes

     Referential Constraint 0 CASCADE
     Referential Constraint 1 RESTRICT
     Referential Constraint 2 SET NULL
     Referential Constraint 3 NO ACTION
     Referential Constraint 4 SET DEFAULT
     */
    interface ReferentialAction {

        int CASCADE     = 0;
        int RESTRICT    = 1;
        int SET_NULL    = 2;
        int NO_ACTION   = 3;
        int SET_DEFAULT = 4;
    }

    interface Deferable {

        int INIT_DEFERRED  = 5;
        int INIT_IMMEDIATE = 6;
        int NOT_DEFERRABLE = 7;
    }

    interface ViewCheckModes {

        int CHECK_NONE    = 0;
        int CHECK_LOCAL   = 1;
        int CHECK_CASCADE = 2;
    }

    interface ParameterModes {

        byte PARAM_UNKNOWN = 0;    // java.sql.ParameterMetaData.parameterModeUnknown
        byte PARAM_IN    = 1;      // java.sql.ParameterMetaData.parameterModeIn
        byte PARAM_OUT   = 4;      // java.sql.ParameterMetaData.parameterModeInOut
        byte PARAM_INOUT = 2;      // java.sql.ParameterMetaData.parameterModeOut
    }

    interface Nullability {

        byte NO_NULLS = 0;            // java.sql.ResultSetMetaData.columnNoNulls
        byte NULLABLE = 1;            // java.sql.ResultSetMetaData.columnNullable
        byte NULLABLE_UNKNOWN = 2;    // java.sql.ResultSetMetaData.columnNullableUnknown
    }

    interface PeriodType {

        int PERIOD_NONE        = 0;
        int PERIOD_SYSTEM      = 1;
        int PERIOD_APPLICATION = 2;
    }

    interface PeriodSystemColumnType {

        int PERIOD_ROW_NONE  = 0;
        int PERIOD_ROW_START = 1;
        int PERIOD_ROW_END   = 2;
    }
}
