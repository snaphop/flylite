package com.snaphop.flylite.test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.testcontainers.postgresql.PostgreSQLContainer;

import com.snaphop.flylite.Flylite;
import com.snaphop.flylite.api.configuration.FluentConfiguration;

/**
 * The databases every end to end test runs against. Each call to {@link #create(Path)}
 * returns a brand new empty database.
 */
public enum Db {

	SQLITE {
		@Override
		public TestDatabase create(Path dir) {
			return new TestDatabase(this, "jdbc:sqlite:" + dir.resolve("flylite-" + COUNTER.incrementAndGet() + ".db"),
					null, null);
		}
	},
	POSTGRES {
		@Override
		public TestDatabase create(Path dir) {
			var pg = Postgres.container();
			String name = "flylite_" + COUNTER.incrementAndGet();
			try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
					Statement s = c.createStatement()) {
				s.execute("CREATE DATABASE " + name);
			}
			catch (SQLException e) {
				throw new IllegalStateException(e);
			}
			String url = "jdbc:postgresql://" + pg.getHost() + ":" + pg.getMappedPort(5432) + "/" + name;
			return new TestDatabase(this, url, pg.getUsername(), pg.getPassword());
		}
	};

	private static final AtomicInteger COUNTER = new AtomicInteger();

	/**
	 * Creates a new empty database.
	 * @param dir a temporary directory owned by the test.
	 * @return database.
	 */
	public abstract TestDatabase create(Path dir);

	/**
	 * A fresh database.
	 *
	 * @param db the kind of database.
	 * @param url jdbc url.
	 * @param user user or null.
	 * @param password password or null.
	 */
	public record TestDatabase(Db db, String url, String user, String password) {

		public FluentConfiguration configure() {
			return Flylite.configure().dataSource(url, user, password);
		}

		public Jdbc jdbc() {
			return new Jdbc(this);
		}

		public boolean isPostgres() {
			return db == POSTGRES;
		}

	}

	/**
	 * One PostgreSQL container shared by all tests in the JVM. Testcontainers removes it
	 * when the JVM exits.
	 */
	static final class Postgres {

		private static final String IMAGE = System.getProperty("flylite.test.postgres.image", "postgres:17-alpine");

		private static volatile PostgreSQLContainer container;

		static synchronized PostgreSQLContainer container() {
			var c = container;
			if (c == null) {
				c = new PostgreSQLContainer(IMAGE);
				c.start();
				container = c;
			}
			return c;
		}

	}

}
