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
 * Implementation of SQL period metadata.<p>
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 2.5.0
 */
public class PeriodDefinition implements SchemaObject {

    final HsqlName         periodName;
    final int              periodType;
    ColumnSchema           startColumn;
    ColumnSchema           endColumn;
    OrderedHashSet<String> columnNames;

    PeriodDefinition(
            HsqlName periodName,
            int periodType,
            OrderedHashSet<String> columnNames) {
        this.periodName  = periodName;
        this.periodType  = periodType;
        this.columnNames = columnNames;
    }

    public ColumnSchema getStartColumn() {
        return startColumn;
    }

    public ColumnSchema getEndColumn() {
        return endColumn;
    }

    public int getPeriodType() {
        return periodType;
    }

    public int getType() {
        return SchemaObject.PERIOD;
    }

    public HsqlName getName() {
        return periodName;
    }

    public HsqlName getSchemaName() {
        return periodName.schema;
    }

    public HsqlName getCatalogName() {
        return periodName.schema.schema;
    }

    public Grantee getOwner() {
        return periodName.schema.owner;
    }

    public String getSQL() {
        return "";
    }

    public long getChangeTimestamp() {
        return 0L;
    }
}
