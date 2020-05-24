package com.cse240;

import com.cse240.hierarchy.Cache;

public class Main {

    public static void main(String[] args) {
        Cache l1Cache = new Cache(
                "L1",
                16,
                2,
                32,
                5,
                Cache.WriteOption.WRITE_BACK,
                Cache.EvictOption.LRU
        );

        Cache l2Cache = new Cache(
                "L2",
                20,
                4,
                32,
                5,
                Cache.WriteOption.WRITE_BACK,
                Cache.EvictOption.LRU
        );

        l1Cache.cascade(l2Cache);
        l1Cache.setVerbose(true);
        l2Cache.setVerbose(true);

        long a = 0x110000;
        long b = 0x120000;
        long c = 0x130000;

        for (int i = 0; i < 16384; ++i) {
            System.out.printf("-------- iter %d --------\n", i+1);
            l1Cache.read(a + (i << 2));
            l1Cache.read(b + (i << 2));
            l1Cache.write(c + (i << 2));
        }
        System.out.println();
        System.out.println("----------------- Statistics -----------------");
        System.out.printf("L1 Hit: %d, ", l1Cache.getHitCount());
        System.out.printf("L1 Miss: %d\n", l1Cache.getMissCount());
        System.out.printf("L2 Hit: %d, ", l2Cache.getHitCount());
        System.out.printf("L2 Miss: %d\n", l2Cache.getMissCount());
        System.out.println("----------------------------------------------");
    }
}
