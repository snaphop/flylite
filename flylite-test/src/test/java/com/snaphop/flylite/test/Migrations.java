package com.snaphop.flylite.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes migration scripts to a directory used as a {@code filesystem:} location so tests
 * can add, change and remove migrations between runs.
 */
public final class Migrations {

	private final Path dir;

	public Migrations(Path dir) {
		this.dir = dir;
		try {
			Files.createDirectories(dir);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public String location() {
		return "filesystem:" + dir;
	}

	public Path dir() {
		return dir;
	}

	public Migrations add(String name, String sql) {
		return add(name, sql, StandardCharsets.UTF_8);
	}

	public Migrations add(String name, String sql, Charset charset) {
		try {
			Path file = dir.resolve(name);
			Files.createDirectories(file.getParent());
			Files.writeString(file, sql, charset);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	public Migrations remove(String name) {
		try {
			Files.delete(dir.resolve(name));
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return this;
	}

	/**
	 * The standard set of migrations used by many tests: two tables, a population script
	 * and a repeatable view.
	 * @return this.
	 */
	public Migrations standard() {
		return add("V1__Create_person.sql", """
				CREATE TABLE person (
				  id INT NOT NULL PRIMARY KEY,
				  name VARCHAR(100) NOT NULL
				);
				""") //
			.add("V1_1__Populate_person.sql", """
					-- a comment; with a semicolon
					INSERT INTO person (id, name) VALUES (1, 'Mr. Semicolon;');
					INSERT INTO person (id, name) VALUES (2, 'Mrs. ''Quote''');
					""") //
			.add("V2__Create_address.sql", """
					/* block comment */
					CREATE TABLE address (
					  id INT NOT NULL PRIMARY KEY,
					  person_id INT NOT NULL REFERENCES person (id),
					  street VARCHAR(100)
					);
					INSERT INTO address (id, person_id, street) VALUES (1, 1, 'Main St');
					""") //
			.add("R__Person_view.sql",
					"""
							DROP VIEW IF EXISTS person_view;
							CREATE VIEW person_view AS SELECT p.name, a.street FROM person p JOIN address a ON a.person_id = p.id;
							""");
	}

}
