#!/bin/sh
docker compose up -d --build
echo ""
echo "API:        http://localhost:8080"
echo "Swagger UI: http://localhost:8080/swagger-ui.html"
