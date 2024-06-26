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

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.hsqldb.error.Error;
import org.hsqldb.error.ErrorCode;
import org.hsqldb.lib.ArrayUtil;
import org.hsqldb.lib.FileAccess;
import org.hsqldb.lib.FileUtil;
import org.hsqldb.map.ValuePool;

/**
 * Wrapper for java.util.Properties to limit values to Specific types and
 * allow saving and loading.<p>
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.7.3
 * @since 1.7.0
 */
public class HsqlProperties {

    //
    public static final int ANY_ERROR        = 0;
    public static final int NO_VALUE_FOR_KEY = 1;
    protected String        fileName;
    protected String        fileExtension = "";
    protected Properties    stringProps;
    protected int[]         errorCodes = ValuePool.emptyIntArray;
    protected String[]      errorKeys  = ValuePool.emptyStringArray;
    protected FileAccess    fa;

    public HsqlProperties() {
        stringProps = new Properties();
        fileName    = null;
    }

    public HsqlProperties(String fileName) {
        this(fileName, ".properties");
    }

    public HsqlProperties(String fileName, String fileExtension) {

        stringProps        = new Properties();
        this.fileName      = fileName;
        this.fileExtension = fileExtension;
        fa                 = FileUtil.getFileUtil();
    }

    public HsqlProperties(String fileName, FileAccess accessor, boolean b) {

        stringProps        = new Properties();
        this.fileName      = fileName;
        this.fileExtension = ".properties";
        fa                 = accessor;
    }

    public HsqlProperties(Properties props) {
        stringProps = props;
    }

    public void setFileName(String name) {
        fileName = name;
    }

    public String setProperty(String key, int value) {
        return setProperty(key, Integer.toString(value));
    }

    public String setProperty(String key, boolean value) {
        return setProperty(key, String.valueOf(value));
    }

    public String setProperty(String key, String value) {
        return (String) stringProps.put(key, value);
    }

    public String setPropertyIfNotExists(String key, String value) {
        value = getProperty(key, value);

        return setProperty(key, value);
    }

    public Properties getProperties() {
        return stringProps;
    }

    public String getProperty(String key) {
        return stringProps.getProperty(key);
    }

    public String getProperty(String key, String defaultValue) {
        return stringProps.getProperty(key, defaultValue);
    }

    public int getIntegerProperty(String key, int defaultValue) {
        return getIntegerProperty(stringProps, key, defaultValue);
    }

    public static int getIntegerProperty(
            Properties props,
            String key,
            int defaultValue) {

        String prop = props.getProperty(key);

        try {
            if (prop != null) {
                prop         = prop.trim();
                defaultValue = Integer.parseInt(prop);
            }
        } catch (NumberFormatException e) {}

        return defaultValue;
    }

    public boolean isPropertyTrue(String key) {
        return isPropertyTrue(key, false);
    }

    public boolean isPropertyTrue(String key, boolean defaultValue) {

        String value = stringProps.getProperty(key);

        if (value == null) {
            return defaultValue;
        }

        value = value.trim();

        return value.equalsIgnoreCase("true");
    }

    public void removeProperty(String key) {
        stringProps.remove(key);
    }

    public void addProperties(Properties props) {

        if (props == null) {
            return;
        }

        Enumeration keys = props.propertyNames();

        while (keys.hasMoreElements()) {
            String key   = (String) keys.nextElement();
            String value = props.getProperty(key);

            this.stringProps.put(key, value);
        }
    }

    public void addProperties(HsqlProperties props) {

        if (props == null) {
            return;
        }

        addProperties(props.stringProps);
    }

// oj@openoffice.org
    public boolean propertiesFileExists() {

        if (fileName == null) {
            return false;
        }

        String propFilename = fileName + fileExtension;

        return fa.isStreamElement(propFilename);
    }

    public boolean load() throws Exception {

        if (fileName == null || fileName.isEmpty()) {
            throw new FileNotFoundException(
                Error.getMessage(ErrorCode.M_HsqlProperties_load));
        }

        if (!propertiesFileExists()) {
            return false;
        }

        InputStream fis           = null;
        String      propsFilename = fileName + fileExtension;

// oj@openoffice.org
        try {
            fis = fa.openInputStreamElement(propsFilename);

            stringProps.load(fis);
        } finally {
            if (fis != null) {
                fis.close();
            }
        }

        return true;
    }

    /**
     *  Saves the properties.
     */
    public void save() throws Exception {

        if (fileName == null || fileName.isEmpty()) {
            throw new java.io.FileNotFoundException(
                Error.getMessage(ErrorCode.M_HsqlProperties_load));
        }

        String filestring = fileName + fileExtension;

        save(filestring);
    }

    /**
     *  Saves the properties
     */
    public void save(String fileString) throws Exception {

// oj@openoffice.org
        fa.createParentDirs(fileString);
        fa.removeElement(fileString);

        OutputStream        fos = fa.openOutputStreamElement(fileString);
        FileAccess.FileSync outDescriptor = fa.getFileSync(fos);
        String name = HsqlDatabaseProperties.PRODUCT_NAME + " "
                      + HsqlDatabaseProperties.THIS_FULL_VERSION;

        stringProps.store(fos, name);
        fos.flush();
        outDescriptor.sync();
        fos.close();

        outDescriptor = null;
        fos           = null;
    }

    /**
     * Adds the error code and the key to the list of errors. This list
     * is populated during construction or addition of elements and is used
     * outside this class to act upon the errors.
     */
    protected void addError(int code, String key) {

        errorCodes = (int[]) ArrayUtil.resizeArray(
            errorCodes,
            errorCodes.length + 1);
        errorKeys = (String[]) ArrayUtil.resizeArray(
            errorKeys,
            errorKeys.length + 1);
        errorCodes[errorCodes.length - 1] = code;
        errorKeys[errorKeys.length - 1]   = key;
    }

    /**
     * Creates and populates an HsqlProperties Object from the arguments
     * array of a Main method. Properties are in the form of "-key value"
     * pairs. Each key is prefixed with the type argument and a dot before
     * being inserted into the properties Object. <p>
     *
     * "--help" is treated as a key with no value and not inserted.
     */
    public static HsqlProperties argArrayToProps(String[] arg, String type) {

        HsqlProperties props = new HsqlProperties();

        for (int i = 0; i < arg.length; i++) {
            String p = arg[i];

            if (p.equals("--help") || p.equals("-help")) {
                props.addError(NO_VALUE_FOR_KEY, p.substring(1));
            } else if (p.startsWith("--")) {
                String value = i + 1 < arg.length
                               ? arg[i + 1]
                               : "";

                props.setProperty(type + "." + p.substring(2), value);

                i++;
            } else if (p.charAt(0) == '-') {
                String value = i + 1 < arg.length
                               ? arg[i + 1]
                               : "";

                props.setProperty(type + "." + p.substring(1), value);

                i++;
            }
        }

        return props;
    }

    /**
     * Creates and populates a new HsqlProperties Object using a string
     * such as "key1=value1;key2=value2". <p>
     *
     * The string that represents the = sign above is specified as pairsep
     * and the one that represents the semicolon is specified as delimiter,
     * allowing any string to be used for either.<p>
     *
     * Leading / trailing spaces around the keys and values are discarded.<p>
     *
     * The string is parsed by (1) subdividing into segments by delimiter
     * (2) subdividing each segment in two by finding the first instance of
     * the pairsep (3) trimming each pair of segments from step 2 and
     * inserting into the properties object.<p>
     *
     * Each key is prefixed with the type argument and a dot before being
     * inserted.<p>
     *
     * Any key without a value is added to the list of errors.
     */
    public static HsqlProperties delimitedArgPairsToProps(
            String s,
            String pairsep,
            String dlimiter,
            String type) {

        HsqlProperties props       = new HsqlProperties();
        int            currentpair = 0;

        while (true) {
            int nextpair = s.indexOf(dlimiter, currentpair);

            if (nextpair == -1) {
                nextpair = s.length();
            }

            // find value within the segment
            int valindex = s.substring(0, nextpair)
                            .indexOf(pairsep, currentpair);

            if (valindex == -1) {
                props.addError(
                    NO_VALUE_FOR_KEY,
                    s.substring(currentpair, nextpair).trim());
            } else {
                String key = s.substring(currentpair, valindex).trim();
                String value = s.substring(
                    valindex + pairsep.length(),
                    nextpair)
                                .trim();

                if (type != null) {
                    key = type + "." + key;
                }

                props.setProperty(key, value);
            }

            if (nextpair == s.length()) {
                break;
            }

            currentpair = nextpair + dlimiter.length();
        }

        return props;
    }

    public Enumeration propertyNames() {
        return stringProps.propertyNames();
    }

    public boolean isEmpty() {
        return stringProps.isEmpty();
    }

    public String[] getErrorKeys() {
        return errorKeys;
    }

    public void validate() {}

    public static PropertyMeta newMeta(String name, int type, long defaultVal) {

        PropertyMeta meta = new PropertyMeta();

        meta.propName         = name;
        meta.propType         = type;
        meta.propClass        = "Long";
        meta.propDefaultValue = Long.valueOf(defaultVal);

        return meta;
    }

    public static PropertyMeta newMeta(
            String name,
            int type,
            String defaultValue) {

        PropertyMeta meta = new PropertyMeta();

        meta.propName         = name;
        meta.propType         = type;
        meta.propClass        = "String";
        meta.propDefaultValue = defaultValue;

        return meta;
    }

    public static PropertyMeta newMeta(
            String name,
            int type,
            String defaultValue,
            String[] options) {

        PropertyMeta meta = new PropertyMeta();

        meta.propName         = name;
        meta.propType         = type;
        meta.propClass        = "String";
        meta.propDefaultValue = defaultValue;
        meta.propOptions      = options;

        return meta;
    }

    public static PropertyMeta newMeta(
            String name,
            int type,
            boolean defaultValue) {

        PropertyMeta meta = new PropertyMeta();

        meta.propName         = name;
        meta.propType         = type;
        meta.propClass        = "Boolean";
        meta.propDefaultValue = defaultValue
                                ? Boolean.TRUE
                                : Boolean.FALSE;

        return meta;
    }

    public static PropertyMeta newMeta(
            String name,
            int type,
            int defaultValue,
            int[] values) {

        PropertyMeta meta = new PropertyMeta();

        meta.propName         = name;
        meta.propType         = type;
        meta.propClass        = "Integer";
        meta.propDefaultValue = ValuePool.getInt(defaultValue);
        meta.propValues       = values;

        return meta;
    }

    public static PropertyMeta newMeta(
            String name,
            int type,
            int defaultValue,
            int rangeLow,
            int rangeHigh) {

        PropertyMeta meta = new PropertyMeta();

        meta.propName         = name;
        meta.propType         = type;
        meta.propClass        = "Integer";
        meta.propDefaultValue = ValuePool.getInt(defaultValue);
        meta.propIsRange      = true;
        meta.propRangeLow     = rangeLow;
        meta.propRangeHigh    = rangeHigh;

        return meta;
    }

    /**
     * Performs any range checking for property and return an error message
     */
    public static String validateProperty(
            String key,
            String value,
            PropertyMeta meta) {

        if (meta.propClass.equals("Boolean")) {
            value = value.toLowerCase();

            if (value.equals("true") || value.equals("false")) {
                return null;
            }

            return "invalid boolean value for property: " + key;
        }

        if (meta.propClass.equals("String")) {
            if (meta.propOptions != null) {
                for (int i = 0; i < meta.propOptions.length; i++) {
                    if (meta.propOptions[i].equalsIgnoreCase(value)) {
                        return null;
                    }
                }

                return "value not supported for property: " + key;
            }

            return null;
        }

        if (meta.propClass.equals("Long")) {
            return null;
        }

        if (meta.propClass.equals("Integer")) {
            try {
                int number = Integer.parseInt(value);

                if (meta.propIsRange) {
                    int low  = meta.propRangeLow;
                    int high = meta.propRangeHigh;

                    if (number < low || high < number) {
                        return "value outside range for property: " + key;
                    }
                }

                if (meta.propValues != null) {
                    int[] values = meta.propValues;

                    if (ArrayUtil.find(values, number) == -1) {
                        return "value not supported for property: " + key;
                    }
                }
            } catch (NumberFormatException e) {
                return "invalid integer value for property: " + key;
            }

            return null;
        }

        return null;
    }

    public String toString() {

        StringBuilder sb = new StringBuilder();

        sb.append('{');

        int         len  = stringProps.size();
        Enumeration en   = stringProps.propertyNames();
        List        list = Collections.list(en);

        Collections.sort(list);

        for (int i = 0; i < len; i++) {
            String key = (String) list.get(i);

            sb.append(key);
            sb.append('=');
            sb.append('"');
            sb.append(stringProps.get(key));
            sb.append('"');

            if (i + 1 < len) {
                sb.append(',');
                sb.append(' ');
            }
        }

        sb.append('}');

        return sb.toString();
    }

    public static class PropertyMeta {

        public String   propName;
        public int      propType;
        public String   propClass;
        public boolean  propIsRange;
        public Object   propDefaultValue;
        public int      propRangeLow;
        public int      propRangeHigh;
        public int[]    propValues;
        public String[] propOptions;
    }
}
