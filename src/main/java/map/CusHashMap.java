package map;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.Serializable;
import java.util.*;
import java.util.Map.Entry;

public class CusHashMap<K, V>
        extends CusAbstractMap<K, V>
        implements Map<K, V>, Cloneable, Serializable {

    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

    static final int MAXIMUM_CAPACITY = 1 << 30;

    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    // 所有空 Map 之间，共享一个空数组
    static final Entry<?, ?>[] EMPTY_TABLE = {};

    // 哈希表数组，长度必须为 2 的次幂，需要时会进行扩容
    transient Entry<K, V>[] table = (Entry<K, V>[]) EMPTY_TABLE;


    /**
     * The number of key-value mappings contained in this map.
     * 即键值对数量
     */
    transient int size;

    // 每次判断是否扩容的阈值，一般为 capacity * load factor
    // 初始时，table == EMPTY_TABLE, 则 threshold = initial capacity
    int threshold;

    final float loadFactor;

    // 记录该 Map 发生过多少次结构变化 (1、rehash，2、键值对个数变化)
    // 用于在 iterators 迭代时，判断有没有并发修改，用来 fail-fast 快速失败
    transient int modCount;

    /**
     * A randomizing value associated with this instance that is applied to
     * hash code of keys to make hash collisions harder to find. If 0 then
     * alternative hashing is disabled.
     */
    transient int hashSeed = 0;

    public CusHashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " +
                    initialCapacity);
        }

        if (initialCapacity > MAXIMUM_CAPACITY) {
            initialCapacity = MAXIMUM_CAPACITY;
        }

        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new IllegalArgumentException("Illegal load factor: " +
                    loadFactor);
        }

        this.loadFactor = loadFactor;
        threshold = initialCapacity;
        init();
    }

    public CusHashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public CusHashMap() {
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    public CusHashMap(Map<? extends K, ? extends V> map) {

        // 1. 构造
        this(Math.max((int) (map.size() / DEFAULT_LOAD_FACTOR) + 1,
                DEFAULT_INITIAL_CAPACITY), DEFAULT_LOAD_FACTOR);

        // 2. 初次膨胀
        inflateTable(threshold);

        // 3. putAll
        putAllForCreate(map);

    }

    private static int roundUpToPowerOf2(int number) {
        // assert number >= 0 : "number must be non-negative";
        // 假设 number >= 0, number 必须为非负数

        // -1：为了处理 number = pow(2, x) 的情况，此时返回结果就是 number，而不是 2 * number
        // 所以，number 为 1也需要单独处理
        return number >= MAXIMUM_CAPACITY
                ? MAXIMUM_CAPACITY
                : (number > 1) ? Integer.highestOneBit((number - 1) << 1) : 1;

    }

    // 表初次膨胀
    private void inflateTable(int toSize) {
        // Find a power of 2 >= toSize
        // 找到一个比 toSize 大的一个最小的 2 的幂次方数
        int capacity = roundUpToPowerOf2(toSize);

        // 容量为 MAXIMUM_CAPACITY 时候，threshold 直接 + 1，大于容量，意为永远不需要再扩容
        threshold = (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY + 1);
        table = new Entry[capacity];

        // todo initHashSeedAsNeeded

    }

    /**
     * Initialization hook for subclasses. This method is called
     * in all constructors and pseudo-constructors (clone, readObject)
     * after HashMap has been initialized but before any entries have
     * been inserted.  (In the absence of this method, readObject would
     * require explicit knowledge of subclasses.)
     */
    void init() {
        // 供子类实现使用
    }

    /**
     * Initialize the hashing mask value. We defer initialization until we
     * really need it.
     */
    final boolean initHashSeedAsNeeded(int capacity) {

        // todo rehash

        return false;
    }


    /**
     * Retrieve object hash code and applies a supplemental hash function to the
     * result hash, which defends against poor quality hash functions.  This is
     * critical because HashMap uses power-of-two length hash tables, that
     * otherwise encounter collisions for hashCodes that do not differ
     * in lower bits. Note: Null keys always map to hash 0, thus index 0.
     */
    final int hash(Object k) {
        int h = hashSeed;
        if (0 != h && k instanceof String) {
            return sun.misc.Hashing.stringHash32((String) k);
        }

        h ^= k.hashCode();

        // This function ensures that hashCodes that differ only by
        // constant multiples at each bit position have a bounded
        // number of collisions (approximately 8 at default load factor).
        h ^= (h >>> 20) ^ (h >>> 12);
        return h ^ (h >>> 7) ^ (h >>> 4);
    }

    // 指定 hash，数组长度，返回 存放 index
    // & 操作实现取模：length 长度必须为 2 的次幂
    private int indexFor(int h, int length) {
        // assert Integer.bitCount(length) == 1 : "length must be a non-zero power of 2";
        return h & (length - 1);
    }

    /**
     * Returns the number of key-value mappings in this map.
     */
    public int size() {
        return size;
    }

    /**
     * Returns <tt>true</tt> if this map contains no key-value mappings.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public V get(Object key) {
        if (key == null) {
            return getForNullKey();
        }

        Entry<K, V> entry = getEntry(key);
        return entry == null ? null : entry.getValue();
    }

    /**
     * Offloaded version of get() to look up null keys.  Null keys map
     * to index 0.  This null case is split out into separate methods
     * for the sake of performance in the two most commonly used
     * operations (get and put), but incorporated with conditionals in
     * others.
     */
    private V getForNullKey() {
        if (size == 0) {
            return null;
        }
        for (Entry<K, V> e = table[0]; e != null; e = e.next) {
            if (e.key == null) {
                return e.value;
            }
        }
        return null;
    }

    /**
     * Returns <tt>true</tt> if this map contains a mapping for the
     * specified key.
     *
     * @param key The key whose presence in this map is to be tested
     * @return <tt>true</tt> if this map contains a mapping for the specified
     * key.
     */
    @Override
    public boolean containsKey(Object key) {
        return getEntry(key) != null;
    }

    /**
     * Returns the entry associated with the specified key in the
     * HashMap.  Returns null if the HashMap contains no mapping
     * for the key.
     */
    private Entry<K, V> getEntry(Object key) {
        if (size == 0) {
            return null;
        }

        int hash = key == null ? 0 : hash(key);
        int i = indexFor(hash, table.length);
        for (Entry<K, V> e = table[i]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k)))) {
                return e;
            }
        }
        return null;
    }

    /**
     * Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old
     * value is replaced.
     *
     * @param key   key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>.
     * (A <tt>null</tt> return can also indicate that the map
     * previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    @Override
    public V put(K key, V value) {
        // 1. 判断是否为初始空
        if (table == EMPTY_TABLE) {
            // 第一次为空表，初始化扩容，threshold 即为 initialCapacity
            inflateTable(threshold);
        }

        // 2. 判断 key 为 null, 单独处理
        if (key == null) {
            return putForNullKey(value);
        }

        // 3. 查找已存在数据
        int hash = hash(key);
        int i = indexFor(hash, table.length);

        for (Entry<K, V> e = table[i]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash && ((k = e.key) == key || key.equals(k))) {
                V oldValue = e.value;
                e.value = value;
                e.recordAccess(this);
                return oldValue;
            }
        }

        modCount++;
        addEntry(hash, key, value, i);
        return null;
    }

    /**
     * Offloaded version of put for null keys
     */
    private V putForNullKey(V value) {
        // 1. 遍历链表, 寻找是否存在旧值
        for (Entry<K, V> e = table[0]; e != null; e = e.next) {
            if (e.key == null) {
                // 找到已经存在 key 为 null，替换
                // 没有对结构造成修改，可直接返回 oldValue
                V oldValue = e.value;
                e.setValue(value);
                e.recordAccess(this);
                return oldValue;
            }
        }

        // 2.不存在旧值，先更新修改次数
        modCount++;

        // 3.添加实际 entry, 同时判断扩容
        addEntry(0, null, value, 0);

        // 4.不存在旧值，返回 null
        return null;
    }

    /**
     * This method is used instead of put by constructors and
     * pseudoconstructors (clone, readObject).  It does not resize the table,
     * check for comodification, etc.  It calls createEntry rather than
     * addEntry.
     */
    private void putForCreate(K key, V value) {
        // 直接put，无需扩容

        // 1. hash, index 计算
        int hash = key == null ? 0 : hash(key);
        int i = indexFor(hash, table.length);

        /**
         * Look for preexisting entry for key.  This will never happen for
         * clone or deserialize.  It will only happen for construction if the
         * input Map is a sorted map whose ordering is inconsistent w/ equals.
         */
        // 2. 寻找是否已存在，存在则替换
        for (Entry<K, V> e = table[i]; e != null; e = e.next) {
            Object k;
            if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k)))) {
                e.value = value;
                return;
            }
        }

        // 3.无需判断扩容，则直接 create
        createEntry(hash, key, value, i);
    }

    private void putAllForCreate(Map<? extends K, ? extends V> map) {

        for (Map.Entry<? extends K, ? extends V> e : map.entrySet()) {
            putForCreate(e.getKey(), e.getValue());
        }

    }

    /**
     * Rehashes the contents of this map into a new array with a
     * larger capacity.  This method is called automatically when the
     * number of keys in this map reaches its threshold.
     *
     * <p> reBalance，当 map 中的数量达到 threshold, 将老数组中的内容转移到新数组中 <p/>
     * <p>
     * If current capacity is MAXIMUM_CAPACITY, this method does not
     * resize the map, but sets threshold to Integer.MAX_VALUE.
     * This has the effect of preventing future calls.
     *
     * <p> 如果容量已经达到最大 MAXIMUM_CAPACITY，则 threshold 也设置为 Integer.MAX_VALUE, 减少以后对该方法的调用 <p/>
     *
     * @param newCapacity the new capacity, MUST be a power of two;
     *                    must be greater than current capacity unless current
     *                    capacity is MAXIMUM_CAPACITY (in which case value
     *                    is irrelevant).
     */
    private void resize(int newCapacity) {
        Entry[] oldTable = table;
        int oldCapacity = oldTable.length;

        // 1. 容量达到最大，不再进行扩容，以后再不再进入 resize 方法
        if (oldCapacity == MAXIMUM_CAPACITY) {
            threshold = Integer.MAX_VALUE;
            return;
        }

        // 2. 实际扩容并转移数据
        Entry[] newTable = new Entry[newCapacity];
        transfer(newTable, initHashSeedAsNeeded(newCapacity));
        table = newTable;

        // 3. 扩容后再次进行容量判断
        threshold = (int) Math.min(newCapacity * loadFactor, MAXIMUM_CAPACITY + 1);
    }

    // 将所有数据从老数组转移到新数组中
    private void transfer(Entry[] newTable, boolean rehash) {

        int newCapacity = newTable.length;
        for (Entry<K, V> e : table) {
            while (e != null) {
                Entry<K, V> next = e.next;

                // todo rehash判断
                if (rehash) {

                }
                int i = indexFor(e.hash, newCapacity);
                e.next = newTable[i];
                newTable[i] = e;

                e = next;
            }
        }
    }

    /**
     * Copies all of the mappings from the specified map to this map.
     * These mappings will replace any mappings that this map had for
     * any of the keys currently in the specified map.
     *
     * @param m mappings to be stored in this map
     * @throws NullPointerException if the specified map is null
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        int numberKeysToBeAdded = m.size();
        if (numberKeysToBeAdded == 0) {
            return;
        }

        if (table == EMPTY_TABLE) {
            inflateTable((int) Math.max(numberKeysToBeAdded * loadFactor, threshold));
        }

        /*
         * Expand the map if the map if the number of mappings to be added
         * is greater than or equal to threshold.  This is conservative; the
         * obvious condition is (m.size() + size) >= threshold, but this
         * condition could result in a map with twice the appropriate capacity,
         * if the keys to be added overlap with the keys already in this map.
         * By using the conservative calculation, we subject ourself
         * to at most one extra resize.
         */
        /**
         * 正常扩容大小判断逻辑为 : m.size() + size >= threshold
         * 防止 key 重复、相同的情况，保守判断
         */
        if (numberKeysToBeAdded >= threshold) {
            // 阈值判断
            int targetCapacity = (int) (numberKeysToBeAdded / loadFactor + 1);
            if (targetCapacity > MAXIMUM_CAPACITY) {
                targetCapacity = MAXIMUM_CAPACITY;
            }

            // 左移计算大小
            int newCapacity = table.length;
            while (newCapacity < targetCapacity) {
                newCapacity <<= 1;
            }

            // 是否需要扩容判断
            if (newCapacity > table.length) {
                resize(newCapacity);
            }
        }

        //循环 put
        for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    /**
     * Removes the mapping for the specified key from this map if present.
     *
     * @param key key whose mapping is to be removed from the map
     * @return the previous value associated with <tt>key</tt>, or
     * <tt>null</tt> if there was no mapping for <tt>key</tt>.
     * (A <tt>null</tt> return can also indicate that the map
     * previously associated <tt>null</tt> with <tt>key</tt>.)
     */
    @Override
    public V remove(Object key) {
        Entry<K, V> e = removeEntryForKey(key);
        return e == null ? null : e.getValue();
    }

    /**
     * Removes and returns the entry associated with the specified key
     * in the HashMap.  Returns null if the HashMap contains no mapping
     * for this key.
     */
    private Entry<K, V> removeEntryForKey(Object key) {
        if (size == 0) {
            return null;
        }

        int hash = key == null ? null : hash(key);
        int i = indexFor(hash, table.length);

        Entry<K, V> prev = table[i];
        Entry<K, V> e = table[i];

        while (e != null) {
            Entry<K, V> next = e.next;

            Object k;
            if (e.hash == hash &&
                    ((k = e.key) == key || (key != null && key.equals(k)))) {
                modCount++;
                size--;

                if (prev == e) {
                    table[i] = next;
                } else {
                    prev.next = next;
                }

                e.recordRemoval(this);
                return e;
            }
            prev = e;
            e = next;
        }

        // 出 while 循环后，e 为 null
        return e;
    }

    /**
     * Special version of remove for EntrySet using {@code Map.Entry.equals()}
     * for matching.
     */
    final Entry<K, V> removeMapping(Object o) {
        if (size == 0 || !(o instanceof Map)) {
            return null;
        }

        Map.Entry<K, V> entry = (Map.Entry<K, V>) o;
        K key = entry.getKey();

        int hash = key == null ? 0 : hash(key);
        int i = indexFor(hash, table.length);

        Entry<K, V> prev = table[i];
        Entry<K, V> e = prev;

        while (e != null) {
            Entry<K, V> next = e.next;

            Object k;
            // 删除的为 entry, 使用 entry 的 equals 进行判断，key + value
            if (e.hash == hash
                    && e.equals(entry)) {

                modCount++;
                size--;

                if (prev == e){
                    table[i] = next;
                }else {
                    prev.next = next;
                }

                e.recordRemoval(this);

                return e;
            }

            prev = e;
            e = next;
        }

        return e;
    }

    /**
     * Removes all of the mappings from this map.
     * The map will be empty after this call returns.
     */
    @Override
    public void clear() {
        modCount++;
        Arrays.fill(table, null);
        size = 0;
    }

    /**
     * Returns <tt>true</tt> if this map maps one or more keys to the
     * specified value.
     *
     * @param value value whose presence in this map is to be tested
     * @return <tt>true</tt> if this map maps one or more keys to the
     *         specified value
     */
    @Override
    public boolean containsValue(Object value) {
        if (value == null) {
            return containsNullValue();
        }

        Entry<K, V>[] tab = table;
        for (int i = 0; i < tab.length; i++) {
            for (Entry<K, V> e = tab[i]; e != null; e = e.next) {
                if (value.equals(e.value)){
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Special-case code for containsValue with null argument
     */
    private boolean containsNullValue() {

        Entry<K, V>[] tab = this.table;

        for (int i = 0; i < tab.length; i++) {
            for(Entry<K, V> e = tab[i]; e != null; e = e.next){
                if (e.value == null){
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Returns a shallow copy of this <tt>HashMap</tt> instance: the keys and
     * values themselves are not cloned.
     *
     * @return a shallow copy of this map
     */
    public Object clone() {
        CusHashMap<K,V> result = null;
        try {
            result = (CusHashMap<K,V>)super.clone();
        } catch (CloneNotSupportedException e) {
            // assert false;
        }
        if (result.table != EMPTY_TABLE) {
            result.inflateTable(Math.min(
                    (int) Math.min(
                            size * Math.min(1 / loadFactor, 4.0f),
                            // we have limits...
                            CusHashMap.MAXIMUM_CAPACITY),
                    table.length));
        }
        result.entrySet = null;
        result.modCount = 0;
        result.size = 0;
        result.init();
        result.putAllForCreate(this);

        return result;
    }

    static class Entry<K, V> implements Map.Entry<K, V> {

        final K key;
        V value;
        Entry<K, V> next;
        int hash;

        public Entry(int h, K k, V v, Entry<K, V> n) {
            this.key = k;
            this.value = v;
            this.next = n;
            this.hash = h;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            V oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Map.Entry)) {
                return false;
            }
            Map.Entry e = (Map.Entry) o;
            final Object k1 = getKey();
            final Object k2 = e.getKey();
            if (k1 == k2 || (k1 != null && k1.equals(k2))) {
                final Object v1 = getValue();
                final Object v2 = e.getValue();
                if (v1 == v2 || v1 != null && v1.equals(v2)) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(getKey()) ^ Objects.hashCode(getValue());
        }

        @Override
        public String toString() {
            return getKey() + "=" + getValue();
        }

        /**
         * This method is invoked whenever the value in an entry is
         * overwritten by an invocation of put(k,v) for a key k that's already
         * in the HashMap.
         */
        void recordAccess(CusHashMap<K, V> m) {
        }

        /**
         * This method is invoked whenever the entry is
         * removed from the table.
         */
        void recordRemoval(CusHashMap<K, V> m) {
        }

    }

    /**
     * Adds a new entry with the specified key, value and hash code to
     * the specified bucket.  It is the responsibility of this
     * method to resize the table if appropriate.
     *
     * Subclass overrides this to alter the behavior of put method.
     */
    // 添加实际 entry 对象
    // 同时判断是否需要扩容
    private void addEntry(int hash, K key, V value, int bucketIndex) {
        // 1. 扩容判断
        // 1.1 容量大小达到； 1.2 table[index]!=null, 哈希表不平衡
        if ((size >= threshold) && table[bucketIndex] != null) {
            // 扩容，长度 * 2，保持 pow 2 的关系
            resize(table.length * 2);

            // 扩容后 需重新计算 hash、下标
            hash = key == null ? 0 : hash(key);
            bucketIndex = indexFor(hash, table.length);

        }

        createEntry(hash, key, value, bucketIndex);
    }

    /**
     * Like addEntry except that this version is used when creating entries
     * as part of Map construction or "pseudo-construction" (cloning,
     * deserialization).  This version needn't worry about resizing the table.
     * <p>
     * Subclass overrides this to alter the behavior of HashMap(Map),
     * clone, and readObject.
     */
    private void createEntry(int hash, K key, V value, int bucketIndex) {
        // 1. 头插法
        Entry<K, V> e = table[bucketIndex];
        table[bucketIndex] = new Entry<>(hash, key, value, e);

        // 2. 添加完成后 size++
        size++;
    }


    private abstract class HashIterator<E> implements Iterator<E>{

        Entry<K,V> next;        // next entry to return
        int expectedModCount;   // For fast-fail
        int index;              // current slot
        Entry<K,V> current;     // current entry

        HashIterator(){
            expectedModCount = modCount;
            if (size > 0) {
                Entry[] t = table;
                while (index < t.length && (next = t[index++]) == null)
                    ;
            }
        }

        @Override
        public boolean hasNext() {
            return next != null;
        }

        final Entry<K, V> nextEntry(){
            if (modCount != expectedModCount) {
                throw new ConcurrentModificationException();
            }
            Entry<K, V> e = next;
            if (e == null){
                throw new NoSuchElementException();
            }

            if ((next = e.next) == null){
                Entry[] t = table;
                while (index < t.length && (next = t[index++]) == null)
                    ;
            }
            current = e;
            return e;
        }

        @Override
        public void remove() {
            if (current == null){
                throw new NoSuchElementException();
            }

            if (modCount != expectedModCount){
                throw new ConcurrentModificationException();
            }
            K key = current.key;

            CusHashMap.this.removeEntryForKey(key);

            expectedModCount = modCount;
        }
    }

    private final class valueIterator extends HashIterator<V>{
        @Override
        public V next() {
            return nextEntry().value;
        }
    }

    private final class KeyIterator extends HashIterator<K>{
        @Override
        public K next() {
            return nextEntry().getKey();
        }
    }

    private final class EntryIterator extends HashIterator<Map.Entry<K, V>>{
        @Override
        public Map.Entry<K, V> next() {
            return nextEntry();
        }
    }

    // Subclass overrides these to alter behavior of views' iterator() method
    Iterator<K> newKeyIterator(){
        return new KeyIterator();
    }

    Iterator<V> newValueIterator(){
        return new valueIterator();
    }

    Iterator<Map.Entry<K, V>> newEntryIterator(){
        return new EntryIterator();
    }

    // Views - 视图部分实现

    private transient Set<Map.Entry<K,V>> entrySet = null;

    /**
     * Returns a {@link Set} view of the keys contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation), the results of
     * the iteration are undefined.  The set supports element removal,
     * which removes the corresponding mapping from the map, via the
     * <tt>Iterator.remove</tt>, <tt>Set.remove</tt>,
     * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt>
     * operations.  It does not support the <tt>add</tt> or <tt>addAll</tt>
     * operations.
     */
    @Override
    public Set<K> keySet() {
        Set<K> ks = this.keySet;
        return ks != null ? ks : new KeySet();
    }

    private final class KeySet extends AbstractSet<K>{

        @Override
        public Iterator<K> iterator() {
            return newKeyIterator();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean contains(Object o) {
            return contains(o);
        }

        @Override
        public boolean remove(Object o) {
            return CusHashMap.this.removeEntryForKey(o) != null;
        }

        @Override
        public void clear() {
            CusHashMap.this.clear();
        }
    }

    /**
     * Returns a {@link Collection} view of the values contained in this map.
     * The collection is backed by the map, so changes to the map are
     * reflected in the collection, and vice-versa.  If the map is
     * modified while an iteration over the collection is in progress
     * (except through the iterator's own <tt>remove</tt> operation),
     * the results of the iteration are undefined.  The collection
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Collection.remove</tt>, <tt>removeAll</tt>,
     * <tt>retainAll</tt> and <tt>clear</tt> operations.  It does not
     * support the <tt>add</tt> or <tt>addAll</tt> operations.
     */
    public Collection<V> values() {
        Collection<V> vs = values;
        return (vs != null ? vs : (values = new Values()));
    }

    private final class Values extends AbstractCollection<V> {
        public Iterator<V> iterator() {
            return newValueIterator();
        }
        public int size() {
            return size;
        }
        public boolean contains(Object o) {
            return containsValue(o);
        }
        public void clear() {
            CusHashMap.this.clear();
        }
    }

    /**
     * Returns a {@link Set} view of the mappings contained in this map.
     * The set is backed by the map, so changes to the map are
     * reflected in the set, and vice-versa.  If the map is modified
     * while an iteration over the set is in progress (except through
     * the iterator's own <tt>remove</tt> operation, or through the
     * <tt>setValue</tt> operation on a map entry returned by the
     * iterator) the results of the iteration are undefined.  The set
     * supports element removal, which removes the corresponding
     * mapping from the map, via the <tt>Iterator.remove</tt>,
     * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt> and
     * <tt>clear</tt> operations.  It does not support the
     * <tt>add</tt> or <tt>addAll</tt> operations.
     *
     * @return a set view of the mappings contained in this map
     */
    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return entrySet0();
    }

    private Set<Map.Entry<K,V>> entrySet0() {
        Set<Map.Entry<K,V>> es = entrySet;
        return es != null ? es : (entrySet = new EntrySet());
    }

    private final class EntrySet extends AbstractSet<Map.Entry<K,V>> {
        public Iterator<Map.Entry<K,V>> iterator() {
            return newEntryIterator();
        }
        public boolean contains(Object o) {
            if (!(o instanceof Map.Entry))
                return false;
            Map.Entry<K,V> e = (Map.Entry<K,V>) o;
            Entry<K,V> candidate = getEntry(e.getKey());
            return candidate != null && candidate.equals(e);
        }
        public boolean remove(Object o) {
            return removeMapping(o) != null;
        }
        public int size() {
            return size;
        }
        public void clear() {
            CusHashMap.this.clear();
        }
    }

    /**
     * Save the state of the <tt>HashMap</tt> instance to a stream (i.e.,
     * serialize it).
     *
     * @serialData The <i>capacity</i> of the HashMap (the length of the
     *             bucket array) is emitted (int), followed by the
     *             <i>size</i> (an int, the number of key-value
     *             mappings), followed by the key (Object) and value (Object)
     *             for each key-value mapping.  The key-value mappings are
     *             emitted in no particular order.
     */
    private void writeObject(java.io.ObjectOutputStream s)
            throws IOException
    {
        // Write out the threshold, loadfactor, and any hidden stuff
        s.defaultWriteObject();

        // Write out number of buckets
        if (table==EMPTY_TABLE) {
            s.writeInt(roundUpToPowerOf2(threshold));
        } else {
            s.writeInt(table.length);
        }

        // Write out size (number of Mappings)
        s.writeInt(size);

        // Write out keys and values (alternating)
        if (size > 0) {
            for(Map.Entry<K,V> e : entrySet0()) {
                s.writeObject(e.getKey());
                s.writeObject(e.getValue());
            }
        }
    }

    private static final long serialVersionUID = 362498820763181265L;

    /**
     * Reconstitute the {@code HashMap} instance from a stream (i.e.,
     * deserialize it).
     */
    private void readObject(java.io.ObjectInputStream s)
            throws IOException, ClassNotFoundException
    {
        // Read in the threshold (ignored), loadfactor, and any hidden stuff
        s.defaultReadObject();
        if (loadFactor <= 0 || Float.isNaN(loadFactor)) {
            throw new InvalidObjectException("Illegal load factor: " +
                    loadFactor);
        }

        // set other fields that need values
        table = (Entry<K,V>[]) EMPTY_TABLE;

        // Read in number of buckets
        s.readInt(); // ignored.

        // Read number of mappings
        int mappings = s.readInt();
        if (mappings < 0)
            throw new InvalidObjectException("Illegal mappings count: " +
                    mappings);

        // capacity chosen by number of mappings and desired load (if >= 0.25)
        int capacity = (int) Math.min(
                mappings * Math.min(1 / loadFactor, 4.0f),
                // we have limits...
                CusHashMap.MAXIMUM_CAPACITY);

        // allocate the bucket array;
        if (mappings > 0) {
            inflateTable(capacity);
        } else {
            threshold = capacity;
        }

        init();  // Give subclass a chance to do its thing.

        // Read the keys and values, and put the mappings in the HashMap
        for (int i = 0; i < mappings; i++) {
            K key = (K) s.readObject();
            V value = (V) s.readObject();
            putForCreate(key, value);
        }
    }

    // These methods are used when serializing HashSets
    int   capacity()     { return table.length; }
    float loadFactor()   { return loadFactor;   }


    public static void main(String[] args) {

        // 1.
        int[] numbers = {1, 2, 3, 4, 6, 8, 10, 16, 20, 32};
        for (int number : numbers) {
            System.out.print(Integer.highestOneBit((number - 1) << 1));
            System.out.print(", ");
        }

        // 2.
        System.out.println(null instanceof Map);

    }

}
