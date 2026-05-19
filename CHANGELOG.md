# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.0] - 2026-05-19

### Added
- JWT authentication with stateless token handling
- Notification system with Strategy pattern (SMS, Push, Email channels)
- Flyway versioned migrations replacing Hibernate ddl-auto
- Phone NOT NULL constraint via Flyway V2 migration
- CRUD endpoints for notifications (PUT/DELETE/GET own)
- Swagger/OpenAPI documentation via springdoc
- JaCoCo code coverage with Coveralls CI upload
- CircleCI test pipeline with Gradle caching
- Docker multi-stage build with docker-compose for dev and test
- Architecture decision documentation in `docs/`

### Fixed
- Proxy-aware Swagger URLs via forwarded headers strategy
- PORT env var for Railway compatibility
- Consolidated duplicate Flyway keys in application.yml
- Input validation and enum binding on POST /notifications

### Changed
- Reorganized README with features, areas to improve, and known issues
- Moved architecture docs from `.docs/` to `docs/` for public visibility
