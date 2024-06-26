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


package org.hsqldb.jdbc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;

import java.sql.Blob;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.KMPSearchAlgorithm;
import org.hsqldb.lib.java.JavaSystem;

// campbell-burnet@users 2004-04-xx - patch 1.7.2 - position and truncate methods
//                             implemented; minor changes for moderate thread
//                             safety and optimal performance
// campbell-burnet@users 2004-04-xx - doc 1.7.2 - javadocs updated; methods put in
//                             correct (historical, interface declared) order
// campbell-burnet@users 2005-12-07 - patch 1.8.0.x - initial JDBC 4.0 support work
// campbell-burnet@users 2006-05-22 - doc 1.9.0     - full synch up to JAVA 1.6 (Mustang) Build 84
//                           - patch 1.9.0   - setBinaryStream improvement
// patch 1.9.0
//  - fixed invalid reference to new BinaryStream(...) in getBinaryStream
//
// patch 1.9.0 - full synch up to JAVA 1.6 (Mustang) b90
//             - better bounds checking
//             - added support for clients to decide whether getBinaryStream
//               uses copy of internal byte buffer

/**
 * The representation (mapping) in
 * the Java programming language of an SQL
 * {@code BLOB} value.  An SQL {@code BLOB} is a built-in type
 * that stores a Binary Large Object as a column value in a row of
 * a database table. By default drivers implement {@code Blob} using
 * an SQL {@code locator(BLOB)}, which means that a
 * {@code Blob} object contains a logical pointer to the
 * SQL {@code BLOB} data rather than the data itself.
 * A {@code Blob} object is valid for the duration of the
 * transaction in which it was created.
 *
 * <P>Methods in the interfaces {@link java.sql.ResultSet},
 * {@link java.sql.CallableStatement}, and {@link java.sql.PreparedStatement}, such as
 * {@code getBlob} and {@code setBlob} allow a programmer to
 * access an SQL {@code BLOB} value.
 * The {@code Blob} interface provides methods for getting the
 * length of an SQL {@code BLOB} (Binary Large Object) value,
 * for materializing a {@code BLOB} value on the client, and for
 * determining the position of a pattern of bytes within a
 * {@code BLOB} value. In addition, this interface has methods for updating
 * a {@code BLOB} value.
 * <p>
 * All methods on the {@code Blob} interface must be fully implemented if the
 * JDBC driver supports the data type.
 *
 * <!-- start Release-specific documentation -->
 * <div class="ReleaseSpecificDocumentation">
 * <p class="rshead">HSQLDB-Specific Information:</p>
 *
 * Previous to 2.0, the HSQLDB driver did not implement Blob using an SQL
 * locator(BLOB).  That is, an HSQLDB Blob object did not contain a logical
 * pointer to SQL BLOB data; rather it directly contained a representation of
 * the data (a byte array). As a result, an HSQLDB Blob object was itself
 * valid beyond the duration of the transaction in which is was created,
 * although it did not necessarily represent a corresponding value
 * on the database. Also, the interface methods for updating a BLOB value
 * were unsupported, with the exception of the truncate method,
 * in that it could be used to truncate the local value. <p>
 *
 * Starting with 2.0, the HSQLDB driver fully supports both local and remote
 * SQL BLOB data implementations, meaning that an HSQLDB Blob object <em>may</em>
 * contain a logical pointer to remote SQL BLOB data (see {@link JDBCBlobClient
 * JDBCBlobClient}) or it may directly contain a local representation of the
 * data (as implemented in this class).  In particular, when the product is built
 * under JDK 1.6+ and the Blob instance is constructed as a result of calling
 * JDBCConnection.createBlob(), then the resulting Blob instance is initially
 * disconnected (is not bound to the transaction scope of the vending Connection
 * object), the data is contained directly and all interface methods for
 * updating the BLOB value are supported for local use until the first
 * invocation of free(); otherwise, an HSQLDB Blob's implementation is
 * determined at runtime by the driver, it is typically not valid beyond the
 * duration of the transaction in which is was created, and there no
 * standard way to query whether it represents a local or remote
 * value.
 *
 * </div>
 * <!-- end Release-specific documentation -->
 *
 * @author james house (jhouse@part.net)
 * @author Campbell Burnet (campbell-burnet@users dot sourceforge.net)
 * @version 2.7.3
 * @since JDK 1.2, HSQLDB 1.7.2
 */
public class JDBCBlob implements Blob {

    /**
     * Returns the number of bytes in the {@code BLOB} value
     * designated by this {@code Blob} object.
     *
     * @return length of the {@code BLOB} in bytes
     * @throws SQLException if there is an error accessing the
     * length of the {@code BLOB}
     * @throws SQLFeatureNotSupportedException if the JDBC driver does not support
     * this method
     * @since JDK 1.2, HSQLDB 1.7.2
     */
    public long length() throws SQLException {
        return getData().length;
    }

    /**
     * Retrieves all or part of the {@code BLOB}
     * value that this {@code Blob} object represents, as an array of
     * bytes.  This {@code byte} array contains up to {@code length}
     * consecutive bytes starting at position {@code pos}.
     *
     * <!-- start release-specific documentation -->
     * <div class="ReleaseSpecificDocumentation">
     * <p class="rshead">HSQLDB-Specific Information:</p>
     *
     * The official specification above is ambiguous in that it does not
     * precisely indicate the policy to be observed when
     * {@code pos > this.length() - length}.  One policy would be to retrieve the
     * octets from pos to this.length().  Another would be to throw an
     * exception.  HSQLDB observes the second policy.
     * </div>
     * <!-- end release-specific documentation -->
     *
     * @param pos the ordinal position of the first byte in the
     *        {@code BLOB} value to be extracted; the first byte is at
     *        position 1
     * @param length the number of consecutive bytes to be copied; the value
     *        for length must be 0 or greater
     * @return a byte array containing up to {@code length}
     *         consecutive bytes from the {@code BLOB} value designated
     *         by this {@code Blob} object, starting with the
     *         byte at position {@code pos}
     * @throws SQLException if there is an error accessing the
     *            {@code BLOB} value; if pos is less than 1 or length is
     * less than 0
   * @throws SQLFeatureNotSupportedException if the JDBC driver
   *         does not support this method
     * @see #setBytes
     * @since JDK 1.2, HSQLDB 1.7.2
     */
    public byte[] getBytes(long pos, final int length) throws SQLException {

        final byte[] data = getData();
        final int    dlen = data.length;

        if (pos < MIN_POS || pos - MIN_POS > dlen) {
            throw JDBCUtil.outOfRangeArgument("pos: " + pos);
        }

        final int index = (int) pos - 1;

        if (length < 0 || length > dlen - index) {
            throw JDBCUtil.outOfRangeArgument("length: " + length);
        }

        final byte[] result = new byte[length];

        System.arraycopy(data, index, result, 0, length);

        return result;
    }

    /**
     * Retrieves the {@code BLOB} value designated by this
     * {@code Blob} instance as a stream.
     *
     * @return a stream containing the {@code BLOB} data
     * @throws SQLException if there is an error accessing the
     *            {@code BLOB} value
   * @throws SQLFeatureNotSupportedException if the JDBC driver
   *         does not support this method
     * @see #setBinaryStream
     * @since JDK 1.2, HSQLDB 1.7.2
     */
    public InputStream getBinaryStream() throws SQLException {
        return new ByteArrayInputStream(getData());
    }

    /**
     * Retrieves the byte position at which the specified byte array
     * {@code pattern} begins within the {@code BLOB}
   * value that this {@code Blob} object represents.
   * The search for {@code pattern} begins at position
     * {@code start}.
     *
     * @param pattern the byte array for which to search
     * @param start the position at which to begin searching; the
     *        first position is 1
     * @return the position at which the pattern appears, else -1
     * @throws SQLException if there is an error accessing the
     * {@code BLOB} or if start is less than 1
     * @throws SQLFeatureNotSupportedException if the JDBC driver
     *         does not support this method
     * @since JDK 1.2, HSQLDB 1.7.2
     */
    public long position(
            final byte[] pattern,
            final long start)
            throws SQLException {

        final byte[] data = getData();
        final int    dlen = data.length;

        if (start < MIN_POS) {
            throw JDBCUtil.outOfRangeArgument("start: " + start);
        } else if (start > dlen || pattern == null) {
            return -1L;
        }

        // by now, we know start <= Integer.MAX_VALUE;
        final int startIndex = (int) start - 1;
        final int plen       = pattern.length;

        if (plen == 0 || startIndex > dlen - plen) {
            return -1L;
        }

        final int result = KMPSearchAlgorithm.search(
            data,
            pattern,
            KMPSearchAlgorithm.computeTable(pattern),
            startIndex);

        return (result == -1)
               ? -1
               : result + 1;
    }

    /**
     * Retrieves the byte position in the {@code BLOB} value
     * designated by this {@code Blob} object at which
     * {@code pattern} begins.  The search begins at position
     * {@code start}.
     *
     * @param pattern the {@code Blob} object designating
     * the {@code BLOB} value for which to search
     * @param start the position in the {@code BLOB} value
     *        at which to begin searching; the first position is 1
     * @return the position at which the pattern begins, else -1
     * @throws SQLException if there is an error accessing the
     *            {@code BLOB} value or if start is less than 1
     * @throws SQLFeatureNotSupportedException if the JDBC driver
     *         does not support this method
     * @since JDK 1.2, HSQLDB 1.7.2
     */
    public long position(final Blob pattern, long start) throws SQLException {

        final byte[] data = getData();
        final int    dlen = data.length;

        if (start < MIN_POS) {
            throw JDBCUtil.outOfRangeArgument("start: " + start);
        } else if (start > dlen || pattern == null) {
            return -1L;
        }

        // by now, we know start <= Integer.MAX_VALUE;
        final int  startIndex = (int) (start - MIN_POS);
        final long plen       = pattern.length();

        if (plen == 0 || startIndex > ((long) dlen) - plen) {
            return -1L;
        }

        // by now, we know plen <= Integer.MAX_VALUE
        final int iplen = (int) plen;
        byte[]    bytePattern;

        if (pattern instanceof JDBCBlob) {
            bytePattern = ((JDBCBlob) pattern).data();
        } else {
            bytePattern = pattern.getBytes(1L, iplen);
        }

        final int result = KMPSearchAlgorithm.search(
            data,
            bytePattern,
            KMPSearchAlgorithm.computeTable(bytePattern),
            startIndex);

        return (result == -1)
               ? -1
               : result + 1;
    }

    // -------------------------- JDBC 3.0 -----------------------------------

    /**
     * Writes the given array of bytes to the {@code BLOB} value that
     * this {@code Blob} object represents, starting at position
     * {@code pos}, and returns the number of bytes written.
     * The array of bytes will overwrite the existing bytes
     * in the {@code Blob} object starting at the position
     * {@code pos}.  If the end of the {@code Blob} value is reached
     * while writing the array of bytes, then the length of the {@code Blob}
     * value will be increased to accommodate the extra bytes.
     * <p>
     * <b>Note:</b> If the value specified for {@code pos}
     * is greater than the length+1 of the {@code BLOB} value then the
     * behavior is undefined. Some JDBC drivers may throw an
     * {@code SQLException} while other drivers may support this
     * operation.
     *
     * <!-- start release-specific documentation -->
     * <div class="ReleaseSpecificDocumentation">
     * <p class="rshead">HSQLDB-Specific Information:</p>
     *
     * Starting with HSQLDB 2.0 this feature is supported. <p>
     *
     * When built under JDK 1.6+ and the Blob instance is constructed as a
     * result of calling JDBCConnection.createBlob(), this operation affects
     * only the client-side value; it has no effect upon a value stored in a
     * database because JDBCConnection.createBlob() constructs disconnected,
     * initially empty Blob instances. To propagate the Blob value to a database
     * in this case, it is required to supply the Blob instance to an updating
     * or inserting setXXX method of a Prepared or Callable Statement, or to
     * supply the Blob instance to an updateXXX method of an updatable
     * ResultSet. <p>
     *
     * <b>Implementation Notes:</b><p>
     *
     * Starting with HSQLDB 2.1, JDBCBlob no longer utilizes volatile fields
     * and is effectively thread safe, but still uses local variable
     * snapshot isolation. <p>
     *
     * As such, the synchronization policy still does not strictly enforce
     * serialized read/write access to the underlying data  <p>
     *
     * So, if an application may perform concurrent JDBCBlob modifications and
     * the integrity of the application depends on total order Blob modification
     * semantics, then such operations should be synchronized on an appropriate
     * monitor.
     *
     * </div>
     * <!-- end release-specific documentation -->
     *
     * @param pos the position in the {@code BLOB} object at which
     *        to start writing; the first position is 1
     * @param bytes the array of bytes to be written to the {@code BLOB}
     *        value that this {@code Blob} object represents
     * @return the number of bytes written
     * @throws SQLException if there is an error accessing the
     *            {@code BLOB} value or if pos is less than 1
     * @throws SQLFeatureNotSupportedException if the JDBC driver
     *         does not support this method
     * @see #getBytes
     * @since JDK 1.4, HSQLDB 1.7.2
     */
    public int setBytes(long pos, byte[] bytes) throws SQLException {

        return setBytes(
            pos,
            bytes,
            0,
            bytes == null
            ? 0
            : bytes.length);
    }

    /**
     * Writes all or part of the given {@code byte} array to the
     * {@code BLOB} value that this {@code Blob} object represents
     * and returns the number of bytes written.
     * Writing starts at position {@code pos} in the {@code BLOB}
     * value; {@code len} bytes from the given byte array are written.
     * The array of bytes will overwrite the existing bytes
     * in the {@code Blob} object starting at the position
     * {@code pos}.  If the end of the {@code Blob} value is reached
     * while writing the array of bytes, then the length of the {@code Blob}
     * value will be increased to accommodate the extra bytes.
     * <p>
     * <b>Note:</b> If the value specified for {@code pos}
     * is greater than the length+1 of the {@code BLOB} value then the
     * behavior is undefined. Some JDBC drivers may throw an
     * {@code SQLException} while other drivers may support this
     * operation.
     *
     * <!-- start release-specific documentation -->
     * <div class="ReleaseSpecificDocumentation">
     * <p class="rshead">HSQLDB-Specific Information:</p>
     *
     * Starting with HSQLDB 2.0 this feature is supported. <p>
     *
     * When built under JDK 1.6+ and the Blob instance is constructed as a
     * result of calling JDBCConnection.createBlob(), this operation affects
     * only the client-side value; it has no effect upon a value stored in a
     * database because JDBCConnection.createBlob() constructs disconnected,
     * initially empty Blob instances. To propagate the Blob value to a database
     * in this case, it is required to supply the Blob instance to an updating
     * or inserting setXXX method of a Prepared or Callable Statement, or to
     * supply the Blob instance to an updateXXX method of an updatable
     * ResultSet. <p>
     *
     * <b>Implementation Notes:</b><p>
     *
     * If the value specified for {@code pos}
     * is greater than the length of the {@code BLOB} value, then
     * the {@code BLOB} value is extended in length to accept the
     * written octets and the undefined region up to {@code pos} is
     * filled with (byte)0. <p>
     *
     * Starting with HSQLDB 2.1, JDBCBlob no longer utilizes volatile fields
     * and is effectively thread safe, but still uses local variable
     * snapshot isolation. <p>
     *
     * As such, the synchronization policy still does not strictly enforce
     * serialized read/write access to the underlying data  <p>
     *
     * So, if an application may perform concurrent JDBCBlob modifications and
     * the integrity of the application depends on total order Blob modification
     * semantics, then such operations should be synchronized on an appropriate
     * monitor.
     *
     * </div>
     * <!-- end release-specific documentation -->
     *
     * @param pos the position in the {@code BLOB} object at which
     *        to start writing; the first position is 1
     * @param bytes the array of bytes to be written to this {@code BLOB}
     *        object
     * @param offset the offset into the array {@code bytes} at which
     *        to start reading the bytes to be set
     * @param len the number of bytes to be written to the {@code BLOB}
     *        value from the array of bytes {@code bytes}
     * @return the number of bytes written
     * @throws SQLException if there is an error accessing the
     *            {@code BLOB} value or if pos is less than 1
     * @throws SQLFeatureNotSupportedException if the JDBC driver
     *         does not support this method
     * @see #getBytes
     * @since JDK 1.4, HSQLDB 1.7.2
     */
    public int setBytes(
            long pos,
            byte[] bytes,
            int offset,
            int len)
            throws SQLException {

        checkReadonly();

        if (bytes == null) {
            throw JDBCUtil.nullArgument("bytes");
        }

        if (offset < 0 || offset > bytes.length) {
            throw JDBCUtil.outOfRangeArgument("offset: " + offset);
        }

        if (len > bytes.length - offset) {
            throw JDBCUtil.outOfRangeArgument("len: " + len);
        }

        if (pos < MIN_POS || (pos - MIN_POS) > (Integer.MAX_VALUE - len)) {
            throw JDBCUtil.outOfRangeArgument("pos: " + pos);
        }

        final int index = (int) (pos - MIN_POS);
        byte[]    data  = getData();
        final int dlen  = data.length;

        if (index > dlen - len) {
            byte[] temp = new byte[index + len];

            System.arraycopy(data, 0, temp, 0, dlen);

            data = temp;
            temp = null;
        }

        System.arraycopy(bytes, offset, data, index, len);
        setData(data);

        return len;
    }

    /**
     * Retrieves a stream that can be used to write to the {@code BLOB}
     * value that this {@code Blob} object represents.  The stream begins
     * at position {@code pos}.
     * The  bytes written to the stream will overwrite the existing bytes
     * in the {@code Blob} object starting at the position
     * {@code pos}.  If the end of the {@code Blob} value is reached
     * while writing to the stream, then the length of the {@code Blob}
     * value will be increased to accommodate the extra bytes.
     * <p>
     * <b>Note:</b> If the value specified for {@code pos}
     * is greater than the length+1 of the {@code BLOB} value then the
     * behavior is undefined. Some JDBC drivers may throw an
     * {@code SQLException} while other drivers may support this
     * operation.
     *
     * <!-- start release-specific documentation -->
     * <div class="ReleaseSpecificDocumentation">
     * <p class="rshead">HSQLDB-Specific Information:</p>
     *
     * Starting with HSQLDB 2.0 this feature is supported. <p>
     *
     * When built under JDK 1.6+ and the Blob instance is constructed as a
     * result of calling JDBCConnection.createBlob(), this operation affects
     * only the client-side value; it has no effect upon a value stored in a
     * database because JDBCConnection.createBlob() constructs disconnected,
     * initially empty Blob instances. To propagate the Blob value to a database
     * in this case, it is required to supply the Blob instance to an updating
     * or inserting setXXX method of a Prepared or Callable Statement, or to
     * supply the Blob instance to an updateXXX method of an updatable
     * ResultSet. <p>
     *
     * <b>Implementation Notes:</b><p>
     *
     * The data written to the stream does not appear in this
     * Blob until the stream is closed <p>
     *
     * When the stream is closed, if the value specified for {@code pos}
     * is greater than the length of the {@code BLOB} value, then
     * the {@code BLOB} value is extended in length to accept the
     * written octets and the undefined region up to {@code pos} is
     * filled with (byte)0. <p>
     *
     * Starting with HSQLDB 2.1, JDBCBlob no longer utilizes volatile fields
     * and is effectively thread safe, but still uses local variable
     * snapshot isolation. <p>
     *
     * As such, the synchronization policy still does not strictly enforce
     * serialized read/write access to the underlying data  <p>
     *
     * So, if an application may perform concurrent JDBCBlob modifications and
     * the integrity of the application depends on total order Blob modification
     * semantics, then such operations should be synchronized on an appropriate
     * monitor.
     *
     * </div>
     * <!-- end release-specific documentation -->
     *
     * @param pos the position in the {@code BLOB} value at which
     *        to start writing; the first position is 1
     * @return a {@code java.io.OutputStream} object to which data can
     *         be written
     * @throws SQLException if there is an error accessing the
     *            {@code BLOB} value or if pos is less than 1
     * @throws SQLFeatureNotSupportedException if the JDBC driver
     *         does not support this method
     * @see #getBinaryStream
     * @since JDK 1.4, HSQLDB 1.7.2
     */
    public OutputStream setBinaryStream(final long pos) throws SQLException {

        checkReadonly();

        if (pos < MIN_POS || pos > MAX_POS) {
            throw JDBCUtil.outOfRangeArgument("pos: " + pos);
        }

        checkClosed();

        return new java.io.ByteArrayOutputStream() {

            private boolean closed;
            public synchronized void close() throws java.io.IOException {

                if (closed) {
                    return;
                }

                closed = true;

                byte[] bytes  = super.buf;
                int    length = super.count;

                super.buf   = NO_BYTES;
                super.count = 0;

                try {
                    JDBCBlob.this.setBytes(pos, bytes, 0, length);
                } catch (SQLException se) {
                    throw JavaSystem.toIOException(se);
                } finally {
                    super.close();
                }
            }
        };
    }

    /**
     * Truncates the {@code BLOB} value that this {@code Blob}
     * object represents to be {@code len} bytes in length.
     * <p>
     * <b>Note:</b> If the value specified for {@code pos}
     * is greater than the length+1 of the {@code BLOB} value then the
     * behavior is undefined. Some JDBC drivers may throw an
     * {@code SQLException} while other drivers may support this
     * operation.
     *
     * <!-- start release-specific documentation -->
     * <div class="ReleaseSpecificDocumentation">
     * <p class="rshead">HSQLDB-Specific Information:</p>
     *
     * Starting with HSQLDB 2.0 this feature is fully supported. <p>
     *
     * When built under JDK 1.6+ and the Blob instance is constructed as a
     * result of calling JDBCConnection.createBlob(), this operation affects
     * only the client-side value; it has no effect upon a value stored in a
     * database because JDBCConnection.createBlob() constructs disconnected,
     * initially empty Blob instances. To propagate the truncated Blob value to
     * a database in this case, it is required to supply the Blob instance to
     * an updating or inserting setXXX method of a Prepared or Callable
     * Statement, or to supply the Blob instance to an updateXXX method of an
     * updateable ResultSet.
     *
     * </div>
     * <!-- end release-specific documentation -->
     *
     * @param len the length, in bytes, to which the {@code BLOB} value
     *        that this {@code Blob} object represents should be truncated
     * @throws SQLException if there is an error accessing the
     *            {@code BLOB} value or if len is less than 0
     * @throws SQLFeatureNotSupportedException if the JDBC driver
     *         does not support this method
     * @since JDK 1.4, HSQLDB 1.7.2
     */
    public void truncate(final long len) throws SQLException {

        checkReadonly();

        final byte[] data = getData();

        if (len < 0 || len > data.length) {
            throw JDBCUtil.outOfRangeArgument("len: " + len);
        } else if (len == data.length) {
            return;
        }

        byte[] newData = new byte[(int) len];

        System.arraycopy(data, 0, newData, 0, (int) len);
        setData(newData);
    }

    //------------------------- JDBC 4.0 -----------------------------------

    /**
     * This method frees the {@code Blob} object and releases the resources that
     * it holds. The object is invalid once the {@code free}
     * method is called.
     * <p>
     * After {@code free} has been called, any attempt to invoke a
     * method other than {@code free} will result in an {@code SQLException}
     * being thrown.  If {@code free} is called multiple times, the subsequent
     * calls to {@code free} are treated as a no-op.
     *
     * @throws SQLException if an error occurs releasing
     * the Blob's resources
     * @throws SQLFeatureNotSupportedException if the JDBC driver
     *         does not support this method
     * @since JDK 1.6, HSQLDB 2.0
     */
    public synchronized void free() throws SQLException {
        m_closed = true;
        m_data   = null;
    }

    /**
     * Returns an {@code InputStream} object that contains
     * a partial {@code Blob} value, starting with the byte
     * specified by pos, which is length bytes in length.
     *
     * @param pos the offset to the first byte of the partial value to be
     *        retrieved. The first byte in the {@code Blob} is at position 1.
     * @param length the length in bytes of the partial value to be retrieved
     * @return {@code InputStream} through which
     *         the partial {@code Blob} value can be read.
     * @throws SQLException if pos is less than 1 or if pos is greater
     *         than the number of bytes in the {@code Blob} or if
     *         pos + length is greater than the number of bytes
     * in the {@code Blob}
     *
     * @throws SQLFeatureNotSupportedException if the JDBC driver
     *         does not support this method
     * @since JDK 1.6, HSQLDB 2.0
     */
    public InputStream getBinaryStream(
            long pos,
            long length)
            throws SQLException {

        final byte[] data = getData();
        final int    dlen = data.length;

        if (pos < MIN_POS || pos > dlen) {
            throw JDBCUtil.outOfRangeArgument("pos: " + pos);
        }

        int index = (int) (pos - MIN_POS);

        if (length < 0 || length > dlen - index) {
            throw JDBCUtil.outOfRangeArgument("length: " + length);
        }

        if (index == 0 && length == dlen) {
            return new ByteArrayInputStream(data);
        }

        final int    ilength = (int) length;
        final byte[] result  = new byte[ilength];

        System.arraycopy(data, index, result, 0, ilength);

        return new ByteArrayInputStream(result);
    }

    // ---------------------- internal implementation --------------------------
    private static final long   MIN_POS  = 1L;
    private static final long   MAX_POS  = MIN_POS + (long) Integer.MAX_VALUE;
    private static final byte[] NO_BYTES = new byte[0];
    private boolean             m_closed;
    private byte[]              m_data;
    private final boolean       m_createdByConnection;

    /**
     * Constructs a new JDBCBlob instance wrapping the given octet sequence. <p>
     *
     * This constructor is used internally to retrieve result set values as
     * Blob objects, yet it must be public to allow access from other packages.
     * As such (in the interest of efficiency) this object maintains a reference
     * to the given octet sequence rather than making a copy; special care
     * should be taken by external clients never to use this constructor with a
     * byte array object that may later be modified externally.
     *
     * @param data the octet sequence representing the Blob value
     * @throws SQLException if the argument is null
     */
    public JDBCBlob(final byte[] data) throws SQLException {

        if (data == null) {
            throw JDBCUtil.nullArgument("data");
        }

        m_data                = data;
        m_createdByConnection = false;
    }

    protected JDBCBlob() {
        m_data                = new byte[0];
        m_createdByConnection = true;
    }

    protected void checkReadonly() throws SQLException {
        if (!m_createdByConnection) {
            throw JDBCUtil.sqlException(ErrorCode.X_25006, "Blob is read-only");
        }
    }

    protected synchronized void checkClosed() throws SQLException {
        if (m_closed) {
            throw JDBCUtil.sqlException(ErrorCode.X_07501);
        }
    }

    protected byte[] data() throws SQLException {
        return getData();
    }

    //@SuppressWarnings("ReturnOfCollectionOrArrayField")
    private synchronized byte[] getData() throws SQLException {
        checkClosed();

        return m_data;
    }

    //@SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
    private synchronized void setData(byte[] data) throws SQLException {
        checkClosed();

        m_data = data;
    }
}
