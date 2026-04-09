# Contacto Eléctrico - Backend

Aplicación backend para el sistema de gestión de Contacto Eléctrico, desarrollada con Spring Boot y MongoDB.

## Requisitos Previos

- Java 21
- Maven 3.6.3 o superior
- MongoDB 4.4 o superior
- Git

## Configuración del Entorno Local

1. **Clonar el repositorio**
   ```bash
   git clone [URL_DEL_REPOSITORIO]
   cd backend-contactoelectrico
   ```

2. **Configurar base de datos local**
   - Asegúrate de tener MongoDB instalado y ejecutándose localmente
   - Crea una base de datos llamada `contactoelectrico`
   - Crea un usuario con los permisos necesarios o usa la configuración sin autenticación para desarrollo

3. **Configuración de propiedades**
   - Copia el archivo `application.properties` a `application-local.properties`
   - Modifica la configuración de MongoDB para apuntar a tu instancia local:
     ```properties
     spring.data.mongodb.uri=mongodb://localhost:27017/contactoelectrico
     ```

4. **Ejecutar la aplicación**
   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```
   O desde tu IDE favorito, ejecuta la clase principal `ContactoElectricoApplication` con el perfil `local`.

## Despliegue en Servidor

### Requisitos del Servidor

- Java 21 instalado
- MongoDB accesible (local o remoto)
- Maven instalado (para compilación)
- Usuario del sistema dedicado para la aplicación
- Puerto 8082 accesible (o el puerto configurado)

### Instalación como Servicio en Linux (systemd)

1. **Compilar la aplicación**
   ```bash
   mvn clean package -DskipTests
   ```

2. **Crear directorio de la aplicación**
   ```bash
   sudo mkdir -p /opt/contactoelectrico
   sudo chown -R usuario:usuario /opt/contactoelectrico
   ```

3. **Copiar archivos necesarios**
   ```bash
   cp target/ferreteria-0.0.1-SNAPSHOT.jar /opt/contactoelectrico/contactoelectrico.jar
   cp src/main/resources/application.properties /opt/contactoelectrico/
   ```


4. **Crear archivo de servicio del lado de test systemd**
   ```bash
   sudo nano /etc/systemd/system/contactoelectrico.service
   ```

   Contenido del archivo de servicio:
   ```ini
   [Unit]
   Description=Contacto Electrico Backend Service
   After=syslog.target network.target
   
   [Service]
   User=usuario
   WorkingDirectory=/opt/contactoelectrico
   ExecStart=/usr/bin/java -jar /opt/contactoelectrico/contactoelectrico.jar --spring.config.location=file:/opt/contactoelectrico/application.properties
   SuccessExitStatus=143
   
   # Configuración de reinicio automático
   Restart=always
   RestartSec=10
   
   # Configuración de logs
   StandardOutput=append:/var/log/contactoelectrico/out.log
   StandardError=append:/var/log/contactoelectrico/error.log
   
   # Configuración de memoria
   Environment="JAVA_OPTS=-Xms512m -Xmx1024m -XX:MaxMetaspaceSize=256m"
   
   [Install]
   WantedBy=multi-user.target
   ```

4.1 **Crear archivo de servicio del lado de prod systemd**
    ```bash
     sudo nano /etc/systemd/system/contacto-electrico-api-8082.service
    ```
    [Unit]
    Description=Contacto Electrico API Spring Boot API
    After=network.target
    
    [Service]
    User=root
    WorkingDirectory=/var/apps/contacto-electrico-api-8082/current
    
    # Perfil activo
    Environment=SPRING_PROFILES_ACTIVE=prod
    
    # ---- App / Server
    Environment=SPRING_APPLICATION_NAME=contactoelectrico
    Environment=SERVER_PORT=8082
    Environment=SPRING_PROFILES_ACTIVE=prod
    
    # ---- MongoDB
    Environment=SPRING_DATA_MONGODB_URI=mongodb://contactoelectrico_app:NuevaPasswordSegura2025@whatzmeapi.com:9090/contactoelectrico?authSource=test&directConnection=true
    
    # ---- Swagger/OpenAPI
    Environment=SPRINGDOC_API_DOCS_PATH=/api-docs
    Environment=SPRINGDOC_SWAGGER_UI_PATH=/swagger-ui.html
    Environment=SPRINGDOC_SWAGGER_UI_OPERATIONSSORTER=method
    Environment=SPRINGDOC_SWAGGER_UI_TAGSSORTER=alpha
    Environment=SPRINGDOC_SWAGGER_UI_TRYITOUTENABLED=true
    Environment=SPRINGDOC_SWAGGER_UI_FILTER=true
    
    # ---- Tamaños de subida
    Environment=SERVER_TOMCAT_MAX_HTTP_FORM_POST_SIZE=200MB
    Environment=SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE=200MB
    Environment=SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE=200MB
    
    # ---- Email
    Environment=SPRING_MAIL_HOST=smtp.ionos.mx
    Environment=SPRING_MAIL_PORT=587
    Environment=SPRING_MAIL_USERNAME=contacto@ciriacom.com
    Environment=APP_ADMIN_EMAIL=contacto@ciriacom.com
    Environment=SPRING_MAIL_PASSWORD=solar.120w
    Environment=SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
    Environment=SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
    
    # ---- Propiedad de negocio
    Environment=QUOTE_VALIDITY_DAYS=3
    
    # Crear carpeta de logs relativa al WorkingDirectory
    ExecStartPre=/usr/bin/mkdir -p /var/apps/contacto-electrico-api-8082/current/logs
    
    # Java + JAR  (ajusta el nombre del jar si difiere)
    ExecStart=/usr/bin/java -Xms512m -Xmx512m -jar /var/apps/contacto-electrico-api-8082/current/ferreteria-0.0.1-SNAPSHOT.jar
    
    Restart=always
    RestartSec=5
    SuccessExitStatus=143
    
    StandardOutput=journal
    StandardError=journal
    
    [Install]
    WantedBy=multi-user.target


5. **Crear directorio de logs**
   ```bash
   sudo mkdir -p /var/log/contactoelectrico
   sudo chown -R usuario:usuario /var/log/contactoelectrico
   ```

6. **Habilitar e iniciar el servicio**
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable contactoelectrico.service
   sudo systemctl start contactoelectrico.service
   ```

## Gestión del Servicio

- **Iniciar servicio**: `sudo systemctl start contactoelectrico`
- **Detener servicio**: `sudo systemctl stop contactoelectrico`
- **Reiniciar servicio**: `sudo systemctl restart contactoelectrico`
- **Ver estado**: `sudo systemctl status contactoelectrico`
- **Habilitar inicio automático**: `sudo systemctl enable contactoelectrico`

## Monitoreo de Logs

### Logs de la Aplicación
Los logs de la aplicación se guardan en el directorio `logs/application.log` dentro del directorio de la aplicación. También se pueden configurar logs adicionales según la configuración en `application.properties`.

### Ver logs en tiempo real
```bash
# Ver logs de la aplicación
tail -f /opt/contactoelectrico/logs/application.log

# Ver logs del servicio (systemd)
journalctl -u contactoelectrico -f

# Ver últimos 100 mensajes de log
journalctl -u contactoelectrico -n 100

# Ver logs desde hoy
journalctl -u contactoelectrico --since today
```

### Rotación de Logs
Los logs se rotan automáticamente cuando alcanzan 10MB y se mantienen hasta 30 archivos de respaldo según la configuración en `application.properties`.

## Variables de Entorno Importantes

| Variable | Descripción | Valor por Defecto |
|----------|-------------|-------------------|
| `SPRING_PROFILES_ACTIVE` | Perfil activo de Spring | `default` |
| `JAVA_OPTS` | Opciones de la JVM | `-Xms512m -Xmx1024m` |
| `SPRING_DATA_MONGODB_URI` | URI de conexión a MongoDB | Configurado en application.properties |

## Solución de Problemas

### La aplicación no inicia
1. Verifica que MongoDB esté en ejecución
2. Revisa los logs: `journalctl -u contactoelectrico -n 50 --no-pager`
3. Verifica que el puerto 8082 esté disponible

### Problemas de conexión a MongoDB
1. Verifica que las credenciales en `application.properties` sean correctas
2. Asegúrate de que el usuario tenga los permisos necesarios
3. Verifica que el firewall permita la conexión al puerto de MongoDB (27017 por defecto)

## Seguridad

- No expongas el puerto de MongoDB (27017) a Internet
- Usa siempre HTTPS en producción
- Mantén las credenciales en variables de entorno o en un gestor de secretos
- Actualiza regularmente las dependencias para corregir vulnerabilidades