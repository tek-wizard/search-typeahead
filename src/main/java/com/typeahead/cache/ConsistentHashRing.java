package com.typeahead.cache;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Consistent hash ring using MD5-based hashing.
 * Each physical node gets VIRTUAL_NODES virtual positions on the ring.
 * This spreads keys evenly and minimizes remapping when nodes change.
 */
public class ConsistentHashRing {

    private static final int VIRTUAL_NODES = 150;

    private final TreeMap<Long, String> ring = new TreeMap<>();

    public ConsistentHashRing(List<String> nodeNames) {
        for (String node : nodeNames) {
            for (int i = 0; i < VIRTUAL_NODES; i++) {
                long hash = hash(node + "#" + i);
                ring.put(hash, node);
            }
        }
    }

    /**
     * Returns the node responsible for the given key.
     * Finds the first node clockwise from the key's hash position.
     */
    public String getNode(String key) {
        if (ring.isEmpty()) throw new IllegalStateException("No nodes in ring");
        long hash = hash(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(hash);
        if (entry == null) {
            entry = ring.firstEntry(); // wrap around
        }
        return entry.getValue();
    }

    public long getHashPosition(String key) {
        return hash(key);
    }

    private long hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (digest[i] & 0xFF);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
