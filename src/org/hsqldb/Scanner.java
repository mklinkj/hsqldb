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

import java.math.BigDecimal;

import java.util.Locale;

import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.ArrayUtil;
import org.hsqldb.lib.CharArrayWriter;
import org.hsqldb.lib.HsqlByteArrayOutputStream;
import org.hsqldb.lib.OrderedIntHashSet;
import org.hsqldb.map.BitMap;
import org.hsqldb.map.ValuePool;
import org.hsqldb.types.BinaryData;
import org.hsqldb.types.BinaryType;
import org.hsqldb.types.BitType;
import org.hsqldb.types.CharacterType;
import org.hsqldb.types.DTIType;
import org.hsqldb.types.DateTimeType;
import org.hsqldb.types.HsqlDateTime;
import org.hsqldb.types.IntervalMonthData;
import org.hsqldb.types.IntervalSecondData;
import org.hsqldb.types.IntervalType;
import org.hsqldb.types.NumberType;
import org.hsqldb.types.TimeData;
import org.hsqldb.types.TimestampData;
import org.hsqldb.types.Type;
import org.hsqldb.types.Types;

/**
 * Scans for SQL tokens.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 1.9.0
 */
public class Scanner {

    /*
    <delimiter token> ::=
    <character string literal>
    | <date string>
    | <time string>
    | <timestamp string>
    | <interval string>
    | <delimited identifier>
    | <SQL special character>
    | <not equals operator>
    | <greater than or equals operator>
    | <less than or equals operator>
    | <concatenation operator>
    | <right arrow>
    | <left bracket trigraph>
    | <right bracket trigraph>
    | <double colon>
    | <double period>

    */
    //J-
    static final char[] specials = new char[] {
        '"',
        '%',
        '&',
        '\'',
        '(',
        ')',
        '*',
        '+',
        ',',
        '-',
        '.',
        '/',
        '\\',
        ':',
        ';',
        '<',
        '=',
        '>',
        '?',
        '[',
        ']',
        '^',
        '_',
        '|',
        '{',
        '}'
    };
    static final String[] multi = new String[] {
        "??(",
        "??)",
        "<>",
        ">=",
        "<=",
        "||",
        "->",
        "::",
        "..",
        "--",
        "/*",
        "*/",
    };

    static final char[] whitespace = {
        // SQL extras
        0x9,
        0xA,
        0xB,
        0xC,
        0xD,
        0x20,
        0x85,
        // U Zs
        0x0020,
        0x00A0,
        0x1680,
        0x180E,
        0x2000,
        0x2001,
        0x2002,
        0x2003,
        0x2004,
        0x2005,
        0x2006,
        0x2007,
        0x2008,
        0x2009,
        0x200A,
        0x202F,
        0x205F,
        0x3000,
        // U Zl
        0x2028,
        // U Zp
        0x2029,
    };

//J+

    static final OrderedIntHashSet whiteSpaceSet = new OrderedIntHashSet(32);

    static {
        for (int i = 0; i < whitespace.length; i++) {
            whiteSpaceSet.add(whitespace[i]);
        }
    }

    // single token types
    String  sqlString;
    int     currentPosition;
    int     tokenPosition;
    int     limit;
    Token   token = new Token();
    boolean nullAndBooleanAsValue;
    boolean backtickQuoting;
    boolean hyphenInBinary;
    boolean charLiteral = true;

    //
    private boolean hasNonSpaceSeparator;

    //
    private static final int maxPooledStringLength =
        ValuePool.getMaxStringLength();

    //
    char[]          charBuffer = new char[256];
    CharArrayWriter charWriter = new CharArrayWriter(charBuffer);

    //
    byte[] byteBuffer = new byte[256];
    HsqlByteArrayOutputStream byteOutputStream = new HsqlByteArrayOutputStream(
        byteBuffer);

    public Scanner() {}

    public Scanner(Session session, String sql) {
        reset(session, sql);
    }

    public void reset(Session session, String sql) {

        reset(sql);

        if (session != null) {
            backtickQuoting = session.database.sqlSyntaxMys;
            charLiteral     = session.database.sqlCharLiteral;
        }
    }

    public void reset(String sql) {

        sqlString            = sql;
        currentPosition      = 0;
        tokenPosition        = 0;
        limit                = sqlString.length();
        hasNonSpaceSeparator = false;

        token.reset();

        token.tokenType = Tokens.X_STARTPARSE;
    }

    void resetState() {
        tokenPosition = currentPosition;

        token.reset();
    }

    public void setNullAndBooleanAsValue() {
        nullAndBooleanAsValue = true;
    }

    public void scanNext() {

        if (currentPosition == limit) {
            resetState();

            token.tokenType = Tokens.X_ENDPARSE;

            return;
        }

        if (scanSeparator()) {

//            token.isDelimiter = true;
        }

        if (currentPosition == limit) {
            resetState();

            token.tokenType = Tokens.X_ENDPARSE;

            return;
        }

        boolean needsDelimiter = !token.isDelimiter;

        scanToken();

        if (needsDelimiter && !token.isDelimiter) {

//            token.tokenType = Token.X_UNKNOWN_TOKEN;
        }

        if (token.isMalformed) {
            token.fullString = getPart(tokenPosition, currentPosition);
        }
    }

    public void scanEnd() {

        if (currentPosition == limit) {
            resetState();

            token.tokenType = Tokens.X_ENDPARSE;
        }
    }

    public Token getToken() {
        return token;
    }

    public String getString() {
        return token.tokenString;
    }

    public int getTokenType() {
        return token.tokenType;
    }

    public Object getValue() {
        return token.tokenValue;
    }

    public Type getDataType() {
        return token.dataType;
    }

    public void replaceToken(String newToken) {

        sqlString = sqlString.substring(
            0,
            tokenPosition) + newToken + sqlString.substring(currentPosition);
        limit           = sqlString.length();
        currentPosition = tokenPosition + newToken.length();
    }

    public int getLineNumber() {
        return Scanner.countEndOfLines(sqlString, tokenPosition) + 1;
    }

    int getTokenPosition() {
        return tokenPosition;
    }

    void position(int position) {
        currentPosition = tokenPosition = position;
    }

    String getPart(int start, int end) {
        return sqlString.substring(start, end);
    }

    private int charAt(int i) {

        if (i >= limit) {
            return -1;
        }

        return sqlString.charAt(i);
    }

    void scanBinaryString() {

        byteOutputStream.reset(byteBuffer);

        while (true) {
            scanBinaryStringPart();

            if (token.isMalformed) {
                return;
            }

            if (scanSeparator() && charAt(currentPosition) == '\'') {
                continue;
            }

            break;
        }

        token.tokenValue = new BinaryData(
            byteOutputStream.toByteArray(),
            false);

        byteOutputStream.reset(byteBuffer);
    }

    /**
     * returns hex value of a hex character, or -1 if not a hex character
     */
    static int getHexValue(int c) {

        if (c >= '0' && c <= '9') {
            c -= '0';
        } else if (c >= 'a' && c <= 'f') {
            c -= ('a' - 10);
        } else if (c >= 'A' && c <= 'F') {
            c -= ('A' - 10);
        } else {
            c = -1;
        }

        return c;
    }

    public void scanUUIDStringWithQuote() {

        try {
            hyphenInBinary = true;

            scanBinaryStringWithQuote();

            if (token.tokenValue instanceof BinaryData) {
                if (((BinaryData) token.tokenValue).length(null) != 16) {
                    token.tokenType   = Tokens.X_MALFORMED_BINARY_STRING;
                    token.isMalformed = true;
                }
            }
        } finally {
            hyphenInBinary = false;
        }
    }

    public void scanBinaryStringWithQuote() {

        resetState();
        scanSeparator();

        if (charAt(currentPosition) != '\'') {
            token.tokenType   = Tokens.X_MALFORMED_BINARY_STRING;
            token.isMalformed = true;

            return;
        }

        scanBinaryString();
    }

    void scanBinaryStringPart() {

        boolean complete = false;
        boolean hi       = true;
        byte    b        = 0;

        currentPosition++;

        for (; currentPosition < limit; currentPosition++) {
            int c = sqlString.charAt(currentPosition);

            // code to remove hyphens from UUID strings
            if (hyphenInBinary && c == '-') {
                continue;
            }

            if (c == ' ') {
                continue;
            }

            if (c == '\'') {
                complete = true;

                currentPosition++;
                break;
            }

            c = getHexValue(c);

            if (c == -1) {

                // bad character
                token.tokenType   = Tokens.X_MALFORMED_BINARY_STRING;
                token.isMalformed = true;

                return;
            }

            if (hi) {
                b  = (byte) (c << 4);
                hi = false;
            } else {
                b += (byte) c;

                byteOutputStream.writeByte(b);

                hi = true;
            }
        }

        if (!hi) {

            // odd nibbles
            token.tokenType   = Tokens.X_MALFORMED_BINARY_STRING;
            token.isMalformed = true;

            return;
        }

        if (!complete) {

            // no end quote
            token.tokenType   = Tokens.X_MALFORMED_BINARY_STRING;
            token.isMalformed = true;
        }
    }

    void scanBitString() {

        BitMap map = new BitMap(0, true);

        while (true) {
            scanBitStringPart(map);

            if (token.isMalformed) {
                return;
            }

            if (scanSeparator() && charAt(currentPosition) == '\'') {
                continue;
            }

            break;
        }

        token.tokenValue = BinaryData.getBitData(map.getBytes(), map.size());
    }

    public void scanBitStringWithQuote() {

        resetState();
        scanSeparator();

        if (charAt(currentPosition) != '\'') {
            token.tokenType   = Tokens.X_MALFORMED_BIT_STRING;
            token.isMalformed = true;

            return;
        }

        scanBitString();
    }

    void scanBitStringPart(BitMap map) {

        boolean complete = false;
        int     bitIndex = map.size();

        currentPosition++;

        for (; currentPosition < limit; currentPosition++) {
            char c = sqlString.charAt(currentPosition);

            if (c == ' ') {
                continue;
            }

            if (c == '\'') {
                complete = true;

                currentPosition++;
                break;
            }

            if (c == '0') {
                map.unset(bitIndex);

                bitIndex++;
            } else if (c == '1') {
                map.set(bitIndex);

                bitIndex++;
            } else {
                token.tokenType   = Tokens.X_MALFORMED_BIT_STRING;
                token.isMalformed = true;

                return;
            }
        }

        if (!complete) {
            token.tokenType   = Tokens.X_MALFORMED_BIT_STRING;
            token.isMalformed = true;

            return;
        }

        map.setSize(bitIndex);
    }

    void convertUnicodeString(int escape) {

        charWriter.reset(charBuffer);

        int position = 0;

        for (;;) {
            int nextIndex = token.tokenString.indexOf(escape, position);

            if (nextIndex < 0) {
                nextIndex = token.tokenString.length();
            }

            charWriter.write(token.tokenString, position, nextIndex - position);

            if (nextIndex == token.tokenString.length()) {
                break;
            }

            nextIndex++;

            if (nextIndex == token.tokenString.length()) {
                token.tokenType   = Tokens.X_MALFORMED_UNICODE_STRING;
                token.isMalformed = true;

                return;
            }

            if (token.tokenString.charAt(nextIndex) == escape) {
                charWriter.write(escape);

                nextIndex++;

                position = nextIndex;
                continue;
            }

            if (nextIndex > token.tokenString.length() - 4) {
                token.tokenType   = Tokens.X_MALFORMED_UNICODE_STRING;
                token.isMalformed = true;

                return;
            }

            int hexCount = 4;
            int hexIndex = 0;
            int hexValue = 0;

            if (token.tokenString.charAt(nextIndex) == '+') {
                nextIndex++;

                if (nextIndex > token.tokenString.length() - 6) {
                    token.tokenType   = Tokens.X_MALFORMED_UNICODE_STRING;
                    token.isMalformed = true;

                    return;
                }

                hexIndex = 2;
                hexCount = 8;
            }

            position = nextIndex;

            for (; hexIndex < hexCount; hexIndex++) {
                int character = token.tokenString.charAt(position++);

                character = getHexValue(character);

                if (character == -1) {
                    token.tokenType   = Tokens.X_MALFORMED_UNICODE_STRING;
                    token.isMalformed = true;

                    return;
                }

                hexValue |= character << ((hexCount - hexIndex - 1) * 4);
            }

            if (hexCount == 8) {
                charWriter.write(hexValue >>> 16);
            }

            charWriter.write(hexValue & (hexValue & 0xffff));
        }

        token.tokenValue = charWriter.toString();
    }

    /**
     * Only for identifiers that are part of known token sequences
     */
    public boolean scanSpecialIdentifier(String identifier) {

        int length = identifier.length();

        if (limit - currentPosition < length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            int character = identifier.charAt(i);

            if (character == sqlString.charAt(currentPosition + i)) {
                continue;
            }

            if (character
                    == Character.toUpperCase(sqlString.charAt(currentPosition
                        + i))) {
                continue;
            }

            return false;
        }

        currentPosition += length;

        return true;
    }

    private int scanEscapeDefinition() {

        int c = charAt(currentPosition);

        if (c == '\'') {
            currentPosition++;

            if (!scanWhitespace()) {
                c = charAt(currentPosition);

                if (getHexValue(c) == -1) {
                    if (c != '+' && c != '\'' && c != '\"') {
                        int escape = c;

                        currentPosition++;

                        c = charAt(currentPosition);

                        if (c == '\'') {
                            currentPosition++;

                            return escape;
                        }
                    }
                }
            }
        }

        return -1;
    }

    private void scanUnicodeString() {

        int escape = '\\';

        scanCharacterString();
        scanSeparator();

        int c = charAt(currentPosition);

        if (c == 'u' || c == 'U') {
            if (scanSpecialIdentifier(Tokens.T_UESCAPE)) {
                scanSeparator();

                escape = scanEscapeDefinition();

                if (escape == -1) {
                    token.tokenType   = Tokens.X_MALFORMED_UNICODE_ESCAPE;
                    token.isMalformed = true;

                    return;
                }
            }
        }

        convertUnicodeString(escape);
    }

    private boolean scanUnicodeIdentifier() {

        int escape = '\\';

        scanStringPart('"');

        if (token.isMalformed) {
            return false;
        }

        token.tokenString = charWriter.toString();

        int c = charAt(currentPosition);

        if (c == 'u' || c == 'U') {
            if (scanSpecialIdentifier(Tokens.T_UESCAPE)) {
                scanSeparator();

                escape = scanEscapeDefinition();

                if (escape == -1) {
                    token.tokenType   = Tokens.X_MALFORMED_UNICODE_ESCAPE;
                    token.isMalformed = true;

                    return false;
                }
            }
        }

        convertUnicodeString(escape);

        return !token.isMalformed;
    }

    boolean shiftPrefixes() {

        if (token.namePrePrePrefix != null) {
            return false;
        }

        token.namePrePrePrefix        = token.namePrePrefix;
        token.isDelimitedPrePrePrefix = token.isDelimitedPrePrefix;
        token.namePrePrefix           = token.namePrefix;
        token.isDelimitedPrePrefix    = token.isDelimitedPrefix;
        token.namePrefix              = token.tokenString;
        token.isDelimitedPrefix = (token.tokenType
                                   == Tokens.X_DELIMITED_IDENTIFIER);

        return true;
    }

    private void scanIdentifierChain() {

        int c = charAt(currentPosition);

        switch (c) {

            case '`' :
                if (backtickQuoting) {
                    charWriter.reset(charBuffer);
                    scanStringPart('`');

                    if (token.isMalformed) {
                        return;
                    }

                    token.tokenType   = Tokens.X_DELIMITED_IDENTIFIER;
                    token.tokenString = charWriter.toString();
                    token.isDelimiter = true;
                }

                break;

            case '"' :
                charWriter.reset(charBuffer);
                scanStringPart('"');

                if (token.isMalformed) {
                    return;
                }

                token.tokenType   = Tokens.X_DELIMITED_IDENTIFIER;
                token.tokenString = charWriter.toString();
                token.isDelimiter = true;
                break;

            case 'u' :
            case 'U' :
                if (charAt(currentPosition + 1) == '&') {
                    if (charAt(currentPosition + 1) == '"') {
                        currentPosition += 3;

                        boolean result = scanUnicodeIdentifier();

                        if (!result) {
                            return;
                        }

                        token.tokenType   = Tokens.X_DELIMITED_IDENTIFIER;
                        token.isDelimiter = false;
                        break;
                    }
                }

            // fall through
            default :
                boolean result = scanUndelimitedIdentifier();

                if (!result) {
                    return;
                }

                token.tokenType   = Tokens.X_IDENTIFIER;
                token.isDelimiter = false;
        }

        boolean hasPreSpace = scanWhitespace();

        c = charAt(currentPosition);

        if (c == '.') {
            if (hasPreSpace) {
                int cNext = charAt(currentPosition + 1);

                if (cNext >= '0' && cNext <= '9') {
                    return;
                }
            }

            currentPosition++;

            scanWhitespace();

            c = charAt(currentPosition);

            if (c == '*') {
                currentPosition++;

                shiftPrefixes();

                token.tokenString = Tokens.T_ASTERISK;
                token.tokenType   = Tokens.ASTERISK;
            } else {
                shiftPrefixes();
                scanIdentifierChain();
            }
        }
    }

    public boolean scanUndelimitedIdentifier() {

        if (currentPosition == limit) {
            return false;
        }

        char    start     = sqlString.charAt(currentPosition);
        boolean irregular = start == '_' || start == '$';

        if (!irregular && !Character.isLetter(start)) {
            token.tokenString = Character.toString(start);
            token.tokenType   = Tokens.X_UNKNOWN_TOKEN;
            token.isMalformed = true;

            return false;
        }

        int i = currentPosition + 1;

        for (; i < limit; i++) {
            char c = sqlString.charAt(i);

            if (c == '$') {
                irregular = true;
                continue;
            }

            if (c == '_' || Character.isLetterOrDigit(c)) {
                continue;
            }

            break;
        }

        token.tokenString = sqlString.substring(currentPosition, i)
                                     .toUpperCase(Locale.ENGLISH);
        currentPosition = i;

        if (nullAndBooleanAsValue) {
            int tokenLength = currentPosition - tokenPosition;

            if (tokenLength == 4 || tokenLength == 5) {
                switch (start) {

                    case 'T' :
                    case 't' :
                        if (Tokens.T_TRUE.equals(token.tokenString)) {
                            token.tokenString = Tokens.T_TRUE;
                            token.tokenType   = Tokens.X_VALUE;
                            token.tokenValue  = Boolean.TRUE;
                            token.dataType    = Type.SQL_BOOLEAN;

                            return false;
                        }

                        break;

                    case 'F' :
                    case 'f' :
                        if (Tokens.T_FALSE.equals(token.tokenString)) {
                            token.tokenString = Tokens.T_FALSE;
                            token.tokenType   = Tokens.X_VALUE;
                            token.tokenValue  = Boolean.FALSE;
                            token.dataType    = Type.SQL_BOOLEAN;

                            return false;
                        }

                        break;

                    case 'N' :
                    case 'n' :
                        if (Tokens.T_NULL.equals(token.tokenString)) {
                            token.tokenString = Tokens.T_NULL;
                            token.tokenType   = Tokens.X_VALUE;
                            token.tokenValue  = null;

                            return false;
                        }

                        break;
                }
            }
        }

        if (irregular) {
            token.hasIrregularChar = true;
        }

        return true;
    }

    void scanNumber() {

        int     c;
        boolean hasDigit      = false;
        boolean hasPoint      = false;
        int     exponentIndex = -1;

        token.tokenType = Tokens.X_VALUE;
        token.dataType  = Type.SQL_INTEGER;

        int tokenStart = currentPosition;

        for (; currentPosition < limit; currentPosition++) {
            boolean end = false;

            c = charAt(currentPosition);

            switch (c) {

                case '0' :
                case '1' :
                case '2' :
                case '3' :
                case '4' :
                case '5' :
                case '6' :
                case '7' :
                case '8' :
                case '9' :
                    hasDigit = true;
                    break;

                case '.' :
                    token.dataType = Type.SQL_NUMERIC;

                    if (hasPoint || exponentIndex != -1) {
                        token.tokenString = sqlString.substring(
                            tokenStart,
                            currentPosition + 1);
                        token.tokenType   = Tokens.X_MALFORMED_NUMERIC;
                        token.isMalformed = true;

                        return;
                    }

                    hasPoint = true;
                    break;

                case 'E' :
                case 'e' :
                    token.dataType = Type.SQL_DOUBLE;

                    if (exponentIndex != -1 || !hasDigit) {
                        token.tokenString = sqlString.substring(
                            tokenStart,
                            currentPosition + 1);
                        token.tokenType   = Tokens.X_MALFORMED_NUMERIC;
                        token.isMalformed = true;

                        return;
                    }

                    hasPoint      = true;
                    exponentIndex = currentPosition;
                    break;

                case '-' :
                case '+' :
                    if (exponentIndex != currentPosition - 1) {
                        end = true;
                    }

                    break;

                case 'K' :
                case 'k' :
                case 'M' :
                case 'm' :
                case 'G' :
                case 'g' :
                case 'T' :
                case 't' :
                case 'P' :
                case 'p' :
                    if (!hasDigit || hasPoint) {
                        token.tokenType   = Tokens.X_MALFORMED_NUMERIC;
                        token.isMalformed = true;

                        return;
                    }

                    String s = Character.toString((char) c)
                                        .toUpperCase(Locale.ENGLISH);

                    token.lobMultiplierType = Tokens.getNonKeywordID(
                        s,
                        Tokens.X_MALFORMED_NUMERIC);

                    if (token.lobMultiplierType == Tokens.X_MALFORMED_NUMERIC) {
                        token.tokenType   = Tokens.X_MALFORMED_NUMERIC;
                        token.isMalformed = true;

                        return;
                    }

                    try {
                        token.tokenValue = ValuePool.getInt(
                            Integer.parseInt(sqlString.substring(tokenStart,
                                    currentPosition)));
                        token.tokenType = Tokens.X_LOB_SIZE;

                        currentPosition++;

                        token.fullString = getPart(
                            tokenPosition,
                            currentPosition);
                    } catch (NumberFormatException e) {
                        token.tokenType   = Tokens.X_MALFORMED_NUMERIC;
                        token.isMalformed = true;
                    }

                    return;

                default :
                    end = true;
                    break;
            }

            if (end) {
                break;
            }
        }

        token.tokenString = sqlString.substring(tokenStart, currentPosition);

        switch (token.dataType.typeCode) {

            case Types.SQL_INTEGER :

                // fredt -  -Integer.MIN_VALUE or -Long.MIN_VALUE are promoted
                // to a wider type.
                if (token.tokenString.length() < 20) {
                    try {
                        long longVal = Long.parseLong(token.tokenString);

                        if (token.tokenString.length() < 11) {
                            if (longVal <= Integer.MAX_VALUE) {
                                token.tokenValue = ValuePool.getInt(
                                    (int) longVal);

                                return;
                            }
                        }

                        token.dataType   = Type.SQL_BIGINT;
                        token.tokenValue = ValuePool.getLong(longVal);

                        return;
                    } catch (Exception e2) {}
                }

                token.dataType = Type.SQL_NUMERIC;

            // fall through
            case Types.SQL_NUMERIC :
                try {
                    BigDecimal decimal = new BigDecimal(token.tokenString);

                    token.tokenValue = decimal;
                    token.dataType = NumberType.getNumberTypeForLiteral(
                        decimal);
                } catch (Exception e2) {
                    token.tokenType   = Tokens.X_MALFORMED_NUMERIC;
                    token.isMalformed = true;

                    return;
                }

                return;

            case Types.SQL_DOUBLE :
                try {
                    double d = Double.parseDouble(token.tokenString);
                    long   l = Double.doubleToLongBits(d);

                    token.tokenValue = ValuePool.getDouble(l);
                } catch (Exception e2) {
                    token.tokenType   = Tokens.X_MALFORMED_NUMERIC;
                    token.isMalformed = true;

                    return;
                }

                return;
        }
    }

    boolean scanSeparator() {

        boolean result = false;

        while (true) {
            boolean whiteSpace = scanWhitespace();

            result |= whiteSpace;

            if (scanCommentAsInlineSeparator()) {
                result               = true;
                hasNonSpaceSeparator = true;
                continue;
            }

            break;
        }

//        token.isDelimiter |= result;
        return result;
    }

    boolean scanCommentAsInlineSeparator() {

        int character = charAt(currentPosition);

        if (character == '-' && charAt(currentPosition + 1) == '-') {
            int pos = sqlString.indexOf('\r', currentPosition + 2);

            if (pos == -1) {
                pos = sqlString.indexOf('\n', currentPosition + 2);
            } else if (charAt(pos + 1) == '\n') {
                pos++;
            }

            if (pos == -1) {
                currentPosition = limit;
            } else {
                currentPosition = pos + 1;
            }

            return true;
        } else if (character == '/' && charAt(currentPosition + 1) == '*') {
            return skipBracketedComment();
        }

        return false;
    }

    public boolean scanWhitespace() {

        boolean result = false;

        for (; currentPosition < limit; currentPosition++) {
            char c = sqlString.charAt(currentPosition);

            if (c == ' ') {
                result = true;
                continue;
            }

            if (whiteSpaceSet.contains(c)) {
                hasNonSpaceSeparator = true;
                result               = true;
                continue;
            }

            break;
        }

        return result;
    }

    private static int countEndOfLines(String s, int toPosition) {

        int eolPos    = -2;
        int eolCode   = 0;
        int lineCount = 0;

        for (int i = 0; i < toPosition; i++) {
            int c = s.charAt(i);

            if (c == '\r' || c == '\n') {
                if (i == eolPos + 1) {
                    if (c == '\n' && eolCode != c) {

                        //
                    } else {
                        lineCount++;
                    }
                } else {
                    lineCount++;
                }

                eolPos  = i;
                eolCode = c;
            }
        }

        return lineCount;
    }

    void scanCharacterString() {

        charWriter.reset(charBuffer);

        while (true) {
            scanStringPart('\'');

            if (token.isMalformed) {
                return;
            }

            if (scanSeparator() && charAt(currentPosition) == '\'') {
                continue;
            }

            break;
        }

        token.tokenString = charWriter.toString();
        token.tokenValue  = token.tokenString;
    }

    public void scanStringPart(char quoteChar) {

        currentPosition++;

        for (;;) {
            int nextIndex = sqlString.indexOf(quoteChar, currentPosition);

            if (nextIndex < 0) {
                token.tokenString = sqlString.substring(currentPosition, limit);
                token.tokenType   = quoteChar == '\''
                                    ? Tokens.X_MALFORMED_STRING
                                    : Tokens.X_MALFORMED_IDENTIFIER;
                token.isMalformed = true;

                return;
            }

            if (charAt(nextIndex + 1) == quoteChar) {
                nextIndex += 1;

                charWriter.write(
                    sqlString,
                    currentPosition,
                    nextIndex - currentPosition);

                currentPosition = nextIndex + 1;
            } else {
                charWriter.write(
                    sqlString,
                    currentPosition,
                    nextIndex - currentPosition);

                currentPosition = nextIndex + 1;
                break;
            }
        }
    }

    /**
     * token [separator]  , nondelimiter {delimiter | separator}
     */
    void scanToken() {

        int typeCode;
        int character = charAt(currentPosition);

        resetState();

        token.tokenType = Tokens.X_IDENTIFIER;

        switch (character) {

/*
            case '%' :
            case '^' :
            case '&' :
            case ':' :
            case '{' :
            case '}' :
                break;
*/
            case '[' :
                token.tokenString = Tokens.T_LEFTBRACKET;
                token.tokenType   = Tokens.LEFTBRACKET;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case ']' :
                token.tokenString = Tokens.T_RIGHTBRACKET;
                token.tokenType   = Tokens.RIGHTBRACKET;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case '(' :
                token.tokenString = Tokens.T_OPENBRACKET;
                token.tokenType   = Tokens.OPENBRACKET;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case ')' :
                token.tokenString = Tokens.T_CLOSEBRACKET;
                token.tokenType   = Tokens.CLOSEBRACKET;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case ',' :
                token.tokenString = Tokens.T_COMMA;
                token.tokenType   = Tokens.COMMA;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case '*' :
                token.tokenString = Tokens.T_ASTERISK;
                token.tokenType   = Tokens.ASTERISK;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case '=' :
                token.tokenString = Tokens.T_EQUALS_OP;
                token.tokenType   = Tokens.EQUALS_OP;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case ';' :
                token.tokenString = Tokens.T_SEMICOLON;
                token.tokenType   = Tokens.SEMICOLON;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case '+' :
                token.tokenString = Tokens.T_PLUS_OP;
                token.tokenType   = Tokens.PLUS_OP;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case ':' :
                if (charAt(currentPosition + 1) == ':') {
                    currentPosition   += 2;
                    token.tokenString = Tokens.T_DOUBLE_COLON;
                    token.tokenType   = Tokens.DOUBLE_COLON_OP;
                    token.isDelimiter = true;

                    return;
                } else {
                    token.tokenString = Tokens.T_COLON;
                    token.tokenType   = Tokens.COLON;

                    currentPosition++;

                    token.isDelimiter = true;

                    return;
                }
            case '?' :
                if (charAt(currentPosition + 1) == '?') {
                    if (charAt(currentPosition + 2) == '(') {
                        token.tokenString = Tokens.T_LEFTBRACKET;
                        token.tokenType   = Tokens.LEFTBRACKET;
                        currentPosition   += 3;
                        token.isDelimiter = true;

                        return;
                    } else if (charAt(currentPosition + 2) == ')') {
                        token.tokenString = Tokens.T_RIGHTBRACKET;
                        token.tokenType   = Tokens.RIGHTBRACKET;
                        currentPosition   += 3;
                        token.isDelimiter = true;

                        return;
                    }
                }

                token.tokenString = Tokens.T_QUESTION;
                token.tokenType   = Tokens.QUESTION;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case '!' :
                if (charAt(currentPosition + 1) == '=') {
                    token.tokenString = Tokens.T_NOT_EQUALS;
                    token.tokenType   = Tokens.NOT_EQUALS;
                    currentPosition   += 2;
                    token.isDelimiter = true;

                    return;
                }

                token.tokenString = sqlString.substring(
                    currentPosition,
                    currentPosition + 2);
                token.tokenType   = Tokens.X_UNKNOWN_TOKEN;
                token.isDelimiter = true;

                return;

            case '<' :
                if (charAt(currentPosition + 1) == '>') {
                    token.tokenString = Tokens.T_NOT_EQUALS;
                    token.tokenType   = Tokens.NOT_EQUALS;
                    currentPosition   += 2;
                    token.isDelimiter = true;

                    return;
                }

                if (charAt(currentPosition + 1) == '=') {
                    token.tokenString = Tokens.T_LESS_EQUALS;
                    token.tokenType   = Tokens.LESS_EQUALS;
                    currentPosition   += 2;
                    token.isDelimiter = true;

                    return;
                }

                token.tokenString = Tokens.T_LESS_OP;
                token.tokenType   = Tokens.LESS_OP;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case '>' :
                if (charAt(currentPosition + 1) == '=') {
                    token.tokenString = Tokens.T_GREATER_EQUALS;
                    token.tokenType   = Tokens.GREATER_EQUALS;
                    currentPosition   += 2;
                    token.isDelimiter = true;

                    return;
                }

                token.tokenString = Tokens.T_GREATER_OP;
                token.tokenType   = Tokens.GREATER_OP;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case '|' :
                if (charAt(currentPosition + 1) == '|') {
                    token.tokenString = Tokens.T_CONCAT_OP;
                    token.tokenType   = Tokens.CONCAT_OP;
                    currentPosition   += 2;
                    token.isDelimiter = true;

                    return;
                }

                token.tokenString = sqlString.substring(
                    currentPosition,
                    currentPosition + 2);
                token.tokenType   = Tokens.X_UNKNOWN_TOKEN;
                token.isDelimiter = true;

                return;

            case '/' :
                if (charAt(currentPosition + 1) == '/') {
                    int pos = sqlString.indexOf('\r', currentPosition + 2);

                    if (pos == -1) {
                        pos = sqlString.indexOf('\n', currentPosition + 2);
                    }

                    if (pos == -1) {
                        pos = limit;
                    }

                    token.tokenString = sqlString.substring(
                        currentPosition + 2,
                        pos);
                    token.tokenType   = Tokens.X_REMARK;
                    token.isDelimiter = true;

                    return;
                } else if (charAt(currentPosition + 1) == '*') {
                    scanBracketedComment();

                    return;
                }

                token.tokenString = Tokens.T_DIVIDE_OP;
                token.tokenType   = Tokens.DIVIDE_OP;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case '-' :
                if (charAt(currentPosition + 1) == '-') {
                    int pos = sqlString.indexOf('\r', currentPosition + 2);

                    if (pos == -1) {
                        pos = sqlString.indexOf('\n', currentPosition + 2);
                    }

                    if (pos == -1) {
                        pos = limit;
                    }

                    token.tokenString = sqlString.substring(
                        currentPosition + 2,
                        pos);
                    token.tokenType   = Tokens.X_REMARK;
                    token.isDelimiter = true;

                    return;
                }

                token.tokenString = Tokens.T_MINUS_OP;
                token.tokenType   = Tokens.MINUS_OP;

                currentPosition++;

                token.isDelimiter = true;

                return;

            case '\"' :
                token.tokenType = Tokens.X_DELIMITED_IDENTIFIER;
                break;

            case '`' :
                if (backtickQuoting) {
                    token.tokenType = Tokens.X_DELIMITED_IDENTIFIER;
                }

                break;

            case '\'' :
                scanCharacterString();

                if (token.isMalformed) {
                    return;
                }

                typeCode          = charLiteral
                                    ? Types.SQL_CHAR
                                    : Types.SQL_VARCHAR;
                token.dataType = CharacterType.getCharacterType(
                    typeCode,
                    token.tokenString.length());
                token.tokenType   = Tokens.X_VALUE;
                token.isDelimiter = true;

                return;

            case 'x' :
            case 'X' :
                if (charAt(currentPosition + 1) == '\'') {
                    currentPosition++;

                    scanBinaryString();

                    if (token.isMalformed) {
                        return;
                    }

                    token.dataType = BinaryType.getBinaryType(
                        Types.SQL_VARBINARY,
                        ((BinaryData) token.tokenValue).length(null));
                    token.tokenType = Tokens.X_VALUE;

                    return;
                }

                break;

            case 'b' :
            case 'B' :
                if (charAt(currentPosition + 1) == '\'') {
                    currentPosition++;

                    scanBitString();

                    if (token.isMalformed) {
                        return;
                    }

                    token.dataType = BitType.getBitType(
                        Types.SQL_BIT,
                        ((BinaryData) token.tokenValue).bitLength(null));
                    token.tokenType = Tokens.X_VALUE;

                    return;
                }

                break;

            case 'n' :
            case 'N' :
                if (charAt(currentPosition + 1) == '\'') {
                    currentPosition++;

                    scanCharacterString();

                    if (token.isMalformed) {
                        return;
                    }

                    typeCode        = charLiteral
                                      ? Types.SQL_CHAR
                                      : Types.SQL_VARCHAR;
                    token.dataType = CharacterType.getCharacterType(
                        typeCode,
                        token.tokenString.length());
                    token.tokenType = Tokens.X_VALUE;

                    return;
                }

                break;

            case 'u' :
            case 'U' :
                if (charAt(currentPosition + 1) == '&') {
                    if (charAt(currentPosition + 2) == '\'') {
                        currentPosition += 2;
                        token.dataType  = Type.SQL_CHAR;
                        token.tokenType = Tokens.X_VALUE;

                        scanUnicodeString();

                        if (token.isMalformed) {
                            return;
                        }

                        typeCode = charLiteral
                                   ? Types.SQL_CHAR
                                   : Types.SQL_VARCHAR;
                        token.dataType = CharacterType.getCharacterType(
                            typeCode,
                            ((String) token.tokenValue).length());

                        return;
                    }
                }

                break;

            case '_' :

                /*
                 * @todo 1.9.0 - review following
                 * identifier chain must not have catalog identifier
                 * character set specification to be included in the token.dataType
                 */
                int startPosition = currentPosition;

                currentPosition++;

                scanIdentifierChain();

                if (token.isMalformed) {
                    position(startPosition);
                    resetState();
                    break;
                }

                if (token.tokenType != Tokens.X_IDENTIFIER) {

                    /* @todo 1.9.0 - review message malformed character set identifier */
                    token.tokenType   = Tokens.X_MALFORMED_STRING;
                    token.isMalformed = true;

                    return;
                }

                scanSeparator();

                if (charAt(currentPosition) == '\'') {
                    if (token.namePrePrefix != null) {

                        /* @todo 1.9.0 - review message malformed character set identifier */
                        token.tokenType   = Tokens.X_MALFORMED_STRING;
                        token.isMalformed = true;

                        return;
                    }

                    token.charsetSchema = token.namePrefix;
                    token.charsetName   = token.tokenString;

                    scanCharacterString();

                    token.tokenType   = Tokens.X_VALUE;
                    token.dataType = CharacterType.getCharacterType(
                        Types.SQL_CHAR,
                        token.tokenString.length());
                    token.isDelimiter = true;

                    return;
                } else {
                    position(startPosition);
                    resetState();
                    break;
                }
            case '.' :
            case '0' :
            case '1' :
            case '2' :
            case '3' :
            case '4' :
            case '5' :
            case '6' :
            case '7' :
            case '8' :
            case '9' :
                token.tokenType = Tokens.X_VALUE;

                scanNumber();

                return;
        }

        scanIdentifierChain();
        setIdentifierProperties();
    }

    private boolean skipBracketedComment() {

        int pos = sqlString.indexOf("*/", currentPosition + 2);

        if (pos == -1) {
            token.tokenString = sqlString.substring(
                currentPosition,
                currentPosition + 2);
            token.tokenType   = Tokens.X_MALFORMED_COMMENT;
            token.isMalformed = true;

            return false;
        }

        String comment = sqlString.substring(currentPosition + 2, pos);

        currentPosition = pos + 2;

        return true;
    }

    private boolean scanBracketedComment() {

        int pos = sqlString.indexOf("*/", currentPosition + 2);

        if (pos == -1) {
            token.tokenString = sqlString.substring(
                currentPosition,
                currentPosition + 2);
            token.tokenType   = Tokens.X_UNKNOWN_TOKEN;
            token.isDelimiter = true;

            return false;
        }

        token.tokenString = sqlString.substring(currentPosition + 2, pos);
        token.tokenType   = Tokens.X_REMARK;
        token.isDelimiter = true;

        return true;
    }

    private void setIdentifierProperties() {

        if (token.tokenType == Tokens.X_IDENTIFIER) {
            token.isUndelimitedIdentifier = true;

            if (token.namePrefix == null) {
                token.tokenType = Tokens.getKeywordID(
                    token.tokenString,
                    Tokens.X_IDENTIFIER);

                if (token.tokenType == Tokens.X_IDENTIFIER) {
                    token.tokenType = Tokens.getNonKeywordID(
                        token.tokenString,
                        Tokens.X_IDENTIFIER);
                } else {
                    token.isReservedIdentifier = true;
                    token.isCoreReservedIdentifier = Tokens.isCoreKeyword(
                        token.tokenType);
                }
            }
        } else if (token.tokenType == Tokens.X_DELIMITED_IDENTIFIER) {
            token.isDelimitedIdentifier = true;
        }
    }

    public boolean scanNull() {

        scanSeparator();

        int character = charAt(currentPosition);

        if (character == 'N' || character == 'n') {
            return scanSpecialIdentifier(Tokens.T_NULL);
        }

        return false;
    }

    //
    private void scanNext(int error) {

        scanNext();

        if (token.isMalformed) {
            throw Error.error(error);
        }
    }

    /**
     * Reads the type part of the INTERVAL
     */
    IntervalType scanIntervalType() {

        int       precision = -1;
        int       scale     = -1;
        int       startToken;
        int       endToken;
        final int errorCode = ErrorCode.X_22006;

        startToken = endToken = token.tokenType;

        scanNext(errorCode);

        if (token.tokenType == Tokens.OPENBRACKET) {
            scanNext(errorCode);

            if (token.dataType == null
                    || token.dataType.typeCode != Types.SQL_INTEGER) {
                throw Error.error(errorCode);
            }

            precision = ((Number) this.token.tokenValue).intValue();

            scanNext(errorCode);

            if (token.tokenType == Tokens.COMMA) {
                if (startToken != Tokens.SECOND) {
                    throw Error.error(errorCode);
                }

                scanNext(errorCode);

                if (token.dataType == null
                        || token.dataType.typeCode != Types.SQL_INTEGER) {
                    throw Error.error(errorCode);
                }

                scale = ((Number) token.tokenValue).intValue();

                scanNext(errorCode);
            }

            if (token.tokenType != Tokens.CLOSEBRACKET) {
                throw Error.error(errorCode);
            }

            scanNext(errorCode);
        }

        if (token.tokenType == Tokens.TO) {
            scanNext(errorCode);

            endToken = token.tokenType;

            scanNext(errorCode);
        }

        if (token.tokenType == Tokens.OPENBRACKET) {
            if (endToken != Tokens.SECOND || endToken == startToken) {
                throw Error.error(errorCode);
            }

            scanNext(errorCode);

            if (token.dataType == null
                    || token.dataType.typeCode != Types.SQL_INTEGER) {
                throw Error.error(errorCode);
            }

            scale = ((Number) token.tokenValue).intValue();

            scanNext(errorCode);

            if (token.tokenType != Tokens.CLOSEBRACKET) {
                throw Error.error(errorCode);
            }

            scanNext(errorCode);
        }

        int startIndex = ArrayUtil.find(
            Tokens.SQL_INTERVAL_FIELD_CODES,
            startToken);
        int endIndex = ArrayUtil.find(
            Tokens.SQL_INTERVAL_FIELD_CODES,
            endToken);

        return IntervalType.getIntervalType(
            startIndex,
            endIndex,
            precision,
            scale);
    }

    private String intervalString;
    private int    intervalPosition;
    private int    intervalPrecision;
    private int    fractionPrecision;
    Type           dateTimeType;

    public TimestampData newDate(String s) {

        intervalPosition  = 0;
        fractionPrecision = 0;
        dateTimeType      = null;
        intervalString    = s;

        scanDateParts(2);

        if (intervalPosition != s.length()) {
            throw Error.error(ErrorCode.X_22007);
        }

        long seconds = HsqlDateTime.getDateSeconds(s);

        return new TimestampData(seconds);
    }

    /*
     * @todo 1.9.0 - review the following
     *      - misses nano fractions
     *      - misses displacement
     *      - doesn't allow single digit components
     */
    public TimestampData newTimestamp(String s) {

        long    zoneSeconds = 0;
        long    seconds;
        int     fraction = 0;
        int     endIndex = s.length();
        boolean negate;
        boolean hasZone = false;

        intervalPosition  = 0;
        fractionPrecision = 0;
        dateTimeType      = null;
        intervalString    = s;

        scanDateParts(5);

        if (intervalPosition == 10) {
            seconds = HsqlDateTime.getDateSeconds(
                s.substring(0, intervalPosition));
            dateTimeType = Type.SQL_TIMESTAMP_NO_FRACTION;

            return new TimestampData(seconds, fraction, (int) zoneSeconds);
        } else {
            seconds = HsqlDateTime.getTimestampSeconds(
                s.substring(0, intervalPosition));
        }

        int position;

        fraction = scanIntervalFraction(DTIType.maxFractionPrecision);
        position = intervalPosition;
        negate   = scanIntervalSign();

        if (negate || position != intervalPosition) {
            zoneSeconds = scanIntervalValue(Type.SQL_INTERVAL_HOUR_TO_MINUTE);
            hasZone     = true;

            if (negate) {
                zoneSeconds = -zoneSeconds;
            }
        }

        if (zoneSeconds >= DTIType.yearToSecondFactors[2]
                || zoneSeconds > DTIType.timezoneSecondsLimit
                || -zoneSeconds > DTIType.timezoneSecondsLimit) {
            throw Error.error(ErrorCode.X_22009);
        }

        if (intervalPosition != endIndex) {
            throw Error.error(ErrorCode.X_22007);
        }

        int type = hasZone
                   ? Types.SQL_TIMESTAMP_WITH_TIME_ZONE
                   : Types.SQL_TIMESTAMP;

        dateTimeType = DateTimeType.getDateTimeType(type, fractionPrecision);

        if (hasZone) {
            seconds -= zoneSeconds;
        }

        if (seconds > DateTimeType.epochLimitSeconds) {
            throw Error.error(ErrorCode.X_22008);
        }

        return new TimestampData(seconds, fraction, (int) zoneSeconds);
    }

    void scanDateParts(int lastPart) {

        byte[]    separators    = DTIType.yearToSecondSeparators;
        int       i             = intervalPosition;
        final int firstPart     = 0;
        int       currentPart   = firstPart;
        int       currentDigits = 0;

        while (currentPart <= lastPart) {
            boolean endOfPart = false;

            if (i == intervalString.length()) {
                if (currentPart == lastPart) {
                    endOfPart = true;
                } else if (currentPart < 2) {

                    // parts missing
                    throw Error.error(ErrorCode.X_22007);
                } else {
                    endOfPart = true;
                }
            } else {
                int character = intervalString.charAt(i);

                if (character >= '0' && character <= '9') {
                    currentDigits++;
                    i++;
                } else if (character == separators[currentPart]) {
                    endOfPart = true;

                    if (currentPart != lastPart) {
                        i++;
                    }
                } else if (currentPart == lastPart) {
                    endOfPart = true;
                } else {
                    throw Error.error(ErrorCode.X_22007);
                }
            }

            if (endOfPart) {
                if (currentPart == firstPart) {
                    if (currentDigits < 4) {
                        throw Error.error(ErrorCode.X_22007);
                    }
                } else {
                    if (currentDigits == 0 || currentDigits > 2) {
                        throw Error.error(ErrorCode.X_22007);
                    }
                }

                currentPart++;

                currentDigits = 0;

                if (i == intervalString.length()) {
                    break;
                }
            }
        }

        intervalPosition = i;
    }

    public TimeData newTime(String s) {

        intervalPosition  = 0;
        fractionPrecision = 0;
        dateTimeType      = null;
        intervalString    = s;

        long    seconds = scanIntervalValue(Type.SQL_INTERVAL_HOUR_TO_SECOND);
        int     fraction    = scanIntervalFraction(
            DTIType.maxFractionPrecision);
        long    zoneSeconds = 0;
        int     position    = intervalPosition;
        boolean hasZone     = false;
        boolean negate      = scanIntervalSign();

        if (position != intervalPosition) {
            zoneSeconds = scanIntervalValue(Type.SQL_INTERVAL_HOUR_TO_MINUTE);
            hasZone     = true;
        }

        if (intervalPosition != s.length()) {
            throw Error.error(ErrorCode.X_22009);
        }

        if (seconds >= DTIType.yearToSecondFactors[2]) {
            throw Error.error(ErrorCode.X_22008);
        }

        if (zoneSeconds > DTIType.timezoneSecondsLimit) {
            throw Error.error(ErrorCode.X_22009);
        }

        if (negate) {
            zoneSeconds = -zoneSeconds;
        }

        int type = hasZone
                   ? Types.SQL_TIME_WITH_TIME_ZONE
                   : Types.SQL_TIME;

        dateTimeType = DateTimeType.getDateTimeType(type, fractionPrecision);

        if (hasZone) {
            seconds -= zoneSeconds;
        }

        return new TimeData((int) seconds, fraction, (int) zoneSeconds);
    }

    public Object newInterval(String s, IntervalType type) {

        intervalPosition  = 0;
        fractionPrecision = 0;
        intervalString    = s;

        boolean negate   = scanIntervalSign();
        long    units    = scanIntervalValue(type);
        int     fraction = 0;

        if (type.endIntervalType == Types.SQL_INTERVAL_SECOND) {
            fraction = scanIntervalFraction(type.scale);
        }

        if (intervalPosition != s.length()) {
            throw Error.error(ErrorCode.X_22006);
        }

        if (negate) {
            units    = -units;
            fraction = -fraction;
        }

        dateTimeType = type;

        if (type.defaultPrecision) {
            dateTimeType = IntervalType.getIntervalType(
                type.typeCode,
                type.startIntervalType,
                type.endIntervalType,
                intervalPrecision,
                fractionPrecision,
                false);
        }

        if (type.endPartIndex <= DTIType.INTERVAL_MONTH_INDEX) {
            return new IntervalMonthData(units);
        } else {
            return new IntervalSecondData(units, fraction);
        }
    }

    public long scanIntervalValue(IntervalType type) {

        byte[] separators    = DTIType.yearToSecondSeparators;
        int[]  factors       = DTIType.yearToSecondFactors;
        int[]  limits        = DTIType.yearToSecondLimits;
        int    firstPart     = type.startPartIndex;
        int    lastPart      = type.endPartIndex;
        long   totalValue    = 0;
        long   currentValue  = 0;
        int    i             = intervalPosition;
        int    currentPart   = firstPart;
        int    currentDigits = 0;

        while (currentPart <= lastPart) {
            boolean endOfPart = false;

            if (i == intervalString.length()) {
                if (currentPart == lastPart) {
                    endOfPart = true;
                } else {
                    throw Error.error(ErrorCode.X_22006);
                }
            } else {
                int character = intervalString.charAt(i);

                if (character >= '0' && character <= '9') {
                    int digit = character - '0';

                    currentValue *= 10;
                    currentValue += digit;

                    currentDigits++;
                    i++;
                } else if (character == separators[currentPart]) {
                    endOfPart = true;

                    if (currentPart != lastPart) {
                        i++;
                    }
                } else if (currentPart == lastPart) {
                    endOfPart = true;
                } else {
                    throw Error.error(ErrorCode.X_22006);
                }
            }

            if (endOfPart) {
                if (currentPart == firstPart) {
                    if (!type.defaultPrecision
                            && currentDigits > type.precision) {
                        throw Error.error(ErrorCode.X_22015);
                    }

                    if (currentDigits == 0) {
                        throw Error.error(ErrorCode.X_22006);
                    }

                    int factor = factors[currentPart];

                    totalValue        += currentValue * factor;
                    intervalPrecision = currentDigits;
                } else {
                    if (currentValue >= limits[currentPart]) {
                        throw Error.error(ErrorCode.X_22015);
                    }

                    if (currentDigits == 0 || currentDigits > 2) {
                        throw Error.error(ErrorCode.X_22006);
                    }

                    totalValue += currentValue * factors[currentPart];
                }

                currentPart++;

                currentValue  = 0;
                currentDigits = 0;

                if (i == intervalString.length()) {
                    break;
                }
            }
        }

        intervalPosition = i;

        return totalValue;
    }

    boolean scanIntervalSign() {

        boolean negate = false;

        if (intervalPosition == intervalString.length()) {
            return false;
        }

        if (intervalString.charAt(intervalPosition) == '-') {
            negate = true;

            intervalPosition++;
        } else if (intervalString.charAt(intervalPosition) == '+') {
            intervalPosition++;
        }

        return negate;
    }

    int scanIntervalFraction(int decimalPrecision) {

        if (intervalPosition == intervalString.length()) {
            return 0;
        }

        if (intervalString.charAt(intervalPosition) != '.') {
            return 0;
        }

        intervalPosition++;

        int currentValue  = 0;
        int currentDigits = 0;

        while (intervalPosition < intervalString.length()) {
            int character = intervalString.charAt(intervalPosition);

            if (character >= '0' && character <= '9') {
                int digit = character - '0';

                currentValue *= 10;
                currentValue += digit;

                intervalPosition++;
                currentDigits++;

                if (currentDigits == DTIType.maxFractionPrecision) {
                    break;
                }
            } else {
                break;
            }
        }

        fractionPrecision = currentDigits;
        currentValue = DTIType.normaliseFraction(
            currentValue,
            currentDigits,
            decimalPrecision);

        return currentValue;
    }

    void scanIntervalSpaces() {

        for (; intervalPosition < intervalString.length(); intervalPosition++) {
            if (intervalString.charAt(intervalPosition) != ' ') {
                break;
            }
        }
    }

    /*
     * synchronized methods for use with shared Scanner objects used for type
     *  conversion
     */
    public synchronized Number convertToNumber(
            String s,
            NumberType numberType) {

        Number  number;
        boolean minus = false;
        Type    type;

        reset(s);
        resetState();
        scanWhitespace();
        scanToken();
        scanWhitespace();

        switch (token.tokenType) {

            case Tokens.MINUS_OP :
                minus = true;

                scanToken();
                scanWhitespace();

                if (token.tokenType == Tokens.INFINITY) {
                    minus            = false;
                    token.tokenType  = Tokens.X_VALUE;
                    token.dataType   = Type.SQL_DOUBLE;
                    token.tokenValue = Double.valueOf(Double.NEGATIVE_INFINITY);
                }

                break;

            case Tokens.PLUS_OP :
                scanToken();
                scanWhitespace();
                break;

            case Tokens.NAN :
                token.tokenType  = Tokens.X_VALUE;
                token.dataType   = Type.SQL_DOUBLE;
                token.tokenValue = Double.valueOf(Double.NaN);
                break;

            case Tokens.INFINITY :
                token.tokenType  = Tokens.X_VALUE;
                token.dataType   = Type.SQL_DOUBLE;
                token.tokenValue = Double.valueOf(Double.POSITIVE_INFINITY);
                break;
        }

        if (!hasNonSpaceSeparator
                && token.tokenType == Tokens.X_VALUE
                && token.tokenValue instanceof Number) {
            number = (Number) token.tokenValue;
            type   = token.dataType;

            if (minus) {
                number = (Number) token.dataType.negate(number);
            }

            scanEnd();

            if (token.tokenType == Tokens.X_ENDPARSE) {
                number = (Number) numberType.convertToType(null, number, type);

                return number;
            }
        }

        throw Error.error(ErrorCode.X_22018);
    }

    public synchronized BinaryData convertToBinary(String s, boolean uuid) {

        boolean hi = true;
        byte    b  = 0;

        reset(s);
        resetState();
        byteOutputStream.reset(byteBuffer);

        for (; currentPosition < limit; currentPosition++, hi = !hi) {
            int ch = sqlString.charAt(currentPosition);
            int c  = getHexValue(ch);

            if (c == -1) {
                if (uuid && ch == '-' && hi) {
                    hi = false;
                    continue;
                }

                // bad character
                token.tokenType   = Tokens.X_MALFORMED_BINARY_STRING;
                token.isMalformed = true;
                break;
            }

            if (hi) {
                b = (byte) (c << 4);
            } else {
                b += (byte) c;

                byteOutputStream.writeByte(b);
            }
        }

        if (!hi) {

            // odd nibbles
            token.tokenType   = Tokens.X_MALFORMED_BINARY_STRING;
            token.isMalformed = true;
        }

        if (uuid && byteOutputStream.size() != 16) {
            token.tokenType   = Tokens.X_MALFORMED_BINARY_STRING;
            token.isMalformed = true;
        }

        if (token.isMalformed) {
            throw Error.error(ErrorCode.X_22018);
        }

        BinaryData data = new BinaryData(byteOutputStream.toByteArray(), false);

        byteOutputStream.reset(byteBuffer);

        return data;
    }

    public synchronized BinaryData convertToBit(String s) {

        BitMap map      = new BitMap(0, true);
        int    bitIndex = 0;

        reset(s);
        resetState();
        byteOutputStream.reset(byteBuffer);

        for (; currentPosition < limit; currentPosition++) {
            int c = sqlString.charAt(currentPosition);

            if (c == '0') {
                map.unset(bitIndex);

                bitIndex++;
            } else if (c == '1') {
                map.set(bitIndex);

                bitIndex++;
            } else {
                token.tokenType   = Tokens.X_MALFORMED_BIT_STRING;
                token.isMalformed = true;

                throw Error.error(ErrorCode.X_22018);
            }
        }

        map.setSize(bitIndex);

        return BinaryData.getBitData(map.getBytes(), map.size());
    }

    // should perform range checks etc.
    public synchronized Object convertToDatetimeInterval(
            SessionInterface session,
            String s,
            DTIType type) {

        Object       value;
        IntervalType intervalType  = null;
        int          dateTimeToken = -1;
        int          errorCode     = type.isDateTimeType()
                                     ? ErrorCode.X_22007
                                     : ErrorCode.X_22006;

        reset(s);
        resetState();
        scanToken();
        scanWhitespace();

        switch (token.tokenType) {

            case Tokens.INTERVAL :
            case Tokens.DATE :
            case Tokens.TIME :
            case Tokens.TIMESTAMP :
                dateTimeToken = token.tokenType;

                scanToken();

                if (token.tokenType != Tokens.X_VALUE
                        || !token.dataType.isCharacterType()) {

                    // error datetime bad literal
                    throw Error.error(errorCode);
                }

                s = token.tokenString;

                scanNext(ErrorCode.X_22007);

                if (type.isIntervalType()) {
                    intervalType = scanIntervalType();
                }

                if (token.tokenType != Tokens.X_ENDPARSE) {
                    throw Error.error(errorCode);
                }

                break;

            default :
        }

        switch (type.typeCode) {

            case Types.SQL_DATE : {
                if (dateTimeToken != -1 && dateTimeToken != Tokens.DATE) {
                    throw Error.error(errorCode);
                }

                value = newDate(s);

                return type.convertToType(session, value, Type.SQL_DATE);
            }

            case Types.SQL_TIME :
            case Types.SQL_TIME_WITH_TIME_ZONE : {
                if (dateTimeToken != -1 && dateTimeToken != Tokens.TIME) {
                    throw Error.error(errorCode);
                }

                Object o = newTime(s);

                return type.convertToType(session, o, dateTimeType);
            }

            case Types.SQL_TIMESTAMP :
            case Types.SQL_TIMESTAMP_WITH_TIME_ZONE : {
                if (dateTimeToken != -1 && dateTimeToken != Tokens.TIMESTAMP) {
                    throw Error.error(errorCode);
                }

                value = newTimestamp(s);

                return type.convertToType(session, value, dateTimeType);
            }

            default :
                if (dateTimeToken != -1 && dateTimeToken != Tokens.INTERVAL) {
                    throw Error.error(errorCode);
                }

                if (type.isIntervalType()) {
                    value = newInterval(s, (IntervalType) type);

                    if (intervalType != null) {
                        if (intervalType.startIntervalType
                                != type.startIntervalType
                                || intervalType.endIntervalType
                                   != type.endIntervalType) {
                            throw Error.error(errorCode);
                        }
                    }

                    return type.convertToType(session, value, dateTimeType);
                }

                throw Error.runtimeError(ErrorCode.U_S0500, "Scanner");
        }
    }
}
