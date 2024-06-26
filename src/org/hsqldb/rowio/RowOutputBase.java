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


package org.hsqldb.rowio;

import java.math.BigDecimal;

import org.hsqldb.ColumnSchema;
import org.hsqldb.Row;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.HsqlByteArrayOutputStream;
import org.hsqldb.lib.OrderedHashMap;
import org.hsqldb.types.BinaryData;
import org.hsqldb.types.BlobData;
import org.hsqldb.types.ClobData;
import org.hsqldb.types.IntervalMonthData;
import org.hsqldb.types.IntervalSecondData;
import org.hsqldb.types.JavaObjectData;
import org.hsqldb.types.TimeData;
import org.hsqldb.types.TimestampData;
import org.hsqldb.types.Type;
import org.hsqldb.types.Types;

/**
 * Base class for writing the data for a database row in different formats.
 * Defines the methods that are independent of storage format and declares
 * the format-dependent methods that subclasses should define.
 *
 * @author Bob Preston (sqlbob@users dot sourceforge.net)
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 1.7.0
 */
abstract class RowOutputBase extends HsqlByteArrayOutputStream
        implements RowOutputInterface {

    /**
     *  Constructor used for persistent storage of a Table row
     */
    public RowOutputBase() {
        super();
    }

    /**
     *  Constructor used for result sets
     */
    public RowOutputBase(int initialSize) {
        super(initialSize);
    }

    /**
     *  Constructor used for network transmission of result sets
     */
    public RowOutputBase(byte[] buffer) {
        super(buffer);
    }

    protected void writeFieldPrefix() {}

    protected abstract void writeFieldType(Type type);

    protected abstract void writeNull(Type type);

    protected abstract void writeChar(String s, Type t);

    protected abstract void writeSmallint(Number o);

    protected abstract void writeInteger(Number o);

    protected abstract void writeBigint(Number o);

    protected abstract void writeReal(Double o);

    protected abstract void writeDecimal(BigDecimal o, Type type);

    protected abstract void writeBoolean(Boolean o);

    protected abstract void writeDate(TimestampData o, Type type);

    protected abstract void writeTime(TimeData o, Type type);

    protected abstract void writeTimestamp(TimestampData o, Type type);

    protected abstract void writeYearMonthInterval(
            IntervalMonthData o,
            Type type);

    protected abstract void writeDaySecondInterval(
            IntervalSecondData o,
            Type type);

    protected abstract void writeOther(JavaObjectData o);

    protected abstract void writeBit(BinaryData o);

    protected abstract void writeUUID(BinaryData o);

    protected abstract void writeBinary(BinaryData o);

    protected abstract void writeClob(ClobData o, Type type);

    protected abstract void writeBlob(BlobData o, Type type);

    protected abstract void writeArray(Object[] o, Type type);

    /**
     *  This method is called to write data for a table row.
     */
    public void writeData(Row row, Type[] types) {
        writeData(types.length, types, row.getData(), null, null);
    }

    /**
     *  This method is called directly to write data for a delete statement.
     */
    public void writeData(
            int l,
            Type[] types,
            Object[] data,
            OrderedHashMap<String, ColumnSchema> cols,
            int[] primaryKeys) {

        boolean hasPK = primaryKeys != null && primaryKeys.length != 0;
        int     limit = hasPK
                        ? primaryKeys.length
                        : l;

        for (int i = 0; i < limit; i++) {
            int    j = hasPK
                       ? primaryKeys[i]
                       : i;
            Object o = data[j];
            Type   t = types[j];

            if (cols != null) {
                ColumnSchema col = cols.get(j);

                writeFieldPrefix();
                writeString(col.getName().statementName);
            }

            writeData(o, t);
        }
    }

    public void writeData(Object o, Type t) {

        if (o == null) {
            writeNull(t);

            return;
        }

        writeFieldType(t);

        switch (t.typeCode) {

            case Types.SQL_ALL_TYPES :
                break;

            case Types.SQL_CHAR :
            case Types.SQL_VARCHAR :
                writeChar((String) o, t);
                break;

            case Types.TINYINT :
            case Types.SQL_SMALLINT :
                writeSmallint((Number) o);
                break;

            case Types.SQL_INTEGER :
                writeInteger((Number) o);
                break;

            case Types.SQL_BIGINT :
                writeBigint((Number) o);
                break;

            case Types.SQL_REAL :
            case Types.SQL_FLOAT :
            case Types.SQL_DOUBLE :
                writeReal((Double) o);
                break;

            case Types.SQL_NUMERIC :
            case Types.SQL_DECIMAL :
                writeDecimal((BigDecimal) o, t);
                break;

            case Types.SQL_BOOLEAN :
                writeBoolean((Boolean) o);
                break;

            case Types.SQL_DATE :
                writeDate((TimestampData) o, t);
                break;

            case Types.SQL_TIME :
            case Types.SQL_TIME_WITH_TIME_ZONE :
                writeTime((TimeData) o, t);
                break;

            case Types.SQL_TIMESTAMP :
            case Types.SQL_TIMESTAMP_WITH_TIME_ZONE :
                writeTimestamp((TimestampData) o, t);
                break;

            case Types.SQL_INTERVAL_YEAR :
            case Types.SQL_INTERVAL_YEAR_TO_MONTH :
            case Types.SQL_INTERVAL_MONTH :
                writeYearMonthInterval((IntervalMonthData) o, t);
                break;

            case Types.SQL_INTERVAL_DAY :
            case Types.SQL_INTERVAL_DAY_TO_HOUR :
            case Types.SQL_INTERVAL_DAY_TO_MINUTE :
            case Types.SQL_INTERVAL_DAY_TO_SECOND :
            case Types.SQL_INTERVAL_HOUR :
            case Types.SQL_INTERVAL_HOUR_TO_MINUTE :
            case Types.SQL_INTERVAL_HOUR_TO_SECOND :
            case Types.SQL_INTERVAL_MINUTE :
            case Types.SQL_INTERVAL_MINUTE_TO_SECOND :
            case Types.SQL_INTERVAL_SECOND :
                writeDaySecondInterval((IntervalSecondData) o, t);
                break;

            case Types.OTHER :
                writeOther((JavaObjectData) o);
                break;

            case Types.SQL_BLOB :
                writeBlob((BlobData) o, t);
                break;

            case Types.SQL_CLOB :
                writeClob((ClobData) o, t);
                break;

            case Types.SQL_ARRAY :
                writeArray((Object[]) o, t);
                break;

            case Types.SQL_GUID :
                writeUUID((BinaryData) o);
                break;

            case Types.SQL_BINARY :
            case Types.SQL_VARBINARY :
                writeBinary((BinaryData) o);
                break;

            case Types.SQL_BIT :
            case Types.SQL_BIT_VARYING :
                writeBit((BinaryData) o);
                break;

            default :
                throw Error.runtimeError(
                    ErrorCode.U_S0500,
                    "RowOutputBase - " + t.getNameString());
        }
    }

    // returns the underlying HsqlByteArrayOutputStream
    public HsqlByteArrayOutputStream getOutputStream() {
        return this;
    }

    public abstract RowOutputInterface duplicate();
}
