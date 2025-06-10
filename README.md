# <p align="center">PROYECTO DE BÚSQUEDA DISTRIBUIDA DE NÚMEROS PERFECTOS</p>

## Descripción

<p align="justify">Este proyecto implementa un sistema distribuido para la eficiente búsqueda de números perfectos dentro de un rango numérico dado. Utilizando el framework de comunicación ZeroC Ice y una arquitectura Cliente-Maestro-Trabajadores (Master-Workers), el sistema está diseñado para escalar el procesamiento de manera paralela, distribuyendo la carga computacional entre múltiples nodos.</p>

<p align="justify">La identificación de números perfectos es una tarea que, para rangos grandes, puede ser intensiva en recursos. Por ello, hemos adoptado un enfoque distribuido:</p>

* Cliente: La interfaz principal para el usuario, permitiendo la especificación del rango de búsqueda y la visualización de los resultados.
* Maestro: El componente central que recibe las solicitudes del cliente, divide el rango en subrangos manejables y coordina la distribución de estas subtareas a los trabajadores disponibles. También consolida los resultados y los envía de vuelta al cliente.
* Trabajadores: Nodos computacionales que realizan el trabajo intensivo de buscar números perfectos dentro de los subrangos asignados por el Maestro, reportando sus hallazgos de vuelta.

<p align="justify">La comunicación entre estos componentes se maneja de forma asíncrona a través de Ice, lo que garantiza un flujo de trabajo no bloqueante y una mayor eficiencia en la interacción del sistema. El proyecto está construido con Gradle y utiliza JavaFX para la interfaz gráfica del cliente, asegurando un entorno de desarrollo y ejecución robusto y modular.</p>

## Integrantes
- Daniel Esteban Arcos Cerón &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[A00400760]

- Luna Catalina Martínez Vásquez &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[A00401964]

- Renzo Fernando Mosquera Daza &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[A00401681]

- Hideki Tamura Hernández &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;[A00348618]

## Estructura del proyecto
```plaintext
PerfectNumbersDistributed/
├── build.gradle
├── settings.gradle
├── App.ice
├── client/
│   ├── build.gradle
│   ├── src/main/java/com/example/client/
│   │   ├── ClientApp.java
│   │   ├── ClientAppLauncher.java
│   │   ├── ClientNotifierI.java
│   │   └── ClientViewController.java
│   └── src/main/resources/
│       ├── client.properties
│       └── client-view.fxml
├── master/
│   ├── build.gradle
│   ├── src/main/java/com/example/master/
│   │   ├── MasterApp.java
│   │   ├── MasterControllerI.java
│   │   └── MasterServiceI.java
│   └── src/main/resources/
│       └── master.properties
├── worker/
│   ├── build.gradle
│   ├── src/main/java/com/example/worker/
│   │   ├── WorkerApp.java
│   │   ├── WorkerServiceI.java
│   │   └── WorkerUtils.java
│   └── src/main/resources/
│       └── worker.properties
└── PerfectNumbersApp/
    ├── build.gradle
    ├── bin/
    └── build/
```

## Instrucciones de ejecución

Para empezar la ejecución se deben tener en cuenta la siguiente información respecto a configuraciones y compilación:

### Configuraciones de archivos properties

Los archivos .properties son cruciales para que los componentes se encuentren entre sí. Aquí se asume que se ejecutarán todos los componentes en el mismo equipo (`localhost`). Si se ejecuta en computadores diferentes, se deberá reemplazar `127.0.0.1` o `localhost` con la dirección IP accesible del equipo correspondiente.

* En master.properties el maestro escucha en el puerto 10000 y en todas las IPs disponibles (`0.0.0.0`).
* En worker.properties el worker no especifica un puerto para su propio adaptador, dejando que ICE elija uno. Se debe asegurar que el MasterService.Proxy apunte a la IP y puerto del Maestro (`127.0.0.1:10000` para `localhost`).
* En client.properties el cliente también necesita su propio adaptador (ClientNotifierAdapter) si el Maestro va a llamarlo de vuelta. MasterService.Proxy debe apuntar al Maestro (`127.0.0.1:10000` para `localhost`).

### Compilación

Debe ejecutar en la raíz del proyecto el siguiente comando de Gradle para construir todo el proyecto. Esto incluirá la generación de las clases Java a partir de App.ice en el módulo PerfectNumbersApp, y luego la compilación de todos los módulos.

```bash
./gradlew build
```

Windows:
```bash
gradlew build
```

### 1. Ejecución del sistema

<p align="justify">Luego de tener en cuenta las anteriores recomendaciones se deben seguir los siguientes pasos:</p>

Ejecutar el comando para el slice:

```bash
gradlew.bat :PerfectNumbersApp:build
```

<p align="justify">Debe ejecutar los componentes en un orden específico: primero el Maestro, luego los Workers, y finalmente el Cliente. Abra la terminal para cada componente.</p>

#### 1.1 Terminal Master

Estando en la raíz del proyecto ejecute el comando

```bash
./gradlew.bat :master:run
```

Windows:
```bash
gradlew.bat :master:run
```

#### 1.2 Terminal(es) Workers

Para los trabajadores se puede abrir una nueva terminal por cada Worker que se desee iniciar (pueden iniciarse tantos Workers como se requieran), y ejecutar el comando

```bash
./gradlew.bat :worker:run
```

Windows:
```bash
gradlew.bat :worker:run
```

#### 1.3 Terminal Client

Finalmente para el cliente se abre una nueva terminal y se ejecuta el comando

```bash
./gradlew.bat :client:run
```

Windows:
```bash
gradlew.bat :client:run
```

### 2. Interfaz gráfica de cliente

1. Una vez que la ventana del Cliente se abra:
    * Haz clic en el botón "Actualizar Estado del Maestro".
    * El cliente intentará conectar con el Maestro y te mostrará el estado del Maestro y el número de Workers activos. Asegúrate de que el estado sea "Conectado" y que veas el número de Workers que iniciaste.
2. Ingresa un Rango de Búsqueda (ej., Inicio: 1, Fin: 100000).
3. Ingresa el Número de Workers a Usar (ej., 2, 4, o el número de Workers que iniciaste).
4. Haz clic en el botón "Iniciar Búsqueda".
5. El cliente enviará la solicitud al Maestro. El Maestro distribuirá el trabajo a los Workers. Verás la actividad de procesamiento en las terminales del Maestro y de los Workers.
6. Una vez que los Workers terminen y el Maestro consolide los resultados, el Maestro notificará al Cliente, y los números perfectos encontrados junto con los tiempos de ejecución se mostrarán en el área de resultados del Cliente. También se registrarán en el archivo `tiempos_ejecucion.txt` en la carpeta `client/`.

### Consideraciones Adicionales
* Ejecución en Diferentes Máquinas:
    * Para ejecutar en diferentes máquinas, debes reemplazar `127.0.0.1` o `localhost` en los archivos `.properties` con las direcciones IP reales y accesibles de las máquinas donde se ejecutan los servicios (Maestro, Cliente, Worker).
    * Asegúrate de que los firewalls permitan la comunicación a través de los puertos especificados (por defecto `10000` para el Maestro).
* Terminar Aplicaciones:
    * Puedes cerrar el Cliente simplemente cerrando la ventana.
    * Para el Maestro y los Workers, presiona `Ctrl+C` en sus respectivas terminales para terminar los procesos.
* Limpiar el Proyecto: Para eliminar los archivos generados y de compilación:

```bash
./gradlew clean
```