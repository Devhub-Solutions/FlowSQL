package vn.io.devhub.flowsql;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Quan ly nhieu DataSet (nhieu DB / nhieu pool) theo ten: {@code registry.get("main")}. */
public class DataSetRegistry implements AutoCloseable {
    private final Map<String, DataSet> pools = new ConcurrentHashMap<>();

    public DataSet get(String name) {
        DataSet ds = pools.get(name);
        if (ds == null) {
            throw new IllegalStateException("DataSet '" + name + "' chua dang ky, goi register() truoc. Hien co: " + pools.keySet());
        }
        return ds;
    }

    public boolean contains(String name) { return pools.containsKey(name); }

    public Set<String> names() { return Collections.unmodifiableSet(pools.keySet()); }

    /** Dang ky; neu ten da ton tai thi DataSet cu duoc dong. */
    public DataSet register(String name, DataSet ds) {
        DataSet old = pools.put(name, ds);
        if (old != null && old != ds) old.close();
        return ds;
    }

    public void closeAll() {
        pools.values().forEach(DataSet::close);
        pools.clear();
    }

    @Override
    public void close() { closeAll(); }
}
