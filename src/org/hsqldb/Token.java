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

import org.hsqldb.types.Type;

/**
 * Token created by Scanner.<p>
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 1.9.0
 */
public class Token {

    String  tokenString = "";
    int     tokenType   = Tokens.X_UNKNOWN_TOKEN;
    Type    dataType;
    Object  tokenValue;
    String  namePrefix;
    String  namePrePrefix;
    String  namePrePrePrefix;
    String  charsetSchema;
    String  charsetName;
    String  fullString;
    int     lobMultiplierType = Tokens.X_UNKNOWN_TOKEN;
    boolean isDelimiter;
    boolean isDelimitedIdentifier;
    boolean isDelimitedPrefix;
    boolean isDelimitedPrePrefix;
    boolean isDelimitedPrePrePrefix;
    boolean isUndelimitedIdentifier;
    boolean hasIrregularChar;
    boolean isReservedIdentifier;
    boolean isCoreReservedIdentifier;
    boolean isHostParameter;
    boolean isMalformed;

    //
    int     position;
    Object  expression;
    boolean hasColumnList;

    void reset() {

        tokenString              = "";
        tokenType                = Tokens.X_UNKNOWN_TOKEN;
        dataType                 = null;
        tokenValue               = null;
        namePrefix               = null;
        namePrePrefix            = null;
        namePrePrePrefix         = null;
        charsetSchema            = null;
        charsetName              = null;
        fullString               = null;
        lobMultiplierType        = Tokens.X_UNKNOWN_TOKEN;
        isDelimiter              = false;
        isDelimitedIdentifier    = false;
        isDelimitedPrefix        = false;
        isDelimitedPrePrefix     = false;
        isDelimitedPrePrePrefix  = false;
        isUndelimitedIdentifier  = false;
        hasIrregularChar         = false;
        isReservedIdentifier     = false;
        isCoreReservedIdentifier = false;
        isHostParameter          = false;
        isMalformed              = false;

        //
        expression    = null;
        hasColumnList = false;
    }

    Token duplicate() {

        Token token = new Token();

        copyTo(token);

        return token;
    }

    private void copyTo(Token token) {

        token.tokenString              = tokenString;
        token.tokenType                = tokenType;
        token.dataType                 = dataType;
        token.tokenValue               = tokenValue;
        token.namePrefix               = namePrefix;
        token.namePrePrefix            = namePrePrefix;
        token.namePrePrePrefix         = namePrePrePrefix;
        token.charsetSchema            = charsetSchema;
        token.charsetName              = charsetName;
        token.fullString               = fullString;
        token.lobMultiplierType        = lobMultiplierType;
        token.isDelimiter              = isDelimiter;
        token.isDelimitedIdentifier    = isDelimitedIdentifier;
        token.isDelimitedPrefix        = isDelimitedPrefix;
        token.isDelimitedPrePrefix     = isDelimitedPrePrefix;
        token.isDelimitedPrePrePrefix  = isDelimitedPrePrePrefix;
        token.isUndelimitedIdentifier  = isUndelimitedIdentifier;
        token.hasIrregularChar         = hasIrregularChar;
        token.isReservedIdentifier     = isReservedIdentifier;
        token.isCoreReservedIdentifier = isCoreReservedIdentifier;
        token.isHostParameter          = isHostParameter;
        token.isMalformed              = isMalformed;
    }

    public String getFullString() {
        return fullString;
    }

    public void setExpression(SchemaObject expression) {
        this.expression = expression;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    public void setWithColumnList() {
        hasColumnList = true;
    }

    String getSQL() {

        if (expression instanceof ExpressionColumn) {
            if (tokenType == Tokens.ASTERISK) {
                StringBuilder sb         = new StringBuilder();
                Expression    expression = (Expression) this.expression;

                if (expression.opType == OpTypes.MULTICOLUMN
                        && expression.nodes.length > 0) {
                    sb.append(' ');

                    for (int i = 0; i < expression.nodes.length; i++) {
                        Expression   e = expression.nodes[i];
                        ColumnSchema c = e.getColumn();
                        String       name;

                        if (e.opType == OpTypes.COALESCE) {
                            if (i > 0) {
                                sb.append(',');
                            }

                            sb.append(e.getColumnName());
                            continue;
                        }

                        if (e.getRangeVariable().tableAlias == null) {
                            name = c.getName()
                                    .getSchemaQualifiedStatementName();
                        } else {
                            RangeVariable range = e.getRangeVariable();

                            name = range.tableAlias.getStatementName() + '.'
                                   + c.getName().statementName;
                        }

                        if (i > 0) {
                            sb.append(',');
                        }

                        sb.append(name);
                    }

                    sb.append(' ');
                } else {
                    return tokenString;
                }

                return sb.toString();
            }
        } else if (expression instanceof Type) {
            isDelimiter = false;

            Type type = (Type) expression;

            if (type.isDistinctType() || type.isDomainType()) {
                return type.getName().getSchemaQualifiedStatementName();
            }

            return type.getNameString();
        } else if (expression instanceof ColumnSchema) {
            isDelimiter = false;

            ColumnSchema column = (ColumnSchema) expression;

            return column.getName().getSchemaQualifiedStatementName();
        } else if (expression instanceof SchemaObject) {
            isDelimiter = false;

            String nameString = ((SchemaObject) expression).getName()
                    .getSchemaQualifiedStatementName();

            if (hasColumnList) {
                Table table = ((Table) expression);

                nameString += table.getColumnListSQL(
                    table.defaultColumnMap,
                    table.defaultColumnMap.length);
            }

            return nameString;
        }

        if (namePrefix == null && isUndelimitedIdentifier) {
            return tokenString;
        }

        if (tokenType == Tokens.X_VALUE) {
            return dataType.convertToSQLString(tokenValue);
        }

        StringBuilder sb = new StringBuilder();

        if (namePrePrefix != null) {
            if (isDelimitedPrePrefix) {
                sb.append('"');
                sb.append(namePrePrefix);
                sb.append('"');
            } else {
                sb.append(namePrePrefix);
            }

            sb.append('.');
        }

        if (namePrefix != null) {
            if (isDelimitedPrefix) {
                sb.append('"');
                sb.append(namePrefix);
                sb.append('"');
            } else {
                sb.append(namePrefix);
            }

            sb.append('.');
        }

        if (isDelimitedIdentifier) {
            sb.append('"');
            sb.append(tokenString);
            sb.append('"');

            isDelimiter = false;
        } else {
            sb.append(tokenString);
        }

        return sb.toString();
    }

    static String getSQL(Token[] tokens) {

        boolean       wasDelimiter = true;
        StringBuilder sb           = new StringBuilder();

        for (int i = 0; i < tokens.length; i++) {
            String sql = tokens[i].getSQL();

            if (!tokens[i].isDelimiter && !wasDelimiter) {
                sb.append(' ');
            }

            sb.append(sql);

            wasDelimiter = tokens[i].isDelimiter;
        }

        return sb.toString();
    }

    static Object[] getSimplifiedTokens(Token[] tokens) {

        Object[] array = new Object[tokens.length];

        for (int i = 0; i < tokens.length; i++) {
            if (tokens[i].expression == null) {
                array[i] = tokens[i].getSQL();
            } else {
                array[i] = tokens[i].expression;
            }
        }

        return array;
    }
}
