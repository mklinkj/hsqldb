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

import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;

/*
 * Enumerates supported database types.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.5.1
 * @since 2.3.4
 */
public enum DatabaseType {
    DB_MEM("mem:"), DB_FILE("file:"), DB_RES("res:");

    private final String value;

    DatabaseType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public boolean isFileBased() {

        switch (this) {

            case DB_FILE :
            case DB_RES :
                return true;

            default :
                return false;
        }
    }

    public static boolean isInProcessDatabaseType(String type) {
        return DB_FILE.value.equals(type)
               || DB_RES.value.equals(type)
               || DB_MEM.value.equals(type);
    }

    public static DatabaseType get(String value) {

        if (DB_MEM.value.equals(value)) {
            return DB_MEM;
        }

        if (DB_FILE.value.equals(value)) {
            return DB_FILE;
        }

        if (DB_RES.value.equals(value)) {
            return DB_RES;
        }

        throw Error.runtimeError(ErrorCode.U_S0500, "DatabaseType");
    }
}
