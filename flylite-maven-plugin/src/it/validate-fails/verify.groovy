def log = new File(basedir, 'build.log').text
assert log.contains('Detected resolved migration not applied to database: 1')
return true
