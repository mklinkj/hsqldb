/*
 * For work developed by the HSQL Development Group:
 *
 * Copyright (c) 2001-2024, The HSQL Development Group
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
 *
 *
 *
 * For work originally developed by the Hypersonic SQL Group:
 *
 * Copyright (c) 1995-2000, The Hypersonic SQL Group.
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
 * Neither the name of the Hypersonic SQL Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE HYPERSONIC SQL GROUP,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software consists of voluntary contributions made by many individuals
 * on behalf of the Hypersonic SQL Group.
 */


package org.hsqldb;

import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.HsqlByteArrayOutputStream;
import org.hsqldb.lib.StringUtil;
import org.hsqldb.types.BinaryData;
import org.hsqldb.types.BlobData;
import org.hsqldb.types.CharacterType;
import org.hsqldb.types.ClobData;
import org.hsqldb.types.LobData;
import org.hsqldb.types.Type;

/**
 * Reusable object for processing LIKE queries.
 *
 * Rewritten in HSQLDB based on original Hypersonic code.<p>
 *
 * @author Fred Toussi (fredt@users dot sourceforge dot net)
 * @author Thomas Mueller (Hypersonic SQL Group)
 * @version 2.7.3
 * @since 1.6.2
 */

// campbell-burnet@users 20030930 - patch 1.7.2 - optimize into joins if possible
// fredt@users 20031006 - patch 1.7.2 - reuse Like objects for all rows
// fredt@users 1.9.0 - LIKE for binary strings
// fredt@users 1.9.0 - CompareAt() changes for performance suggested by Gary Frost
class Like implements Cloneable {

    private static final BinaryData maxByteValue = new BinaryData(
        new byte[]{ -128 },
        false);
    private char[]   cLike;
    private int[]    wildCardType;
    private int      iLen;
    private boolean  isIgnoreCase;
    private int      iFirstWildCard;
    private boolean  isNull;
    int              escapeChar;
    String           prefix          = "";
    static final int NORMAL_CHAR     = 0;
    static final int UNDERSCORE_CHAR = 1;
    static final int PERCENT_CHAR    = 2;
    boolean          isVariable      = true;
    boolean          isBinary        = false;
    Type             dataType;

    Like() {}

    void setIgnoreCase(boolean flag) {
        isIgnoreCase = flag;
    }

    private Object getStartsWith() {

        if (iLen == 0) {
            return isBinary
                   ? BinaryData.zeroLengthBinary
                   : "";
        }

        StringBuilder             sb = null;
        HsqlByteArrayOutputStream os = null;

        if (isBinary) {
            os = new HsqlByteArrayOutputStream();
        } else {
            sb = new StringBuilder();
        }

        int i = 0;

        for (; i < iLen && wildCardType[i] == 0; i++) {
            if (isBinary) {
                os.writeByte(cLike[i]);
            } else {
                sb.append(cLike[i]);
            }
        }

        if (i == 0) {
            return null;
        }

        return isBinary
               ? new BinaryData(os.toByteArray(), false)
               : sb.toString();
    }

    Boolean compare(Session session, Object o) {

        if (o == null) {
            return null;
        }

        if (isNull) {
            return null;
        }

        if (isIgnoreCase) {
            o = ((CharacterType) dataType).upper(session, o);
        }

        long    length   = getLength(session, o);
        boolean isString = o instanceof String;

        if (isString && prefix.length() > 0) {
            if (length < prefix.length()) {
                return false;
            }

            o = ((String) o).substring(0, prefix.length());

            int compare = dataType.compare(session, prefix, o);

            return compare == 0;
        }

        return compareAt(session, o, 0, 0, iLen, length, cLike, wildCardType);
    }

    char getChar(Session session, Object o, long i) {

        char c;

        if (isBinary) {
            c = (char) ((BlobData) o).getBytes()[(int) i];
        } else {
            if (o instanceof String) {
                c = ((String) o).charAt((int) i);
            } else if (o instanceof ClobData) {
                c = ((ClobData) o).getChars(session, i, 1)[0];
            } else {
                c = ((char[]) o)[(int) i];
            }
        }

        return c;
    }

    int getLength(SessionInterface session, Object o) {

        int l;

        if (o instanceof LobData) {
            l = (int) ((LobData) o).length(session);
        } else {
            l = ((String) o).length();
        }

        return l;
    }

    private boolean compareAt(
            Session session,
            Object o,
            int i,
            long j,
            int iLen,
            long jLen,
            char[] cLike,
            int[] wildCardType) {

        for (; i < iLen; i++) {
            switch (wildCardType[i]) {

                case NORMAL_CHAR :        // general character
                    if ((j >= jLen) || (cLike[i] != getChar(session, o, j++))) {
                        return false;
                    }

                    break;

                case UNDERSCORE_CHAR :    // underscore: do not test this character
                    if (j++ >= jLen) {
                        return false;
                    }

                    break;

                case PERCENT_CHAR :       // percent: none or any character(s)
                    if (++i >= iLen) {
                        return true;
                    }

                    while (j < jLen) {
                        if ((cLike[i] == getChar(session, o, j))
                                && compareAt(session,
                                             o,
                                             i,
                                             j,
                                             iLen,
                                             jLen,
                                             cLike,
                                             wildCardType)) {
                            return true;
                        }

                        j++;
                    }

                    return false;
            }
        }

        return j == jLen;
    }

    void setPattern(
            Session session,
            Object pattern,
            Object escape,
            boolean hasEscape) {

        isNull = pattern == null;

        if (!hasEscape) {
            escapeChar = -1;
        } else {
            if (escape == null) {
                isNull = true;

                return;
            } else {
                int length = getLength(session, escape);

                if (length != 1) {
                    if (isBinary) {
                        throw Error.error(ErrorCode.X_2200D);
                    } else {
                        throw Error.error(ErrorCode.X_22019);
                    }
                }

                escapeChar = getChar(session, escape, 0);
            }
        }

        if (isNull) {
            return;
        }

        if (isIgnoreCase) {
            pattern = ((CharacterType) dataType).upper(null, pattern);
        }

        iLen           = 0;
        iFirstWildCard = -1;

        int l = getLength(session, pattern);

        cLike        = new char[l];
        wildCardType = new int[l];

        boolean bEscaping = false,
                bPercent  = false;

        for (int i = 0; i < l; i++) {
            char c = getChar(session, pattern, i);

            if (!bEscaping) {
                if (escapeChar == c) {
                    bEscaping = true;
                    continue;
                } else if (c == '_') {
                    wildCardType[iLen] = UNDERSCORE_CHAR;

                    if (iFirstWildCard == -1) {
                        iFirstWildCard = iLen;
                    }
                } else if (c == '%') {
                    if (bPercent) {
                        continue;
                    }

                    bPercent           = true;
                    wildCardType[iLen] = PERCENT_CHAR;

                    if (iFirstWildCard == -1) {
                        iFirstWildCard = iLen;
                    }
                } else {
                    bPercent = false;
                }
            } else {
                if (c == escapeChar || c == '_' || c == '%') {
                    bPercent  = false;
                    bEscaping = false;
                } else {
                    throw Error.error(ErrorCode.X_22025);
                }
            }

            cLike[iLen++] = c;
        }

        if (bEscaping) {
            throw Error.error(ErrorCode.X_22025);
        }

        for (int i = 0; i < iLen - 1; i++) {
            if ((wildCardType[i] == PERCENT_CHAR)
                    && (wildCardType[i + 1] == UNDERSCORE_CHAR)) {
                wildCardType[i]     = UNDERSCORE_CHAR;
                wildCardType[i + 1] = PERCENT_CHAR;
            }
        }

        if (isBinary) {
            return;
        }

        prefix = "";

        int     prefixLength = 0;
        boolean found        = false;

        outerloop:
        for (int i = 0; i < iLen; i++) {
            switch (wildCardType[i]) {

                case NORMAL_CHAR : {
                    if (found) {
                        found = false;
                        break outerloop;
                    }

                    break;
                }

                case UNDERSCORE_CHAR : {
                    found = false;
                    break outerloop;
                }

                case PERCENT_CHAR : {
                    if (found) {
                        found = false;
                        break outerloop;
                    }

                    prefixLength = i;
                    found        = true;
                }
            }
        }

        if (found) {
            prefix = String.valueOf(cLike, 0, prefixLength);
        }
    }

    boolean isEquivalentToUnknownPredicate() {
        return !isVariable && isNull;
    }

    boolean isEquivalentToEqualsPredicate() {
        return !isVariable && iFirstWildCard == -1;
    }

    boolean isEquivalentToNotNullPredicate() {

        if (isVariable || isNull || iFirstWildCard == -1) {
            return false;
        }

        for (int i = 0; i < wildCardType.length; i++) {
            if (wildCardType[i] != PERCENT_CHAR) {
                return false;
            }
        }

        return true;
    }

    int getFirstWildCardIndex() {
        return iFirstWildCard;
    }

    Object getRangeLow() {
        return getStartsWith();
    }

    public String describe(Session session) {

        StringBuilder sb = new StringBuilder();

        sb.append(super.toString())
          .append("[\n")
          .append("escapeChar=")
          .append(escapeChar)
          .append('\n')
          .append("isNull=")
          .append(isNull)
          .append('\n')
          .append("isIgnoreCase=")
          .append(isIgnoreCase)
          .append('\n')
          .append("iLen=")
          .append(iLen)
          .append('\n')
          .append("iFirstWildCard=")
          .append(iFirstWildCard)
          .append('\n')
          .append("cLike=");

        if (cLike != null) {
            sb.append(StringUtil.arrayToString(cLike));
        }

        sb.append('\n').append("wildCardType=");

        if (wildCardType != null) {
            sb.append(StringUtil.arrayToString(wildCardType));
        }

        sb.append(']');

        return sb.toString();
    }

    public Like duplicate() {

        try {
            return (Like) super.clone();
        } catch (CloneNotSupportedException ex) {
            throw Error.runtimeError(ErrorCode.U_S0500, "Expression");
        }
    }
}
