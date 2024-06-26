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


package org.hsqldb.scriptio;

import org.hsqldb.Database;
import org.hsqldb.Row;
import org.hsqldb.Table;
import org.hsqldb.rowio.RowOutputTextLog;
import org.hsqldb.Session;

/*
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.5.1
 * @since 2.5.1
 */
public class ScriptWriterTextColumnNames extends ScriptWriterText {

    public ScriptWriterTextColumnNames(Database db, String file) {
        super(db, file, true, true, true);
    }

    public void writeRow(Session session, Row row, Table table) {

        schemaToLog = table.getName().schema;

        writeSessionIdAndSchema(session);
        rowOut.reset();
        rowOut.setMode(RowOutputTextLog.MODE_INSERT);
        rowOut.writeBytes(BYTES_INSERT_INTO);
        rowOut.writeString(table.getName().statementName);
        rowOut.writeString(
            table.getColumnListSQL(table.getColumnMap(),
                                   table.getColumnCount()));
        rowOut.writeBytes(BYTES_VALUES);
        rowOut.writeData(row, table.getColumnTypes());
        rowOut.writeBytes(BYTES_TERM);
        rowOut.writeBytes(BYTES_LINE_SEP);
        writeRowOutToFile();
    }
}
