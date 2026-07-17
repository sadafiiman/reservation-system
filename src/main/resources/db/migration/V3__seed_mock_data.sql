INSERT INTO users (username, email, password) VALUES
('user1', 'johndoe@example.com', '$2a$10$.tDe3e/15qYPiXdNpcCTmuiHoNOsTjjyOrUSuOwmD/aZgS/hybJfq'), -- plaintext: password123
('user2', 'janedoe@example.com', '$2a$10$.tDe3e/15qYPiXdNpcCTmuiHoNOsTjjyOrUSuOwmD/aZgS/hybJfq'),
('user3', 'user123@example.com', '$2a$10$.tDe3e/15qYPiXdNpcCTmuiHoNOsTjjyOrUSuOwmD/aZgS/hybJfq');

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
