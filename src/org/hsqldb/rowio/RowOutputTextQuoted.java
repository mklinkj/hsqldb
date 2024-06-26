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

import org.hsqldb.lib.StringConverter;
import org.hsqldb.persist.TextFileSettings;

/**
 * This class quotes strings only if they contain the quote character or
 * the separator for the field. The quote character is doubled.
 *
 * @author Bob Preston (sqlbob@users dot sourceforge.net)
 * @version 2.3.4
 * @since 1.7.0
 */
public class RowOutputTextQuoted extends RowOutputText {

    public RowOutputTextQuoted(TextFileSettings textFileSettings) {
        super(textFileSettings);
    }

    protected String checkConvertString(String s, String sep) {

        if (textFileSettings.isAllQuoted
                || s.isEmpty()
                || s.indexOf(textFileSettings.quoteChar) != -1
                || (sep.length() > 0 && s.contains(sep))
                || hasUnprintable(s)) {
            s = StringConverter.toQuotedString(
                s,
                textFileSettings.quoteChar,
                true);
        }

        return s;
    }

    private static boolean hasUnprintable(String s) {

        for (int i = 0, len = s.length(); i < len; i++) {
            if (Character.isISOControl(s.charAt(i))) {
                return true;
            }
        }

        return false;
    }
}
