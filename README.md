# Десктоп приложение администрирования игры «Морской бой»
Объектно-ориентированное программирование, Технологии программирования, 
Математико-механический факультет, СПбГУ.

## Описание

Десктопное приложение для администрирования партий классического
«Морского боя».

Приложение предоставляет:

- создание и хранение игроков;
- отображение текущего рейтинга Эло;
- создание партий между двумя игроками;
- автоматическую корректную расстановку кораблей;
- проверку расстановки кораблей;
- проверку допустимости ходов;
- выполнение и сохранение истории ходов текущей партии;
- определение победителя;
- пересчёт рейтинга Эло после завершения партии;
- отображение статистики игроков;
- стратегии случайного и интеллектуального бота;
- сохранение игроков и рейтингов между запусками приложения.

Графический интерфейс реализован с использованием Compose for Desktop.

## Технологии

- **Язык:** Kotlin 1.9.22
- **Система сборки:** Gradle Wrapper
- **GUI:** Compose for Desktop
- **UI:** Material 3
- **Сериализация:** kotlinx.serialization JSON
- **Тестирование:** JUnit 5
- **Хранение:** JSON-файлы для игроков и рейтингов; партии в текущей реализации хранятся in-memory

## Сборка

Для сборки проекта необходимо выполнить:

```bash
./gradlew build
```

## Структура проекта

```
src/
├── main/
│   └── kotlin/
│       └── battleship/
│           ├── Main.kt
│           │
│           ├── application/
│           │   ├── GameSession.kt
│           │   ├── GameSessionImpl.kt
│           │   └── RandomShipPlacer.kt
│           │
│           ├── domain/
│           │   ├── bot/
│           │   │   └── Bots.kt
│           │   │
│           │   ├── model/
│           │   │   ├── Board.kt
│           │   │   ├── Coordinate.kt
│           │   │   ├── Game.kt
│           │   │   ├── Move.kt
│           │   │   ├── Player.kt
│           │   │   ├── Ship.kt
│           │   │   ├── ShipType.kt
│           │   │   └── ValueObjects.kt
│           │   │
│           │   ├── repository/
│           │   │   └── Repositories.kt
│           │   │
│           │   └── service/
│           │       ├── EloRatingService.kt
│           │       ├── EloRatingServiceImpl.kt
│           │       ├── ShipPlacementValidator.kt
│           │       ├── StatisticsService.kt
│           │       └── TurnValidator.kt
│           │
│           ├── infrastructure/
│           │   ├── Repositories.kt
│           │   └── FileRepositories.kt
│           │
│           └── presentation/
│               └── gui/
│                   ├── App.kt
│                   └── GuiController.kt
│
├── test/
│   └── kotlin/
│       └── MainTest.kt
│
├── README.md
├── diagram.puml
├── build.gradle.kts
└── settings.gradle.kts
```
