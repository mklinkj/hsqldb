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

import org.hsqldb.Row;
import org.hsqldb.RowAVL;
import org.hsqldb.RowAction;
import org.hsqldb.Session;
import org.hsqldb.Table;
import org.hsqldb.TableBase;
import org.hsqldb.index.Index;
import org.hsqldb.index.NodeAVL;
import org.hsqldb.navigator.RowIterator;

/*
 * Implementation of PersistentStore for information schema and temp tables.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.5.0
 * @since 2.0.1
 */
public class RowStoreAVLHybridExtended extends RowStoreAVLHybrid {

    Session session;

    public RowStoreAVLHybridExtended(
            Session session,
            TableBase table,
            boolean diskBased) {
        super(session, table, diskBased);

        this.session = session;
    }

    public CachedObject getNewCachedObject(
            Session session,
            Object object,
            boolean tx) {

        if (!resettingAccessor && table.getIndexCount() != indexList.length) {
            resetAccessorKeys(session, table.getIndexList());
        }

        return super.getNewCachedObject(session, object, tx);
    }

    public synchronized void add(
            Session session,
            CachedObject object,
            boolean tx) {

        super.add(session, object, tx);

        if (tx) {
            RowAction.addInsertAction(session, table, this, (Row) object);
        }
    }

    public void indexRow(Session session, Row row) {

        NodeAVL node  = ((RowAVL) row).getNode(0);
        int     count = 0;

        while (node != null) {
            count++;

            node = node.nNext;
        }

        if (isCached && row.isMemory() || count != indexList.length) {
            row = (Row) getNewCachedObject(session, row.getData(), true);
        }

        super.indexRow(session, row);
    }

    /**
     * Row might have changed from memory to disk or indexes added
     */
    public void delete(Session session, Row row) {

        NodeAVL node  = ((RowAVL) row).getNode(0);
        int     count = 0;

        while (node != null) {
            count++;

            node = node.nNext;
        }

        if ((isCached && row.isMemory()) || count != indexList.length) {
            row = ((Table) table).getDeleteRowFromLog(session, row.getData());
        }

        if (row != null) {
            super.delete(session, row);
        }
    }

    public synchronized double searchCost(
            Session session,
            Index index,
            int count,
            int opType) {

        if (table.getIndexCount() != indexList.length) {
            resetAccessorKeys(session, table.getIndexList());
        }

        return super.searchCost(session, index, count, opType);
    }

    boolean resettingAccessor = false;

    public CachedObject getAccessor(Index key) {

        if (!resettingAccessor
                && key.getPosition() > 0
                && table.getIndexCount() != indexList.length) {
            resetAccessorKeys(session, table.getIndexList());
        }

        return super.getAccessor(key);
    }

    public synchronized void resetAccessorKeys(Session session, Index[] keys) {

        resettingAccessor = true;

        try {
            if (indexList.length == 0 || accessorList[0] == null) {
                indexList    = keys;
                accessorList = new CachedObject[indexList.length];

                return;
            }

            boolean result;

            if (isCached) {
                resetAccessorKeysCached(keys);
            } else {
                super.resetAccessorKeys(session, keys);
            }
        } finally {
            resettingAccessor = false;
        }
    }

    private void resetAccessorKeysCached(Index[] keys) {

        RowStoreAVLHybrid tempStore = new RowStoreAVLHybridExtended(
            session,
            table,
            true);

        tempStore.changeToDiskTable(session);

        tempStore.indexList    = indexList;
        tempStore.accessorList = accessorList;

        tempStore.elementCount.set(elementCount.get());

        //
        indexList    = keys;
        accessorList = new CachedObject[indexList.length];

        elementCount.set(0);

        RowIterator iterator = tempStore.rowIterator();

        while (iterator.next()) {
            Row row    = iterator.getCurrentRow();
            Row newRow = (Row) getNewCachedObject(
                session,
                row.getData(),
                false);

            indexRow(session, newRow);
        }
    }
}
