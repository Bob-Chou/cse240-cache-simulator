# cse240-cache-simulator
A helper program for cse240 (or any other computer architecture course!) to 
simulate cache behaviors (hits/misses). Maybe useful for solving cache 
hits/misses homework, but not sure.

## How to use
Modify the `Main.main` function with your own cache access sequence, build and 
run `Main` with
```shell
make clean && make
java -classpath out/production/cse240-cache-simulator com.cse240.Main
```

## Features
- [x] cache hit/miss statistics
- [x] associative cache
- [x] hierarchical cache (L1/L2/...)
- [x] write-back write-through cache
- [x] FIFO/LRU eviction
- [ ] TLB
- [ ] multicore cache