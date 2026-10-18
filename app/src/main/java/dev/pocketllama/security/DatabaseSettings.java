package dev.pocketllama.security;

import net.zetetic.database.sqlcipher.SQLiteConnection;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;

/** Configure every keyed connection, including secondary WAL readers. */
public final class DatabaseSettings {
    private DatabaseSettings() {}
    public static final SQLiteDatabaseHook HOOK = new SQLiteDatabaseHook() {
        @Override public void preKey(SQLiteConnection connection) {}
        @Override public void postKey(SQLiteConnection connection) {
            connection.execute("PRAGMA temp_store=MEMORY", null, null);
            // secure_delete returns its new value: consume the row, and propagate SQL errors.
            if (connection.executeForLong("PRAGMA secure_delete=ON", null, null) != 1)
                throw new IllegalStateException("Не удалось включить защиту базы. Данные сохранены");
            connection.execute("PRAGMA cipher_memory_security=ON", null, null);
        }
    };
}
