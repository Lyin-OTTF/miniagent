package io.github.lyin.miniagent.checkpoint;

import java.util.HashMap;
import java.util.Map;

/**
 * 快照存储：sessionId → 最新一次 Checkpoint。
 *
 * <p>当前为内存实现，进程退出即丢失——这正是「崩溃后无法续跑」的真实成因之一。
 * 生产中应落到数据库或对象存储，并且只保留每个会话的最后一个快照即可满足续跑，
 * 若要回溯历史步数才需要版本化。</p>
 */
public class CheckpointStore {

    private final Map<String, Checkpoint> store = new HashMap<>();

    public void save(String sessionId, Checkpoint checkpoint) {
        store.put(sessionId, checkpoint);
    }

    /**
     * @return 该会话的最新快照，不存在时返回 {@code null}
     */
    public Checkpoint load(String sessionId) {
        return store.get(sessionId);
    }
}
