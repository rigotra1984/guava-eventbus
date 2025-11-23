#!/bin/bash
# Script para desplegar a Maven Central (OSSRH)

echo "============================================"
echo "Deployment a Maven Central (OSSRH)"
echo "============================================"
echo ""

# Verificar que estamos en el directorio correcto
if [ ! -f "pom.xml" ]; then
    echo "❌ Error: Este script debe ejecutarse desde el directorio raíz del proyecto (donde está pom.xml)"
    exit 1
fi

# Verificar configuración de GPG
if ! command -v gpg &> /dev/null; then
    echo "❌ Error: GPG no está instalado. Instala GPG antes de continuar."
    echo "   En Ubuntu/Debian: sudo apt-get install gnupg"
    echo "   En Mac: brew install gnupg"
    exit 1
fi

# Verificar que existe una clave GPG
if ! gpg --list-secret-keys &> /dev/null; then
    echo "⚠️  Advertencia: No se encontraron claves GPG"
    echo "   Genera una clave con: gpg --gen-key"
    echo "   Y publícala con: gpg --keyserver keyserver.ubuntu.com --send-keys TU_KEY_ID"
fi

# Verificar configuración de Maven
if [ ! -f "$HOME/.m2/settings.xml" ]; then
    echo "⚠️  Advertencia: No se encontró ~/.m2/settings.xml"
    echo "   Necesitas configurar tus credenciales de Sonatype"
    echo "   Revisa MAVEN_CENTRAL_DEPLOYMENT.md para más detalles"
fi

echo "Opciones de deployment:"
echo "1. Snapshot (desarrollo) - Publica versión SNAPSHOT"
echo "2. Release (producción) - Publica versión oficial"
echo "3. Dry-run (prueba) - Compila y verifica sin publicar"
echo "4. Cancelar"
echo ""
read -p "Selecciona una opción (1-4): " option

case $option in
    1)
        echo ""
        echo "📦 Desplegando SNAPSHOT..."
        echo "   Asegúrate que la versión en pom.xml termine en -SNAPSHOT"
        echo ""
        read -p "¿Continuar? (y/n): " confirm
        if [ "$confirm" == "y" ]; then
            mvn clean deploy
        fi
        ;;
    2)
        echo ""
        echo "🚀 Desplegando RELEASE..."
        echo "   Asegúrate que la versión en pom.xml NO termine en -SNAPSHOT"
        echo "   Esta versión será publicada en Maven Central"
        echo ""
        read -p "¿Estás seguro? (y/n): " confirm
        if [ "$confirm" == "y" ]; then
            mvn clean deploy -P release
            echo ""
            echo "✅ Deployment completado"
            echo "   Ve a https://s01.oss.sonatype.org/ para verificar y liberar"
            echo "   1. Login con tus credenciales"
            echo "   2. Ve a 'Staging Repositories'"
            echo "   3. Busca tu repositorio y selecciónalo"
            echo "   4. Click en 'Close' para validar"
            echo "   5. Si pasa validación, click en 'Release'"
        fi
        ;;
    3)
        echo ""
        echo "🔍 Ejecutando dry-run (verificación)..."
        mvn clean verify
        echo ""
        echo "✅ Verificación completada"
        echo "   Revisa los archivos generados en target/"
        ls -lh target/*.jar
        ;;
    4)
        echo "Operación cancelada"
        exit 0
        ;;
    *)
        echo "❌ Opción inválida"
        exit 1
        ;;
esac

echo ""
echo "============================================"
echo "Deployment finalizado"
echo "============================================"
