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

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;

import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import org.hsqldb.Database;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.ArrayUtil;
import org.hsqldb.lib.EventLogInterface;
import org.hsqldb.lib.java.JavaSystem;

/**
 * NIO version of ScaledRAFile. This class is used only for storing a CACHED
 * TABLE .data file and cannot be used for TEXT TABLE source files.
 *
 * Once the maximum data file size allowed for NIO is reached, an ordinary
 * ScaledRAFile is used for data access.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version  2.7.0
 * @since 1.8.0.5
 */
final class RAFileNIO implements RandomAccessInterface {

    private final EventLogInterface logger;
    private final boolean           readOnly;
    private final long              maxLength;
    private long                    fileLength;
    private RandomAccessFile        file;
    private FileDescriptor          fileDescriptor;
    private MappedByteBuffer        buffer;
    private long                    bufferPosition;
    private int                     bufferLength;
    private long                    currentPosition;
    private FileChannel             channel;
    private boolean                 buffersModified;

    //
    private MappedByteBuffer[] buffers = new MappedByteBuffer[]{};

    //
    private static final String JVM_ERROR = "NIO access failed";

    //
    static final int  largeBufferScale = 24;
    static final int  largeBufferSize  = 1 << largeBufferScale;
    static final long largeBufferMask  = 0xffffffffffffffffL
        << largeBufferScale;

    RAFileNIO(
            EventLogInterface logger,
            String name,
            boolean readOnly,
            long requiredLength,
            long maxLength)
            throws IOException {

        this.logger    = logger;
        this.maxLength = maxLength;

        java.io.File tempFile = new java.io.File(name);

        if (readOnly) {
            requiredLength = tempFile.length();
        } else {
            if (tempFile.length() > requiredLength) {
                requiredLength = tempFile.length();
            }

            requiredLength = ArrayUtil.getBinaryNormalisedCeiling(
                requiredLength,
                largeBufferScale);
        }

        file                = new RandomAccessFile(
            name,
            readOnly
            ? "r"
            : "rw");
        this.readOnly       = readOnly;
        this.channel        = file.getChannel();
        this.fileDescriptor = file.getFD();

        if (ensureLength(requiredLength)) {
            buffer          = buffers[0];
            bufferLength    = buffer.limit();
            bufferPosition  = 0;
            currentPosition = 0;
        } else {
            close();

            IOException io = new IOException("NIO buffer allocation failed");

            throw io;
        }
    }

    public long length() throws IOException {

        try {
            return file.length();
        } catch (IOException e) {
            logger.logWarningEvent(JVM_ERROR, e);

            throw e;
        } catch (Throwable t) {
            logger.logWarningEvent(JVM_ERROR, t);

            IOException io = JavaSystem.toIOException(t);

            throw io;
        }
    }

    public void seek(long newPos) throws IOException {

        try {
            positionBufferSeek(newPos);
            buffer.position((int) (newPos - bufferPosition));
        } catch (IllegalArgumentException e) {
            logger.logWarningEvent(JVM_ERROR, e);

            IOException io = JavaSystem.toIOException(e);

            throw io;
        } catch (Throwable t) {
            logger.logWarningEvent(JVM_ERROR, t);

            IOException io = JavaSystem.toIOException(t);

            throw io;
        }
    }

    public long getFilePointer() throws IOException {

        try {
            return currentPosition;
        } catch (Throwable t) {
            logger.logWarningEvent(JVM_ERROR, t);

            IOException io = JavaSystem.toIOException(t);

            throw io;
        }
    }

    public int read() throws IOException {

        try {
            if (currentPosition == fileLength) {
                return -1;
            }

            int value = buffer.get();

            positionBufferMove(1);

            return value;
        } catch (Throwable t) {
            logger.logWarningEvent(JVM_ERROR, t);

            IOException io = JavaSystem.toIOException(t);

            throw io;
        }
    }

    public void read(byte[] b, int offset, int length) throws IOException {

        try {
            while (true) {
                checkBuffer();

                long transferLength = bufferPosition + bufferLength
                                      - currentPosition;

                if (transferLength > length) {
                    transferLength = length;
                }

                buffer.get(b, offset, (int) transferLength);
                positionBufferMove((int) transferLength);

                length -= transferLength;
                offset += transferLength;

                if (length == 0) {
                    break;
                }
            }
        } catch (Throwable t) {
            logger.logWarningEvent(JVM_ERROR, t);

            IOException io = JavaSystem.toIOException(t);

            throw io;
        }
    }

    public int readInt() throws IOException {

        try {
            int value = buffer.getInt();

            positionBufferMove(4);

            return value;
        } catch (Throwable t) {
            logger.logWarningEvent(JVM_ERROR, t);

            IOException io = JavaSystem.toIOException(t);

            throw io;
        }
    }

    public long readLong() throws IOException {

        try {
            long value = buffer.getLong();

            positionBufferMove(8);

            return value;
        } catch (Throwable t) {
            logger.logWarningEvent(JVM_ERROR, t);

            IOException io = JavaSystem.toIOException(t);

            throw io;
        }
    }

    public void write(byte[] b, int offset, int length) throws IOException {

        long transferLength;

        try {
            buffersModified = true;

            while (true) {
                checkBuffer();

                transferLength = bufferPosition + bufferLength
                                 - currentPosition;

                if (transferLength > length) {
                    transferLength = length;
                }

                buffer.put(b, offset, (int) transferLength);
                positionBufferMove((int) transferLength);

                length -= transferLength;
                offset += transferLength;

                if (length == 0) {
                    break;
                }
            }
        } catch (Throwable t) {
            logger.logWarningEvent(JVM_ERROR, t);

            IOException io = JavaSystem.toIOException(t);

            throw io;
        }
    }

    public void writeInt(int i) throws IOException {

        try {
            buffersModified = true;

            buffer.putInt(i);
            positionBufferMove(4);
        } catch (Throwable t) {
            logger.logWarningEvent(JVM_ERROR, t);

            IOException io = JavaSystem.toIOException(t);

            throw io;
        }
    }

    public void writeLong(long i) throws IOException {

        try {
            buffersModified = true;

            buffer.putLong(i);
            positionBufferMove(8);
        } catch (Throwable t) {
            logger.logWarningEvent(JVM_ERROR, t);

            IOException io = JavaSystem.toIOException(t);

            throw io;
        }
    }

    public void close() throws IOException {

        Throwable thrown = null;

        try {
            logger.logDetailEvent("NIO file close, size: " + fileLength);

            buffer  = null;
            channel = null;

            for (int i = 0; i < buffers.length; i++) {
                thrown     = JavaSystem.unmap(buffers[i]);
                buffers[i] = null;
            }

            file.close();
        } catch (Throwable t) {
            logger.logWarningEvent("NIO buffer close error", t);

            IOException io = JavaSystem.toIOException(t);

            throw io;
        } finally {
            if (thrown != null) {
                logger.logWarningEvent("NIO buffer unmap exception", thrown);
            }
        }
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean ensureLength(long newLength) {

        if (newLength > maxLength) {
            return false;
        }

        while (newLength > fileLength) {
            if (!enlargeFile(newLength)) {
                return false;
            }
        }

        return true;
    }

    private boolean enlargeFile(long newFileLength) {

        try {
            long newBufferLength;

            if (readOnly) {
                newBufferLength = largeBufferSize;

                if (file.length() - fileLength < largeBufferSize) {
                    newBufferLength = file.length() - fileLength;
                }
            } else {
                newBufferLength = largeBufferSize;
            }

            MapMode mapMode = readOnly
                              ? FileChannel.MapMode.READ_ONLY
                              : FileChannel.MapMode.READ_WRITE;

            if (!readOnly && file.length() < fileLength + newBufferLength) {
                file.seek(fileLength + newBufferLength - 1);
                file.writeByte(0);
            }

            MappedByteBuffer[] newBuffers =
                new MappedByteBuffer[buffers.length + 1];
            MappedByteBuffer newBuffer = channel.map(
                mapMode,
                fileLength,
                newBufferLength);

            System.arraycopy(buffers, 0, newBuffers, 0, buffers.length);

            newBuffers[buffers.length] = newBuffer;
            buffers                    = newBuffers;
            fileLength                 += newBufferLength;

            logger.logDetailEvent(
                "NIO buffer instance, file size " + fileLength);
        } catch (Throwable e) {
            logger.logDetailEvent(
                "NOI buffer allocate failed, file size " + newFileLength);

            return false;
        }

        return true;
    }

    public boolean setLength(long newLength) {

        if (newLength > fileLength) {
            return enlargeFile(newLength);
        } else {
            try {
                seek(0);
            } catch (Throwable t) {

                //
            }

            return true;
        }
    }

    public Database getDatabase() {
        return null;
    }

    public void synch() {

        boolean error    = false;
        int     errIndex = 0;

        for (int i = 0; i < buffers.length; i++) {
            try {
                buffers[i].force();
            } catch (Throwable t) {
                logger.logWarningEvent(
                    "NIO buffer force error: pos " + i * largeBufferSize + " ",
                    t);

                if (!error) {
                    errIndex = i;
                }

                error = true;
            }
        }

        if (error) {
            for (int i = errIndex; i < buffers.length; i++) {
                try {
                    buffers[i].force();
                } catch (Throwable t) {
                    logger.logWarningEvent(
                        "NIO buffer force error " + i * largeBufferSize + " ",
                        t);
                }
            }
        }

        try {
            fileDescriptor.sync();

            buffersModified = false;
        } catch (Throwable t) {
            logger.logSevereEvent("NIO RA file sync error ", t);

            throw Error.error(t, ErrorCode.FILE_IO_ERROR, null);
        }
    }

    private void positionBufferSeek(long offset) {

        if (offset < bufferPosition
                || offset >= bufferPosition + bufferLength) {
            setCurrentBuffer(offset);
        }

        buffer.position((int) (offset - bufferPosition));

        currentPosition = offset;
    }

    private void positionBufferMove(int relOffset) {

        long offset = currentPosition + relOffset;

        if (offset >= bufferPosition + bufferLength) {
            setCurrentBuffer(offset);
        }

        buffer.position((int) (offset - bufferPosition));

        currentPosition = offset;
    }

    private void setCurrentBuffer(long offset) {

        int bufferIndex = (int) (offset >> largeBufferScale);

        // when moving to last position in file
        if (bufferIndex == buffers.length) {
            bufferIndex    = buffers.length - 1;
            bufferPosition = (long) bufferIndex * largeBufferSize;
            buffer         = buffers[bufferIndex];

            return;
        }

        buffer         = buffers[bufferIndex];
        bufferPosition = offset & largeBufferMask;
    }

    /**
     * checks for two types of potential errors before reads and writes and
     * fixes them.
     *
     */
    private void checkBuffer() {

        if (readOnly) {
            return;
        }

        int bufferIndex = (int) (currentPosition >> largeBufferScale);

        if (currentPosition != bufferPosition + buffer.position()) {
            buffer         = buffers[bufferIndex];
            bufferPosition = currentPosition & largeBufferMask;

            buffer.position((int) (currentPosition - bufferPosition));
        } else if (buffer != buffers[bufferIndex]) {
            buffer = buffers[bufferIndex];
        }
    }
}
