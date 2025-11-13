# 🗺️ Route Planner - Frontend

Frontend simple para interactuar con la API de Route Planner.

## 🚀 Cómo usar

### 1. Iniciar el Backend
```bash
# Desde la raíz del proyecto
mvn spring-boot:run
```

### 2. Abrir el Frontend
Abre tu navegador y ve a:
```
http://localhost:8080
```

¡Eso es todo! El frontend se sirve automáticamente desde Spring Boot.

## 📋 Algoritmos Disponibles

### Búsqueda de Caminos
- **BFS** - Breadth-First Search (búsqueda en anchura)
- **DFS** - Depth-First Search (búsqueda en profundidad)
- **Dijkstra** - Camino más corto por distancia
- **Greedy** - Optimización por precio/duración/distancia
- **Greedy City** - Búsqueda greedy entre ciudades

### Árboles de Expansión Mínima
- **Prim** - MST creciendo desde un nodo
- **Kruskal** - MST ordenando aristas

### Algoritmos Avanzados
- **Catálogo** - Ordenamiento con QuickSort/MergeSort
- **Tour TSP** - Programación Dinámica (Traveling Salesman)
- **Branch & Bound** - Optimización con presupuesto
- **Backtracking** - Todas las rutas posibles

## 🏙️ Códigos de Estaciones y Ciudades

### Aeropuertos
- `EZE` - Buenos Aires
- `GRU` - São Paulo
- `MAD` - Madrid
- `BCN` - Barcelona
- `CDG` - París
- `FCO` - Roma
- `LHR` - Londres
- `JFK` - Nueva York
- `BER` - Berlín

### Estaciones de Tren
- `MAD-ATO` - Madrid Atocha
- `BCN-SANTS` - Barcelona Sants
- `PAR-GARE` - París Gare du Nord
- `ROM-TER` - Roma Termini
- `LON-KGX` - Londres King's Cross
- `BER-HBF` - Berlin Hauptbahnhof

### Códigos de Ciudad (para endpoints que usan ciudades)
- `BUE` - Buenos Aires
- `GRU-CITY` - São Paulo
- `MAD-CITY` - Madrid
- `BCN-CITY` - Barcelona
- `PAR-CITY` - París
- `ROM-CITY` - Roma
- `LON-CITY` - Londres
- `BER-CITY` - Berlín

## 🎯 Ejemplos de Uso

### BFS: Ruta más corta en saltos
```
Desde: EZE
Hasta: MAD
Max Depth: 6
```

### Greedy: Búsqueda entre estaciones específicas
```
Desde: EZE
Hasta: MAD
Criterio: price
```

### Greedy City: Mejor combinación entre todas las estaciones de cada ciudad
```
Ciudad origen: MAD-CITY
Ciudad destino: BCN-CITY
Criterio: price

Nota: Este algoritmo prueba TODAS las combinaciones posibles:
MAD→BCN, MAD→BCN-SANTS, MAD-ATO→BCN, MAD-ATO→BCN-SANTS
y devuelve la mejor según el criterio seleccionado.
```

### Tour TSP: Visitar múltiples ciudades
```
Ciudad origen: MAD-CITY
Ciudades: BCN-CITY,PAR-CITY,ROM-CITY
Criterio: price
```

### Branch & Bound: Maximizar ciudades con presupuesto
```
Ciudad origen: MAD-CITY
Presupuesto: 500
Max ciudades: 10
¿Regresar?: ✓
```

## 🛠️ Tecnologías

- **HTML5** - Estructura
- **TailwindCSS** - Estilos (vía CDN)
- **JavaScript Vanilla** - Lógica del frontend
- **Fetch API** - Peticiones HTTP

## 📝 Notas

- El frontend está integrado en el mismo proyecto Spring Boot
- No requiere servidor separado ni configuración adicional
- Los estilos usan TailwindCSS vía CDN (sin build process)
- CORS ya está habilitado en el backend (`@CrossOrigin`)

