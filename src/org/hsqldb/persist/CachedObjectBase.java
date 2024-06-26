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

import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;

/**
 * Base class for a stored simple object.
 *
 * @author Fred Toussi (fredt@users dot sourceforge dot net)
 * @version 2.3.3
 */
public abstract class CachedObjectBase implements CachedObject {

    boolean isMemory;
    long    position;
    int     storageSize;
    boolean isInMemory;
    boolean hasChanged;
    int     keepCount;
    int     accessCount;

    public boolean isMemory() {
        return isMemory;
    }

    public void updateAccessCount(int count) {
        accessCount = count;
    }

    public int getAccessCount() {
        return accessCount;
    }

    public void setStorageSize(int size) {
        storageSize = size;
    }

    public int getStorageSize() {
        return storageSize;
    }

    public final boolean isInvariable() {
        return false;
    }

    public final boolean isBlock() {
        return true;
    }

    public long getPos() {
        return position;
    }

    public void setPos(long pos) {
        position = pos;
    }

    public boolean isNew() {
        return false;
    }

    public boolean hasChanged() {
        return hasChanged;
    }

    public final void setChanged(boolean flag) {
        hasChanged = flag;
    }

    public boolean isKeepInMemory() {
        return keepCount > 0;
    }

    public boolean keepInMemory(boolean keep) {

        if (!isInMemory) {
            return false;
        }

        if (keep) {
            keepCount++;
        } else {
            if (keepCount == 0) {
                throw Error.runtimeError(
                    ErrorCode.U_S0500,
                    "CachedObjectBase - keep count");
            }

            keepCount--;
        }

        return true;
    }

    public boolean isInMemory() {
        return isInMemory;
    }

    public void setInMemory(boolean in) {
        isInMemory = in;
    }

    public boolean equals(Object other) {
        return other instanceof CachedObjectBase
               && ((CachedObjectBase) other).position == this.position;
    }

    public int hashCode() {
        return (int) position;
    }

    public void restore() {}

    public void destroy() {}
}
