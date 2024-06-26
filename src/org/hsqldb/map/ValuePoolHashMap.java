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


package org.hsqldb.map;

import org.hsqldb.types.TimestampData;

/*
 * implementation notes:
 *
 * NB: As of this version this class cannot be used for mixed object types
 * It is relatively easy to support this by adding an 'instanceof' test inside
 * each getOrAddXxxx method before casting the Set values to the target type
 * for comparison purposes.
 *
 * superclass is used as an Object Set
 * getOrAddXxxx methods are implemented directly for speed
 * the superclass infrastructure is otherwise used
 */

/**
 * Subclass of BaseHashMap for maintaining a pool of objects. Supports a
 * range of java.lang.* objects.
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.5.1
 * @since 1.7.2
 *
 */
public class ValuePoolHashMap extends BaseHashMap {

    long hits;

    public ValuePoolHashMap(
            int initialCapacity,
            int maxCapacity,
            int purgePolicy)
            throws IllegalArgumentException {

        super(
            initialCapacity,
            BaseHashMap.objectKeyOrValue,
            BaseHashMap.noKeyOrValue,
            true);

        this.maxCapacity = maxCapacity;
        this.purgePolicy = purgePolicy;
    }

    protected Integer getOrAddInteger(int intKey) {

        Integer testValue;
        int     index      = hashIndex.getHashIndex(intKey);
        int     lookup     = hashIndex.hashTable[index];
        int     lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup, lookup = hashIndex.getNextLookup(lookup)) {
            testValue = (Integer) objectKeyTable[lookup];

            int keyValue = testValue.intValue();

            if (keyValue == intKey) {
                int count = accessCount.incrementAndGet();

                if (count < 0) {
                    resetAccessCount();

                    count = accessCount.incrementAndGet();
                }

                accessTable[lookup] = count;

                hits++;

                return testValue;
            } else if (keyValue > intKey) {
                break;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddInteger(intKey);
        }

        lookup                 = hashIndex.linkNode(index, lastLookup);
        testValue              = Integer.valueOf(intKey);
        objectKeyTable[lookup] = testValue;

        int count = accessCount.incrementAndGet();

        if (count < 0) {
            resetAccessCount();

            count = accessCount.incrementAndGet();
        }

        accessTable[lookup] = count;

        return testValue;
    }

    protected Long getOrAddLong(long longKey) {

        Long testValue;
        int  index = hashIndex.getHashIndex((int) (longKey ^ (longKey >>> 32)));
        int  lookup     = hashIndex.hashTable[index];
        int  lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup, lookup = hashIndex.getNextLookup(lookup)) {
            testValue = (Long) objectKeyTable[lookup];

            long keyValue = testValue.longValue();

            if (keyValue == longKey) {
                int count = accessCount.incrementAndGet();

                if (count < 0) {
                    resetAccessCount();

                    count = accessCount.incrementAndGet();
                }

                accessTable[lookup] = count;

                hits++;

                return testValue;
            } else if (keyValue > longKey) {
                break;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddLong(longKey);
        }

        lookup                 = hashIndex.linkNode(index, lastLookup);
        testValue              = Long.valueOf(longKey);
        objectKeyTable[lookup] = testValue;

        int count = accessCount.incrementAndGet();

        if (count < 0) {
            resetAccessCount();

            count = accessCount.incrementAndGet();
        }

        accessTable[lookup] = count;

        return testValue;
    }

    /**
     * This is dissimilar to normal hash map get() methods. The key Object
     * should have an equals(String) method which should return true if the
     * key.toString().equals(String) is true. Also the key.hashCode() method
     * must return the same value as key.toString.hashCode().<p>
     *
     * The above is always true when the key is a String. But it means it is
     * possible to submit special keys that fulfill the contract. For example
     * a wrapper around a byte[] can be submitted as key to retrieve either
     * a new String, which is the result of the toString() method of the
     * wrapper, or return an existing String which would be equal to the result
     * of toString().
     *
     * @param key String or other Object with compatible equals(String)
     * and hashCode().
     * @return String from map or a new String
     */
    protected String getOrAddString(Object key) {

        String testValue;
        int    index      = hashIndex.getHashIndex(key.hashCode());
        int    lookup     = hashIndex.hashTable[index];
        int    lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup, lookup = hashIndex.getNextLookup(lookup)) {
            testValue = (String) objectKeyTable[lookup];

            if (key.equals(testValue)) {
                int count = accessCount.incrementAndGet();

                if (count < 0) {
                    resetAccessCount();

                    count = accessCount.incrementAndGet();
                }

                accessTable[lookup] = count;

                hits++;

                return testValue;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddString(key);
        }

        testValue              = key.toString();
        lookup                 = hashIndex.linkNode(index, lastLookup);
        objectKeyTable[lookup] = testValue;

        int count = accessCount.incrementAndGet();

        if (count < 0) {
            resetAccessCount();

            count = accessCount.incrementAndGet();
        }

        accessTable[lookup] = count;

        return testValue;
    }

    protected String getOrAddSubString(String key, int from, int limit) {

        // to improve
        key = key.substring(from, limit);

        String testValue;
        int    index      = hashIndex.getHashIndex(key.hashCode());
        int    lookup     = hashIndex.hashTable[index];
        int    lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup, lookup = hashIndex.getNextLookup(lookup)) {
            testValue = (String) objectKeyTable[lookup];

            if (key.equals(testValue)) {
                int count = accessCount.incrementAndGet();

                if (count < 0) {
                    resetAccessCount();

                    count = accessCount.incrementAndGet();
                }

                accessTable[lookup] = count;

                hits++;

                return testValue;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddString(key);
        }

        testValue              = new String(key.toCharArray());
        lookup                 = hashIndex.linkNode(index, lastLookup);
        objectKeyTable[lookup] = testValue;

        int count = accessCount.incrementAndGet();

        if (count < 0) {
            resetAccessCount();

            count = accessCount.incrementAndGet();
        }

        accessTable[lookup] = count;

        return testValue;
    }

    protected TimestampData getOrAddDate(long longKey) {

        TimestampData testValue;
        int           hash       = (int) longKey ^ (int) (longKey >>> 32);
        int           index      = hashIndex.getHashIndex(hash);
        int           lookup     = hashIndex.hashTable[index];
        int           lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup, lookup = hashIndex.getNextLookup(lookup)) {
            testValue = (TimestampData) objectKeyTable[lookup];

            if (testValue.getSeconds() == longKey) {
                int count = accessCount.incrementAndGet();

                if (count < 0) {
                    resetAccessCount();

                    count = accessCount.incrementAndGet();
                }

                accessTable[lookup] = count;

                hits++;

                return testValue;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddDate(longKey);
        }

        lookup                 = hashIndex.linkNode(index, lastLookup);
        testValue              = new TimestampData(longKey);
        objectKeyTable[lookup] = testValue;

        int count = accessCount.incrementAndGet();

        if (count < 0) {
            resetAccessCount();

            count = accessCount.incrementAndGet();
        }

        accessTable[lookup] = count;

        return testValue;
    }

    protected Double getOrAddDouble(long longKey) {

        Double testValue;
        int index = hashIndex.getHashIndex((int) (longKey ^ (longKey >>> 32)));
        int    lookup     = hashIndex.hashTable[index];
        int    lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup, lookup = hashIndex.getNextLookup(lookup)) {
            testValue = (Double) objectKeyTable[lookup];

            if (Double.doubleToLongBits(testValue.doubleValue()) == longKey) {
                int count = accessCount.incrementAndGet();

                if (count < 0) {
                    resetAccessCount();

                    count = accessCount.incrementAndGet();
                }

                accessTable[lookup] = count;

                hits++;

                return testValue;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddDouble(longKey);
        }

        lookup                 = hashIndex.linkNode(index, lastLookup);
        testValue = Double.valueOf(Double.longBitsToDouble(longKey));
        objectKeyTable[lookup] = testValue;

        int count = accessCount.incrementAndGet();

        if (count < 0) {
            resetAccessCount();

            count = accessCount.incrementAndGet();
        }

        accessTable[lookup] = count;

        return testValue;
    }

    protected Object getOrAddObject(Object key) {

        Object testValue;
        int    index      = hashIndex.getHashIndex(key.hashCode());
        int    lookup     = hashIndex.hashTable[index];
        int    lastLookup = -1;

        for (; lookup >= 0;
                lastLookup = lookup, lookup = hashIndex.getNextLookup(lookup)) {
            testValue = objectKeyTable[lookup];

            if (testValue.equals(key)) {
                int count = accessCount.incrementAndGet();

                if (count < 0) {
                    resetAccessCount();

                    count = accessCount.incrementAndGet();
                }

                accessTable[lookup] = count;

                hits++;

                return testValue;
            }
        }

        if (hashIndex.elementCount >= threshold) {
            reset();

            return getOrAddObject(key);
        }

        lookup                 = hashIndex.linkNode(index, lastLookup);
        objectKeyTable[lookup] = key;

        int count = accessCount.incrementAndGet();

        if (count < 0) {
            resetAccessCount();

            count = accessCount.incrementAndGet();
        }

        accessTable[lookup] = count;

        return key;
    }
}
