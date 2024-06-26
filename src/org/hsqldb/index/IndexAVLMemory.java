/*
 * For work developed by the HSQL Development Group:
 *
 * Copyright (c) 2001-2024, The HSQL Development Group
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
 *
 *
 *
 * For work originally developed by the Hypersonic SQL Group:
 *
 * Copyright (c) 1995-2000, The Hypersonic SQL Group.
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
 * Neither the name of the Hypersonic SQL Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE HYPERSONIC SQL GROUP,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * This software consists of voluntary contributions made by many individuals
 * on behalf of the Hypersonic SQL Group.
 */


package org.hsqldb.index;

import org.hsqldb.HsqlNameManager.HsqlName;
import org.hsqldb.TableBase;
import org.hsqldb.persist.PersistentStore;
import org.hsqldb.types.Type;

/**
 * Implementation of an AVL for memory tables.<p>
 *
 *  New class derived from Hypersonic SQL code and enhanced in HSQLDB. <p>
 *
 * @author Thomas Mueller (Hypersonic SQL Group)
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 2.5.0
 * @since Hypersonic SQL
 */
public class IndexAVLMemory extends IndexAVL {

    /**
     * Constructor declaration
     *
     * @param name HsqlName of the index
     * @param id persistnece id
     * @param table table of the index
     * @param columns array of column indexes
     * @param descending boolean[] for result sets
     * @param nullsLast boolean[] for result sets
     * @param colTypes array of column types
     * @param pk is index for a primary key
     * @param unique is this a unique index
     * @param constraint does this index belong to a constraint
     * @param forward is this an auto-index for an FK that refers to a table
     *   defined after this table
     */
    public IndexAVLMemory(
            HsqlName name,
            long id,
            TableBase table,
            int[] columns,
            boolean[] descending,
            boolean[] nullsLast,
            Type[] colTypes,
            boolean pk,
            boolean unique,
            boolean constraint,
            boolean forward) {

        super(
            name,
            id,
            table,
            columns,
            descending,
            nullsLast,
            colTypes,
            pk,
            unique,
            constraint,
            forward);
    }

    void delete(PersistentStore store, NodeAVL x) {

        if (x == null) {
            return;
        }

        NodeAVL n;

        if (x.nLeft == null) {
            n = x.nRight;
        } else if (x.nRight == null) {
            n = x.nLeft;
        } else {
            NodeAVL d = x;

            x = x.nLeft;

            while (true) {
                NodeAVL temp = x.nRight;

                if (temp == null) {
                    break;
                }

                x = temp;
            }

            // x will be replaced with n later
            n = x.nLeft;

            // swap d and x
            int b = x.iBalance;

            x.iBalance = d.iBalance;
            d.iBalance = b;

            // set x.parent
            NodeAVL xp = x.nParent;
            NodeAVL dp = d.nParent;

            if (d.isRoot(store)) {
                store.setAccessor(this, x);
            }

            x.nParent = dp;

            if (dp != null) {
                if (dp.nRight == d) {
                    dp.nRight = x;
                } else {
                    dp.nLeft = x;
                }
            }

            // relink d.parent, x.left, x.right
            if (d == xp) {
                d.nParent = x;

                if (d.nLeft == x) {
                    x.nLeft = d;

                    NodeAVL dr = d.nRight;

                    x.nRight = dr;
                } else {
                    x.nRight = d;

                    NodeAVL dl = d.nLeft;

                    x.nLeft = dl;
                }
            } else {
                d.nParent = xp;
                xp.nRight = d;

                NodeAVL dl = d.nLeft;
                NodeAVL dr = d.nRight;

                x.nLeft  = dl;
                x.nRight = dr;
            }

            x.nRight.nParent = x;
            x.nLeft.nParent  = x;

            // set d.left, d.right
            d.nLeft = n;

            if (n != null) {
                n.nParent = d;
            }

            d.nRight = null;
            x        = d;
        }

        boolean isleft = x.isFromLeft(store);

        x.replace(store, this, n);

        n = x.nParent;

        x.delete();

        while (n != null) {
            x = n;

            int sign = isleft
                       ? 1
                       : -1;

            switch (x.iBalance * sign) {

                case -1 :
                    x.iBalance = 0;
                    break;

                case 0 :
                    x.iBalance = sign;

                    return;

                case 1 :
                    NodeAVL r = x.child(store, !isleft);
                    int     b = r.iBalance;

                    if (b * sign >= 0) {
                        x.replace(store, this, r);

                        NodeAVL child = r.child(store, isleft);

                        x.set(store, !isleft, child);
                        r.set(store, isleft, x);

                        if (b == 0) {
                            x.iBalance = sign;
                            r.iBalance = -sign;

                            return;
                        }

                        x.iBalance = 0;
                        r.iBalance = 0;
                        x          = r;
                    } else {
                        NodeAVL l = r.child(store, isleft);

                        x.replace(store, this, l);

                        b = l.iBalance;

                        r.set(store, isleft, l.child(store, !isleft));
                        l.set(store, !isleft, r);
                        x.set(store, !isleft, l.child(store, isleft));
                        l.set(store, isleft, x);

                        x.iBalance = (b == sign)
                                     ? -sign
                                     : 0;
                        r.iBalance = (b == -sign)
                                     ? sign
                                     : 0;
                        l.iBalance = 0;
                        x          = l;
                    }
            }

            isleft = x.isFromLeft(store);
            n      = x.nParent;
        }
    }

    NodeAVL next(PersistentStore store, NodeAVL x) {

        NodeAVL r = x.nRight;

        if (r != null) {
            x = r;

            NodeAVL l = x.nLeft;

            while (l != null) {
                x = l;
                l = x.nLeft;
            }

            return x;
        }

        NodeAVL ch = x;

        x = x.nParent;

        while (x != null && ch == x.nRight) {
            ch = x;
            x  = x.nParent;
        }

        return x;
    }

    NodeAVL last(PersistentStore store, NodeAVL x) {

        if (x == null) {
            return null;
        }

        NodeAVL left = x.nLeft;

        if (left != null) {
            x = left;

            NodeAVL right = x.nRight;

            while (right != null) {
                x     = right;
                right = x.nRight;
            }

            return x;
        }

        NodeAVL ch = x;

        x = x.nParent;

        while (x != null && ch.equals(x.nLeft)) {
            ch = x;
            x  = x.nParent;
        }

        return x;
    }

    /**
     * Balances part of the tree after an alteration to the index.
     */
    void balance(PersistentStore store, NodeAVL x, boolean isleft) {

        while (true) {
            int sign = isleft
                       ? 1
                       : -1;

            switch (x.iBalance * sign) {

                case 1 :
                    x.iBalance = 0;

                    return;

                case 0 :
                    x.iBalance = -sign;
                    break;

                case -1 :
                    NodeAVL l = isleft
                                ? x.nLeft
                                : x.nRight;

                    if (l.iBalance == -sign) {
                        x.replace(store, this, l);
                        x.set(store, isleft, l.child(store, !isleft));
                        l.set(store, !isleft, x);

                        x.iBalance = 0;
                        l.iBalance = 0;
                    } else {
                        NodeAVL r = !isleft
                                    ? l.nLeft
                                    : l.nRight;

                        x.replace(store, this, r);
                        l.set(store, !isleft, r.child(store, isleft));
                        r.set(store, isleft, l);
                        x.set(store, isleft, r.child(store, !isleft));
                        r.set(store, !isleft, x);

                        int rb = r.iBalance;

                        x.iBalance = (rb == -sign)
                                     ? sign
                                     : 0;
                        l.iBalance = (rb == sign)
                                     ? -sign
                                     : 0;
                        r.iBalance = 0;
                    }

                    return;
            }

            if (x.nParent == null) {
                return;
            }

            isleft = x == x.nParent.nLeft;
            x      = x.nParent;
        }
    }
}
