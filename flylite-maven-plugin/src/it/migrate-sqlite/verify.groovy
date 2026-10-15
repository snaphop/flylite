def log = new File(basedir, 'build.log').text
assert log.contains('Successfully applied 3 migrations to schema "main", now at version v3')
assert log.contains('Schema version: 3')
assert log.contains('| Versioned | 2       | Java')
assert log.contains('Successfully validated 3 migrations')
assert new File(basedir, 'target/it.db').exists()
return true
