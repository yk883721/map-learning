package map;

import sun.misc.Unsafe;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class CusConcurrentHashMap<K, V> {

    /**
     * The maximum capacity, used if a higher value is implicitly
     * specified by either of the constructors with arguments.  MUST
     * be a power of two <= 1<<30 to ensure that entries are indexable
     * using ints.
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * ConcurrentHashMap list entry. Note that this is never exported
     * out as a user-visible Map.Entry.
     */
    static final class HashEntry<K, V> {

        final int hash;
        final K key;
        volatile V value;
        volatile HashEntry<K, V> next;

        public HashEntry(int hash, K key, V value, HashEntry<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        /**
         * Sets next field with volatile write semantics.  (See above
         * about use of putOrderedObject.)
         */
        final void setNext(HashEntry<K, V> next) {
            UNSAFE.putOrderedObject(this, nextOffset, n);
        }

        static final sun.misc.Unsafe UNSAFE;
        static final long nextOffset;

        static {
            try {
                UNSAFE = sun.misc.Unsafe.getUnsafe();

                Class k = HashEntry.class;
                nextOffset = UNSAFE.objectFieldOffset
                        (k.getDeclaredField("next"));
            } catch (Exception e) {
                throw new Error(e);
            }
        }
    }

    /**
     * Get the ith element of given table (if nonnull) with volatile
     * read semantics. Note: This is manually integrated into a few
     * performance-sensitive methods to reduce call overhead.
     */
    static final <K, V> HashEntry<K, V> entryAt(HashEntry<K, V>[] tab, int i) {
        return (tab == null) ? null :
                (HashEntry<K, V>) UNSAFE.getObjectVolatile
                        (tab, ((long) i << TSHIFT) + TBASE);
    }

    /**
     * Sets the ith element of given table, with volatile write
     * semantics. (See above about use of putOrderedObject.)
     */
    static final <K, V> void setEntryAt(HashEntry<K, V>[] tab, int i,
                                        HashEntry<K, V> e) {

        // putOrderedObject 为 putObjectVolatile 的延迟版本
        // 懒写入 方式，因为所有调用 该方法的后续都跟着 unock() 方法，会保证及时、一致性
        UNSAFE.putOrderedObject(tab, ((long) i << TSHIFT) + TBASE, e);
    }


    /**
     * Segments are specialized versions of hash tables.  This
     * subclasses from ReentrantLock opportunistically, just to
     * simplify some locking and avoid separate construction.
     *
     * <p>特化版本的 hash table. 便于分段锁<p/>
     */
    static final class Segment<K, V> extends ReentrantLock implements Serializable {

        /*
         * Segments maintain a table of entry lists that are always
         * kept in a consistent state, so can be read (via volatile
         * reads of segments and tables) without locking.  This
         * requires replicating nodes when necessary during table
         * resizing, so the old lists can be traversed by readers
         * still using old version of table.
         *
         * This class defines only mutative methods requiring locking.
         * Except as noted, the methods of this class perform the
         * per-segment versions of ConcurrentHashMap methods.  (Other
         * methods are integrated directly into ConcurrentHashMap
         * methods.) These mutative methods use a form of controlled
         * spinning on contention via methods scanAndLock and
         * scanAndLockForPut. These intersperse tryLocks with
         * traversals to locate nodes.  The main benefit is to absorb
         * cache misses (which are very common for hash tables) while
         * obtaining locks so that traversal is faster once
         * acquired. We do not actually use the found nodes since they
         * must be re-acquired under lock anyway to ensure sequential
         * consistency of updates (and in any case may be undetectably
         * stale), but they will normally be much faster to re-locate.
         * Also, scanAndLockForPut speculatively creates a fresh node
         * to use in put if no node is found.
         */

        private static final long serialVersionUID = 2249069246763182397L;

        /**
         * The maximum number of times to tryLock in a prescan before
         * possibly blocking on acquire in preparation for a locked
         * segment operation. On multiprocessors, using a bounded
         * number of retries maintains cache acquired while locating
         * nodes.
         */
        static final int MAX_SCAN_RETRIES =
                Runtime.getRuntime().availableProcessors() > 1 ? 64 : 1;

        /**
         * The per-segment table. Elements are accessed via
         * entryAt/setEntryAt providing volatile semantics.
         */
        transient volatile HashEntry<K, V>[] table;

        /**
         * The number of elements. Accessed only either within locks
         * or among other volatile reads that maintain visibility.
         */
        transient int count;

        /**
         * The total number of mutative operations in this segment.
         * Even though this may overflows 32 bits, it provides
         * sufficient accuracy for stability checks in CHM isEmpty()
         * and size() methods.  Accessed only either within locks or
         * among other volatile reads that maintain visibility.
         */
        transient int modCount;

        /**
         * The table is rehashed when its size exceeds this threshold.
         * (The value of this field is always <tt>(int)(capacity *
         * loadFactor)</tt>.)
         */
        transient int threshold;

        /**
         * The load factor for the hash table.  Even though this value
         * is same for all segments, it is replicated to avoid needing
         * links to outer object.
         *
         * @serial
         */
        final float loadFactor;

        Segment(float lf, int threshold, HashEntry<K, V>[] tab) {
            this.loadFactor = lf;
            this.threshold = threshold;
            this.table = tab;
        }

        final V put(K key, int hash, V value, boolean onlyIfAbsent) {
            HashEntry<K, V> node = tryLock() ? null :
                    scanAndLockForPut(key, hash, value);
            V oldValue;
            try {

                HashEntry<K, V>[] tab = this.table;
                int index = (tab.length - 1) & hash;
                HashEntry<K, V> first = entryAt(tab, index);

                for (HashEntry<K, V> e = first; ; ) {
                    if (e != null) {
                        K k;
                        if ((k = e.key) == key ||
                                (e.hash == hash && key.equals(k))) {
                            oldValue = e.value;
                            if (!onlyIfAbsent) {
                                e.value = value;
                                modCount++;
                            }
                            break;
                        }
                        e = e.next;
                    } else {
                        // e 为 null，遍历完成，头插法插入即可
                        if (node != null) {
                            node.setNext(first);
                        } else {
                            node = new HashEntry<>(hash, key, value, first);
                        }

                        int c = count + 1;
                        // 扩容 - 添加
                        if (c > threshold && tab.length < MAXIMUM_CAPACITY) {
                            rehash(node);
                        } else {
                            setEntryAt(tab, index, node);
                        }

                        modCount++;
                        count = c;
                        oldValue = null;
                        break;
                    }
                }
            } finally {
                unlock();
            }
            return oldValue;
        }

        /**
         * Doubles size of table and repacks entries, also adding the
         * given node to new table
         */
        private void rehash(HashEntry<K, V> node) {
            /*
             * Reclassify nodes in each list to new table.  Because we
             * are using power-of-two expansion, the elements from
             * each bin must either stay at same index, or move with a
             * power of two offset. We eliminate unnecessary node
             * creation by catching cases where old nodes can be
             * reused because their next fields won't change.
             * Statistically, at the default threshold, only about
             * one-sixth of them need cloning when a table
             * doubles. The nodes they replace will be garbage
             * collectable as soon as they are no longer referenced by
             * any reader thread that may be in the midst of
             * concurrently traversing table. Entry accesses use plain
             * array indexing because they are followed by volatile
             * table write.
             */

            HashEntry<K, V>[] oldTable = this.table;
            int oldCapacity = oldTable.length;
            int newCapacity = oldCapacity << 1;
            threshold = (int) (newCapacity * loadFactor);

            HashEntry<K, V>[] newTable =
                    (HashEntry<K, V>[]) new HashEntry[newCapacity];
            int sizeMask = newCapacity - 1;

            for (int i = 0; i < oldCapacity; i++) {
                HashEntry<K, V> e = oldTable[i];
                if (e != null) {
                    HashEntry<K, V> next = e.next;
                    int idx = e.hash & sizeMask;
                    if (next == null) { //  Single node on list - 只有一个节点，直接迁移
                        newTable[idx] = e;
                    } else {
                        // 1. 找到一个节点，这个节点之后的 index 都相同，即这个节点及其后面的节点都会映射到新链表同一个位置
                        // 则只需将这个节点迁移过去，next 后续的会自动带过去
                        // 2. 然后，再处理之前的节点
                        HashEntry<K, V> lastRun = e;
                        int lastIdx = idx;
                        for (HashEntry<K, V> last = next;
                             last != null;
                             last = last.next) {
                            int k = last.hash & sizeMask;
                            if (k != lastIdx) {
                                lastIdx = k;
                                lastRun = last;
                            }
                        }

                        newTable[lastIdx] = lastRun;

                        for (HashEntry<K, V> p = e; p != lastRun; p = p.next) {
                            V value = p.value;
                            int hash = p.hash;
                            int k = p.hash & sizeMask;
                            HashEntry<K, V> n = newTable[k];
                            newTable[k] = new HashEntry<>(hash, p.key, value, n);
                        }
                    }
                }
            }
            // 单独添加 Node 节点
            int nodeIndex = node.hash & sizeMask;
            node.setNext(newTable[nodeIndex]);
            newTable[nodeIndex] = node;
            table = newTable;
        }

        /**
         * Scans for a node containing given key while trying to
         * acquire lock, creating and returning one if not found. Upon
         * return, guarantees that lock is held. UNlike in most
         * methods, calls to method equals are not screened: Since
         * traversal speed doesn't matter, we might as well help warm
         * up the associated code and accesses as well.
         *
         * <p>未获取锁时，做一些预处理操作<p/>
         * <p>1. 预先生成 node. 2. 预热代码<p/>
         *
         * @return a new node if key not found, else null
         */
        private HashEntry<K, V> scanAndLockForPut(K key, int hash, V value) {

            HashEntry<K, V> first = entryForHash(this, hash);
            HashEntry<K, V> e = first;

            int retries = -1;

            HashEntry<K, V> node = null;
            while (!tryLock()) {
                HashEntry<K, V> f;
                if (retries < 0) {
                    if (e == null) {
                        if (node == null) {
                            node = new HashEntry<>(hash, key, value, null);
                        }
                        retries = 0;
                    } else if (key.equals(e.key)) {
                        retries = 0;
                    } else {
                        e = e.next;
                    }
                } else if (++retries > MAX_SCAN_RETRIES) {
                    lock();
                    break;
                } else if ((retries & 1) == 0 && (f = entryForHash(this, hash)) != first) {
                    e = first = f;
                    retries = -1;
                }
            }

            return node;
        }

        /**
         * Scans for a node containing the given key while trying to
         * acquire lock for a remove or replace operation. Upon
         * return, guarantees that lock is held.  Note that we must
         * lock even if the key is not found, to ensure sequential
         * consistency of updates.
         *
         * <p>删除、替换时使用 扫描</p>
         * <p>为了后续的一致性，即使没有找到元素，也需要获取锁</p>
         */
        private void scanAndLock(Object key, int hash) {
            // similar to but simpler than scanAndLockForPut

            HashEntry<K, V> first = entryForHash(this, hash);
            HashEntry<K, V> e = first;
            int retries = -1;
            while (!tryLock()) {
                HashEntry<K, V> f;
                if (retries < 0) {
                    if (e == null || key.equals(e.key)) {
                        retries = 0;
                    }else {
                        e = e.next;
                    }
                } else if (++retries > MAX_SCAN_RETRIES) {
                    lock();
                    break;
                }else if ((retries & 1) == 0 &&
                        (f = entryForHash(this, hash)) != first){
                    e = first = f;
                    retries = -1;
                }
            }
        }

        /**
         * Remove; match on key only if value null, else match both.
         */
        final V remove(Object key, int hash, Object value) {
            if (!tryLock()) {
                scanAndLock(key, hash);
            }
            V oldValue = null;

            try {

                HashEntry<K, V>[] tab = this.table;
                int index = hash & (tab.length - 1);
                HashEntry<K, V> e = entryAt(tab, index);
                HashEntry<K, V> prev = e;
                while (e != null) {
                    HashEntry<K, V> next = e.next;
                    K k;
                    if ((k = e.key) == key || (e.hash == hash && key.equals(k))) {
                        V v = e.value;
                        if (value == null || value == v || value.equals(v)) {
                            if (prev == null) {
                                setEntryAt(tab, index, e);
                            }else {
                                prev.setNext(next);
                            }

                            modCount++;
                            count--;
                            oldValue = v;
                        }
                        break;
                    }
                    prev = e;
                    e = next;
                }
            }finally {
                unlock();
            }
            return oldValue;
        }

        final boolean replace(K key, int hash, V oldValue, V newValue) {
            if (!tryLock()) {
                scanAndLock(key, hash);
            }
            boolean replaced = false;
            try {
                HashEntry<K, V> e;
                for (e = entryForHash(this, hash); e != null; e = e.next) {
                    K k;
                    if ((k = e.key) == key ||
                            (e.hash == hash && key.equals(k))) {
                        if (oldValue.equals(e.value)) {
                            e.value = newValue;
                            ++modCount;
                            replaced = true;
                        }
                        break;
                    }
                }
            }finally {
                unlock();
            }
            return replaced;
        }

        final V replace(K key, int hash, V value) {
            if (!tryLock()) {
                scanAndLock(key, hash);
            }
            V oldValue = null;
            try {
                HashEntry<K, V> e;
                for (e = entryForHash(this, hash); )

            }finally {
                unlock();
            }
            return oldValue;
        }

    }

    /**
     * Gets the table entry for the given segment and hash
     */
    static final <K, V> HashEntry<K, V> entryForHash(Segment<K, V> seg, int h) {
        HashEntry<K, V>[] tab;
        return (seg == null || (tab = seg.table) == null) ? null :
                (HashEntry<K, V>) UNSAFE.getObjectVolatile
                        (tab, ((long) (((tab.length - 1) & h)) << TSHIFT) + TBASE);
    }


    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long TBASE;
    private static final long TSHIFT;

    static {
        int ts;
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();

            Class tc = HashEntry[].class;

            TBASE = UNSAFE.arrayBaseOffset(tc);
            ts = UNSAFE.arrayIndexScale(tc);


        } catch (Exception e) {
            throw new Error(e);
        }

        if ((ts & (ts - 1)) != 0) {
            throw new Error("data type scale not a power of two");
        }

        // ( i << (31 - Integer.numberOfLeadingZeros(ts))) + TBASE = TBASE + (i * ts)
        TSHIFT = (31 - Integer.numberOfLeadingZeros(ts));
    }

}
