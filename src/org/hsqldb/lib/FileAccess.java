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


package org.hsqldb.lib;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface for abstraction of file access.
 *
 * @author  Ocke Janssen oj@openoffice.org
 * @version 2.7.0
 * @since 1.8.0
 */
public interface FileAccess {

    int ELEMENT_READ         = 1;
    int ELEMENT_SEEKABLEREAD = 3;
    int ELEMENT_WRITE        = 4;
    int ELEMENT_READWRITE    = 7;
    int ELEMENT_TRUNCATE     = 8;

    InputStream openInputStreamElement(String streamName) throws IOException;

    OutputStream openOutputStreamElement(String streamName) throws IOException;

    OutputStream openOutputStreamElementAppend(
            String streamName)
            throws IOException;

    boolean isStreamElement(String elementName);

    void createParentDirs(String filename);

    boolean removeElement(String filename);

    boolean renameElement(String oldName, String newName);

    boolean renameElementOrCopy(
            String oldName,
            String newName,
            EventLogInterface logger);

    interface FileSync {
        void sync() throws IOException;
    }

    FileSync getFileSync(OutputStream os) throws IOException;
}
