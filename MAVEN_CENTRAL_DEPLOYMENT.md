# Guía para Publicar en Maven Central

Este documento describe los pasos necesarios para publicar el proyecto EventBus en Maven Central.

## Requisitos Previos

### 1. Cuenta en Sonatype OSSRH
- Crear una cuenta en [Sonatype JIRA](https://issues.sonatype.org/)
- Crear un ticket para reclamar tu groupId (ejemplo: `com.rigoberto.pr`)
- Esperar aprobación (usualmente 2 días laborables)

### 2. Configurar GPG
Necesitas una clave GPG para firmar tus artefactos:

```bash
# Generar clave GPG
gpg --gen-key

# Listar claves
gpg --list-keys

# Publicar clave pública al servidor de claves
gpg --keyserver keyserver.ubuntu.com --send-keys TU_KEY_ID
```

### 3. Configurar Maven Settings
Edita `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>ossrh</id>
      <username>tu-usuario-sonatype</username>
      <password>tu-password-sonatype</password>
    </server>
  </servers>
  
  <profiles>
    <profile>
      <id>ossrh</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.executable>gpg</gpg.executable>
        <gpg.passphrase>tu-passphrase-gpg</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>
```

**Nota de Seguridad**: Para producción, usa [Maven Password Encryption](https://maven.apache.org/guides/mini/guide-encryption.html)

## Pasos para Publicar

### 1. Verificar la Configuración del Proyecto

Asegúrate que el `pom.xml` tiene:
- ✅ Información completa del proyecto (name, description, url)
- ✅ Licencia
- ✅ Desarrolladores
- ✅ SCM (Git)
- ✅ Plugin de sources
- ✅ Plugin de javadoc
- ✅ Plugin GPG
- ✅ Plugin Nexus Staging

### 2. Actualizar la Versión

Para release, la versión NO debe terminar en `-SNAPSHOT`:
```xml
<version>1.0.0</version>
```

Para snapshot:
```xml
<version>1.0.1-SNAPSHOT</version>
```

### 3. Verificar que Todo Compila

```bash
# Compilar y ejecutar tests
mvn clean verify

# Verificar que se generan sources y javadoc
mvn clean package
ls -la target/
# Deberías ver: eventbus-1.0.0.jar, eventbus-1.0.0-sources.jar, eventbus-1.0.0-javadoc.jar
```

### 4. Desplegar a Maven Central

#### Para SNAPSHOT (desarrollo):
```bash
mvn clean deploy
```

#### Para RELEASE (versión oficial):
```bash
# Desplegar y firmar artefactos con perfil release
mvn clean deploy -P release

# O si prefieres control manual del staging:
mvn clean deploy -P release
# Luego ve a https://s01.oss.sonatype.org/ para cerrar y liberar el staging repository
```

**Nota**: El perfil `-P release` activa la firma GPG. Para desarrollo local sin GPG configurado, usa `mvn clean package`.

### 5. Verificar el Deployment

1. Ve a [Nexus Repository Manager](https://s01.oss.sonatype.org/)
2. Login con tus credenciales de Sonatype
3. Ve a "Staging Repositories"
4. Busca tu repositorio (comrigoberto-...)
5. Verifica el contenido
6. Haz click en "Close" para validar
7. Si pasa validación, haz click en "Release"

### 6. Esperar Sincronización

- El artefacto aparecerá en Maven Central en ~10 minutos
- La búsqueda en Maven Central puede tardar ~2 horas
- Verifica en: https://search.maven.org/

## Uso del Artefacto

Una vez publicado, los usuarios pueden agregarlo a sus proyectos:

```xml
<dependency>
    <groupId>com.rigoberto.pr</groupId>
    <artifactId>eventbus</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Versionado Semántico

Sigue [Semantic Versioning](https://semver.org/):
- **MAJOR** (1.x.x): Cambios incompatibles en API
- **MINOR** (x.1.x): Nueva funcionalidad compatible con versiones anteriores
- **PATCH** (x.x.1): Correcciones de bugs compatibles

## Solución de Problemas

### Error: "401 Unauthorized"
- Verifica credenciales en `~/.m2/settings.xml`
- Asegúrate que el groupId está aprobado en tu ticket de Sonatype

### Error: "No public key"
- Verifica que tu clave GPG está publicada en un keyserver público
- Usa: `gpg --keyserver keyserver.ubuntu.com --recv-keys TU_KEY_ID`

### Error al firmar
- Verifica la passphrase de GPG en settings.xml
- O usa: `mvn clean deploy -Dgpg.passphrase=tu-passphrase`

### Los artefactos no pasan validación
- Verifica que tienes sources.jar
- Verifica que tienes javadoc.jar
- Verifica que todos los POMs tienen la información requerida
- Verifica que los artefactos están firmados (.asc files)

## Referencias

- [Sonatype OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [Maven Central Requirements](https://central.sonatype.org/pages/requirements.html)
- [GPG Setup](https://central.sonatype.org/publish/requirements/gpg/)
