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


package org.hsqldb.types;

import org.hsqldb.OpTypes;
import org.hsqldb.Session;
import org.hsqldb.SessionInterface;
import org.hsqldb.Tokens;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.map.BitMap;

/**
 * Type implementation for BOOLEAN.<p>
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.0
 * @since 1.9.0
 */
public final class BooleanType extends Type {

    static final BooleanType booleanType = new BooleanType();

    private BooleanType() {
        super(Types.SQL_BOOLEAN, Types.SQL_BOOLEAN, 0, 0);
    }

    public int displaySize() {
        return 5;
    }

    public int getJDBCTypeCode() {
        return Types.BOOLEAN;
    }

    public Class getJDBCClass() {
        return Boolean.class;
    }

    public String getJDBCClassName() {
        return "java.lang.Boolean";
    }

    public String getNameString() {
        return Tokens.T_BOOLEAN;
    }

    public String getDefinition() {
        return Tokens.T_BOOLEAN;
    }

    public boolean isBooleanType() {
        return true;
    }

    public Type getAggregateType(Type other) {

        if (other == null) {
            return this;
        }

        if (other == SQL_ALL_TYPES) {
            return this;
        }

        if (typeCode == other.typeCode) {
            return this;
        }

        if (other.isCharacterType()) {
            return other.getAggregateType(this);
        }

        throw Error.error(ErrorCode.X_42562);
    }

    public Type getCombinedType(Session session, Type other, int operation) {

        switch (operation) {
            case OpTypes.EQUAL :
                if (other.isBooleanType()) {
                    return this;
                }
        }

        throw Error.error(ErrorCode.X_42562);
    }

    public int compare(Session session, Object a, Object b) {

        if (a == b) {
            return 0;
        }

        if (a == null) {
            return -1;
        }

        if (b == null) {
            return 1;
        }

        boolean boola = ((Boolean) a).booleanValue();
        boolean boolb = ((Boolean) b).booleanValue();

        return (boola == boolb)
               ? 0
               : (boolb
                  ? -1
                  : 1);
    }

    public Object convertToTypeLimits(SessionInterface session, Object a) {
        return a;
    }

    public Object convertToType(
            SessionInterface session,
            Object a,
            Type otherType) {

        if (a == null) {
            return a;
        }

        switch (otherType.typeCode) {

            case Types.SQL_BOOLEAN :
                return a;

            case Types.SQL_BIT :
            case Types.SQL_BIT_VARYING : {
                BinaryData b = (BinaryData) a;

                if (b.bitLength(session) == 1) {
                    return BitMap.isSet(b.getBytes(), 0)
                           ? Boolean.TRUE
                           : Boolean.FALSE;
                }

                break;
            }

            case Types.SQL_CLOB :
                a = Type.SQL_VARCHAR.convertToType(session, a, otherType);

            // fall through
            case Types.SQL_CHAR :
            case Types.SQL_VARCHAR : {
                a = ((CharacterType) otherType).trim(
                    session,
                    a,
                    ' ',
                    true,
                    true);

                if (((String) a).equalsIgnoreCase(Tokens.T_TRUE)) {
                    return Boolean.TRUE;
                } else if (((String) a).equalsIgnoreCase(Tokens.T_FALSE)) {
                    return Boolean.FALSE;
                } else if (((String) a).equalsIgnoreCase(Tokens.T_UNKNOWN)) {
                    return null;
                }

                break;
            }

            case Types.SQL_NUMERIC :
            case Types.SQL_DECIMAL :
                return NumberType.isZero(a)
                       ? Boolean.FALSE
                       : Boolean.TRUE;

            case Types.TINYINT :
            case Types.SQL_SMALLINT :
            case Types.SQL_INTEGER :
            case Types.SQL_BIGINT : {
                if (((Number) a).longValue() == 0) {
                    return Boolean.FALSE;
                } else {
                    return Boolean.TRUE;
                }
            }
        }

        throw Error.error(ErrorCode.X_22018);
    }

    /**
     * ResultSet getBoolean support
     */
    public Object convertToTypeJDBC(
            SessionInterface session,
            Object a,
            Type otherType) {

        if (a == null) {
            return a;
        }

        switch (otherType.typeCode) {

            case Types.SQL_BOOLEAN :
                return a;

            default :
                if (otherType.isLobType()) {
                    throw Error.error(ErrorCode.X_42561);
                }

                if (otherType.isCharacterType()) {
                    if ("0".equals(a)) {
                        return Boolean.FALSE;
                    } else if ("1".equals(a)) {
                        return Boolean.TRUE;
                    } else if ("N".equalsIgnoreCase((String) a)) {
                        return Boolean.FALSE;
                    } else if ("Y".equalsIgnoreCase((String) a)) {
                        return Boolean.TRUE;
                    }
                }

                return convertToType(session, a, otherType);
        }
    }

    public Object convertToDefaultType(SessionInterface session, Object a) {

        if (a == null) {
            return null;
        }

        if (a instanceof Boolean) {
            return a;
        } else if (a instanceof String) {
            return convertToType(session, a, Type.SQL_VARCHAR);
        } else if (a instanceof Number) {
            return NumberType.isZero(a)
                   ? Boolean.FALSE
                   : Boolean.TRUE;
        }

        throw Error.error(ErrorCode.X_42561);
    }

    public Object convertJavaToSQL(SessionInterface session, Object a) {
        return convertToDefaultType(session, a);
    }

    public String convertToString(Object a) {

        if (a == null) {
            return null;
        }

        return ((Boolean) a).booleanValue()
               ? Tokens.T_TRUE
               : Tokens.T_FALSE;
    }

    public String convertToSQLString(Object a) {

        if (a == null) {
            return Tokens.T_UNKNOWN;
        }

        return ((Boolean) a).booleanValue()
               ? Tokens.T_TRUE
               : Tokens.T_FALSE;
    }

    public void convertToJSON(Object a, StringBuilder sb) {

        if (a == null) {
            sb.append("null");

            return;
        }

        String val = ((Boolean) a).toString();

        sb.append(val);
    }

    public boolean canConvertFrom(Type otherType) {

        return otherType.typeCode == Types.SQL_ALL_TYPES
               || otherType.isBooleanType()
               || otherType.isCharacterType()
               || otherType.isIntegralType()
               || (otherType.isBitType() && otherType.precision == 1);
    }

    public int canMoveFrom(Type otherType) {
        return otherType.isBooleanType()
               ? ReType.keep
               : ReType.change;
    }

    public static BooleanType getBooleanType() {
        return booleanType;
    }
}
