package db.javamig;

import com.snaphop.flylite.api.migration.BaseJavaMigration;

/**
 * Abstract classes are not migrations and must be skipped by the scanner.
 */
public abstract class AbstractIgnoredMigration extends BaseJavaMigration {

}
