# Adaptive Travel Planner

Microservice-based trip planner built with Java 25, Spring Boot 4.1.1 and Spring Cloud Gateway.
It plans the journey, hotels, a daily itinerary, food, reviews, replanning and solo-traveller
connections using Geoapify (places), Open-Meteo (weather) and TomTom (traffic).

## How to run
1. Get free API keys from Geoapify and TomTom.
2. Set them in PowerShell, then reopen the terminal:
   ```
   setx GEOAPIFY_KEY "your geoapify key"
   setx TOMTOM_KEY "your tomtom key"
   ```
3. Start `smart-planner-service` (port 8090):  `.\mvnw.cmd spring-boot:run`
4. Start `api-gateway` (port 8080):  `.\mvnw.cmd clean spring-boot:run`
5. Open http://localhost:8080

## Notes
- Hotel prices and crowd levels are estimates; hotel booking is a demo; bus booking opens redBus.
- Data is held in memory and resets when a service restarts.
- The project report is in the `docs` folder.