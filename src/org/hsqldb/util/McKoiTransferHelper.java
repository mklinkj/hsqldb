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


package org.hsqldb.util;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

/**
 * Helper class for conversion from a different databases
 *
 * @author Bob Preston (sqlbob@users dot sourceforge.net)
 * @version 1.7.0
 */
class McKoiTransferHelper extends TransferHelper {

    McKoiTransferHelper() {
        super();
    }

    String fixupColumnDefRead(
            TransferTable t,
            ResultSetMetaData meta,
            String columnType,
            ResultSet columnDesc,
            int columnIndex) {

        String CompareString = "UNIQUEKEY('" + t.Stmts.sDestTable + "'";

        if (columnType.indexOf(CompareString) > 0) {

            // We just found a increment
            columnType = "SERIAL";
        }

        return (columnType);
    }

    public McKoiTransferHelper(TransferDb database, Traceable t, String q) {
        super(database, t, q);
    }

    String fixupColumnDefWrite(
            TransferTable t,
            ResultSetMetaData meta,
            String columnType,
            ResultSet columnDesc,
            int columnIndex) {

        if (columnType.equals("SERIAL")) {
            columnType = "INTEGER DEFAULT UNIQUEKEY ('" + t.Stmts.sSourceTable
                         + "')";
        }

        return (columnType);
    }

    boolean needTransferTransaction() {
        return (true);
    }
}
