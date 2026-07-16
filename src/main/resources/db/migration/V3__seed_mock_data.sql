INSERT INTO users (username, email, password) VALUES
('user1', 'johndoe@example.com', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5X.QhH6Dv6P.zi5j5x0YQ6mF4c6.2'), -- bcrypt hash, plaintext: password123
('user2', 'janedoe@example.com', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5X.QhH6Dv6P.zi5j5x0YQ6mF4c6.2'),
('user3', 'user123@example.com', '$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5X.QhH6Dv6P.zi5j5x0YQ6mF4c6.2');

INSERT INTO available_slots (start_time, end_time, is_reserved) VALUES
('2024-12-29 09:00:00', '2024-12-29 10:00:00', FALSE),
('2024-12-29 10:00:00', '2024-12-29 11:00:00', FALSE),
('2024-12-29 11:00:00', '2024-12-29 12:00:00', FALSE),
('2024-12-29 12:00:00', '2024-12-29 13:00:00', FALSE),
('2024-12-29 13:00:00', '2024-12-29 14:00:00', FALSE),
('2024-12-29 14:00:00', '2024-12-29 15:00:00', FALSE),
('2024-12-29 15:00:00', '2024-12-29 16:00:00', FALSE),
('2024-12-29 16:00:00', '2024-12-29 17:00:00', FALSE),
('2024-12-30 09:00:00', '2024-12-30 10:00:00', FALSE),
('2024-12-30 10:00:00', '2024-12-30 11:00:00', FALSE);
