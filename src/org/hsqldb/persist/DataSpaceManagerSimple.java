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
public class DataSpaceManagerSimple implements DataSpaceManager {

    final DataFileCache     cache;
    final TableSpaceManager defaultSpaceManager;
    final int               fileBlockSize = fixedDiskBlockSize;
    long                    totalFragmentSize;
    int                     spaceIdSequence = tableIdFirst;
    LongLookup              lookup;

    /**
     * Used for non-space default, readonly, Text and Session data files
     */
    DataSpaceManagerSimple(DataFileCache cache, boolean isReadOnly) {

        this.cache = cache;

        if (cache instanceof DataFileCacheSession) {
            defaultSpaceManager = new TableSpaceManagerSimple(cache);
        } else if (cache instanceof TextCache) {
            defaultSpaceManager = new TableSpaceManagerSimple(cache);
        } else {
            int capacity = cache.database.logger.propMaxFreeBlocks;

            defaultSpaceManager = new TableSpaceManagerBlocks(
                this,
                tableIdDefault,
                fileBlockSize,
                capacity,
                cache.getDataFileScale());

            if (!isReadOnly) {
                initialiseSpaces();

                cache.spaceManagerPosition = 0;
            }
        }

        totalFragmentSize = cache.lostSpaceSize;
    }

    public TableSpaceManager getDefaultTableSpace() {
        return defaultSpaceManager;
    }

    public TableSpaceManager getTableSpace(int spaceId) {

        if (spaceId >= spaceIdSequence) {
            spaceIdSequence = spaceId + 2;
        }

        return defaultSpaceManager;
    }

    public int getNewTableSpaceID() {

        int id = spaceIdSequence;

        spaceIdSequence += 2;

        return id;
    }

    public long getFileBlocks(int spaceId, int blockCount) {

        long filePosition = cache.getFileFreePos();

        cache.enlargeFileSpace(
            filePosition + (long) blockCount * fileBlockSize);

        return filePosition;
    }

    public void initialiseTableSpace(TableSpaceManagerBlocks tableSpace) {}

    public void freeTableSpace(int spaceId) {}

    public void freeTableSpace(
            int spaceId,
            LongLookup spaceList,
            long offset,
            long limit) {

        totalFragmentSize += spaceList.getTotalValues()
                             * cache.getDataFileScale();

        if (cache.fileFreePosition == limit) {
            cache.writeLock.lock();

            try {
                cache.fileFreePosition = offset;
            } finally {
                cache.writeLock.unlock();
            }
        } else {
            totalFragmentSize += limit - offset;
        }

        if (spaceList.size() != 0) {
            lookup = spaceList.duplicate();
        }
    }

    public long getLostBlocksSize() {
        return totalFragmentSize + defaultSpaceManager.getLostBlocksSize();
    }

    public int getFileBlockSize() {
        return 1024 * 1024 * cache.getDataFileScale() / 16;
    }

    public boolean isModified() {
        return true;
    }

    public void initialiseSpaces() {

        long filePosition = cache.getFileFreePos();
        long totalBlocks  = (filePosition + fileBlockSize) / fileBlockSize;
        long lastFreePosition = cache.enlargeFileSpace(
            totalBlocks * fileBlockSize);

        defaultSpaceManager.initialiseFileBlock(
            lookup,
            lastFreePosition,
            cache.getFileFreePos());

        if (lookup != null) {
            totalFragmentSize -= lookup.getTotalValues()
                                 * cache.getDataFileScale();
            lookup = null;
        }
    }

    public void reset() {
        defaultSpaceManager.reset();
    }

    public boolean isMultiSpace() {
        return false;
    }

    public int getFileBlockItemCount() {
        return 1024 * 64;
    }

    public DirectoryBlockCachedObject[] getDirectoryList() {
        return new DirectoryBlockCachedObject[0];
    }
}
