# 🚀 Guía Rápida - Maven Central

## Comandos Esenciales

### Desarrollo Local
```bash
# Compilar sin firmar (desarrollo)
mvn clean package

# Verificar con tests
mvn clean verify

# Tests de integración
./run-integration-tests.sh
```

### Publicación a Maven Central

#### 1. Preparación (una sola vez)
```bash
# Generar clave GPG
gpg --gen-key

# Publicar clave en keyservers
gpg --keyserver keyserver.ubuntu.com --send-keys TU_KEY_ID
gpg --keyserver keys.openpgp.org --send-keys TU_KEY_ID
gpg --keyserver pgp.mit.edu --send-keys TU_KEY_ID

# Configurar ~/.m2/settings.xml con credenciales de Sonatype
# (ver CONFIGURATION_EXAMPLES.md)
```

#### 2. Deployment
```bash
# Opción A: Con script interactivo (recomendado)
./deploy.sh
# Selecciona opción 2 (Release)

# Opción B: Comando directo
mvn clean deploy -P release
```

#### 3. Liberar en Nexus
1. https://s01.oss.sonatype.org/
2. Staging Repositories → Busca tu repo
3. Close → Espera validación
4. Release

## Perfiles Maven Disponibles

```bash
# Sin perfil: compilación normal (sin firma GPG)
mvn clean package

# Perfil integration-tests: ejecuta tests de integración
mvn test -P integration-tests

# Perfil release: firma con GPG para Maven Central
mvn deploy -P release
```

## Verificación Rápida

```bash
# Ver versión en pom.xml
grep "<version>" pom.xml | head -1

# Ver artefactos generados
ls -lh target/*.jar

# Verificar información del proyecto
mvn help:effective-pom | grep -A 5 "<developers>"
```

## URLs Importantes

- **Sonatype JIRA**: https://issues.sonatype.org/
- **OSSRH Nexus**: https://s01.oss.sonatype.org/
- **Maven Central Search**: https://search.maven.org/
- **Tu Proyecto**: https://search.maven.org/search?q=g:com.rigoberto.pr

## Cambiar Versión

```bash
# Manualmente: edita pom.xml
<version>1.0.0</version>  # Release
<version>1.0.1-SNAPSHOT</version>  # Desarrollo

# O usa Maven Versions Plugin
mvn versions:set -DnewVersion=1.0.1-SNAPSHOT
```

## Troubleshooting One-Liners

```bash
# Verificar configuración GPG
gpg --list-secret-keys

# Verificar configuración Maven
cat ~/.m2/settings.xml

# Limpiar completamente
mvn clean && rm -rf target/

# Verificar que keyserver tiene tu clave
gpg --keyserver keyserver.ubuntu.com --recv-keys TU_KEY_ID

# Deploy con debug
mvn deploy -P release -X
```

## Pre-requisitos Checklist

- [ ] Cuenta en Sonatype JIRA
- [ ] Ticket aprobado para groupId `com.rigoberto.pr`
- [ ] Clave GPG generada y publicada
- [ ] `~/.m2/settings.xml` configurado
- [ ] Versión en pom.xml sin `-SNAPSHOT`
- [ ] Tests pasando: `mvn test`

## Post-deployment

```bash
# Actualizar a siguiente SNAPSHOT
# Edita pom.xml: 1.0.0 → 1.0.1-SNAPSHOT

# Commit
git add pom.xml
git commit -m "Prepare for next development iteration"
git push

# Tag de release
git tag -a v1.0.0 -m "Release version 1.0.0"
git push --tags
```

---

📚 **Documentación Completa**: Ver archivos `MAVEN_CENTRAL_DEPLOYMENT.md`, `DEPLOYMENT_CHECKLIST.md`, y `CONFIGURATION_EXAMPLES.md`
