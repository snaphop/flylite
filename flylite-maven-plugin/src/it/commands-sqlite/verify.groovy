def log = new File(basedir, 'build.log').text
assert log.contains('Skipping Flylite execution')
assert log.contains('Successfully baselined schema with version: 1')
assert log.contains('Successfully applied 1 migration to schema "main", now at version v2')
assert log.contains('Successfully repaired schema history table')
assert log.contains('it baseline')
assert log.contains('Successfully cleaned schema "main"')
assert log.contains('now at version v1')
return true
