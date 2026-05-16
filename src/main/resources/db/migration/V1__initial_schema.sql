-- Flyway V1: Initial Schema
-- Maps JPA entity annotations for User, Notification, and Notification.attachmentIds

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(255),
    device_token VARCHAR(255),
    created_at TIMESTAMP
);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(255) NOT NULL,
    channel VARCHAR(255) NOT NULL,
    status VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    CONSTRAINT FK9y21adhxn0ayjhfocscqox7bh FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE notification_attachment_ids (
    notification_id BIGINT NOT NULL,
    attachment_id BIGINT,
    CONSTRAINT FK32s744xjpwyx12pvdwkkv7y35 FOREIGN KEY (notification_id) REFERENCES notifications(id)
);
