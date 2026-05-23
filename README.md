<div align="center">
  <img src="src/main/resources/logo.png" alt="CineAgent Logo" width="120">
  <h1 style="color: #2196F3;">CineAgent — Sistema Multiagente de Recomendación Cinematográfica</h1>
  <h3 style="color: #4CAF50;">Sistemas Inteligentes — ETSII UPM — Curso 2025-26</h3>
</div>

<hr>

<p>
Sistema multiagente desarrollado en JADE que recomienda películas adaptadas a los gustos y preferencias del usuario. El usuario indica qué tipo de películas le apetece ver (géneros, época, valoración mínima, idioma) y el sistema analiza esa información para seleccionar y presentar las opciones cinematográficas que mejor se ajustan a lo que está buscando.
</p>


## Collaborators 

| <img src="https://github.com/Zippo231.png" width="100px;"/><br /><sub><b>Federico Pizzi</b></sub><br />[@Zippo231](https://github.com/Zippo231) | <img src="https://github.com/rocimarquez.png" width="100px;"/><br /><sub><b>Rocío Márquez</b></sub><br />[@rocimarquez](https://github.com/rocimarquez) | <img src="https://github.com/JaviPello.png" width="100px;"/><br /><sub><b>Javier Fernández-Pello</b></sub><br />[@JaviPello](https://github.com/JaviPello) | <img src="https://github.com/sergiohra.png" width="100px;"/><br /><sub><b>Sergio Hernández</b></sub><br />[@sergiohra](https://github.com/sergiohra) | <img src="https://github.com/guillermodlp.png" width="100px;"/><br /><sub><b>Guillermo De la Piñera</b></sub><br />[@guillermodlp](https://github.com/guillermodlp) |
| :---: | :---: | :---: | :---: | :---: |


---

## 🏗️ Arquitectura del sistema

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│                          Plataforma JADE                                           │
│                                                                                    │
│  ┌─────────────┐  REQUEST [movie-search]   ┌───────────────────┐                   │
│  │  UserAgent  │ ────────────────────────► │   SearchAgent     │                   │
│  │             │                           │   (Percepción)    │                   │
│  │  Formulario │ ◄─ CONFIRM─────┐          │   Consulta TMDB   │                   │
│  └─────────────┘                │          └────────┬──────────┘                   │
│                                 │                   │ PROPOSE                      │
│                                 │                   │ [movie-evaluate]             │ 
│                                 │                   │                              │
│                                 │         ┌───────────────┬───────────────┐        │
│                                 │         ▼               ▼               ▼        │
│                                 │   ┌────────────┐┌────────────────┐┌───────────┐  │
│                                 │   │            ││                ││           │  │
│                                 │   │CriticRating││CriticPopularity││CriticGenre│  │
│                                 │   │            ││                ││           │  │
│                                 │   └─────┬──────┘└───────┬────────┘└─────┬─────┘  │
│                                 │         └───────────────────────────────┘        │
│                                 │            │ INFORM                              │
│                                 │            │ [critic-vote]                       │
│                                 │            ▼                                     │
│                                 │   ┌────────────────────┐                         │
│                                 │   │    JuryAgent       │                         │
│                                 │   │  Algoritmo Borda   │                         │
│                                 │   └────────┬───────────┘                         │
│                                 │            │ INFORM                              │
│                                 │            │ [movie-results]                     │
│                                 │            ▼                                     │
│                                 │   ┌────────────────────┐                         │
│                                 └── │   DisplayAgent     │                         │
│                             CONFIRM │   Resultados con   │                         │
│                                     │   pósters          │                         │
│                                     └────────────────────┘                         │
│                                                                                    │
│                      Directory Facilitator (DF)                                    │
│               [Todos los agentes registrados aquí]                                 │
└────────────────────────────────────────────────────────────────────────────────────┘
```

### Agentes

| Agente | Rol | Comportamiento JADE |
|--------|-----|---------------------|
| `UserAgent` | Recoge preferencias mediante formulario Swing y gestiona el ciclo de búsqueda | `CyclicBehaviour` + `blockingReceive` (filtro bloqueante esperando `CONFIRM`) |
| `SearchAgent` | Consulta la API de TMDB con los filtros del usuario y distribuye las películas a los críticos | `CyclicBehaviour` + `blockingReceive` |
| `CriticAgent` (×3) | Evalúa y ordena las películas según un criterio propio (valoración, popularidad o coincidencia de género) | `CyclicBehaviour` + `blockingReceive` |
| `JuryAgent` | Recibe los tres rankings y los combina mediante el algoritmo de Borda para producir el ranking final | `CyclicBehaviour` + `blockingReceive` |
| `DisplayAgent` | Muestra el top 10 en una ventana Swing con pósters cargados de forma asíncrona | `CyclicBehaviour` + `blockingReceive` |

### Algoritmo de deliberación

El sistema usa un proceso de votación en dos niveles:

**Nivel 1 — Puntuación individual de cada CriticAgent:**

| Crítico | Criterio |
|---------|----------|
| `CriticRating` | `vote_average / 10` — premia la calidad valorada por la comunidad |
| `CriticPopularity` | `popularidad / máx. del conjunto` — premia las películas más vistas |
| `CriticGenre` | `géneros coincidentes / géneros solicitados` — premia la relevancia al usuario |

**Nivel 2 — Deliberación del JuryAgent (algoritmo de Borda):**

```
Para cada película en cada ranking:
  puntos_borda = N − posición   (N puntos al 1.º, N−1 al 2.º, …, 1 al último)

puntuación_final = suma de puntos_borda de los tres críticos

Bonus: se calcula la varianza de posiciones entre críticos como
indicador de consenso. Si varianza_media > 5.0 → ⚠️ consenso débil.
```

Los pesos de cada crítico son iguales. El consenso entre ellos se refleja
en la varianza: una varianza baja indica que los tres críticos coinciden
en la valoración de las películas; una varianza alta indica discrepancia.

## ⚙️ Instalación

### Requisitos previos

- Java 11 o superior
- Maven 3.6+
- Eclipse IDE (recomendado) o IntelliJ IDEA
- Clave de API de TMDB (gratuita)

### 1. Clonar el repositorio

```bash
git clone https://github.com/TU_USUARIO/CineAgent.git
cd CineAgent
```

### 2. Obtener JADE

1. Descarga `jade.jar` desde [jade.tilab.com](https://jade.tilab.com/dl.php?file=JADE-all-4.5.0.zip)
2. Coloca el archivo en la carpeta `lib/` del proyecto

### 3. Obtener clave API de TMDB

1. Crea una cuenta gratuita en [themoviedb.org](https://www.themoviedb.org/)
2. Ve a **Configuración → API** y crea una clave de tipo *Developer*
3. Copia la clave y pégala en `SearchAgent.java`:

```java
private static final String TMDB_API_KEY = "TU_API_KEY_AQUI";
```

### 4. Instalar dependencias Maven

```bash
mvn install
```

---

## ▶️ Ejecución

### Desde Eclipse

1. Importa el proyecto: `File → Import → Existing Maven Projects`
2. Abre `Main.java` (`src/main/java/es/upm/cineagent/launcher/Main.java`)
3. Click derecho → `Run As → Java Application`

### Desde línea de comandos

```bash
mvn package
java -jar target/cineagent-1.0-SNAPSHOT.jar
```

Al ejecutar aparecerán dos ventanas:
- **Formulario de búsqueda** (UserAgent) — introduce tus preferencias
- **Ventana de resultados** (DisplayAgent) — muestra el ranking de películas

---

## 🎬 Datos de ejemplo

El sistema acepta cualquier combinación de filtros. Algunos ejemplos:

| Ejemplo | Géneros | Valoración mín. | Época | Idioma |
|---------|---------|-----------------|-------|--------|
| Clásicos de acción | Acción, Thriller | 7.5 | Años 90 | Inglés |
| Animación familiar | Animación, Comedia | 7.0 | Años 2010 | Cualquiera |
| Cine español | Drama, Comedia | 6.0 | Años 2010 | Español |
| Ciencia ficción reciente | Ciencia Ficción | 7.0 | Años 2020 | Cualquiera |

---

## 📋 Declaración de uso de IA

En el desarrollo de este proyecto se ha utilizado Claude (Anthropic) como asistente para:
- Generación de la estructura base del código Java/JADE
- Resolución de dudas sobre la API de JADE (behaviours, DF, mensajes ACL)
- Depuración y refactorización del código
- Redacción de comentarios y documentación

Todo el código ha sido revisado, comprendido y adaptado por los miembros del grupo.

---