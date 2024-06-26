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

import java.io.Serializable;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Wrapper;

import java.util.Properties;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;

import javax.sql.DataSource;

/**
 * <p>A factory for connections to the physical data source that this
 * {@code DataSource} object represents.  An alternative to the
 * {@code DriverManager} facility, a {@code DataSource} object
 * is the preferred means of getting a connection. An object that implements
 * the {@code DataSource} interface will typically be
 * registered with a naming service based on the
 * Java&trade; Naming and Directory (JNDI) API.
 * <P>
 * The {@code DataSource} interface is implemented by a driver vendor.
 * There are three types of implementations:
 * <OL>
 *   <LI>Basic implementation -- produces a standard {@code Connection}
 *       object
 *   <LI>Connection pooling implementation -- produces a {@code Connection}
 *       object that will automatically participate in connection pooling.  This
 *       implementation works with a middle-tier connection pooling manager.
 *   <LI>Distributed transaction implementation -- produces a
 *       {@code Connection} object that may be used for distributed
 *       transactions and almost always participates in connection pooling.
 *       This implementation works with a middle-tier
 *       transaction manager and almost always with a connection
 *       pooling manager.
 * </OL>
 * <P>
 * A {@code DataSource} object has properties that can be modified
 * when necessary.  For example, if the data source is moved to a different
 * server, the property for the server can be changed.  The benefit is that
 * because the data source's properties can be changed, any code accessing
 * that data source does not need to be changed.
 * <P>
 * A driver that is accessed via a {@code DataSource} object does not
 * register itself with the {@code DriverManager}.  Rather, a
 * {@code DataSource} object is retrieved though a lookup operation
 * and then used to create a {@code Connection} object.  With a basic
 * implementation, the connection obtained through a {@code DataSource}
 * object is identical to a connection obtained through the
 * {@code DriverManager} facility.
 *
 * <!-- start Release-specific documentation -->
 * <div class="ReleaseSpecificDocumentation">
 * <p class="rshead">HSQLDB-Specific Information:</p>
 *
 * This implementation of data source is a basic implementation and does not
 * perform connection pooling.<p>
 *
 * The getter and setter methods of the parent class, {@link JDBCCommonDataSource},
 * can be used.
 * </div>
 * <!-- end Release-specific documentation -->
 * @author Campbell Burnet (campbell-burnet@users dot sourceforge.net)
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since JDK 1.4, HSQLDB 1.7.2
 */
@SuppressWarnings("serial")
public class JDBCDataSource extends JDBCCommonDataSource
        implements DataSource, Serializable, Referenceable, Wrapper {

    /**
     * Retrieves a new connection using the properties that have already been
     * set.
     *
     * @return  a connection to the data source
     * @throws SQLException if a database access error occurs
     */
    public Connection getConnection() throws SQLException {

        if (url == null) {
            throw JDBCUtil.nullArgument("url");
        }

        if (connectionProps == null) {
            if (user == null) {
                throw JDBCUtil.invalidArgument("user");
            }

            if (password == null) {
                throw JDBCUtil.invalidArgument("password");
            }

            return getConnection(user, password);
        }

        return getConnection(url, connectionProps);
    }

    /**
     * Retrieves a new connection using the given username and password,
     * and the database url that has been set. No other properties are
     * used for the connection
     *
     * @param username the database user on whose behalf the connection is
     *  being made
     * @param password the user's password
     * @return  a connection to the data source
     * @throws SQLException if a database access error occurs
     */
    public Connection getConnection(
            String username,
            String password)
            throws SQLException {

        if (username == null) {
            throw JDBCUtil.invalidArgument("user");
        }

        if (password == null) {
            throw JDBCUtil.invalidArgument("password");
        }

        Properties props = new Properties();

        props.setProperty("user", username);
        props.setProperty("password", password);
        props.setProperty("loginTimeout", Integer.toString(loginTimeout));

        return getConnection(url, props);
    }

    private Connection getConnection(
            String url,
            Properties props)
            throws SQLException {

        if (!url.startsWith("jdbc:hsqldb:")) {
            url = "jdbc:hsqldb:" + url;
        }

        return JDBCDriver.getConnection(url, props);
    }

    //------------------------- JDBC 4.0 -----------------------------------
    // ------------------- java.sql.Wrapper implementation ---------------------

    /**
     * Returns an object that implements the given interface to allow access to
     * non-standard methods, or standard methods not exposed by the proxy.
     *
     * If the receiver implements the interface then the result is the receiver
     * or a proxy for the receiver. If the receiver is a wrapper
     * and the wrapped object implements the interface then the result is the
     * wrapped object or a proxy for the wrapped object. Otherwise return
     * the result of calling {@code unwrap} recursively on the wrapped object
     * or a proxy for that result. If the receiver is not a
     * wrapper and does not implement the interface, then an {@code SQLException} is thrown.
     *
     * @param iface A Class defining an interface that the result must implement.
     * @return an object that implements the interface. May be a proxy for the actual implementing object.
     * @throws java.sql.SQLException If no object found that implements the interface
     * @since JDK 1.6, HSQLDB 2.0
     */
    @SuppressWarnings("unchecked")
    public <T> T unwrap(java.lang.Class<T> iface) throws java.sql.SQLException {

        if (isWrapperFor(iface)) {
            return (T) this;
        }

        throw JDBCUtil.invalidArgument("iface: " + iface);
    }

    /**
     * Returns true if this either implements the interface argument or is directly or indirectly a wrapper
     * for an object that does. Returns false otherwise. If this implements the interface then return true,
     * else if this is a wrapper then return the result of recursively calling {@code isWrapperFor} on the wrapped
     * object. If this does not implement the interface and is not a wrapper, return false.
     * This method should be implemented as a low-cost operation compared to {@code unwrap} so that
     * callers can use this method to avoid expensive {@code unwrap} calls that may fail. If this method
     * returns true then calling {@code unwrap} with the same argument should succeed.
     *
     * @param iface a Class defining an interface.
     * @return true if this implements the interface or directly or indirectly wraps an object that does.
     * @throws java.sql.SQLException  if an error occurs while determining whether this is a wrapper
     * for an object with the given interface.
     * @since JDK 1.6, HSQLDB 2.0
     */
    public boolean isWrapperFor(
            java.lang.Class<?> iface)
            throws java.sql.SQLException {
        return (iface != null && iface.isAssignableFrom(this.getClass()));
    }

    /**
     * Retrieves the Reference of this object.
     *
     * @return The non-null Reference of this object.
     * @throws NamingException If a naming exception was encountered
     *          while retrieving the reference.
     */
    public Reference getReference() throws NamingException {

        String    cname = "org.hsqldb.jdbc.JDBCDataSourceFactory";
        Reference ref   = new Reference(getClass().getName(), cname, null);

        ref.add(new StringRefAddr("database", getDatabase()));
        ref.add(new StringRefAddr("user", getUser()));
        ref.add(new StringRefAddr("password", password));
        ref.add(
            new StringRefAddr("loginTimeout", Integer.toString(loginTimeout)));

        return ref;
    }

    // ------------------------ custom public methods ------------------------
    public JDBCDataSource() {}
}
