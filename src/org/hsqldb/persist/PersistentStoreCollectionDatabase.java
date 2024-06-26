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

import java.util.concurrent.atomic.AtomicLong;

import org.hsqldb.Database;
import org.hsqldb.TableBase;
import org.hsqldb.lib.Iterator;
import org.hsqldb.lib.LongKeyHashMap;

/**
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 1.9.0
 */
public class PersistentStoreCollectionDatabase
        implements PersistentStoreCollection {

    private Database database;
    private AtomicLong persistentStoreIdSequence = new AtomicLong();
    private final LongKeyHashMap<PersistentStore> rowStoreMap =
        new LongKeyHashMap<>();

    public PersistentStoreCollectionDatabase(Database db) {
        this.database = db;
    }

    synchronized public PersistentStore getStore(TableBase table) {

        long            persistenceId = table.getPersistenceId();
        PersistentStore store         = rowStoreMap.get(persistenceId);

        if (store == null) {
            store = database.logger.newStore(null, this, table);

            rowStoreMap.put(persistenceId, store);

            table.store = store;
        }

        return store;
    }

    synchronized public void setStore(TableBase table, PersistentStore store) {
        rowStoreMap.put(table.getPersistenceId(), store);

        table.store = store;
    }

    public void release() {

        if (rowStoreMap.isEmpty()) {
            return;
        }

        Iterator<PersistentStore> it = rowStoreMap.values().iterator();

        while (it.hasNext()) {
            PersistentStore store = it.next();

            store.release();
        }

        rowStoreMap.clear();
    }

    synchronized public void removeStore(TableBase table) {

        PersistentStore store = rowStoreMap.get(table.getPersistenceId());

        if (store != null) {
            store.removeAll();
            store.release();
            rowStoreMap.remove(table.getPersistenceId());
        }
    }

    public long getNextId() {
        return persistentStoreIdSequence.getAndIncrement();
    }

    public void setNewTableSpaces() {

        DataFileCache dataCache = database.logger.getCache();

        if (dataCache == null) {
            return;
        }

        Iterator<PersistentStore> it = rowStoreMap.values().iterator();

        while (it.hasNext()) {
            PersistentStore store = it.next();

            if (store == null) {
                continue;
            }

            TableBase table = store.getTable();

            if (table.getTableType() == TableBase.CACHED_TABLE) {
                TableSpaceManager tableSpace =
                    dataCache.spaceManager.getTableSpace(
                        table.getSpaceID());

                store.setSpaceManager(tableSpace);
            }
        }
    }
}
