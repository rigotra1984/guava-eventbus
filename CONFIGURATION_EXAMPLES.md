# Ejemplos de Configuración para Maven Central

## 1. Ejemplo de settings.xml

Ubicación: `~/.m2/settings.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              https://maven.apache.org/xsd/settings-1.0.0.xsd">
  
  <servers>
    <!-- Servidor de Sonatype OSSRH -->
    <server>
      <id>ossrh</id>
      <username>tu-usuario-jira-sonatype</username>
      <password>tu-password-jira-sonatype</password>
    </server>
  </servers>

  <profiles>
    <!-- Perfil para deployment a Maven Central -->
    <profile>
      <id>ossrh</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <!-- Configuración de GPG -->
        <gpg.executable>gpg</gpg.executable>
        <gpg.passphrase>tu-passphrase-gpg</gpg.passphrase>
        <!-- Opcional: especificar keyname si tienes múltiples claves -->
        <!-- <gpg.keyname>TU_KEY_ID</gpg.keyname> -->
      </properties>
    </profile>
  </profiles>

</settings>
```

## 2. Ejemplo de settings.xml con passwords encriptados (RECOMENDADO)

### Paso 1: Crear master password

```bash
mvn --encrypt-master-password
```

Ingresa tu master password cuando se te pida. El comando devolverá algo como:
```
{jSMOWnoPFgsHVpMvz5VrIt5kRbzGpI8u+9EF1iFQyJQ=}
```

### Paso 2: Crear settings-security.xml

Ubicación: `~/.m2/settings-security.xml`

```xml
<settingsSecurity>
  <master>{jSMOWnoPFgsHVpMvz5VrIt5kRbzGpI8u+9EF1iFQyJQ=}</master>
</settingsSecurity>
```

### Paso 3: Encriptar passwords

```bash
mvn --encrypt-password tu-password-sonatype
mvn --encrypt-password tu-passphrase-gpg
```

Obtendrás algo como:
```
{COQLCE6DU6GtcS5P=}
```

### Paso 4: Usar en settings.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
                              https://maven.apache.org/xsd/settings-1.0.0.xsd">
  
  <servers>
    <server>
      <id>ossrh</id>
      <username>tu-usuario-jira-sonatype</username>
      <password>{COQLCE6DU6GtcS5P=}</password>
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
        <gpg.passphrase>{Bg8KLX5NQqt7Y8gZ=}</gpg.passphrase>
      </properties>
    </profile>
  </profiles>

</settings>
```

## 3. Comandos GPG útiles

### Generar nueva clave GPG

```bash
gpg --gen-key
```

Responde las preguntas:
- Tipo de clave: RSA and RSA (default)
- Tamaño: 4096 bits (recomendado para producción)
- Validez: 0 = no expira (o el tiempo que prefieras)
- Nombre real: Tu nombre
- Email: tu-email@example.com
- Comentario: (opcional)
- Passphrase: una contraseña fuerte

### Listar claves

```bash
# Listar claves públicas
gpg --list-keys

# Listar claves privadas
gpg --list-secret-keys

# Formato corto
gpg --list-keys --keyid-format SHORT
```

### Publicar clave pública

```bash
# Obtener tu Key ID (8 caracteres hexadecimales)
gpg --list-keys --keyid-format SHORT

# Publicar en keyservers (hacer en los 3)
gpg --keyserver keyserver.ubuntu.com --send-keys TU_KEY_ID
gpg --keyserver keys.openpgp.org --send-keys TU_KEY_ID
gpg --keyserver pgp.mit.edu --send-keys TU_KEY_ID
```

### Verificar publicación

```bash
gpg --keyserver keyserver.ubuntu.com --recv-keys TU_KEY_ID
```

### Exportar clave (para backup)

```bash
# Exportar clave pública
gpg --armor --export TU_KEY_ID > public-key.asc

# Exportar clave privada (¡guárdala de forma segura!)
gpg --armor --export-secret-keys TU_KEY_ID > private-key.asc
```

### Importar clave

```bash
# Importar clave pública
gpg --import public-key.asc

# Importar clave privada
gpg --import private-key.asc
```

## 4. Variables de entorno alternativas

En lugar de poner las credenciales en `settings.xml`, puedes usar variables de entorno:

```bash
export OSSRH_USERNAME=tu-usuario-sonatype
export OSSRH_PASSWORD=tu-password-sonatype
export GPG_PASSPHRASE=tu-passphrase-gpg
export GPG_KEYNAME=TU_KEY_ID
```

Y luego modificar el comando de deployment:

```bash
mvn clean deploy \
  -Possrh \
  -Dgpg.passphrase=${GPG_PASSPHRASE} \
  -Dgpg.keyname=${GPG_KEYNAME}
```

## 5. Deployment desde CI/CD (GitHub Actions, GitLab CI, etc.)

### Configurar secretos en tu CI/CD:

- `OSSRH_USERNAME`: Tu usuario de Sonatype
- `OSSRH_PASSWORD`: Tu password de Sonatype
- `GPG_PRIVATE_KEY`: Tu clave GPG privada (exportada en formato ASCII)
- `GPG_PASSPHRASE`: Tu passphrase de GPG

### Ejemplo de GitHub Actions workflow:

```yaml
name: Publish to Maven Central

on:
  release:
    types: [created]

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
          server-id: ossrh
          server-username: MAVEN_USERNAME
          server-password: MAVEN_PASSWORD
          gpg-private-key: ${{ secrets.GPG_PRIVATE_KEY }}
          gpg-passphrase: MAVEN_GPG_PASSPHRASE
      
      - name: Publish to Maven Central
        run: mvn clean deploy -P release
        env:
          MAVEN_USERNAME: ${{ secrets.OSSRH_USERNAME }}
          MAVEN_PASSWORD: ${{ secrets.OSSRH_PASSWORD }}
          MAVEN_GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
```

## 6. Solución de problemas comunes

### Error: "Failed to execute goal org.apache.maven.plugins:maven-gpg-plugin"

**Problema**: GPG no puede acceder a la passphrase

**Solución 1**: Especificar passphrase en línea de comando:
```bash
mvn clean deploy -Dgpg.passphrase=tu-passphrase
```

**Solución 2**: Usar gpg-agent (recomendado para desarrollo local):
```bash
echo "use-agent" >> ~/.gnupg/gpg.conf
echo "pinentry-mode loopback" >> ~/.gnupg/gpg.conf
gpgconf --kill gpg-agent
```

### Error: "401 Unauthorized" en deployment

**Problema**: Credenciales incorrectas o no configuradas

**Solución**: Verificar `~/.m2/settings.xml`:
- El `<id>ossrh</id>` en `<server>` debe coincidir con el ID en distributionManagement del pom.xml
- Username y password deben ser correctos
- Si usaste encriptación, verificar que `~/.m2/settings-security.xml` existe

### Error: "No public key found"

**Problema**: La clave GPG no está publicada en keyservers públicos

**Solución**:
```bash
gpg --keyserver keyserver.ubuntu.com --send-keys TU_KEY_ID
gpg --keyserver keys.openpgp.org --send-keys TU_KEY_ID
gpg --keyserver pgp.mit.edu --send-keys TU_KEY_ID
```

### Staging repository no pasa validación

**Problema**: Faltan artefactos requeridos o tienen errores

**Soluciones**:
1. Verificar que se generan sources.jar y javadoc.jar
2. Verificar que todos los POMs tienen información completa (nombre, descripción, url, licencia, developers, scm)
3. Verificar que todos los artefactos están firmados (archivos .asc)
4. Ver logs detallados en Nexus para identificar el problema específico

## Referencias

- [Maven Encryption Guide](https://maven.apache.org/guides/mini/guide-encryption.html)
- [GPG Quick Start](https://central.sonatype.org/publish/requirements/gpg/)
- [OSSRH Publishing Guide](https://central.sonatype.org/publish/publish-guide/)
