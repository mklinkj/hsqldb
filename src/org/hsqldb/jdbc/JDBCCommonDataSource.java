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

import java.io.PrintWriter;
import java.io.Serializable;

import java.sql.SQLException;

import java.util.Properties;

import java.sql.SQLFeatureNotSupportedException;

import javax.sql.CommonDataSource;

/**
 * Common base for DataSource implementations.
 *
 * This class implements the methods used for setting the properties for new
 * connections.
 *
 * The setUrl() or setDatabase() methods are used to set the URL.
 *
 * It is possible to set all the rest of required properties in a Properties
 * file and use the setProperties() method.
 *
 * Alternatively, the setXXX() methods for user and password can be used.
 *
 * It is best to use only one method for setting the properties.
 *
 * If setXXX() methods are used, the values override the values set in a
 * call made to setProperties() before or after calling setXXX().
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since JDK 1.2, HSQLDB 2.0
 */
public abstract class JDBCCommonDataSource
        implements CommonDataSource, Serializable {

    /**
     * <p>Retrieves the log writer for this {@code DataSource}
     * object.
     *
     * <p>The log writer is a character output stream to which all logging
     * and tracing messages for this data source will be
     * printed.  This includes messages printed by the methods of this
     * object, messages printed by methods of other objects manufactured
     * by this object, and so on.  Messages printed to a data source
     * specific log writer are not printed to the log writer associated
     * with the {@code java.sql.DriverManager} class.  When a
     * {@code DataSource} object is
     * created, the log writer is initially null; in other words, the
     * default is for logging to be disabled.
     *
     * @return the log writer for this data source or null if
     *        logging is disabled
     * @throws java.sql.SQLException if a database access error occurs
     * @see #setLogWriter
     * @since 1.4
     */
    public PrintWriter getLogWriter() throws SQLException {
        return logWriter;
    }

    /**
     * <p>Sets the log writer for this {@code DataSource}
     * object to the given {@code java.io.PrintWriter} object.
     *
     * <p>The log writer is a character output stream to which all logging
     * and tracing messages for this data source will be
     * printed.  This includes messages printed by the methods of this
     * object, messages printed by methods of other objects manufactured
     * by this object, and so on.  Messages printed to a data source-
     * specific log writer are not printed to the log writer associated
     * with the {@code java.sql.DriverManager} class. When a
     * {@code DataSource} object is created the log writer is
     * initially null; in other words, the default is for logging to be
     * disabled.
     *
     * @param out the new log writer; to disable logging, set to null
     * @throws SQLException if a database access error occurs
     * @see #getLogWriter
     * @since 1.4
     */
    public void setLogWriter(java.io.PrintWriter out) throws SQLException {
        logWriter = out;
    }

    /**
     * <p>Sets the maximum time in seconds that this data source will wait
     * while attempting to connect to a database.  A value of zero
     * specifies that the timeout is the default system timeout
     * if there is one; otherwise, it specifies that there is no timeout.
     * When a {@code DataSource} object is created, the login timeout is
     * initially zero.
     *
     * @param seconds the data source login time limit
     * @throws SQLException if a database access error occurs.
     * @see #getLoginTimeout
     * @since 1.4
     */
    public void setLoginTimeout(int seconds) throws SQLException {

        loginTimeout = seconds;

        connectionProps.setProperty(
            "loginTimeout",
            Integer.toString(loginTimeout));
    }

    /**
     * Gets the maximum time in seconds that this data source can wait
     * while attempting to connect to a database.  A value of zero
     * means that the timeout is the default system timeout
     * if there is one; otherwise, it means that there is no timeout.
     * When a {@code DataSource} object is created, the login timeout is
     * initially zero.
     *
     * @return the data source login time limit
     * @throws SQLException if a database access error occurs.
     * @see #setLoginTimeout
     * @since 1.4
     */
    public int getLoginTimeout() throws SQLException {
        return loginTimeout;
    }

    // ------------------------ custom public methods ------------------------

    /**
     * Retrieves the description of the data source.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Retrieves the name of the data source.
     *
     * @return the description
     */
    public String getDataSourceName() {
        return dataSourceName;
    }

    /**
     * Retrieves the network protocol of the data source.
     *
     * @return the network protocol
     */
    public String getNetworkProtocol() {
        return networkProtocol;
    }

    /**
     * Retrieves the server name attribute.
     *
     * @return the server name attribute
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Synonym for getUrl().
     *
     * @return the jdbc database connection url attribute
     */
    public String getDatabaseName() {
        return url;
    }

    /**
     * Synonym for getUrl().
     *
     * @return the jdbc database connection url attribute
     */
    public String getDatabase() {
        return url;
    }

    /**
     * Retrieves the jdbc database connection url attribute.
     *
     * @return the jdbc database connection url attribute
     */
    public String getUrl() {
        return url;
    }

    /**
     * Retrieves the jdbc database connection url attribute.
     *
     * @return the jdbc database connection url attribute
     */
    public String getURL() {
        return url;
    }

    /**
     * Retrieves the user name for the connection.
     *
     * @return the username for the connection
     */
    public String getUser() {
        return user;
    }

    /**
     * Synonym for setUrl(String).
     *
     * @param databaseName the new value for the attribute
     */
    public void setDatabaseName(String databaseName) {
        this.url = databaseName;
    }

    /**
     * Synonym for setUrl(String).
     *
     * @param database the new value for the attribute
     */
    public void setDatabase(String database) {
        this.url = database;
    }

    /**
     * Sets the jdbc database URL.
     *
     * @param url the new value of this object's jdbc database connection
     *      url attribute
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Sets the jdbc database URL.
     *
     * @param url the new value of this object's jdbc database connection
     *      url attribute
     */
    public void setURL(String url) {
        this.url = url;
    }

    /**
     * Sets the password for the username.
     *
     * @param password the password
     */
    public void setPassword(String password) {
        this.password = password;

        connectionProps.setProperty("password", password);
    }

    /**
     * Sets the user name.
     *
     * @param user the user id
     */
    public void setUser(String user) {
        this.user = user;

        connectionProps.setProperty("user", user);
    }

    /**
     * Sets connection properties. If user / password / loginTimeout has been
     * set with one of the setXXX() methods it will be added to the Properties
     * object.
     *
     * @param props properties.  If null, then existing properties will be
     *                           cleared/replaced.
     */
    public void setProperties(Properties props) {

        connectionProps = (props == null)
                          ? new Properties()
                          : (Properties) props.clone();

        if (user != null) {
            connectionProps.setProperty("user", user);
        }

        if (password != null) {
            connectionProps.setProperty("password", password);
        }

        if (loginTimeout != 0) {
            connectionProps.setProperty(
                "loginTimeout",
                Integer.toString(loginTimeout));
        }
    }

    //------------------------- JDBC 4.1 -----------------------------------

    /**
     * Return the parent Logger of all the Loggers used by this data source. This
     * should be the Logger farthest from the root Logger that is
     * still an ancestor of all of the Loggers used by this data source. Configuring
     * this Logger will affect all of the log messages generated by the data source.
     * In the worst case, this may be the root Logger.
     *
     * @return the parent Logger for this data source
     * @throws SQLFeatureNotSupportedException if the data source does not use {@code java.util.logging}.
     * @since JDK 1.7, HSQLDB 2.0.1
     */
    public java.util.logging.Logger getParentLogger()
            throws java.sql.SQLFeatureNotSupportedException {
        throw(java.sql.SQLFeatureNotSupportedException) JDBCUtil.notSupported();
    }

    // ------------------------ internal implementation ------------------------
    protected Properties connectionProps = new Properties();

    /** description of data source - informational */
    protected String description = null;

    /** name of data source - informational */
    protected String dataSourceName = null;

    /** name of server - informational */
    protected String serverName = null;

    /** network protocol - informational */
    protected String networkProtocol = null;

    /** login timeout */
    protected int loginTimeout = 0;

    /** log writer */
    protected transient PrintWriter logWriter;

    /** connection user */
    protected String user = null;

    /** connection password */
    protected String password = null;

    /** database URL */
    protected String url = null;
}
