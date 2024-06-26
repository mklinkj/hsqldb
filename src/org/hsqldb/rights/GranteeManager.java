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


package org.hsqldb.rights;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.hsqldb.Database;
import org.hsqldb.HsqlNameManager;
import org.hsqldb.HsqlNameManager.HsqlName;
import org.hsqldb.Routine;
import org.hsqldb.RoutineSchema;
import org.hsqldb.SchemaObject;
import org.hsqldb.Session;
import org.hsqldb.SqlInvariants;
import org.hsqldb.Tokens;
import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.Collection;
import org.hsqldb.lib.HsqlArrayList;
import org.hsqldb.lib.IntValueHashMap;
import org.hsqldb.lib.Iterator;
import org.hsqldb.lib.List;
import org.hsqldb.lib.OrderedHashMap;
import org.hsqldb.lib.OrderedHashSet;
import org.hsqldb.lib.Set;
import org.hsqldb.lib.StringConverter;
import org.hsqldb.lib.java.JavaSystem;

/**
 * Contains a set of Grantee objects, and supports operations for creating,
 * finding, modifying and deleting Grantee objects for a Database; plus
 * Administrative privileges.
 *
 *
 * @author Campbell Burnet (campbell-burnet@users dot sourceforge.net)
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @author Blaine Simpson (blaine dot simpson at admc dot com)
 *
 * @version 2.7.3
 * @since 1.8.0
 * @see Grantee
 */
public class GranteeManager {

    /**
     * The grantee object for the _SYSTEM role.
     */
    static User systemAuthorisation;

    static {
        HsqlName name = HsqlNameManager.newSystemObjectName(
            SqlInvariants.SYSTEM_AUTHORIZATION_NAME,
            SchemaObject.GRANTEE);

        systemAuthorisation          = new User(name, null);
        systemAuthorisation.isSystem = true;

        systemAuthorisation.setAdminDirect();
        systemAuthorisation.setInitialSchema(
            SqlInvariants.SYSTEM_SCHEMA_HSQLNAME);

        SqlInvariants.INFORMATION_SCHEMA_HSQLNAME.owner = systemAuthorisation;
        SqlInvariants.SESSION_SCHEMA_HSQLNAME.owner     = systemAuthorisation;
        SqlInvariants.SYSTEM_SCHEMA_HSQLNAME.owner      = systemAuthorisation;
        SqlInvariants.LOBS_SCHEMA_HSQLNAME.owner        = systemAuthorisation;
        SqlInvariants.SQLJ_SCHEMA_HSQLNAME.owner        = systemAuthorisation;
    }

    /**
     * Map of grantee-String-to-Grantee-objects.<p>
     * Keys include all USER and ROLE names
     */
    private OrderedHashMap<String, Grantee> map = new OrderedHashMap<>();

    /**
     * Map of role-Strings-to-Grantee-object.<p>
     * Keys include all ROLES names
     */
    private OrderedHashMap<String, Grantee> roleMap = new OrderedHashMap<>();

    /**
     * Used only to pass the SchemaManager to Grantees for checking
     * schema authorizations.
     */
    Database database;

    /**
     * MessageDigest instance for database
     */
    private MessageDigest digester;

    /**
     * MessageDigest algorithm
     */
    private String digestAlgo;

    /**
     * The PUBLIC role.
     */
    Grantee publicRole;

    /**
     * The DBA role.
     */
    Grantee dbaRole;

    /**
     * The role for schema creation rights.
     */
    Grantee schemaRole;

    /**
     * The role for changing authorization rights.
     */
    Grantee changeAuthRole;

    /**
     * The role for script operations rights.
     */
    Grantee scriptOpsRole;

    /**
     * Construct the GranteeManager for a Database. Construct special Grantee
     * objects for _SYSTEM, PUBLIC and DBA, and add them to the Grantee map.
     *
     * @param database Only needed to link to the RoleManager later on.
     */
    public GranteeManager(Database database) {

        this.database = database;

//        map.add(systemAuthorisation.getNameString(), systemAuthorisation);
//        roleMap.add(systemAuthorisation.getNameString(), systemAuthorisation);
        addRole(
            this.database.nameManager.newHsqlName(
                SqlInvariants.PUBLIC_ROLE_NAME,
                false,
                SchemaObject.GRANTEE));

        publicRole          = getRole(SqlInvariants.PUBLIC_ROLE_NAME);
        publicRole.isPublic = true;

        addRole(
            this.database.nameManager.newHsqlName(
                SqlInvariants.DBA_ADMIN_ROLE_NAME,
                false,
                SchemaObject.GRANTEE));

        dbaRole = getRole(SqlInvariants.DBA_ADMIN_ROLE_NAME);

        dbaRole.setAdminDirect();
        addRole(
            this.database.nameManager.newHsqlName(
                SqlInvariants.SCHEMA_CREATE_ROLE_NAME,
                false,
                SchemaObject.GRANTEE));

        schemaRole = getRole(SqlInvariants.SCHEMA_CREATE_ROLE_NAME);

        addRole(
            this.database.nameManager.newHsqlName(
                SqlInvariants.CHANGE_AUTH_ROLE_NAME,
                false,
                SchemaObject.GRANTEE));

        changeAuthRole = getRole(SqlInvariants.CHANGE_AUTH_ROLE_NAME);

        addRole(
            this.database.nameManager.newHsqlName(
                SqlInvariants.SCRIPT_OPS_ROLE_NAME,
                false,
                SchemaObject.GRANTEE));

        scriptOpsRole = getRole(SqlInvariants.SCRIPT_OPS_ROLE_NAME);
    }

    static final IntValueHashMap<String> rightsStringLookup =
        new IntValueHashMap<>(
            11);

    static {
        rightsStringLookup.put(Tokens.T_ALL, GrantConstants.ALL);
        rightsStringLookup.put(Tokens.T_SELECT, GrantConstants.SELECT);
        rightsStringLookup.put(Tokens.T_UPDATE, GrantConstants.UPDATE);
        rightsStringLookup.put(Tokens.T_DELETE, GrantConstants.DELETE);
        rightsStringLookup.put(Tokens.T_INSERT, GrantConstants.INSERT);
        rightsStringLookup.put(Tokens.T_EXECUTE, GrantConstants.EXECUTE);
        rightsStringLookup.put(Tokens.T_USAGE, GrantConstants.USAGE);
        rightsStringLookup.put(Tokens.T_REFERENCES, GrantConstants.REFERENCES);
        rightsStringLookup.put(Tokens.T_TRIGGER, GrantConstants.TRIGGER);
    }

    public Grantee getDBARole() {
        return dbaRole;
    }

    public static Grantee getSystemRole() {
        return systemAuthorisation;
    }

    /**
     * Grants the rights represented by the Right object on dbObject to
     * the Grantee objects in granteeList.
     */
    public void grant(
            Session session,
            OrderedHashSet<String> granteeList,
            SchemaObject dbObject,
            Right right,
            Grantee grantor,
            boolean withGrantOption) {

        if (dbObject instanceof RoutineSchema) {
            SchemaObject[] routines =
                ((RoutineSchema) dbObject).getSpecificRoutines();

            grant(
                session,
                granteeList,
                routines,
                right,
                grantor,
                withGrantOption);

            return;
        }

        HsqlName name = dbObject.getName();

        if (dbObject instanceof Routine) {
            name = ((Routine) dbObject).getSpecificName();
        }

        if (!grantor.isAccessible(dbObject)) {
            throw Error.error(
                ErrorCode.X_0L000,
                grantor.getName().getNameString());
        }

        if (!grantor.isGrantable(dbObject, right)) {
            session.addWarning(
                Error.error(ErrorCode.W_01007,
                            grantor.getName().getNameString()));

            return;
        }

        if (grantor.isAdmin()) {
            grantor = dbObject.getOwner();
        }

        checkGranteeList(granteeList);

        for (int i = 0; i < granteeList.size(); i++) {
            Grantee grantee = get(granteeList.get(i));

            if (!grantee.isRole) {
                if (right.hasFilter()) {
                    throw Error.error(
                        ErrorCode.X_0P000,
                        grantee.getName().name);
                }
            }

            grantee.grant(name, right, grantor, withGrantOption);

            if (grantee.isRole) {
                updateAllRights(grantee);
            }
        }
    }

    public void grant(
            Session session,
            OrderedHashSet<String> granteeList,
            SchemaObject[] routines,
            Right right,
            Grantee grantor,
            boolean withGrantOption) {

        boolean granted = false;

        for (int i = 0; i < routines.length; i++) {
            if (!grantor.isGrantable(routines[i], right)) {
                continue;
            }

            grant(
                session,
                granteeList,
                routines[i],
                right,
                grantor,
                withGrantOption);

            granted = true;
        }

        if (!granted) {
            throw Error.error(
                ErrorCode.X_0L000,
                grantor.getName().getNameString());
        }
    }

    public void checkGranteeList(OrderedHashSet<String> granteeList) {

        for (int i = 0; i < granteeList.size(); i++) {
            String  name    = granteeList.get(i);
            Grantee grantee = get(name);

            if (grantee == null) {
                throw Error.error(ErrorCode.X_28501, name);
            }

            if (isImmutable(name)) {
                throw Error.error(ErrorCode.X_28502, name);
            }

            if (grantee instanceof User && ((User) grantee).isExternalOnly) {
                throw Error.error(ErrorCode.X_28000, name);
            }
        }
    }

    /**
     * Grant a role to this Grantee.
     */
    public void grant(String granteeName, String roleName, Grantee grantor) {

        Grantee grantee = get(granteeName);

        if (grantee == null) {
            throw Error.error(ErrorCode.X_28501, granteeName);
        }

        if (isImmutable(granteeName)) {
            throw Error.error(ErrorCode.X_28502, granteeName);
        }

        Grantee role = getRole(roleName);

        if (role == null) {
            throw Error.error(ErrorCode.X_0P000, roleName);
        }

        if (role == grantee) {
            throw Error.error(ErrorCode.X_0P501, granteeName);
        }

        // campbell-burnet@users 20050515
        // SQL 2003 Foundation, 4.34.3
        // No cycles of role grants are allowed.
        if (role.hasRole(grantee)) {

            // campbell-burnet@users

            /* @todo: Correct reporting of actual grant path */
            throw Error.error(ErrorCode.X_0P501, roleName);
        }

        if (!grantor.isGrantable(role)) {
            throw Error.error(
                ErrorCode.X_0L000,
                grantor.getName().getNameString());
        }

        grantee.grant(role);
        grantee.updateAllRights();

        if (grantee.isRole) {
            updateAllRights(grantee);
        }
    }

    public void checkRoleList(
            String granteeName,
            OrderedHashSet<String> roleList,
            Grantee grantor,
            boolean grant) {

        Grantee grantee = get(granteeName);

        for (int i = 0; i < roleList.size(); i++) {
            String  roleName = roleList.get(i);
            Grantee role     = getRole(roleName);

            if (role == null) {
                throw Error.error(ErrorCode.X_0P000, roleName);
            }

            if (roleName.equals(SqlInvariants.SYSTEM_AUTHORIZATION_NAME)
                    || roleName.equals(SqlInvariants.PUBLIC_ROLE_NAME)) {
                throw Error.error(ErrorCode.X_28502, roleName);
            }

            if (grant) {
                if (grantee.getDirectRoles().contains(role)) {

                    /* no-op */
                }
            } else {
                if (!grantee.getDirectRoles().contains(role)) {

                    /* no-op */
                }
            }

            if (!grantor.isAdmin()) {
                throw Error.error(
                    ErrorCode.X_0L000,
                    grantor.getName().getNameString());
            }
        }
    }

    public void grantSystemToPublic(SchemaObject object, Right right) {
        publicRole.grant(object.getName(), right, systemAuthorisation, true);
    }

    /**
     * Revoke a role from a Grantee
     */
    public void revoke(String granteeName, String roleName, Grantee grantor) {

        if (!grantor.isAdmin()) {
            throw Error.error(ErrorCode.X_42507);
        }

        Grantee grantee = get(granteeName);

        if (grantee == null) {
            throw Error.error(ErrorCode.X_28000, granteeName);
        }

        Grantee role = roleMap.get(roleName);

        grantee.revoke(role);
        grantee.updateAllRights();

        if (grantee.isRole) {
            updateAllRights(grantee);
        }
    }

    /**
     * Revokes the rights represented by the Right object on dbObject from
     * the Grantee objects in granteeList.<p>
     * @see #grant
     */
    public void revoke(
            OrderedHashSet<String> granteeList,
            SchemaObject dbObject,
            Right rights,
            Grantee grantor,
            boolean grantOption,
            boolean cascade) {

        if (dbObject instanceof RoutineSchema) {
            SchemaObject[] routines =
                ((RoutineSchema) dbObject).getSpecificRoutines();

            revoke(
                granteeList,
                routines,
                rights,
                grantor,
                grantOption,
                cascade);

            return;
        }

        HsqlName name = dbObject.getName();

        if (dbObject instanceof Routine) {
            name = ((Routine) dbObject).getSpecificName();
        }

        if (!grantor.isFullyAccessibleByRole(name)) {
            throw Error.error(ErrorCode.X_42501, dbObject.getName().name);
        }

        if (grantor.isAdmin()) {
            grantor = dbObject.getOwner();
        }

        checkGranteeList(granteeList);

        for (int i = 0; i < granteeList.size(); i++) {
            String  granteeName = granteeList.get(i);
            Grantee g           = get(granteeName);

            g.revoke(dbObject, rights, grantor, grantOption);
            g.updateAllRights();

            if (g.isRole) {
                updateAllRights(g);
            }
        }
    }

    public void revoke(
            OrderedHashSet<String> granteeList,
            SchemaObject[] routines,
            Right rights,
            Grantee grantor,
            boolean grantOption,
            boolean cascade) {

        for (int i = 0; i < routines.length; i++) {
            revoke(
                granteeList,
                routines[i],
                rights,
                grantor,
                grantOption,
                cascade);
        }
    }

    /**
     * Removes a role without any privileges from all grantees
     */
    void removeEmptyRole(Grantee role) {

        for (int i = 0; i < map.size(); i++) {
            Grantee grantee = map.get(i);

            grantee.roles.remove(role);
        }
    }

    /**
     * Removes all rights mappings for the database object identified by
     * the name argument from all Grantee objects in the database.
     */
    public void removeDbObject(HsqlName name) {

        for (int i = 0; i < map.size(); i++) {
            Grantee g = map.get(i);

            g.revokeDbObject(name);
        }
    }

    public void removeDbObjects(OrderedHashSet<HsqlName> nameSet) {

        Iterator<HsqlName> it = nameSet.iterator();

        while (it.hasNext()) {
            HsqlName name = it.next();

            for (int i = 0; i < map.size(); i++) {
                Grantee g = map.get(i);

                g.revokeDbObject(name);
            }
        }
    }

    /**
     * First updates all ROLE Grantee objects. Then updates all USER Grantee
     * Objects.
     */
    void updateAllRights(Grantee role) {

        for (int i = 0; i < map.size(); i++) {
            Grantee grantee = map.get(i);

            if (grantee.isRole) {
                grantee.updateNestedRoles(role);
            }
        }

        for (int i = 0; i < map.size(); i++) {
            Grantee grantee = map.get(i);

            if (!grantee.isRole) {
                grantee.updateAllRights();
            }
        }
    }

    /**
     */
    public boolean removeGrantee(String name) {

        /*
         * Explicitly can't remove PUBLIC_USER_NAME and system grantees.
         */
        if (isReserved(name)) {
            return false;
        }

        Grantee g = map.remove(name);

        if (g == null) {
            return false;
        }

        g.clearPrivileges();
        updateAllRights(g);

        if (g.isRole) {
            roleMap.remove(name);
            removeEmptyRole(g);
        }

        return true;
    }

    /**
     * Creates a new Role object. <p>
     *
     *  A set of constraints regarding user creation is imposed:
     *
     *  <OL>
     *    <LI>Can't create a role with name same as any right.
     *
     *    <LI>If this object's collection already contains an element whose
     *        name attribute equals the name argument, then
     *        a GRANTEE_ALREADY_EXISTS or ROLE_ALREADY_EXISTS Trace
     *        is thrown.
     *        (This will catch attempts to create Reserved grantee names).
     *  </OL>
     */
    public Grantee addRole(HsqlName name) {

        if (map.containsKey(name.name)) {
            throw Error.error(ErrorCode.X_28503, name.name);
        }

        if (SqlInvariants.SYSTEM_AUTHORIZATION_NAME.equals(name.name)) {
            throw Error.error(ErrorCode.X_28502, name.name);
        }

        if (SqlInvariants.isLobsSchemaName(name.name)
                || SqlInvariants.isSystemSchemaName(name.name)) {
            throw Error.error(ErrorCode.X_28502, name.name);
        }

        Grantee g = new Grantee(name, this);

        g.isRole = true;

        map.put(name.name, g);
        roleMap.add(name.name, g);

        return g;
    }

    public User addUser(HsqlName name) {

        if (map.containsKey(name.name)) {
            throw Error.error(ErrorCode.X_28503, name.name);
        }

        if (SqlInvariants.SYSTEM_AUTHORIZATION_NAME.equals(name.name)) {
            throw Error.error(ErrorCode.X_28502, name.name);
        }

        if (SqlInvariants.isLobsSchemaName(name.name)
                || SqlInvariants.isSystemSchemaName(name.name)) {
            throw Error.error(ErrorCode.X_28502, name.name);
        }

        User g = new User(name, this);

        map.put(name.name, g);

        return g;
    }

    /**
     * Only used for a recently added user with no dependencies
     */
    public void removeNewUser(HsqlName name) {
        map.remove(name.name);
    }

    /**
     * Returns true if named Grantee object exists.
     * This will return true for reserved Grantees
     * SYSTEM_AUTHORIZATION_NAME, ADMIN_ROLE_NAME, PUBLIC_USER_NAME.
     */
    boolean isGrantee(String name) {
        return map.containsKey(name);
    }

    public static int getCheckSingleRight(String token) {

        int r = getRight(token);

        if (r != 0) {
            return r;
        }

        throw Error.error(ErrorCode.X_42581, token);
    }

    /**
     * Translate a string representation or token(s) into its numeric form.
     */
    public static int getRight(String token) {
        return rightsStringLookup.get(token, 0);
    }

    public Grantee get(String name) {
        return map.get(name);
    }

    public Collection<Grantee> getGrantees() {
        return map.values();
    }

    public static boolean validRightString(String rightString) {
        return getRight(rightString) != 0;
    }

    public static boolean isImmutable(String name) {

        return name.equals(SqlInvariants.SYSTEM_AUTHORIZATION_NAME)
               || name.equals(SqlInvariants.DBA_ADMIN_ROLE_NAME)
               || name.equals(SqlInvariants.SCHEMA_CREATE_ROLE_NAME)
               || name.equals(SqlInvariants.CHANGE_AUTH_ROLE_NAME)
               || name.equals(SqlInvariants.SCRIPT_OPS_ROLE_NAME);
    }

    public static boolean isReserved(String name) {

        return name.equals(SqlInvariants.SYSTEM_AUTHORIZATION_NAME)
               || name.equals(SqlInvariants.DBA_ADMIN_ROLE_NAME)
               || name.equals(SqlInvariants.SCHEMA_CREATE_ROLE_NAME)
               || name.equals(SqlInvariants.CHANGE_AUTH_ROLE_NAME)
               || name.equals(SqlInvariants.SCRIPT_OPS_ROLE_NAME)
               || name.equals(SqlInvariants.PUBLIC_ROLE_NAME);
    }

    /**
     * Attempts to drop a Role with the specified name
     *  from this object's set. <p>
     *
     *  A successful drop action consists of:
     *
     *  <UL>
     *
     *    <LI>removing the Grantee object with the specified name
     *        from the set.
     *  </UL>
     *
     */
    public void dropRole(String name) {

        if (!isRole(name)) {
            throw Error.error(ErrorCode.X_0P000, name);
        }

        if (GranteeManager.isReserved(name)) {
            throw Error.error(ErrorCode.X_42507);
        }

        removeGrantee(name);
    }

    public Set<String> getRoleNames() {
        return roleMap.keySet();
    }

    public Collection<Grantee> getRoles() {
        return roleMap.values();
    }

    /**
     * Returns Grantee for the named Role
     */
    public Grantee getRole(String name) {

        Grantee g = roleMap.get(name);

        if (g == null) {
            throw Error.error(ErrorCode.X_0P000, name);
        }

        return g;
    }

    public boolean isRole(String name) {
        return roleMap.containsKey(name);
    }

    public List<String> getSQLArray() {

        HsqlArrayList<String> list = new HsqlArrayList<>();

        // roles
        Iterator<Grantee> it = getRoles().iterator();

        while (it.hasNext()) {
            Grantee grantee = it.next();

            // built-in role names are not persisted
            if (!GranteeManager.isReserved(grantee.getName().getNameString())) {
                list.add(grantee.getSQL());
            }
        }

        // users
        it = getGrantees().iterator();

        while (it.hasNext()) {
            Grantee grantee = it.next();

            if (grantee instanceof User) {
                if (((User) grantee).isExternalOnly) {
                    continue;
                }

                list.add(grantee.getSQL());

                if (((User) grantee).isLocalOnly) {
                    list.add(((User) grantee).getLocalUserSQL());
                }
            }
        }

        return list;
    }

    public List<String> getRightsSQLArray() {

        HsqlArrayList<String> list     = new HsqlArrayList<>();
        Iterator<Grantee>     grantees = getGrantees().iterator();

        while (grantees.hasNext()) {
            Grantee grantee = grantees.next();
            String  name    = grantee.getName().getNameString();

            // _SYSTEM user, DBA Role grants not persisted
            if (GranteeManager.isImmutable(name)) {
                continue;
            }

            if (grantee instanceof User && ((User) grantee).isExternalOnly) {
                continue;
            }

            HsqlArrayList<String> subList = grantee.getRightsSQL();

            list.addAll(subList);
        }

        return list;
    }

    public void setDigestAlgo(String algo) {
        digestAlgo = algo;
    }

    public String getDigestAlgo() {
        return digestAlgo;
    }

    synchronized MessageDigest getDigester() {

        if (digester == null) {
            try {
                digester = MessageDigest.getInstance(digestAlgo);
            } catch (NoSuchAlgorithmException e) {
                throw Error.error(ErrorCode.GENERAL_ERROR, e);
            }
        }

        return digester;
    }

    String digest(String string) throws RuntimeException {

        byte[] data;

        data = string.getBytes(JavaSystem.CS_ISO_8859_1);
        data = getDigester().digest(data);

        return StringConverter.byteArrayToHexString(data);
    }
}
