# Checklist para Publicación en Maven Central

## Pre-requisitos (hacer una sola vez)

### 1. Cuenta en Sonatype OSSRH
- [ ] Crear cuenta en https://issues.sonatype.org/
- [ ] Crear ticket JIRA para reclamar groupId `com.rigoberto.pr`
- [ ] Esperar aprobación del ticket (~2 días)

### 2. Configurar GPG
- [ ] Instalar GPG: `sudo apt-get install gnupg` (Linux) o `brew install gnupg` (Mac)
- [ ] Generar clave GPG: `gpg --gen-key`
- [ ] Listar claves: `gpg --list-keys`
- [ ] Anotar el Key ID (8 caracteres hexadecimales después de rsa)
- [ ] Publicar clave pública:
  ```bash
  gpg --keyserver keyserver.ubuntu.com --send-keys TU_KEY_ID
  gpg --keyserver keys.openpgp.org --send-keys TU_KEY_ID
  gpg --keyserver pgp.mit.edu --send-keys TU_KEY_ID
  ```
- [ ] Verificar publicación: `gpg --keyserver keyserver.ubuntu.com --recv-keys TU_KEY_ID`

### 3. Configurar Maven Settings
- [ ] Crear/editar `~/.m2/settings.xml` con las credenciales:
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
- [ ] Para producción, encriptar passwords: `mvn --encrypt-password tu-password`

## Pre-publicación (hacer cada vez)

### 4. Verificar Proyecto
- [ ] Actualizar versión en `pom.xml` (quitar `-SNAPSHOT` para release)
- [ ] Verificar que `LICENSE` existe
- [ ] Verificar que `README.md` está actualizado
- [ ] Ejecutar tests: `mvn test`
- [ ] Ejecutar tests de integración: `./run-integration-tests.sh`
- [ ] Compilar proyecto: `mvn clean package`
- [ ] Verificar que se generan los 3 JARs:
  - [ ] `eventbus-X.Y.Z.jar`
  - [ ] `eventbus-X.Y.Z-sources.jar`
  - [ ] `eventbus-X.Y.Z-javadoc.jar`

### 5. Verificar Información del POM
- [ ] groupId correcto: `com.rigoberto.pr`
- [ ] artifactId correcto: `eventbus`
- [ ] Versión correcta (sin -SNAPSHOT para release)
- [ ] Nombre del proyecto
- [ ] Descripción clara y concisa
- [ ] URL del proyecto (GitHub)
- [ ] Licencia (MIT)
- [ ] Desarrolladores con información completa
- [ ] SCM (Git) correctamente configurado

### 6. Commit y Tag
- [ ] Commit todos los cambios: `git add . && git commit -m "Release version X.Y.Z"`
- [ ] Crear tag: `git tag -a vX.Y.Z -m "Version X.Y.Z"`
- [ ] Push con tags: `git push origin main --tags`

## Publicación

### 7. Desplegar a OSSRH

#### Opción A: Usando el script (recomendado)
- [ ] Ejecutar: `./deploy.sh`
- [ ] Seleccionar opción 2 (Release)
- [ ] Confirmar deployment

#### Opción B: Manualmente
- [ ] Ejecutar: `mvn clean deploy`
- [ ] Esperar a que termine el proceso

### 8. Validar y Liberar en Nexus

- [ ] Ir a https://s01.oss.sonatype.org/
- [ ] Login con credenciales de Sonatype
- [ ] Click en "Staging Repositories" (lado izquierdo)
- [ ] Buscar tu repositorio (comrigoberto-XXXX)
- [ ] Seleccionar el repositorio
- [ ] Click en "Close" (arriba)
- [ ] Esperar validación (~5 minutos)
- [ ] Si hay errores, corregir y hacer nuevo deploy
- [ ] Si pasa validación, click en "Release"
- [ ] Confirmar el release

### 9. Verificar Publicación

- [ ] Esperar ~10 minutos
- [ ] Verificar en Maven Central: https://search.maven.org/
- [ ] Buscar: `g:com.rigoberto.pr AND a:eventbus`
- [ ] Confirmar que aparece la nueva versión
- [ ] La búsqueda puede tardar ~2 horas en indexar

### 10. Actualizar Documentación

- [ ] Actualizar README.md con nueva versión
- [ ] Crear Release en GitHub con notas de cambios
- [ ] Anunciar en redes sociales/foros (opcional)

## Post-publicación

### 11. Preparar siguiente versión

- [ ] Actualizar versión en `pom.xml` a siguiente SNAPSHOT
  - Ejemplo: Si publicaste 1.0.0, cambiar a 1.0.1-SNAPSHOT
- [ ] Commit: `git commit -am "Prepare for next development iteration"`
- [ ] Push: `git push origin main`

## Solución de Problemas

### Error: "401 Unauthorized"
- Verificar credenciales en `~/.m2/settings.xml`
- Verificar que el groupId está aprobado en tu ticket de Sonatype

### Error: "No public key"
- Verificar que la clave GPG está publicada en keyservers
- Publicar en múltiples keyservers (ubuntu, openpgp, mit)

### Error al firmar
- Verificar la passphrase de GPG en settings.xml
- O usar: `mvn clean deploy -Dgpg.passphrase=tu-passphrase`

### Validación falla en Nexus
- Verificar que tienes sources.jar
- Verificar que tienes javadoc.jar
- Verificar que todos los POMs tienen información completa
- Verificar que los artefactos están firmados (.asc files)

### No aparece en Maven Central
- Esperar 10 minutos después del release
- Verificar en https://repo1.maven.org/maven2/com/rigoberto/pr/eventbus/
- La búsqueda puede tardar ~2 horas

## Referencias

- [OSSRH Guide](https://central.sonatype.org/publish/publish-guide/)
- [Maven Central Requirements](https://central.sonatype.org/pages/requirements.html)
- [GPG Setup](https://central.sonatype.org/publish/requirements/gpg/)
- [Maven Password Encryption](https://maven.apache.org/guides/mini/guide-encryption.html)
