# Algoritmo de Optimización de Viajes: Branch & Bound -> TripOptimizationService

## Descripción General

Este servicio implementa un algoritmo de **Branch & Bound (Ramificación y Poda)** para optimizar rutas de viaje entre ciudades. El objetivo es **maximizar la cantidad de ciudades visitadas** dentro de un presupuesto determinado, encontrando la mejor combinación posible de destinos.

## ¿Qué Hace el Algoritmo?

El algoritmo resuelve el siguiente problema: dado un punto de partida, un presupuesto limitado y opcionalmente un número máximo de ciudades, encuentra la ruta que permita visitar la mayor cantidad de ciudades posible sin exceder el presupuesto disponible.

### Características Principales

- **Maximización de destinos**: Prioriza visitar más ciudades sobre minimizar costos
- **Restricción presupuestaria**: Respeta estrictamente el límite de gasto establecido
- **Opción de retorno**: Puede configurarse para que la ruta termine en el origen
- **Optimización inteligente**: Usa técnicas de poda para evitar explorar rutas sin potencial

## Funcionamiento del Algoritmo Branch & Bound

### 1. Fase de Inicialización

**Pre-cálculo de rutas**: Antes de comenzar la búsqueda, el algoritmo calcula todas las conexiones posibles entre pares de ciudades usando un servicio greedy (búsqueda voraz). Esto crea un "mapa" completo de rutas disponibles con sus costos, duraciones y distancias.

**Identificación de la conexión más barata**: Determina cuál es el viaje más económico entre cualquier par de ciudades. Este valor es crucial para la poda posterior.

### 2. Exploración del Árbol de Decisiones

El algoritmo funciona como un árbol donde:
- Cada **nodo** representa una ciudad en un momento específico del viaje
- Cada **rama** representa la decisión de viajar a una ciudad nueva
- Cada **camino** desde la raíz hasta una hoja es una posible ruta completa

### 3. Técnica de Poda (Bound)

La poda es lo que hace eficiente al algoritmo. En cada punto de la búsqueda, calcula:

**Bound optimista**: Estima cuántas ciudades más se podrían visitar en el mejor caso posible, dividiendo el presupuesto restante por el costo de la conexión más barata.

**Criterio de poda**: Si incluso en el escenario más optimista no se puede superar la mejor solución encontrada hasta ahora, se descarta toda esa rama del árbol sin explorarla.

```
Ejemplo:
- Presupuesto restante: €50
- Conexión más barata: €20
- Máximo optimista de ciudades adicionales: 50/20 = 2 ciudades
- Si la mejor solución actual tiene 8 ciudades y llevamos 5, como máximo llegaríamos a 7
- Como 7 < 8, podamos esta rama (no vale la pena explorarla)
```

### 4. Búsqueda Recursiva

El algoritmo explora el espacio de soluciones recursivamente:

1. **Desde cada ciudad actual**, considera todas las ciudades alcanzables
2. **Ordena las opciones** por precio (de menor a mayor) para encontrar buenas soluciones rápido
3. **Para cada opción**:
   - Verifica que no haya sido visitada (salvo en retorno al origen)
   - Comprueba que el presupuesto sea suficiente
   - Aplica la poda optimista
   - Si pasa los filtros, continúa recursivamente desde esa ciudad

### 5. Actualización de la Mejor Solución

Cada vez que se completa una ruta válida que mejora la mejor solución conocida:
- Se almacena el camino completo de ciudades
- Se guardan las métricas (precio, duración, distancia)
- Se registran los segmentos detallados del viaje

## Partes Fundamentales del Código

### 1. Caché de Conexiones (City Connection Cache)
```
Map<String, Map<String, GreedyPathDto>> cityConnectionCache
```
Estructura de datos que almacena todas las rutas precalculadas entre ciudades. Evita recalcular rutas durante la exploración, mejorando significativamente el rendimiento.

### 2. Estado de la Mejor Solución (OptimalTripState)
Objeto que mantiene la mejor solución encontrada hasta el momento:
- Ruta completa (`path`)
- Número de ciudades visitadas (`citiesVisited`)
- Costos totales (`totalPrice`, `totalDuration`, `totalDistance`)
- Score (número de ciudades, usado para comparaciones)
- Segmentos detallados del viaje

### 3. Contadores de Métricas
- `nodesExplored`: Nodos del árbol que fueron evaluados
- `nodesPruned`: Nodos que fueron descartados por la poda
Estos contadores permiten evaluar la eficiencia del algoritmo.

### 4. Función Recursiva Principal (optimizeTripSearch)
El corazón del algoritmo, que:
- Evalúa la solución actual
- Calcula el bound optimista para poda
- Genera y explora nuevas ramas
- Gestiona el backtracking (deshacer decisiones)

### 5. Manejo de Retorno al Origen
Lógica especial que permite:
- Detectar cuando se ha alcanzado el límite de ciudades
- Intentar cerrar el circuito volviendo al punto de partida
- Validar que el presupuesto permita el retorno

## Ventajas del Branch & Bound

1. **Garantía de optimalidad**: Si se explora completamente, encuentra la mejor solución posible
2. **Eficiencia mejorada**: La poda reduce drásticamente el espacio de búsqueda
3. **Flexibilidad**: Puede detenerse en cualquier momento con la mejor solución encontrada hasta ese punto
4. **Métricas de rendimiento**: Proporciona información sobre nodos explorados y podados

## Complejidad

- **Peor caso**: O(n!) donde n es el número de ciudades (sin poda efectiva)
- **Caso promedio**: Significativamente mejor gracias a la poda, especialmente cuando existe una buena solución temprana
- **Espacio**: O(n) para la recursión y almacenamiento de la solución actual

## Casos de Uso Ideales

- Planificación de viajes con presupuesto limitado
- Optimización de rutas turísticas
- Problemas de maximización de visitas con restricciones económicas
- Escenarios donde explorar muchos destinos es más importante que minimizar costos