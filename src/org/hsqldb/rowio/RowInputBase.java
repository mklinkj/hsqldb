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

import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.HsqlByteArrayInputStream;
import org.hsqldb.types.BinaryData;
import org.hsqldb.types.BlobData;
import org.hsqldb.types.ClobData;
import org.hsqldb.types.IntervalMonthData;
import org.hsqldb.types.IntervalSecondData;
import org.hsqldb.types.TimeData;
import org.hsqldb.types.TimestampData;
import org.hsqldb.types.Type;
import org.hsqldb.types.Types;

/**
 * Base class for reading the data for a database row in different formats.
 * Defines the methods that are independent of storage format and declares
 * the format-dependent methods that subclasses should define.
 *
 * @author Bob Preston (sqlbob@users dot sourceforge.net)
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.4.0
 * @since 1.7.0
 */
abstract class RowInputBase extends HsqlByteArrayInputStream {

    static final int NO_POS = -1;

    // fredt - initialisation may be unnecessary as it's done in resetRow()
    protected long filePos = NO_POS;
    protected int  size;

    RowInputBase() {
        this(new byte[4]);
    }

    RowInputBase(int size) {
        this(new byte[size]);
    }

    /**
     * Constructor takes a complete row
     */
    RowInputBase(byte[] buf) {
        super(buf);

        size = buf.length;
    }

    public long getFilePosition() {

        if (filePos == NO_POS) {

//                Trace.printSystemOut(Trace.DatabaseRowInput_getPos);
        }

        return filePos;
    }

    public int getSize() {
        return size;
    }

// fredt@users - comment - methods used for node and type data
    public abstract int readType();

    public abstract String readString();

// fredt@users - comment - methods used for SQL types
    protected abstract boolean readNull();

    protected abstract String readChar(Type type);

    protected abstract Integer readSmallint();

    protected abstract Integer readInteger();

    protected abstract Long readBigint();

    protected abstract Double readReal();

    protected abstract BigDecimal readDecimal(Type type);

    protected abstract Boolean readBoole();

    protected abstract TimeData readTime(Type type);

    protected abstract TimestampData readDate(Type type);

    protected abstract TimestampData readTimestamp(Type type);

    protected abstract IntervalMonthData readYearMonthInterval(Type type);

    protected abstract IntervalSecondData readDaySecondInterval(Type type);

    protected abstract Object readOther();

    protected abstract BinaryData readUUID();

    protected abstract BinaryData readBinary();

    protected abstract BinaryData readBit();

    protected abstract ClobData readClob();

    protected abstract BlobData readBlob();

    protected abstract Object[] readArray(Type type);

    /**
     *  reads row data from a stream using the JDBC types in colTypes
     *
     * @param  colTypes data types
     */
    public Object[] readData(Type[] colTypes) {

        int      l    = colTypes.length;
        Object[] data = new Object[l];

        for (int i = 0; i < l; i++) {
            Type type = colTypes[i];

            data[i] = readData(type);
        }

        return data;
    }

    public Object readData(Type type) {

        Object o = null;

        if (readNull()) {
            return null;
        }

        switch (type.typeCode) {

            case Types.SQL_ALL_TYPES :
                break;

            case Types.SQL_CHAR :
            case Types.SQL_VARCHAR :
                o = readChar(type);
                break;

            case Types.TINYINT :
            case Types.SQL_SMALLINT :
                o = readSmallint();
                break;

            case Types.SQL_INTEGER :
                o = readInteger();
                break;

            case Types.SQL_BIGINT :
                o = readBigint();
                break;

            case Types.SQL_REAL :
            case Types.SQL_FLOAT :
            case Types.SQL_DOUBLE :
                o = readReal();
                break;

            case Types.SQL_NUMERIC :
            case Types.SQL_DECIMAL :
                o = readDecimal(type);
                break;

            case Types.SQL_DATE :
                o = readDate(type);
                break;

            case Types.SQL_TIME :
            case Types.SQL_TIME_WITH_TIME_ZONE :
                o = readTime(type);
                break;

            case Types.SQL_TIMESTAMP :
            case Types.SQL_TIMESTAMP_WITH_TIME_ZONE :
                o = readTimestamp(type);
                break;

            case Types.SQL_INTERVAL_YEAR :
            case Types.SQL_INTERVAL_YEAR_TO_MONTH :
            case Types.SQL_INTERVAL_MONTH :
                o = readYearMonthInterval(type);
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
                o = readDaySecondInterval(type);
                break;

            case Types.SQL_BOOLEAN :
                o = readBoole();
                break;

            case Types.OTHER :
                o = readOther();
                break;

            case Types.SQL_CLOB :
                o = readClob();
                break;

            case Types.SQL_BLOB :
                o = readBlob();
                break;

            case Types.SQL_ARRAY :
                o = readArray(type);
                break;

            case Types.SQL_GUID :
                o = readUUID();
                break;

            case Types.SQL_BINARY :
            case Types.SQL_VARBINARY :
                o = readBinary();
                break;

            case Types.SQL_BIT :
            case Types.SQL_BIT_VARYING :
                o = readBit();
                break;

            default :
                throw Error.runtimeError(
                    ErrorCode.U_S0500,
                    "RowInputBase - " + type.getNameString());
        }

        return o;
    }

    /**
     *  Used to reset the row, ready for a new row to be written into the
     *  byte[] buffer by an external routine.
     *
     */
    public void resetRow(long filepos, int rowsize) {

        mark = 0;

        reset();

        if (buffer.length < rowsize) {
            buffer = new byte[rowsize];
        }

        filePos   = filepos;
        size      = count = rowsize;
        pos       = 4;
        buffer[0] = (byte) ((rowsize >>> 24) & 0xFF);
        buffer[1] = (byte) ((rowsize >>> 16) & 0xFF);
        buffer[2] = (byte) ((rowsize >>> 8) & 0xFF);
        buffer[3] = (byte) ((rowsize) & 0xFF);
    }

    /**
     *  Used to reset the row, ready for a new row to be written into the
     *  byte[] buffer by an external routine.
     *
     */
    public void resetBlock(long filepos, int rowsize) {

        mark = 0;

        reset();

        if (buffer.length < rowsize) {
            buffer = new byte[rowsize];
        }

        filePos = filepos;
        size    = count = rowsize;
    }

    public byte[] getBuffer() {
        return buffer;
    }

    public int skipBytes(int n) {
        throw Error.runtimeError(ErrorCode.U_S0500, "RowInputBase");
    }

    public String readLine() {
        throw Error.runtimeError(ErrorCode.U_S0500, "RowInputBase");
    }
}
