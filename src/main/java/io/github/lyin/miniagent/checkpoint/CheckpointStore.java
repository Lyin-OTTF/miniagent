package io.github.lyin.miniagent.checkpoint;

import java.util.HashMap;
import java.util.Map;

public class CheckpointStore {
    // 内存存储：sessionId → Checkpoint
    private final Map<String, Checkpoint> store = new HashMap<>();

    /**
     * 存一个快照。
     */
    public void save(String sessionId, Checkpoint checkpoint) {
        store.put(sessionId, checkpoint);
    }

    /**
     * 取一个快照，没有就返回 null。
     */
    public Checkpoint load(String sessionId) {
        return store.get(sessionId);
        //return null; // 先留空
    }
}
