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

import org.hsqldb.HsqlNameManager.HsqlName;
import org.hsqldb.lib.Iterator;
import org.hsqldb.lib.LongKeyHashMap;
import org.hsqldb.rights.User;

import java.util.concurrent.atomic.AtomicLong;
import java.util.TimeZone;

/**
 * Container that maintains a map of session id's to Session objects.
 * Responsible for managing opening and closing of sessions.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 1.7.2
 */
public class SessionManager {

    AtomicLong                            sessionIdCount = new AtomicLong();
    private final LongKeyHashMap<Session> sessionMap = new LongKeyHashMap<>();
    private final Session                 sysSession;
    private final Session                 sysLobSession;

    /*
     * @todo:
     * Eliminate the Database-centric nature of SessionManager.
     * e.g. Sessions should be able to migrate from one Database instance
     * to another using session control language moderated by SessionManager
     */

    /**
     * Constructs a new SessionManager handling the specified Database.
     * Creates a SYS User.
     */
    public SessionManager(Database db) {

        User sysUser = db.getUserManager().getSysUser();

        sysSession = new Session(
            db,
            sysUser,
            false,
            false,
            sessionIdCount.getAndIncrement(),
            TimeZone.getTimeZone("GMT"));
        sysLobSession = new Session(
            db,
            sysUser,
            true,
            false,
            sessionIdCount.getAndIncrement(),
            TimeZone.getTimeZone("GMT"));
    }

    /*
     *  @todo:
     * It should be possible to create an initially 'disconnected' Session that
     * can execute general commands using a SessionCommandInterpreter.
     *
     * EXAMPLES: Open a Session to start a Server, add/remove
     *          databases hosted by an existing Server, connect to a
     *          Database...
     *
     * REQUIRES:  auth scheme independent of any particular Database instance
     *           e.g. provide service to use /etc/passwd and /etc/groups,
     *                JAAS-plugin, etc.
     */

    /**
     * Binds the specified Session object into this SessionManager's active
     * Session registry. This method is typically called internally as
     * the final step, when a successful connection has been made.
     *
     * @param db the database to which the new Session is initially connected
     * @param user the Session User
     * @param readonly the ReadOnly attribute for the new Session
     * @param zone the session time zone
     * @return Session
     */
    public synchronized Session newSession(
            Database db,
            User user,
            boolean readonly,
            boolean autoCommit,
            TimeZone zone) {

        long    sessionId = sessionIdCount.getAndIncrement();
        Session s = new Session(
            db,
            user,
            autoCommit,
            readonly,
            sessionId,
            zone);

        sessionMap.put(sessionId, s);

        return s;
    }

    public synchronized Session newSessionForLog(Database db) {

        long sessionId = sessionIdCount.getAndIncrement();
        Session s = new Session(
            db,
            db.getUserManager().getSysUser(),
            false,
            false,
            sessionId,
            TimeZone.getTimeZone("GMT"));

        s.isProcessingLog = true;

        sessionMap.put(sessionId, s);

        return s;
    }

    /**
     * Retrieves a new SYS Session.
     */
    public Session getSysSessionForScript(Database db) {

        Session session = new Session(
            db,
            db.getUserManager().getSysUser(),
            false,
            false,
            0,
            TimeZone.getTimeZone("GMT"));

        // some old 1.8.0 do not have SET SCHEMA PUBLIC
        session.setCurrentSchemaHsqlName(
            db.schemaManager.defaultSchemaHsqlName);

        session.isProcessingScript = true;

        return session;
    }

    public Session getSysLobSession() {
        return sysLobSession;
    }

    /**
     * Retrieves the common SYS Session.
     */
    public Session getSysSession() {

        sysSession.currentSchema =
            sysSession.database.schemaManager.getDefaultSchemaHsqlName();
        sysSession.isProcessingScript = false;
        sysSession.isProcessingLog    = false;

        sysSession.setUser(sysSession.database.getUserManager().getSysUser());

        return sysSession;
    }

    /**
     * Retrieves a transient transaction session.
     */
    synchronized public Session newSysSession() {

        long sessionId = sessionIdCount.getAndIncrement();
        Session session = new Session(
            sysSession.database,
            sysSession.getUser(),
            false,
            false,
            sessionId,
            TimeZone.getTimeZone("GMT"));

        session.currentSchema =
            sysSession.database.schemaManager.getDefaultSchemaHsqlName();

        sessionMap.put(sessionId, session);

        return session;
    }

    synchronized public Session newSysSession(HsqlName schema, User user) {

        long sessionId = sessionIdCount.getAndIncrement();
        Session session = new Session(
            sysSession.database,
            user,
            false,
            false,
            sessionId,
            TimeZone.getTimeZone("GMT"));

        session.currentSchema = schema;

        return session;
    }

    /**
     * Closes all Sessions registered with this SessionManager.
     */
    public void closeAllSessions() {

        // don't disconnect system user; need it to save database
        Session[] sessions = getAllSessions();

        for (int i = 0; i < sessions.length; i++) {
            sessions[i].close();
        }

        synchronized (this) {
            sessionMap.clear();
        }
    }

    /**
     *  Removes the session from management and disconnects.
     */
    synchronized void removeSession(Session session) {
        sessionMap.remove(session.getId());
    }

    /**
     * Closes all sessions and system
     */
    synchronized void close() {
        closeAllSessions();
        sysSession.close();
        sysLobSession.close();
    }

    /**
     * Returns true if no session exists beyond the sys session.
     */
    synchronized boolean isEmpty() {
        return sessionMap.isEmpty();
    }

    /**
     * Retrieves a list of the Sessions in this container that
     * are visible to the specified Session, given the access rights of
     * the Session User.
     */
    public synchronized Session[] getVisibleSessions(Session session) {
        return session.isAdmin()
               ? getAllSessions()
               : new Session[]{ session };
    }

    /**
     * Retrieves the Session with the specified Session identifier or null
     * if no such Session is registered with this SessionManager.
     */
    synchronized Session getSession(long id) {
        return sessionMap.get(id);
    }

    public synchronized Session[] getAllSessions() {

        Session[]         sessions = new Session[sessionMap.size()];
        Iterator<Session> it       = sessionMap.values().iterator();

        for (int i = 0; it.hasNext(); i++) {
            sessions[i] = it.next();
        }

        return sessions;
    }

    public synchronized boolean isUserActive(String userName) {

        Iterator<Session> it = sessionMap.values().iterator();

        for (int i = 0; it.hasNext(); i++) {
            Session session = it.next();

            if (!session.isClosed()
                    && userName.equals(
                        session.getUser().getName().getNameString())) {
                return true;
            }
        }

        return false;
    }

    public synchronized void removeSchemaReference(HsqlName schemaName) {

        Iterator<Session> it = sessionMap.values().iterator();

        for (int i = 0; it.hasNext(); i++) {
            Session session = it.next();

            if (session.getCurrentSchemaHsqlName() == schemaName) {
                session.resetSchema();
            }
        }
    }

    public synchronized void resetLoggedSchemas() {

        Iterator<Session> it = sessionMap.values().iterator();

        for (int i = 0; it.hasNext(); i++) {
            Session session = it.next();

            session.loggedSchema = null;
        }

        this.sysLobSession.loggedSchema = null;
    }

    public synchronized long resetNewLobIDs() {

        Iterator<Session> it          = sessionMap.values().iterator();
        long              newLobFloor = Long.MAX_VALUE;

        for (int i = 0; it.hasNext(); i++) {
            Session session      = it.next();
            long    currentFloor = session.sessionData.newLobFloor;

            if (currentFloor != SessionData.noLobFloor
                    && currentFloor < newLobFloor) {
                newLobFloor = currentFloor;
            }

            session.sessionData.newLobFloor = SessionData.noLobFloor;
        }

        return newLobFloor;
    }
}
