#!/bin/bash

# Script para ejecutar tests de integración con PostgreSQL en Docker
# Este script inicia un contenedor PostgreSQL, ejecuta los tests manuales, y limpia después

set -e

CONTAINER_NAME="test-postgres-eventbus"
POSTGRES_DB="testdb"
POSTGRES_USER="testuser"
POSTGRES_PASSWORD="testpass"
POSTGRES_PORT="5432"

echo "🐘 Iniciando contenedor PostgreSQL..."
docker run -d --name $CONTAINER_NAME \
  -e POSTGRES_DB=$POSTGRES_DB \
  -e POSTGRES_USER=$POSTGRES_USER \
  -e POSTGRES_PASSWORD=$POSTGRES_PASSWORD \
  -p $POSTGRES_PORT:5432 \
  postgres:15-alpine

echo "⏳ Esperando a que PostgreSQL esté listo..."
sleep 5

# Verificar que PostgreSQL está listo
until docker exec $CONTAINER_NAME pg_isready -U $POSTGRES_USER; do
  echo "PostgreSQL no está listo aún, esperando..."
  sleep 2
done

echo "✅ PostgreSQL está listo!"

# Ejecutar los tests manuales
echo "🧪 Ejecutando tests de integración..."
mvn test -Dtest=EventSystemManualTest || TEST_FAILED=true

# Limpiar
echo "🧹 Limpiando contenedor PostgreSQL..."
docker stop $CONTAINER_NAME
docker rm $CONTAINER_NAME

if [ "$TEST_FAILED" = true ]; then
    echo "❌ Los tests fallaron"
    exit 1
else
    echo "✅ Todos los tests pasaron exitosamente"
    exit 0
fi
