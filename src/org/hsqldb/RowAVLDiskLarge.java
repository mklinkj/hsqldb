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

import org.hsqldb.index.NodeAVL;
import org.hsqldb.index.NodeAVLDiskLarge;
import org.hsqldb.persist.PersistentStore;
import org.hsqldb.rowio.RowInputInterface;

/**
 * Subclass of Row huge databases. <p>
 * @author Fred Toussi (fredt@users dot sourceforge dot net)
 * @version 2.4.0
 * @since 2.2.9
 */
public class RowAVLDiskLarge extends RowAVLDisk {

    /**
     *  Constructor for new Rows.  Variable hasDataChanged is set to true in
     *  order to indicate the data needs saving.
     *
     * @param t table
     * @param o row data
     */
    public RowAVLDiskLarge(TableBase t, Object[] o, PersistentStore store) {
        super(t, o, store);
    }

    /**
     *  Constructor when read from the disk into the Cache.
     *
     * @param store store
     * @param in data source
     */
    public RowAVLDiskLarge(PersistentStore store, RowInputInterface in) {

        super(store.getTable());

        position    = in.getFilePosition();
        storageSize = in.getSize();

        int indexcount = store.getAccessorKeys().length;

        nPrimaryNode = new NodeAVLDiskLarge(this, in, 0);

        NodeAVL n = nPrimaryNode;

        for (int i = 1; i < indexcount; i++) {
            n.nNext = new NodeAVLDiskLarge(this, in, i);
            n       = n.nNext;
        }

        rowData = in.readData(table.getColumnTypes());
    }

    public void setNewNodes(PersistentStore store) {

        int indexcount = store.getAccessorKeys().length;

        nPrimaryNode = new NodeAVLDiskLarge(this, 0);

        NodeAVL n = nPrimaryNode;

        for (int i = 1; i < indexcount; i++) {
            n.nNext = new NodeAVLDiskLarge(this, i);
            n       = n.nNext;
        }
    }
}
