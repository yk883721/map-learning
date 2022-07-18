package map;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    public CusHashMap(int initialCapacity, float loadFactor){
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Illegal initial capacity: " +
                    initialCapacity);
        }

        if (initialCapacity > MAXIMUM_CAPACITY){
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

    public CusHashMap(int initialCapacity){
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    public CusHashMap(){
        this(DEFAULT_INITIAL_CAPACITY, DEFAULT_LOAD_FACTOR);
    }

    private static int roundUpToPowerOf2(int number){
        // assert number >= 0 : "number must be non-negative";
        // 假设 number >= 0, number 必须为非负数

        // -1：为了处理 number = pow(2, x) 的情况，此时返回结果就是 number，而不是 2 * number
        // 所以，number 为 1也需要单独处理
        return number >= MAXIMUM_CAPACITY
                ? MAXIMUM_CAPACITY
                : (number > 1) ? Integer.highestOneBit( (number - 1) << 1 ) : 1;

    }

    private void inflateTable(int toSize){
        // Find a power of 2 >= toSize
        // 找到一个比 toSize 大的一个最小的 2 的幂次方数
        int capacity = roundUpToPowerOf2(toSize);

        // 容量为 MAXIMUM_CAPACITY 时候，threshold 直接 + 1，大于容量，意为永远不需要再扩容
        threshold = (int) Math.min(capacity * loadFactor, MAXIMUM_CAPACITY + 1);
        table = new Entry[capacity];

        // todo initHashSeedAsNeeded

    }

    final boolean initHashSeedAsNeeded(int capacity) {
        return false;
    }

//    @Override
//    public V put(K key, V value) {
//        // 1. 判断是否为初始空
//        if (table == EMPTY_TABLE){
//            // 第一次为空表，初始化扩容，threshold 即为 initialCapacity
//            inflateTable(threshold);
//        }
//
//        // 2. 判断 key 为 null, 单独处理
//        if (key == null) {
//            return putForNull(value);
//        }
//    }

    private V putForNull(V value) {
        // 1. 遍历链表, 寻找是否存在旧值
        for (Entry<K, V> e = table[0]; e != null; e = e.next){
            if (e.key == null){
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


    void init(){

    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return null;
    }

    static class Entry<K, V> implements Map.Entry<K, V>{

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
            if (!(o instanceof Map.Entry)){
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
        void recordAccess(CusHashMap<K,V> m) {
        }

        /**
         * This method is invoked whenever the entry is
         * removed from the table.
         */
        void recordRemoval(CusHashMap<K,V> m) {
        }

    }

    // 添加实际 entry 对象
    // 同时判断是否需要扩容
    private void addEntry(int hash, K key, V value, int bucketIndex) {
        // 1. 扩容判断
        // 1.1 容量大小达到； 1.2 table[index]!=null, 哈希表不平衡
        if ((size >= threshold) && table[bucketIndex] != null){
            // 扩容，长度 * 2，保持 pow 2 的关系
            resize(table.length * 2);
        }

    }

    /**
     * Rehashes the contents of this map into a new array with a
     * larger capacity.  This method is called automatically when the
     * number of keys in this map reaches its threshold.
     *
     * <p> reBalance，当 map 中的数量达到 threshold, 将老数组中的内容转移到新数组中 <p/>
     *
     * If current capacity is MAXIMUM_CAPACITY, this method does not
     * resize the map, but sets threshold to Integer.MAX_VALUE.
     * This has the effect of preventing future calls.
     *
     * <p> 如果容量已经达到最大 MAXIMUM_CAPACITY，则 threshold 也设置为 Integer.MAX_VALUE, 减少以后对该方法的调用 <p/>
     *
     * @param newCapacity the new capacity, MUST be a power of two;
     *        must be greater than current capacity unless current
     *        capacity is MAXIMUM_CAPACITY (in which case value
     *        is irrelevant).
     */
    private void resize(int newCapacity) {
        Entry[] oldTable = table;
        int oldCapacity = oldTable.length;

        // 1. 容量达到最大，不再进行扩容，以后再不再进入 resize 方法
        if (oldCapacity == MAXIMUM_CAPACITY){
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
            while (e != null){
                Entry<K, V> next = e.next;

                // todo rehash判断
                if (rehash){

                }
                int i = indexFor(e.hash, newCapacity);
                e.next = newTable[i];
                newTable[i] = e;

                e = next;
            }
        }

    }

    // 指定 hash，数组长度，返回 存放 index
    // & 操作实现取模：length 长度必须为 2 的次幂
    private int indexFor(int h, int length) {
        // assert Integer.bitCount(length) == 1 : "length must be a non-zero power of 2";
        return h & (length - 1);
    }

    public static void main(String[] args) {

        // 1.
        int[] numbers = {1, 2, 3, 4, 6, 8, 10, 16, 20, 32};
        for (int number : numbers) {
            System.out.print(Integer.highestOneBit((number - 1) << 1));
            System.out.print(", ");
        }

    }

}
