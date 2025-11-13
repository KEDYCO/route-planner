// Configuración de la API
const API_BASE_URL = 'http://localhost:8080/graph';

// Variables globales para almacenar ciudades y estaciones
let cities = [];
let stations = [];

// Cargar ciudades y estaciones al inicio
async function loadCitiesAndStations() {
    try {
        // Obtener catálogo completo
        const response = await fetch(`${API_BASE_URL}/catalog?sortAlgorithm=quicksort`);
        const catalog = await response.json();
        
        // Extraer códigos de ciudad únicos (ej: MAD-CITY, BCN-CITY)
        const cityCodeSet = new Set();
        const cityMap = new Map(); // Para mostrar nombre + código
        
        catalog.byCountry.forEach(country => {
            country.airports.forEach(station => {
                cityCodeSet.add(station.cityCode);
                cityMap.set(station.cityCode, station.cityName);
            });
            country.trainStations.forEach(station => {
                cityCodeSet.add(station.cityCode);
                cityMap.set(station.cityCode, station.cityName);
            });
        });
        
        // Crear array de objetos {code, name} y ordenar por nombre
        cities = Array.from(cityCodeSet).map(code => ({
            code: code,
            name: cityMap.get(code)
        })).sort((a, b) => a.name.localeCompare(b.name));
        
        // Extraer todas las estaciones con sus nombres y tipos
        const stationMap = new Map();
        catalog.byCountry.forEach(country => {
            country.airports.forEach(station => {
                stationMap.set(station.code, {
                    name: station.name,
                    type: 'AIRPORT'
                });
            });
            country.trainStations.forEach(station => {
                stationMap.set(station.code, {
                    name: station.name,
                    type: 'TRAIN'
                });
            });
        });
        
        // Crear array de objetos {code, name, type} y ordenar por código
        stations = Array.from(stationMap.entries()).map(([code, info]) => ({
            code: code,
            name: info.name,
            type: info.type
        })).sort((a, b) => a.code.localeCompare(b.code));
        
        // Poblar todos los dropdowns
        populateDropdowns();
        
    } catch (error) {
        console.error('Error cargando ciudades y estaciones:', error);
    }
}

// Poblar dropdowns con las opciones disponibles
function populateDropdowns() {
    // Poblar dropdowns de ciudades
    const citySelects = document.querySelectorAll('.city-select');
    citySelects.forEach(select => {
        select.innerHTML = '<option value="">-- Selecciona una ciudad --</option>';
        cities.forEach(city => {
            const option = document.createElement('option');
            option.value = city.code;
            option.textContent = `🏙️ ${city.name} (${city.code})`;
            select.appendChild(option);
        });
    });
    
    // Poblar dropdowns de estaciones
    const stationSelects = document.querySelectorAll('.station-select');
    stationSelects.forEach(select => {
        select.innerHTML = '<option value="">-- Selecciona una estación --</option>';
        stations.forEach(station => {
            const option = document.createElement('option');
            option.value = station.code;
            // Emoji según el tipo de estación
            const emoji = station.type === 'AIRPORT' ? '✈️' : '🚂';
            option.textContent = `${emoji} ${station.code} - ${station.name}`;
            select.appendChild(option);
        });
    });
}

// Llamar a la función de carga cuando se carga la página
document.addEventListener('DOMContentLoaded', () => {
    loadCitiesAndStations();
});

// Función auxiliar para obtener el emoji de una estación según su código
function getStationEmoji(stationCode) {
    // Si termina en -CITY, es una ciudad
    if (stationCode && stationCode.endsWith('-CITY')) {
        return '🏙️';
    }
    
    // Buscar en el array de estaciones
    const station = stations.find(s => s.code === stationCode);
    if (station) {
        return station.type === 'AIRPORT' ? '✈️' : '🚂';
    }
    
    // Por defecto, si no encontramos info, intentamos adivinar por el código
    // Los aeropuertos suelen ser códigos de 3 letras (EZE, MAD, BCN)
    // Las estaciones de tren suelen tener guiones (MAD-ATO, BCN-SANTS)
    if (stationCode && stationCode.includes('-') && !stationCode.endsWith('-CITY')) {
        return '🚂';
    }
    
    return '✈️'; // Por defecto aeropuerto
}

// Función para mostrar el formulario del algoritmo seleccionado
function showAlgorithm(algorithm) {
    // Ocultar todos los formularios
    document.querySelectorAll('.algorithm-form').forEach(form => {
        form.classList.add('hidden');
    });
    
    // Mostrar el formulario seleccionado
    const formId = `form-${algorithm}`;
    document.getElementById(formId).classList.remove('hidden');
    
    // Ocultar resultados previos
    document.getElementById('results').classList.add('hidden');
    
    // Scroll suave al formulario
    document.getElementById(formId).scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// Función auxiliar para mostrar resultados
function showResults(data) {
    const resultsDiv = document.getElementById('results');
    const resultsContent = document.getElementById('results-content');
    const loading = document.getElementById('loading');
    
    loading.classList.add('hidden');
    resultsDiv.classList.remove('hidden');
    
    // Generar HTML formateado según el tipo de respuesta
    const formattedHtml = formatResults(data);
    resultsContent.innerHTML = formattedHtml;
    
    // Scroll a los resultados
    resultsDiv.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// Función para formatear resultados de forma user-friendly
function formatResults(data) {
    // Si hay error
    if (data.error) {
        return `
            <div class="bg-red-50 border-l-4 border-red-500 p-4 mb-4">
                <p class="text-red-700 font-semibold">❌ Error</p>
                <p class="text-red-600">${data.message}</p>
            </div>
        `;
    }
    
    // BFS/DFS/Dijkstra - PathDto
    if (data.path && Array.isArray(data.path)) {
        return formatPathResult(data);
    }
    
    // Greedy - GreedyPathDto
    if (data.segments && data.criterion) {
        return formatGreedyResult(data);
    }
    
    // All Paths - AllPathsDto
    if (data.paths && Array.isArray(data.paths)) {
        return formatAllPathsResult(data);
    }
    
    // Tour - MultiCityTourDto
    if (data.tourOrder && data.segments) {
        return formatTourResult(data);
    }
    
    // Optimize Trip - OptimalTripDto
    if (data.optimalRoute && data.budget !== undefined) {
        return formatOptimizeTripResult(data);
    }
    
    // MST - MSTDto
    if (data.edges && data.totalWeight !== undefined) {
        return formatMSTResult(data);
    }
    
    // Catalog - CatalogDto
    if (data.stats && data.byCountry) {
        return formatCatalogResult(data);
    }
    
    // Default: JSON formateado
    return `<pre class="text-sm bg-gray-50 p-4 rounded">${JSON.stringify(data, null, 2)}</pre>`;
}

// Formatear resultados de Path (BFS/DFS/Dijkstra)
function formatPathResult(data) {
    const hasPath = data.path && data.path.length > 0;
    
    if (!hasPath) {
        return `
            <div class="bg-yellow-50 border-l-4 border-yellow-500 p-4">
                <p class="text-yellow-700 font-semibold">⚠️ No se encontró ruta</p>
            </div>
        `;
    }
    
    return `
        <div class="space-y-4">
            <div class="bg-green-50 border-l-4 border-green-500 p-4">
                <p class="text-green-700 font-semibold">✅ Ruta encontrada</p>
            </div>
            
            <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div class="bg-blue-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">Número de Saltos</p>
                    <p class="text-2xl font-bold text-blue-600">${data.hops}</p>
                </div>
                <div class="bg-purple-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">Nodos Visitados</p>
                    <p class="text-2xl font-bold text-purple-600">${data.visited.length}</p>
                </div>
                <div class="bg-orange-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">Distancia Total</p>
                    <p class="text-2xl font-bold text-orange-600">${data.totalDistance ? data.totalDistance.toFixed(0) + ' km' : 'N/A'}</p>
                </div>
            </div>
            
            <div class="bg-white border-2 border-gray-200 rounded-lg p-4">
                <h3 class="font-semibold text-gray-700 mb-3">🛤️ Ruta:</h3>
                <div class="flex flex-wrap items-center gap-2">
                    ${data.path.map((station, idx) => `
                        <span class="bg-indigo-100 text-indigo-800 px-3 py-1 rounded-full font-mono font-semibold">${getStationEmoji(station)} ${station}</span>
                        ${idx < data.path.length - 1 ? '<span class="text-gray-400">→</span>' : ''}
                    `).join('')}
                </div>
            </div>
            
            <details class="bg-gray-50 rounded-lg p-4">
                <summary class="cursor-pointer font-semibold text-gray-700">📊 Ver JSON completo</summary>
                <pre class="mt-2 text-xs bg-white p-2 rounded">${JSON.stringify(data, null, 2)}</pre>
            </details>
        </div>
    `;
}

// Formatear resultados de Greedy
function formatGreedyResult(data) {
    const hasPath = data.path && data.path.length > 0;
    
    if (!hasPath) {
        return `
            <div class="bg-yellow-50 border-l-4 border-yellow-500 p-4">
                <p class="text-yellow-700 font-semibold">⚠️ No se encontró ruta</p>
            </div>
        `;
    }
    
    return `
        <div class="space-y-4">
            <div class="bg-green-50 border-l-4 border-green-500 p-4">
                <p class="text-green-700 font-semibold">✅ Ruta Greedy encontrada (Criterio: ${data.criterion})</p>
            </div>
            
            <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div class="bg-blue-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">Saltos</p>
                    <p class="text-2xl font-bold text-blue-600">${data.hops}</p>
                </div>
                <div class="bg-green-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">💰 Precio</p>
                    <p class="text-2xl font-bold text-green-600">${data.totalPrice ? '€' + data.totalPrice.toFixed(2) : 'N/A'}</p>
                </div>
                <div class="bg-purple-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">⏱️ Duración</p>
                    <p class="text-2xl font-bold text-purple-600">${data.totalDuration ? data.totalDuration.toFixed(1) + 'h' : 'N/A'}</p>
                </div>
                <div class="bg-orange-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">📏 Distancia</p>
                    <p class="text-2xl font-bold text-orange-600">${data.totalDistance ? data.totalDistance.toFixed(0) + ' km' : 'N/A'}</p>
                </div>
            </div>
            
            <div class="bg-white border-2 border-gray-200 rounded-lg p-4">
                <h3 class="font-semibold text-gray-700 mb-3">🛤️ Ruta:</h3>
                <div class="flex flex-wrap items-center gap-2 mb-4">
                    ${data.path.map((station, idx) => `
                        <span class="bg-indigo-100 text-indigo-800 px-3 py-1 rounded-full font-mono font-semibold">${getStationEmoji(station)} ${station}</span>
                        ${idx < data.path.length - 1 ? '<span class="text-gray-400">→</span>' : ''}
                    `).join('')}
                </div>
                
                ${data.segments && data.segments.length > 0 ? `
                    <h3 class="font-semibold text-gray-700 mb-2 mt-4">✈️ Detalles de Segmentos:</h3>
                    <div class="space-y-2">
                        ${data.segments.map((seg, idx) => `
                            <div class="bg-gray-50 p-3 rounded">
                                <div class="flex items-center justify-between">
                                    <span class="font-semibold">${idx + 1}. ${getStationEmoji(seg.from)} ${seg.fromName || seg.from} → ${getStationEmoji(seg.to)} ${seg.toName || seg.to}</span>
                                    <span class="text-xs bg-blue-100 text-blue-800 px-2 py-1 rounded">${seg.mode} - ${seg.carrier}</span>
                                </div>
                                <div class="text-sm text-gray-600 mt-1">
                                    💰 €${seg.price ? seg.price.toFixed(2) : 'N/A'} | 
                                    ⏱️ ${seg.duration ? seg.duration.toFixed(1) + 'h' : 'N/A'} | 
                                    📏 ${seg.distance ? seg.distance.toFixed(0) + ' km' : 'N/A'}
                                </div>
                            </div>
                        `).join('')}
                    </div>
                ` : ''}
            </div>
            
            <details class="bg-gray-50 rounded-lg p-4">
                <summary class="cursor-pointer font-semibold text-gray-700">📊 Ver JSON completo</summary>
                <pre class="mt-2 text-xs bg-white p-2 rounded">${JSON.stringify(data, null, 2)}</pre>
            </details>
        </div>
    `;
}

// Formatear All Paths (Backtracking)
function formatAllPathsResult(data) {
    return `
        <div class="space-y-4">
            <div class="bg-green-50 border-l-4 border-green-500 p-4">
                <p class="text-green-700 font-semibold">✅ ${data.totalPathsFound} rutas encontradas</p>
                <p class="text-sm text-gray-600 mt-1">${data.message}</p>
            </div>
            
            <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div class="bg-blue-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">⏱️ Tiempo</p>
                    <p class="text-xl font-bold text-blue-600">${data.executionTimeMs.toFixed(2)}ms</p>
                </div>
                <div class="bg-purple-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">🔍 Nodos Explorados</p>
                    <p class="text-xl font-bold text-purple-600">${data.nodesExplored}</p>
                </div>
                <div class="bg-green-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">💰 Más Barata</p>
                    <p class="text-xl font-bold text-green-600">€${data.cheapestPath ? data.cheapestPath.totalPrice.toFixed(2) : 'N/A'}</p>
                </div>
                <div class="bg-orange-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">⚡ Más Rápida</p>
                    <p class="text-xl font-bold text-orange-600">${data.fastestPath ? data.fastestPath.totalDuration.toFixed(1) + 'h' : 'N/A'}</p>
                </div>
            </div>
            
            ${data.paths && data.paths.length > 0 ? `
                <div class="bg-white border-2 border-gray-200 rounded-lg p-4">
                    <h3 class="font-semibold text-gray-700 mb-3">🛤️ Top ${Math.min(5, data.paths.length)} Rutas:</h3>
                    <div class="space-y-3">
                        ${data.paths.slice(0, 5).map((path, idx) => `
                            <div class="bg-gray-50 p-3 rounded">
                                <div class="flex items-center justify-between mb-2">
                                    <span class="font-semibold">Ruta ${idx + 1} (${path.stops} paradas)</span>
                                    <span class="text-sm">
                                        💰 €${path.totalPrice.toFixed(2)} | 
                                        ⏱️ ${path.totalDuration.toFixed(1)}h | 
                                        📏 ${path.totalDistance.toFixed(0)}km
                                    </span>
                                </div>
                                <div class="flex flex-wrap items-center gap-1">
                                    ${path.path.map((station, sidx) => `
                                        <span class="text-xs bg-indigo-100 text-indigo-800 px-2 py-0.5 rounded font-mono">${getStationEmoji(station)} ${station}</span>
                                        ${sidx < path.path.length - 1 ? '<span class="text-gray-400 text-xs">→</span>' : ''}
                                    `).join('')}
                                </div>
                            </div>
                        `).join('')}
                    </div>
                    ${data.paths.length > 5 ? `<p class="text-sm text-gray-500 mt-2">... y ${data.paths.length - 5} rutas más</p>` : ''}
                </div>
            ` : ''}
            
            <details class="bg-gray-50 rounded-lg p-4">
                <summary class="cursor-pointer font-semibold text-gray-700">📊 Ver JSON completo</summary>
                <pre class="mt-2 text-xs bg-white p-2 rounded">${JSON.stringify(data, null, 2)}</pre>
            </details>
        </div>
    `;
}

// Formatear Tour (TSP)
function formatTourResult(data) {
    return `
        <div class="space-y-4">
            <div class="bg-green-50 border-l-4 border-green-500 p-4">
                <p class="text-green-700 font-semibold">✅ ${data.message}</p>
            </div>
            
            <div class="grid grid-cols-2 md:grid-cols-4 gap-4">
                <div class="bg-blue-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">🏙️ Ciudades</p>
                    <p class="text-2xl font-bold text-blue-600">${data.citiesVisited}</p>
                </div>
                <div class="bg-green-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">💰 Precio</p>
                    <p class="text-2xl font-bold text-green-600">${data.totalPrice ? '€' + data.totalPrice.toFixed(2) : 'N/A'}</p>
                </div>
                <div class="bg-purple-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">⏱️ Duración</p>
                    <p class="text-2xl font-bold text-purple-600">${data.totalDuration ? data.totalDuration.toFixed(1) + 'h' : 'N/A'}</p>
                </div>
                <div class="bg-orange-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">⏱️ Tiempo de Cálculo</p>
                    <p class="text-2xl font-bold text-orange-600">${data.executionTimeMs.toFixed(2)}ms</p>
                </div>
            </div>
            
            <div class="bg-white border-2 border-gray-200 rounded-lg p-4">
                <h3 class="font-semibold text-gray-700 mb-3">🗺️ Orden del Tour:</h3>
                <div class="flex flex-wrap items-center gap-2">
                    ${data.tourOrder.map((city, idx) => `
                        <span class="bg-teal-100 text-teal-800 px-3 py-1 rounded-full font-semibold">${getStationEmoji(city)} ${city}</span>
                        ${idx < data.tourOrder.length - 1 ? '<span class="text-gray-400">→</span>' : ''}
                    `).join('')}
                </div>
            </div>
            
            <details class="bg-gray-50 rounded-lg p-4">
                <summary class="cursor-pointer font-semibold text-gray-700">📊 Ver JSON completo</summary>
                <pre class="mt-2 text-xs bg-white p-2 rounded">${JSON.stringify(data, null, 2)}</pre>
            </details>
        </div>
    `;
}

// Formatear Optimize Trip (Branch & Bound)
function formatOptimizeTripResult(data) {
    return `
        <div class="space-y-4">
            <div class="bg-green-50 border-l-4 border-green-500 p-4">
                <p class="text-green-700 font-semibold">✅ ${data.message}</p>
            </div>
            
            <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
                <div class="bg-blue-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">🏙️ Ciudades</p>
                    <p class="text-2xl font-bold text-blue-600">${data.citiesVisited}</p>
                </div>
                <div class="bg-green-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">💰 Gastado</p>
                    <p class="text-xl font-bold text-green-600">€${data.totalPrice.toFixed(2)}</p>
                </div>
                <div class="bg-red-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">💵 Presupuesto</p>
                    <p class="text-xl font-bold text-red-600">€${data.budget.toFixed(2)}</p>
                </div>
                <div class="bg-purple-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">🔍 Explorados</p>
                    <p class="text-xl font-bold text-purple-600">${data.nodesExplored}</p>
                </div>
                <div class="bg-yellow-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">✂️ Podados</p>
                    <p class="text-xl font-bold text-yellow-600">${data.nodesPruned}</p>
                </div>
            </div>
            
            ${data.optimalRoute && data.optimalRoute.length > 0 ? `
                <div class="bg-white border-2 border-gray-200 rounded-lg p-4">
                    <h3 class="font-semibold text-gray-700 mb-3">🗺️ Ruta Óptima:</h3>
                    <div class="flex flex-wrap items-center gap-2">
                        ${data.optimalRoute.map((city, idx) => `
                            <span class="bg-cyan-100 text-cyan-800 px-3 py-1 rounded-full font-semibold">${getStationEmoji(city)} ${city}</span>
                            ${idx < data.optimalRoute.length - 1 ? '<span class="text-gray-400">→</span>' : ''}
                        `).join('')}
                    </div>
                </div>
            ` : ''}
            
            <details class="bg-gray-50 rounded-lg p-4">
                <summary class="cursor-pointer font-semibold text-gray-700">📊 Ver JSON completo</summary>
                <pre class="mt-2 text-xs bg-white p-2 rounded">${JSON.stringify(data, null, 2)}</pre>
            </details>
        </div>
    `;
}

// Formatear MST (Prim/Kruskal)
function formatMSTResult(data) {
    return `
        <div class="space-y-4">
            <div class="bg-green-50 border-l-4 border-green-500 p-4">
                <p class="text-green-700 font-semibold">✅ ${data.message}</p>
            </div>
            
            <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div class="bg-blue-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">🌳 Nodos en MST</p>
                    <p class="text-2xl font-bold text-blue-600">${data.nodeCount}</p>
                </div>
                <div class="bg-purple-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">🔗 Aristas</p>
                    <p class="text-2xl font-bold text-purple-600">${data.edges.length}</p>
                </div>
                <div class="bg-orange-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">⚖️ Peso Total</p>
                    <p class="text-2xl font-bold text-orange-600">${data.totalWeight.toFixed(2)}</p>
                </div>
            </div>
            
            ${data.edges && data.edges.length > 0 && data.edges.length <= 20 ? `
                <div class="bg-white border-2 border-gray-200 rounded-lg p-4">
                    <h3 class="font-semibold text-gray-700 mb-3">🔗 Aristas del MST:</h3>
                    <div class="space-y-2">
                        ${data.edges.map((edge, idx) => `
                            <div class="bg-gray-50 p-2 rounded flex justify-between items-center">
                                <span class="font-mono text-sm">${edge.from} ↔️ ${edge.to}</span>
                                <span class="bg-blue-100 text-blue-800 px-2 py-1 rounded text-xs font-semibold">${edge.weight.toFixed(2)}</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            ` : data.edges.length > 20 ? `
                <div class="bg-yellow-50 p-4 rounded">
                    <p class="text-yellow-700">⚠️ Demasiadas aristas para mostrar (${data.edges.length}). Ver JSON completo abajo.</p>
                </div>
            ` : ''}
            
            <details class="bg-gray-50 rounded-lg p-4">
                <summary class="cursor-pointer font-semibold text-gray-700">📊 Ver JSON completo</summary>
                <pre class="mt-2 text-xs bg-white p-2 rounded">${JSON.stringify(data, null, 2)}</pre>
            </details>
        </div>
    `;
}

// Formatear Catalog
function formatCatalogResult(data) {
    return `
        <div class="space-y-4">
            <div class="bg-green-50 border-l-4 border-green-500 p-4">
                <p class="text-green-700 font-semibold">✅ Catálogo ordenado con ${data.sortingAlgorithm}</p>
                <p class="text-sm text-gray-600">Tiempo de ordenamiento: ${data.sortingTimeMs.toFixed(2)}ms</p>
            </div>
            
            <div class="grid grid-cols-2 md:grid-cols-5 gap-4">
                <div class="bg-blue-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">🌍 Países</p>
                    <p class="text-2xl font-bold text-blue-600">${data.stats.totalCountries}</p>
                </div>
                <div class="bg-green-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">🏙️ Ciudades</p>
                    <p class="text-2xl font-bold text-green-600">${data.stats.totalCities}</p>
                </div>
                <div class="bg-purple-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">🚉 Estaciones</p>
                    <p class="text-2xl font-bold text-purple-600">${data.stats.totalStations}</p>
                </div>
                <div class="bg-orange-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">✈️ Aeropuertos</p>
                    <p class="text-2xl font-bold text-orange-600">${data.stats.totalAirports}</p>
                </div>
                <div class="bg-red-50 p-4 rounded-lg">
                    <p class="text-gray-600 text-sm">🚂 Trenes</p>
                    <p class="text-2xl font-bold text-red-600">${data.stats.totalTrainStations}</p>
                </div>
            </div>
            
            ${data.topAirports && data.topAirports.length > 0 ? `
                <div class="bg-white border-2 border-gray-200 rounded-lg p-4">
                    <h3 class="font-semibold text-gray-700 mb-3">✈️ Top 5 Aeropuertos (por conexiones):</h3>
                    <div class="space-y-2">
                        ${data.topAirports.map((airport, idx) => `
                            <div class="bg-gradient-to-r from-blue-50 to-purple-50 p-3 rounded flex justify-between items-center">
                                <div>
                                    <span class="font-semibold text-lg">${idx + 1}. ${airport.name}</span>
                                    <span class="text-gray-600 text-sm ml-2">(${airport.code})</span>
                                    <p class="text-xs text-gray-500">${airport.cityName}, ${airport.country}</p>
                                </div>
                                <span class="bg-blue-600 text-white px-3 py-1 rounded-full font-bold">${airport.connectionCount} conexiones</span>
                            </div>
                        `).join('')}
                    </div>
                </div>
            ` : ''}
            
            <details class="bg-gray-50 rounded-lg p-4">
                <summary class="cursor-pointer font-semibold text-gray-700">📊 Ver JSON completo</summary>
                <pre class="mt-2 text-xs bg-white p-2 rounded">${JSON.stringify(data, null, 2)}</pre>
            </details>
        </div>
    `;
}

// Función auxiliar para mostrar el loading
function showLoading() {
    const loading = document.getElementById('loading');
    const resultsDiv = document.getElementById('results');
    const resultsContent = document.getElementById('results-content');
    
    loading.classList.remove('hidden');
    resultsDiv.classList.remove('hidden');
    resultsContent.innerHTML = '<p class="text-gray-600">⏳ Procesando solicitud...</p>';
    
    resultsDiv.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

// Función auxiliar para manejar errores
function handleError(error) {
    const loading = document.getElementById('loading');
    loading.classList.add('hidden');
    
    showResults({
        error: true,
        message: error.message || 'Error al procesar la solicitud',
        details: error.toString()
    });
}

// ==================== ALGORITMOS ====================

// BFS (Breadth-First Search)
async function executeBFS() {
    const from = document.getElementById('bfs-from').value;
    const to = document.getElementById('bfs-to').value;
    const maxDepth = document.getElementById('bfs-depth').value || 6;
    
    if (!from || !to) {
        alert('Por favor completa todos los campos');
        return;
    }
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/bfs?from=${from}&to=${to}&maxDepth=${maxDepth}`);
        const data = await response.json();
        showResults(data);
    } catch (error) {
        handleError(error);
    }
}

// DFS (Depth-First Search)
async function executeDFS() {
    const from = document.getElementById('dfs-from').value;
    const to = document.getElementById('dfs-to').value;
    const depth = document.getElementById('dfs-depth').value || 6;
    
    if (!from || !to) {
        alert('Por favor completa todos los campos');
        return;
    }
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/dfs?from=${from}&to=${to}&depth=${depth}`);
        const data = await response.json();
        showResults(data);
    } catch (error) {
        handleError(error);
    }
}

// Dijkstra (Shortest Path)
async function executeDijkstra() {
    const from = document.getElementById('dijkstra-from').value;
    const to = document.getElementById('dijkstra-to').value;
    
    if (!from || !to) {
        alert('Por favor completa todos los campos');
        return;
    }
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/dijkstra?from=${from}&to=${to}`);
        const data = await response.json();
        showResults(data);
    } catch (error) {
        handleError(error);
    }
}

// Greedy (Algoritmo Codicioso)
async function executeGreedy() {
    const from = document.getElementById('greedy-from').value;
    const to = document.getElementById('greedy-to').value;
    const criterion = document.getElementById('greedy-criterion').value;
    
    if (!from || !to) {
        alert('Por favor completa todos los campos');
        return;
    }
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/greedy?from=${from}&to=${to}&criterion=${criterion}`);
        const data = await response.json();
        showResults(data);
    } catch (error) {
        handleError(error);
    }
}

// Greedy por Ciudad
async function executeGreedyCity() {
    const fromCity = document.getElementById('greedy-city-from').value;
    const toCity = document.getElementById('greedy-city-to').value;
    const criterion = document.getElementById('greedy-city-criterion').value;
    
    if (!fromCity || !toCity) {
        alert('Por favor completa todos los campos');
        return;
    }
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/greedy-city?fromCity=${fromCity}&toCity=${toCity}&criterion=${criterion}`);
        const data = await response.json();
        showResults(data);
    } catch (error) {
        handleError(error);
    }
}

// Prim (Minimum Spanning Tree)
async function executePrim() {
    const start = document.getElementById('prim-start').value;
    
    if (!start) {
        alert('Por favor ingresa un nodo inicial');
        return;
    }
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/prim?start=${start}`);
        const data = await response.json();
        showResults(data);
    } catch (error) {
        handleError(error);
    }
}

// Kruskal (Minimum Spanning Tree)
async function executeKruskal() {
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/kruskal`);
        const data = await response.json();
        showResults(data);
    } catch (error) {
        handleError(error);
    }
}

// Catálogo de Estaciones
async function executeCatalog() {
    const sortAlgorithm = document.getElementById('catalog-sort').value;
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/catalog?sortAlgorithm=${sortAlgorithm}`);
        const data = await response.json();
        showResults(data);
    } catch (error) {
        handleError(error);
    }
}

// Tour Multi-Ciudad (TSP - Programación Dinámica)
async function executeTour() {
    const startCity = document.getElementById('tour-start').value;
    const citiesSelect = document.getElementById('tour-cities');
    const criterion = document.getElementById('tour-criterion').value;
    
    // Obtener todas las opciones seleccionadas del select múltiple
    const selectedOptions = Array.from(citiesSelect.selectedOptions).map(option => option.value);
    
    if (!startCity) {
        alert('Por favor selecciona la ciudad de origen');
        return;
    }
    
    if (selectedOptions.length === 0 || selectedOptions[0] === '') {
        alert('Por favor selecciona al menos una ciudad a visitar');
        return;
    }
    
    showLoading();
    
    try {
        const citiesParam = selectedOptions.join(',');
        const response = await fetch(`${API_BASE_URL}/multi-city-tour?startCity=${startCity}&cities=${citiesParam}&criterion=${criterion}`);
        const data = await response.json();
        showResults(data);
    } catch (error) {
        handleError(error);
    }
}

// Optimización de Viaje (Branch & Bound)
async function executeOptimize() {
    const startCity = document.getElementById('optimize-start').value;
    const budget = document.getElementById('optimize-budget').value;
    const maxCities = document.getElementById('optimize-max-cities').value;
    const returnToOrigin = document.getElementById('optimize-return').checked;
    
    if (!startCity || !budget) {
        alert('Por favor completa al menos la ciudad y el presupuesto');
        return;
    }
    
    showLoading();
    
    try {
        let url = `${API_BASE_URL}/optimize-trip?startCity=${startCity}&budget=${budget}`;
        if (maxCities) url += `&maxCities=${maxCities}`;
        url += `&returnToOrigin=${returnToOrigin}`;
        
        const response = await fetch(url);
        const data = await response.json();
        showResults(data);
    } catch (error) {
        handleError(error);
    }
}

// Todas las Rutas (Backtracking)
async function executeAllPaths() {
    const fromCity = document.getElementById('allpaths-from').value;
    const toCity = document.getElementById('allpaths-to').value;
    const maxStops = document.getElementById('allpaths-stops').value || 5;
    
    if (!fromCity || !toCity) {
        alert('Por favor completa todos los campos');
        return;
    }
    
    showLoading();
    
    try {
        const response = await fetch(`${API_BASE_URL}/all-paths?fromCity=${fromCity}&toCity=${toCity}&maxStops=${maxStops}`);
        const data = await response.json();
        showResults(data);
    } catch (error) {
        handleError(error);
    }
}

// Inicialización
document.addEventListener('DOMContentLoaded', function() {
    console.log('🚀 Route Planner Frontend cargado correctamente');
    console.log('📡 API Base URL:', API_BASE_URL);
});

