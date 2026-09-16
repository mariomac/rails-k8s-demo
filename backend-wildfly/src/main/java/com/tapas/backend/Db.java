package com.tapas.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

final class Db {

    private Db() {
    }

    static Connection getConnection() throws SQLException {
        String host = env("DB_HOST", "tapas-db");
        String port = env("DB_PORT", "5432");
        String database = env("POSTGRES_DB", "tapas");
        String user = env("POSTGRES_USER", "tapas");
        String password = env("POSTGRES_PASSWORD", "tapas_password");

        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        return DriverManager.getConnection(url, user, password);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return (value == null || value.isEmpty()) ? fallback : value;
    }
}
