# Десктоп приложение администрирования игры «Морской бой»

Объектно-ориентированное программирование, Технологии программирования,
Математико-механический факультет, СПбГУ.

## Сборка и запуск

Используется Gradle Wrapper, поэтому глобальный Gradle устанавливать не нужно.

```bash
./gradlew clean build
```

Для запуска консольного интерфейса:

```bash
./gradlew run
```

Также собирается самостоятельный JAR:

```bash
./gradlew jar
java -jar build/libs/battleship-assistant.jar
```

Проверка тестов отдельно:

```bash
./gradlew test
```

## Сценарий работы

1. В меню `Игроки` добавьте минимум двух игроков.
2. Выберите `Создать и провести партию`.
3. Выберите первого и второго игрока.
4. Для каждого игрока введите ровно 10 кораблей в одной строке, разделяя их `;`.
5. После принятия двух расстановок начнётся ввод ходов.
6. При `HIT`/`SUNK` тот же игрок продолжает ход; после `MISS` ход переходит сопернику.
7. После победы приложение сразу печатает полный административный отчёт и возвращает управление в главное меню.
8. Пункт `История партий` позволяет снова вывести отчёты всех созданных партий.

## Формат ручной расстановки

Длинный корабль задаётся началом и концом:

```text
A1-A4
```

Одноклеточный корабль задаётся одной координатой:

```text
J10
```

Полный пример корректного флота:

```text
A1-A4; C1-C3; E1-E3; G1-G2; I1-I2; G4-G5; A7; C7; E7; G7
```

Парсер принимает только координаты `A1`…`J10`, а валидатор дополнительно проверяет:

- 1 линкор;
- 2 крейсера;
- 3 эсминца;
- 4 катера;
- прямолинейность и непрерывность каждого корабля;
- отсутствие пересечений;
- отсутствие соприкосновений, включая диагонали.

## Архитектура

Проект разделён на четыре логические зоны:

```text
Presentation (console)
        ↓
Application (GameSession)
        ↓
Domain (Game, Board, Ship, Player, валидаторы, Elo, статистика)
        ↑
Repository contracts (domain.repository)
        ↑
Infrastructure (in-memory implementations)
```

`Domain` и `Application` не зависят от конкретного способа хранения. Контракты репозиториев
находятся в `domain/repository`, реализации — в `infrastructure`.

## Структура

```text
src/main/kotlin/battleship/
├── Main.kt
├── application/
│   ├── GameSession.kt
│   └── GameSessionImpl.kt
├── domain/
│   ├── model/
│   │   ├── Board.kt
│   │   ├── Coordinate.kt
│   │   ├── Enums.kt
│   │   ├── Game.kt
│   │   ├── Player.kt
│   │   ├── Ship.kt
│   │   ├── ShipType.kt
│   │   └── ValueObjects.kt
│   ├── repository/
│   │   └── Repositories.kt
│   └── service/
│       ├── EloRatingService.kt
│       ├── EloRatingServiceImpl.kt
│       ├── ShipPlacementValidator.kt
│       ├── StatisticsService.kt
│       └── TurnValidator.kt
├── infrastructure/
│   └── Repositories.kt
└── presentation/
    └── console/
        ├── ConsoleApplication.kt
        ├── GameHistoryFormatter.kt
        └── PlacementParser.kt

test/kotlin/
└── MainTest.kt

docs/
├── architecture.png
├── domain.png
├── sequence.png
└── README.md
```
