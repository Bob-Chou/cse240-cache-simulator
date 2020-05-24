package com.cse240.hierarchy;

import java.util.*;

/**
 * A virtual cache simulates the cache operations. It supports the following
 * features:
 * <ul>
 *     <li>
 *         It can be cascaded to arbitrary levels of hierarchy (L1 cache, L2
 *         cache, L3 cache, ...), and each level functions independently.
 *     </li>
 *     <li>
 *         It can mimic cache reads/writes and record cache hit, cache miss,
 *         but it <strong>DOES NOT</strong> care about the read/write value.
 *     </li>
 * </ul>
 *
 * @author Bob Chou
 */
public class Cache {

    public enum WriteOption {
        WRITE_BACK,
        WRITE_THROUGH
    }

    public enum EvictOption {
        LRU,
        FIFO
    }

    static private class FrameEntry {
        private long addr;
        private int tag;
        private boolean dirty;
        private long lastAccessTime;

        public FrameEntry() {
            lastAccessTime = Integer.MAX_VALUE;
            tag = -1;
        }

        public FrameEntry(long birthtime, long addr, int tag) {
            lastAccessTime = birthtime;
            dirty = false;
            this.addr = addr;
            this.tag = tag;
        }
    }

    public WriteOption writeOption;
    public EvictOption evictOption;

    public int bits;
    public int ways;

    public int addrBits;
    public int blockBits;
    public int indexBits;
    public int tagBits;

    private long clock;

    private long indexMask;
    private long tagMask;
    private Cache nextHierarchy;

    private int missCount;
    private int writeMissCount;
    private int readMissCount;
    private int hitCount;
    private int writeHitCount;
    private int readHitCount;

    private List<LinkedHashMap<Integer, FrameEntry>> content;

    /**
     * Returns the log2 result of x (ceiling), useful for determines required
     * bits given a size.
     *
     * @param x a given int
     * @return ceiling result of log2 of x
     */
    private static int log2(int x) {
        ++x;
        int bit = 0;
        while (x > 1) {
            x >>= 1;
            ++bit;
        }
        return bit;
    }

    /**
     * Returns the mask given the mask bits and offset from LSB.
     *
     * @param bits bits of mask
     * @param offset offset bits from LSB
     * @return a int represents the mask
     */
    private static long getMask(int bits, int offset) {
        if (bits < 0) {
            return 0;
        }

        long mask = 0x01;
        for (int i = 0; i < bits; ++i) {
            mask <<= 1;
            mask |= 0x01;
        }

        return mask << offset;
    }

    /**
     * Construct a cache.
     *
     * @param bits bits of cache, in terms of capacity
     * @param ways number of associative ways
     * @param addrBits bits of the OS address
     * @param blockBits bits of the block, in terms of block size
     * @param ws write scheme, either write-through or write-back
     * @param es evict scheme, either LRU or FIFO
     */
    public Cache(
            int bits,
            int ways,
            int addrBits,
            int blockBits,
            WriteOption ws,
            EvictOption es
    ) {
        this.bits = bits;
        this.ways = ways;
        this.addrBits = addrBits;
        this.blockBits = blockBits;
        this.writeOption = ws;
        this.evictOption = es;

        this.indexBits = this.bits - this.blockBits - log2(ways);
        this.tagBits = this.addrBits - this.indexBits - this.blockBits;

        this.indexMask = getMask(this.indexBits, this.blockBits);
        this.tagMask = getMask(this.tagBits, this.blockBits + this.indexBits);

        this.content = new ArrayList<>(1<<indexBits);
        // dummy fill array
        for (int i = 0; i < 1<<indexBits; ++i) {
            this.content.add(null);
        }

        // increment clock to 1 to avoid LRU logic error
        clock();
    }

    /**
     * Cascade cache hierarchy. Cascaded caches will pass reads/writes to each
     * other.
     *
     * @param next next component in memory hierarchy, typically is another
     *             cache. If null, means that this cache is the last level, and
     *             next hierarchy is main memory
     */
    public void cascade(Cache next) {
        this.nextHierarchy = next;
    }

    /**
     * Mimic a normal cache read but <strong>DOES NOT</strong> care about
     * values.
     *
     * @param addr memory address
     */
    public void read(long addr) {
        int index = getIndex(addr);
        int tag = getTag(addr);

        // get associative set
        LinkedHashMap<Integer, FrameEntry> set = content.get(index);

        if (set != null && set.containsKey(tag)) {
            // cache hit
            ++hitCount;
            ++readHitCount;

            // update latest access time
            set.get(tag).lastAccessTime = clock();
        } else {
            // cache miss
            ++missCount;
            ++readMissCount;

            // if have not access the set yet, need initialization
            if (set == null)
                set = new LinkedHashMap<>();

            // read next hierarchy
            readNextHierarchy(addr);

            // if set is full, we need to evict a block
            if (set.size() >= ways)
                evictVictim(set);

            // put read data into cache
            set.put(tag, new FrameEntry(clock(), addr, tag));
        }
    }

    /**
     * Mimic a normal cache write but <strong>DOES NOT</strong> care about
     * values.
     *
     * @param addr memory address
     */
    public void write(long addr) {
        int index = getIndex(addr);
        int tag = getTag(addr);

        // get associative set
        LinkedHashMap<Integer, FrameEntry> set = content.get(index);

        if (set != null && set.containsKey(tag)) {
            // cache hit
            ++hitCount;
            ++writeHitCount;

            // update latest access time
            set.get(tag).lastAccessTime = clock();
        } else {
            // cache miss
            ++missCount;
            ++writeMissCount;

            // if have not access the set yet, need initialization
            if (set == null)
                set = new LinkedHashMap<>();

            // if set is full, we need to evict a block
            if (set.size() >= ways)
                evictVictim(set);

            // put written data into cache
            set.put(tag, new FrameEntry(clock(), addr, tag));

            // write to next hierarchy if use write-through cache
            if (writeOption == WriteOption.WRITE_THROUGH)
                writeNextHierarchy(addr);
        }

        // set dirty bit
        set.get(tag).dirty = true;

        // update set
        content.set(index, set);
    }

    /**
     * Get statistics of hit counts.
     *
     * @return count of cache hits
     */
    public int getHitCount() {
        return hitCount;
    }

    /**
     * Get statistics of write hit counts.
     *
     * @return count of write cache hits
     */
    public int getWriteHitCount() {
        return writeHitCount;
    }

    /**
     * Get statistics of read hit counts.
     *
     * @return count of read cache hits
     */
    public int getReadHitCount() {
        return readHitCount;
    }

    /**
     * Get statistics of miss counts.
     *
     * @return count of cache misses
     */
    public int getMissCount() {
        return missCount;
    }

    /**
     * Get statistics of write miss counts.
     *
     * @return count of write cache misses
     */
    public int getWriteMissCount() {
        return writeMissCount;
    }

    /**
     * Get statistics of read miss counts.
     *
     * @return count of read cache misses
     */
    public int getReadMissCount() {
        return readMissCount;
    }

    /**
     * Internal logical clock. Monotonously increments with each read/write,
     * used for determine eviction of LRU
     *
     * @return clock value
     */
    private long clock() {
        return clock++;
    }

    /**
     * Returns the tag given an address.
     *
     * @param addr memory address
     * @return tag
     */
    private int getTag(long addr) {
        addr &= tagMask;
        return (int)(addr >> tagBits);
    }

    /**
     * Returns the index given an address.
     *
     * @param addr memory address
     * @return index
     */
    private int getIndex(long addr) {
        addr &= indexMask;
        return (int)(addr >> indexBits);
    }

    /**
     * Write to next hierarchy if it exists.
     *
     * @param addr memory address
     */
    private void writeNextHierarchy(long addr) {
        if (nextHierarchy != null) {
            nextHierarchy.write(addr);
        }
    }

    /**
     * Read from next hierarchy if it exists.
     *
     * @param addr memory address
     */
    private void readNextHierarchy(long addr) {
        if (nextHierarchy != null) {
            nextHierarchy.read(addr);
        }
    }

    /**
     * Evict a frame from given set. Will follows the LRU/FIFO rule.
     *
     * @param set associative set
     */
    private void evictVictim(LinkedHashMap<Integer, FrameEntry> set) {
        FrameEntry victim = new FrameEntry();

        switch (evictOption) {
            case LRU:
                for (int k : set.keySet()) {
                    FrameEntry v = set.get(k);
                    if (v.lastAccessTime < victim.lastAccessTime) {
                        victim = v;
                    }
                }
                break;
            case FIFO:
                Iterator<Integer> iterator = set.keySet().iterator();
                if (iterator.hasNext()) {
                    victim = set.get(iterator.next());
                }
                break;
        }

        // evict victim
        if (victim.tag != -1) {
            set.remove(victim.tag);
            // if we use write-back cache and block is dirty, need to write to
            // next level
            if (writeOption == WriteOption.WRITE_BACK && victim.dirty)
                writeNextHierarchy(victim.addr);
        }
    }
}
