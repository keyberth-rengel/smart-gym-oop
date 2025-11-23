-- data.sql
-- Seed idempotente para entorno de desarrollo H2.
-- Se eliminan filas específicas antes de insertar para evitar errores de clave duplicada
-- si la base persiste entre reinicios.
-- NOTA: Solo datos mínimos necesarios para que el script de smoke tests funcione
-- sin depender de creaciones iniciales (trainer y customer baseline + identidad).

-- Limpiar baseline (safe: si no existen, DELETE no falla en H2)
DELETE FROM bookings; -- opcional: evitar colisiones horarias residuales
DELETE FROM identity_links WHERE dni = '11111111';
DELETE FROM customers WHERE email = 'alice@example.com';
DELETE FROM trainers WHERE email = 'mike@smartgym.com';

-- Trainer baseline usado en pruebas (TEST_TRAIN)
INSERT INTO trainers (email, name, age, specialty)
VALUES ('mike@smartgym.com', 'Mike', 35, 'Strength');

-- Customer baseline (TEST_EMAIL2)
INSERT INTO customers (email, name, age, card_number)
VALUES ('alice@example.com', 'Alice', 28, NULL);

-- Identity link baseline (GOOD_DNI -> alice@example.com)
INSERT INTO identity_links (dni, email)
VALUES ('11111111', 'alice@example.com');

-- Fin de seed