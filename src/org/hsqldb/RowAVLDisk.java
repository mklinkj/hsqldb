/*
 * For work developed by the HSQL Development Group:
 *
 * Copyright (c) 2001-2024, The HSQL Development Group
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
 *
 *
 *
 * For work originally developed by the Hypersonic SQL Group:
 *
 * Copyright (c) 1995-2000, The Hypersonic SQL Group.
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
 * Neither the name of the Hypersonic SQL Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE HYPERSONIC SQL GROUP,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software consists of voluntary contributions made by many individuals
 * on behalf of the Hypersonic SQL Group.
 */


package org.hsqldb;

import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.index.NodeAVL;
import org.hsqldb.index.NodeAVLDisk;
import org.hsqldb.lib.LongLookup;
import org.hsqldb.persist.PersistentStore;
import org.hsqldb.rowio.RowInputInterface;
import org.hsqldb.rowio.RowOutputInterface;

// fredt@users 20020221 - patch 513005 by sqlbob@users (RMP)
// fredt@users 20020920 - patch 1.7.1 - refactoring to cut memory footprint
// fredt@users 20021205 - patch 1.7.2 - enhancements
// fredt@users 20021215 - doc 1.7.2 - javadoc comments
// campbell-burnet@users - 20040411 - doc 1.7.2 - javadoc comments

/**
 *  In-memory representation of a disk-based database row object with  methods
 *  for serialization and de-serialization. <p>
 *
 *  New class derived from Hypersonic SQL code and enhanced in HSQLDB. <p>
 *
 * @author Fred Toussi (fredt@users dot sourceforge dot net)
 * @author Thomas Mueller (Hypersonic SQL Group)
 * @version 2.5.1
 * @since Hypersonic SQL
 */
public class RowAVLDisk extends RowAVL {

    public static final int NO_POS = -1;

    //
    int              storageSize;
    int              keepCount;
    volatile boolean isInMemory;
    boolean          isNew;
    boolean          isFromFile;

    /**
     *  Flag indicating unwritten data.
     */
    boolean hasDataChanged;

    /**
     *  Flag indicating Node data has changed.
     */
    private boolean hasNodesChanged;

    /**
     *  Constructor for new Rows.  Variable hasDataChanged is set to true in
     *  order to indicate the data needs saving.
     *
     * @param t table
     * @param o row data
     */
    public RowAVLDisk(TableBase t, Object[] o, PersistentStore store) {

        super(t, o);

        setNewNodes(store);

        hasDataChanged = hasNodesChanged = isNew = true;
    }

    /**
     *  Constructor when read from the disk into the Cache.
     *
     * @param store store
     * @param in data source
     */
    public RowAVLDisk(PersistentStore store, RowInputInterface in) {

        super(store.getTable(), null);

        position    = in.getFilePosition();
        storageSize = in.getSize();

        int indexcount = store.getAccessorKeys().length;

        nPrimaryNode = new NodeAVLDisk(this, in, 0);

        NodeAVL n = nPrimaryNode;

        for (int i = 1; i < indexcount; i++) {
            n.nNext = new NodeAVLDisk(this, in, i);
            n       = n.nNext;
        }

        rowData    = in.readData(table.getColumnTypes());
        isFromFile = true;
    }

    RowAVLDisk(TableBase t) {
        super(t, null);
    }

    public NodeAVL insertNode(int index) {
        return null;
    }

    private void readRowInfo(RowInputInterface in) {

        // for use when additional transaction info is attached to rows
    }

    /**
     * Sets flag for Node data change.
     */
    public synchronized void setNodesChanged() {
        hasNodesChanged = true;
    }

    public void updateAccessCount(int count) {}

    public int getAccessCount() {
        return 0;
    }

    public int getStorageSize() {
        return storageSize;
    }

    public boolean isMemory() {
        return false;
    }

    /**
     * Sets the file position for the row
     *
     * @param pos position in data file
     */
    public void setPos(long pos) {
        position = pos;
    }

    public boolean isNew() {
        return isNew;
    }

    /**
     * Returns true if Node data has changed.
     *
     * @return boolean
     */
    public synchronized boolean hasChanged() {
        return hasNodesChanged || hasDataChanged;
    }

    public synchronized void setChanged(boolean flag) {
        hasNodesChanged = flag;
        hasDataChanged  = flag;
        isNew           = flag;
    }

    /**
     * Returns the Table to which this Row belongs.
     *
     * @return Table
     */
    public TableBase getTable() {
        return table;
    }

    public void setStorageSize(int size) {
        storageSize = size;
    }

    /**
     * Returns true if any of the Nodes for this row is a root node.
     * Used only in Cache.java to avoid removing the row from the cache.
     *
     * @return boolean
     */
    public synchronized boolean isKeepInMemory() {
        return keepCount > 0;
    }

    /**
     * No action as references don't need to be removed
     */
    public void delete(PersistentStore store) {}

    public void destroy() {}

    public synchronized boolean keepInMemory(boolean keep) {

        if (!isInMemory) {
            return false;
        }

        if (keep) {
            keepCount++;
        } else {
            keepCount--;

            if (keepCount < 0) {
                throw Error.runtimeError(
                    ErrorCode.U_S0500,
                    "RowAVLDisk - keep count");
            }
        }

        return true;
    }

    public synchronized boolean isInMemory() {
        return isInMemory;
    }

    public synchronized void setInMemory(boolean in) {
        isInMemory = in;
    }

    public void setNewNodes(PersistentStore store) {

        int indexcount = store.getAccessorKeys().length;

        nPrimaryNode = new NodeAVLDisk(this, 0);

        NodeAVL n = nPrimaryNode;

        for (int i = 1; i < indexcount; i++) {
            n.nNext = new NodeAVLDisk(this, i);
            n       = n.nNext;
        }
    }

    public int getRealSize(RowOutputInterface out) {
        return out.getSize(this);
    }

    /**
     * Used exclusively by Cache to save the row to disk. New implementation in
     * 1.7.2 writes out only the Node data if the table row data has not
     * changed. This situation accounts for the majority of invocations as for
     * each row deleted or inserted, the Nodes for several other rows will
     * change.
     */
    public void write(RowOutputInterface out) {

        writeNodes(out);

        if (hasDataChanged) {
            out.writeData(this, table.colTypes);
            out.writeEnd();
        }
    }

    public void write(RowOutputInterface out, LongLookup lookup) {

        out.writeSize(storageSize);

        NodeAVL rownode = nPrimaryNode;

        while (rownode != null) {
            rownode.write(out, lookup);

            rownode = rownode.nNext;
        }

        out.writeData(this, table.colTypes);
        out.writeEnd();
    }

    /**
     *  Writes the Nodes, immediately after the row size.
     *
     * @param out row output
     */
    void writeNodes(RowOutputInterface out) {

        out.writeSize(storageSize);

        NodeAVL n = nPrimaryNode;

        while (n != null) {
            n.write(out);

            n = n.nNext;
        }
    }
}
