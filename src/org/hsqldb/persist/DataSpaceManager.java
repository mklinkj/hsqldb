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


package org.hsqldb.persist;

import org.hsqldb.lib.LongLookup;

/**
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.5.1
 * @since 2.3.0
 */
public interface DataSpaceManager {

    int tableIdEmpty     = 0;
    int tableIdDirectory = 1;
    int tableIdLookup    = 5;
    int tableIdSetAside  = 6;
    int tableIdDefault   = 7;
    int tableIdFirst     = 8;

    //
    int fixedDiskBlockSize = 4096;

    TableSpaceManager getDefaultTableSpace();

    TableSpaceManager getTableSpace(int spaceId);

    int getNewTableSpaceID();

    long getFileBlocks(int spaceId, int blockCount);

    void initialiseTableSpace(TableSpaceManagerBlocks tableSpace);

    void freeTableSpace(int spaceId);

    void freeTableSpace(
            int spaceId,
            LongLookup spaceList,
            long offset,
            long limit);

    long getLostBlocksSize();

    boolean isModified();

    int getFileBlockSize();

    void initialiseSpaces();

    void reset();

    boolean isMultiSpace();

    int getFileBlockItemCount();

    DirectoryBlockCachedObject[] getDirectoryList();
}
