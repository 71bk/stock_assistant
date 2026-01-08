package tw.bk.appcommon.id;

import java.util.UUID;

public final class UuidIdGenerator implements IdGenerator {
    @Override
    public String nextId() {
        return UUID.randomUUID().toString();
    }
}
