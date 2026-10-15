/*
 * Copyright (C) Red Gate Software Ltd 2010-2023
 * Modifications Copyright (C) Snaphop 2026
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.snaphop.flylite.internal.sqlscript;

import org.jspecify.annotations.Nullable;

import com.snaphop.flylite.internal.logging.Log;
import com.snaphop.flylite.api.FlyliteException;
import com.snaphop.flylite.api.resource.LoadableResource;
import com.snaphop.flylite.internal.parser.Parser;
import java.util.*;

public class ParserSqlScript implements SqlScript {

	private static final Log LOG = Log.of(ParserSqlScript.class);

	/**
	 * The sql statements contained in this script.
	 */
	protected final List<SqlStatement> sqlStatements = new ArrayList<>();

	private int sqlStatementCount;

	/**
	 * Whether this SQL script contains at least one non-transactional statement.
	 */
	private boolean nonTransactionalStatementFound;

	/**
	 * The resource containing the statements.
	 */
	protected final LoadableResource resource;

	private final SqlScriptMetadata metadata;

	protected final Parser parser;

	private final boolean mixed;

	private boolean parsed;

	private final Set<SqlScript> referencedSqlScripts = new TreeSet<>();

	/**
	 * Creates a new sql script from this source.
	 * @param resource The sql script resource.
	 * @param metadataResource The sql script metadata resource.
	 * @param mixed Whether to allow mixing transactional and non-transactional statements
	 * within the same migration.
	 */
	public ParserSqlScript(Parser parser, LoadableResource resource, @Nullable LoadableResource metadataResource,
			boolean mixed) {
		this.resource = resource;
		this.metadata = SqlScriptMetadata.fromResource(metadataResource, parser);
		this.parser = parser;
		this.mixed = mixed;
	}

	protected void parse() {
		try (SqlStatementIterator sqlStatementIterator = parser.parse(resource, metadata)) {
			boolean transactionalStatementFound = false;
			while (sqlStatementIterator.hasNext()) {
				SqlStatement sqlStatement = sqlStatementIterator.next();
				this.sqlStatements.add(sqlStatement);
				sqlStatementCount++;
				if (sqlStatement.canExecuteInTransaction()) {
					transactionalStatementFound = true;
				}
				else {
					nonTransactionalStatementFound = true;
				}
				if (!mixed && transactionalStatementFound && nonTransactionalStatementFound
						&& metadata.executeInTransaction() == null && parser.configuration.isExecuteInTransaction()) {
					throw new FlyliteException(
							"Detected both transactional and non-transactional statements within the same migration"
									+ " (even though mixed is false). Offending statement found at line "
									+ sqlStatement.getLineNumber() + ": " + sqlStatement.getSql()
									+ (sqlStatement.canExecuteInTransaction() ? "" : " [non-transactional]"));
				}
				if (LOG.isDebugEnabled()) {
					LOG.debug("Found statement at line " + sqlStatement.getLineNumber() + ": " + sqlStatement.getSql()
							+ (sqlStatement.canExecuteInTransaction() ? "" : " [non-transactional]"));
				}
			}
		}
		parsed = true;
	}

	@Override
	public void validate() {
		if (!parsed) {
			parse();
		}
	}

	@Override
	public SqlStatementIterator getSqlStatements() {
		validate();
		final Iterator<SqlStatement> iterator = sqlStatements.iterator();
		return new SqlStatementIterator() {
			@Override
			public void close() {
			}

			@Override
			public boolean hasNext() {
				return iterator.hasNext();
			}

			@Override
			public SqlStatement next() {
				return iterator.next();
			}

			@Override
			public void remove() {
				iterator.remove();
			}
		};
	}

	@Override
	public int getSqlStatementCount() {
		validate();
		return sqlStatementCount;
	}

	@Override
	public Collection<SqlScript> getReferencedSqlScripts() {
		// Don't trigger validation if there can't be any referenced SQL scripts anyway
		if (parser.supportsReferencedSqlScripts()) {
			validate();
		}
		return referencedSqlScripts;
	}

	@Override
	public final LoadableResource getResource() {
		return resource;
	}

	@Override
	public boolean executeInTransaction() {
		Boolean executeInTransactionOverride = metadata.executeInTransaction();
		if (executeInTransactionOverride != null) {
			LOG.debug("Using executeInTransaction=" + executeInTransactionOverride + " from script configuration");
			return executeInTransactionOverride;
		}
		validate();
		return !nonTransactionalStatementFound && parser.configuration.isExecuteInTransaction();
	}

	@Override
	public boolean shouldExecute() {
		return metadata.shouldExecute();
	}

	@Override
	public int compareTo(SqlScript o) {
		return resource.getRelativePath().compareTo(o.getResource().getRelativePath());
	}

}
