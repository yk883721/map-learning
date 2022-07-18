package map;

import java.util.*;

public abstract class CusAbstractMap<K, V> implements Map<K, V> {

    protected CusAbstractMap(){

    };

    // 查询操作

    // 若数量大于 Integer.MAX_VALUE, 则返回 Integer.MAX_VALUE
    @Override
    public int size() {
        return entrySet().size();
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean containsValue(Object value) {
        final Iterator<Entry<K, V>> iterator = entrySet().iterator();

        if (value == null){
            while (iterator.hasNext()){
                final Entry<K, V> e = iterator.next();
                if (e.getValue() == null){
                    return true;
                }
            }
        }else {
            while (iterator.hasNext()){
                final Entry<K, V> e = iterator.next();
                if (e.getValue().equals(value)){
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean containsKey(Object key) {
        Iterator<Entry<K, V>> i = entrySet().iterator();
        if (key == null){
            while (i.hasNext()){
                Entry<K, V> e = i.next();
                if (e.getKey() == null){
                    return true;
                }
            }
        }else {
            while (i.hasNext()){
                Entry<K, V> e = i.next();
                if (key.equals(e.getKey())){
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public V get(Object key) {
        final Iterator<Entry<K, V>> i = entrySet().iterator();
        if (key == null){
            while (i.hasNext()){
                final Entry<K, V> e = i.next();
                if (e.getKey() == null){
                    return e.getValue();
                }
            }
        }else {
           while (i.hasNext()){
               final Entry<K, V> e = i.next();
               if (key.equals(e.getKey())){
                   return e.getValue();
               }
           }
        }
        return null;
    }

    // 更新操作
    @Override
    public V put(K key, V value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V remove(Object key) {
        final Iterator<Entry<K, V>> i = entrySet().iterator();
        Map.Entry<K, V> correctEntry = null;
        if (key == null){
            while (correctEntry == null && i.hasNext()) {
                Entry<K, V> e = i.next();
                if (e.getKey() == null) {
                    correctEntry = e;
                }
            }
        }else {
            while (correctEntry == null && i.hasNext()) {
                final Entry<K, V> e = i.next();
                if (key.equals(e.getKey())) {
                    correctEntry = e;
                }
            }
        }

        V oldValue = null;
        if (correctEntry != null){
            oldValue = correctEntry.getValue();
            i.remove();
        }

        return oldValue;
    }

    // 批量操作
    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        for (Entry<? extends K, ? extends V> e : m.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    @Override
    public void clear() {
        entrySet().clear();
    }

    // Views - 查询操作

    transient volatile Set<K> keySet = null;
    transient volatile Collection<V> values = null;

    @Override
    public Set<K> keySet() {
        if (keySet == null){
            keySet = new AbstractSet<K>() {

                public Iterator<K> iterator() {
                    return new Iterator<K>() {

                        private Iterator<Entry<K,V>> i = entrySet().iterator();

                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        public K next() {
                            return i.next().getKey();
                        }

                        public void remove() {
                            i.remove();
                        }
                    };
                }

                @Override
                public int size() {
                    return CusAbstractMap.this.size();
                }

                @Override
                public boolean isEmpty() {
                    return CusAbstractMap.this.isEmpty();
                }

                @Override
                public void clear() {
                    CusAbstractMap.this.clear();
                }

                @Override
                public boolean contains(Object k) {
                    return CusAbstractMap.this.containsKey(k);
                }

            };
        }
        return keySet;
    }

    @Override
    public Collection<V> values() {
        if (values == null) {
            values = new AbstractCollection<V>() {
                @Override
                public Iterator<V> iterator() {

                    return new Iterator<V>() {

                        private Iterator<Map.Entry<K, V>> i = entrySet().iterator();

                        @Override
                        public boolean hasNext() {
                            return i.hasNext();
                        }

                        @Override
                        public V next() {
                            return i.next().getValue();
                        }

                        @Override
                        public void remove() {
                            i.remove();
                        }
                    };
                }

                @Override
                public int size() {
                    return CusAbstractMap.this.size();
                }

                @Override
                public boolean isEmpty() {
                    return CusAbstractMap.this.isEmpty();
                }

                @Override
                public void clear() {
                    CusAbstractMap.this.clear();
                }

                @Override
                public boolean contains(Object v) {
                    return CusAbstractMap.this.containsValue(v);
                }
            };
        }
        return values;
    }

    public abstract Set<Entry<K, V>> entrySet();

    // Comparison an hashing - 比较 与 哈希

    @Override
    public boolean equals(Object o) {
        if (o == this){
            return true;
        }

        if (!(o instanceof Map)){
            return false;
        }

        Map<K, V> m = (Map<K, V>) o;
        if (m.size() != this.size()){
            return false;
        }

        try {
            final Iterator<Entry<K, V>> i = entrySet().iterator();
            while (i.hasNext()){
                final Entry<K, V> e = i.next();
                final K key = e.getKey();
                final V value = e.getValue();

                if (value == null){
                    if (!(m.get(key) == null && m.containsKey(key))){
                        return false;
                    }
                }else {
                    if (!value.equals(m.get(key))){
                        return false;
                    }
                }
            }
        } catch (ClassCastException unused) {
            return false;
        } catch (NullPointerException unused) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        final Iterator<Entry<K, V>> i = entrySet().iterator();
        while (i.hasNext()){
            h += i.next().hashCode();
        }
        return h;
    }

    @Override
    public String toString() {
        final Iterator<Entry<K, V>> i = entrySet().iterator();
        if (i.hasNext()){
            return "{}";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{");

        for (;;){
            final Entry<K, V> e = i.next();
            final K key = e.getKey();
            final V value = e.getValue();
            sb.append(key == null ? "(this Map)" : key);
            sb.append("=");
            sb.append(value == null ? "(this Map)" : value);
            if (!i.hasNext()){
                return sb.append("}").toString();
            }

            sb.append(',').append(' ');
        }
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        CusAbstractMap<K,V> result = (CusAbstractMap<K,V>)super.clone();
        result.keySet = null;
        result.values = null;
        return result;
    }


}
