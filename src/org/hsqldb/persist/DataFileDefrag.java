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

import org.hsqldb.Database;
import org.hsqldb.Session;
import org.hsqldb.Table;
import org.hsqldb.TableBase;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.DoubleIntIndex;
import org.hsqldb.lib.DoubleLongIndex;
import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.LongLookup;
import org.hsqldb.lib.StopWatch;
import org.hsqldb.lib.StringUtil;

/**
 *  Routine to defrag the *.data file.
 *
 *  This method iterates over the primary index of a table to find the
 *  disk position for each row and stores it, together with the new position
 *  in an array.
 *
 *  A second pass over the primary index writes each row to the new disk
 *  image after translating the old pointers to the new.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version    2.7.3
 * @since      1.7.2
 */
final class DataFileDefrag {

    DataFileCache dataFileOut;
    StopWatch     stopw = new StopWatch();
    String        dataFileName;
    long[][]      rootsList;
    Database      database;
    DataFileCache dataCache;
    LongLookup    pointerLookup;

    DataFileDefrag(Database db, DataFileCache cache) {
        this.database     = db;
        this.dataCache    = cache;
        this.dataFileName = cache.getFileName();
    }

    void process(Session session) {

        Throwable error = null;

        database.logger.logDetailEvent("Defrag process begins");

        HsqlArrayList<Table> allTables = database.schemaManager.getAllTables(
            true);

        rootsList = new long[allTables.size()][];

        long maxSize = 0;

        for (int i = 0, tSize = allTables.size(); i < tSize; i++) {
            Table table = allTables.get(i);

            if (table.getTableType() == TableBase.CACHED_TABLE) {
                RowStoreAVLDisk store =
                    (RowStoreAVLDisk) database.persistentStoreCollection.getStore(
                        table);
                long size = store.elementCount();

                if (size > maxSize) {
                    maxSize = size;
                }

                if (dataCache.spaceManager.isMultiSpace()
                        && store.getSpaceManager().isDefaultSpace()) {
                    if (store.getStorageSizeEstimate()
                            > dataCache.spaceManager.getFileBlockSize() / 2) {
                        int spaceId =
                            dataCache.spaceManager.getNewTableSpaceID();

                        table.setSpaceID(spaceId);
                    }
                }
            }
        }

        if (maxSize > Integer.MAX_VALUE) {
            throw Error.error(ErrorCode.X_2200T);
        }

        try {
            String baseFileName = database.getCanonicalPath();

            dataFileOut = new DataFileCache(database, baseFileName, true);

            if (dataCache.fileFreePosition
                    < (long) Integer.MAX_VALUE * dataCache.dataFileScale) {
                pointerLookup = new DoubleIntIndex((int) maxSize);
            } else {
                pointerLookup = new DoubleLongIndex((int) maxSize);
            }

            for (int i = 0, tSize = allTables.size(); i < tSize; i++) {
                Table t = allTables.get(i);

                if (t.getTableType() == TableBase.CACHED_TABLE) {
                    long[] rootsArray = writeTableToDataFile(session, t);

                    rootsList[i] = rootsArray;
                } else {
                    rootsList[i] = null;
                }
            }

            dataFileOut.close();

            dataFileOut = null;

            for (int i = 0, size = rootsList.length; i < size; i++) {
                long[] roots = rootsList[i];

                if (roots != null) {
                    database.logger.logDetailEvent(
                        "roots: " + StringUtil.getList(roots, ",", ""));
                }
            }
        } catch (OutOfMemoryError e) {
            error = e;

            throw Error.error(ErrorCode.OUT_OF_MEMORY, e);
        } catch (Throwable t) {
            error = t;

            throw Error.error(ErrorCode.GENERAL_ERROR, t);
        } finally {
            try {
                if (dataFileOut != null) {
                    dataFileOut.release();
                }
            } catch (Throwable t) {}

            if (error instanceof OutOfMemoryError) {
                database.logger.logInfoEvent(
                    "defrag failed - out of memory - required: " + maxSize * 8);
            }

            if (error == null) {
                database.logger.logDetailEvent(
                    "Defrag transfer complete: " + stopw.elapsedTime());
            } else {
                database.logger.logSevereEvent("defrag failed ", error);

                if (dataFileOut != null) {
                    dataFileOut.deleteDataFile();
                }
            }
        }
    }

    long[] writeTableToDataFile(Session session, Table table) {

        RowStoreAVLDisk store =
            (RowStoreAVLDisk) table.database.persistentStoreCollection.getStore(
                table);
        long[] rootsArray = table.getIndexRootsArray();

        pointerLookup.clear();
        database.logger.logDetailEvent(
            "lookup begins " + table.getName().statementName + " "
            + stopw.elapsedTime());
        store.moveDataToSpace(dataFileOut, pointerLookup);

        for (int i = 0; i < table.getIndexCount(); i++) {
            if (rootsArray[i] == -1) {
                continue;
            }

            long pos = pointerLookup.lookup(rootsArray[i], -1);

            if (pos == -1) {
                throw Error.error(ErrorCode.DATA_FILE_ERROR);
            }

            rootsArray[i] = pos;
        }

        // log any discrepency in row count
        long count = store.elementCount();

        if (count != pointerLookup.size()) {
            database.logger.logSevereEvent(
                "discrepency in row count " + table.getName().name + " "
                + count + " " + pointerLookup.size(),
                null);
        }

        database.logger.logDetailEvent(
            "table written " + table.getName().statementName);

        return rootsArray;
    }

    public long[][] getIndexRoots() {
        return rootsList;
    }
}
