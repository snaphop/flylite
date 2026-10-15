CREATE TABLE greeting (id INT NOT NULL PRIMARY KEY, text VARCHAR(100));
INSERT INTO greeting (id, text) VALUES (1, '${greeting}');
