package dev.veyrath.engine.atlas;

/**
 * Primitive binary min-heap of (double key, int value) pairs.
 *
 * <p>Used by priority-flood over million-cell grids every erosion iteration, so it avoids boxing
 * and object allocation entirely. Ties are broken by insertion order (a monotonically increasing
 * sequence number), which keeps flood order and therefore drainage networks deterministic.
 */
final class MinHeap {
    private double[] keys;
    private long[] seq;
    private int[] values;
    private int size;
    private long counter;

    MinHeap(int capacity) {
        keys = new double[capacity];
        seq = new long[capacity];
        values = new int[capacity];
    }

    void clear() {
        size = 0;
        counter = 0;
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    void push(double key, int value) {
        if (size == keys.length) grow();
        int i = size++;
        long s = counter++;
        while (i > 0) {
            int p = (i - 1) >>> 1;
            if (less(keys[p], seq[p], key, s)) break;
            keys[i] = keys[p];
            seq[i] = seq[p];
            values[i] = values[p];
            i = p;
        }
        keys[i] = key;
        seq[i] = s;
        values[i] = value;
    }

    double peekKey() {
        return keys[0];
    }

    /** Removes the minimum and returns its value; its key must be read with {@link #peekKey()} first. */
    int pop() {
        int result = values[0];
        int last = --size;
        if (last > 0) {
            double k = keys[last];
            long s = seq[last];
            int v = values[last];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= last) break;
                int r = l + 1;
                int c = (r < last && less(keys[r], seq[r], keys[l], seq[l])) ? r : l;
                if (less(k, s, keys[c], seq[c])) break;
                keys[i] = keys[c];
                seq[i] = seq[c];
                values[i] = values[c];
                i = c;
            }
            keys[i] = k;
            seq[i] = s;
            values[i] = v;
        }
        return result;
    }

    private static boolean less(double k1, long s1, double k2, long s2) {
        return k1 < k2 || (k1 == k2 && s1 < s2);
    }

    private void grow() {
        int n = keys.length * 2;
        keys = java.util.Arrays.copyOf(keys, n);
        seq = java.util.Arrays.copyOf(seq, n);
        values = java.util.Arrays.copyOf(values, n);
    }
}
