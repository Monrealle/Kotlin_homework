# Десктоп приложение администрирования игры «Морской бой»
Объектно-ориентированное программирование, 1 курс, Технологии программирования,Математико-механический факультет, СПбГУ.

## Описание
Администратор партий классического «Морского боя»: запись и валидация ходов, расчёт рейтинга Эло, сохранение истории игр, поддержка игры против бота.

## Зависимости
- **Язык:** Kotlin 2.3+
- **Система сборки:** Gradle (wrapper включён)
- **Тестирование:** (в будущем)
- **GUI:** (в будущем)
- **База данных:** (в будущем)

## Сборка
1. Клонируйте репозиторий:
    ```
    bash
    git clone https://github.com/ТВОЙ_ЛОГИН/battleship-assistant.git
    cd battleship-assistant
    ```

2. Запустите сборку:
    ```
    bash
    ./gradlew build
    Собранный JAR появится в build/libs/.
    ```

3. Для запуска:
    ```
    bash
    java -jar build/libs/battleship-assistant.jar
    ```

## Архитектура

Приложение построено по многослойной архитектуре с чёткими зонами ответственности:

- **Domain** – модели игры, правила валидации, стратегии бота, расчёт рейтинга.
- **Application** – координатор игры `GameSession`, принимающий ходы и управляющий состоянием.
- **Infrastructure** – репозитории для сохранения и загрузки партий.
- **Presentation** – GUI (в будущем).

### Основные интерфейсы и классы

#### Игрок и рейтинг
- `Player` – `id: String`, `name: String` (имена вводятся перед партией).
- `EloRating` – текущий рейтинг игрока, привязан к `Player` (начальный – 1000).
- `EloRatingService` – интерфейс расчёта нового рейтинга; реализация `EloRatingServiceImpl` использует случайную дельту 25–33.
- `EloChange` – фиксирует старый рейтинг, новый и дельту для конкретного игрока.

#### Игровые сущности
- `Coordinate(row, col)` – координаты клетки поля 10×10.
- `ShipType` – перечисление типов кораблей: BATTLESHIP (4), CRUISER (3), DESTROYER (2), BOAT (1).
- `Ship` – тип корабля, список сегментов (`List<Coordinate>`), метод `isSunk()`.
- `Board` – сетка 10×10 из состояний ячеек (`CellState`), список кораблей. Методы: `receiveShot()`, `allShipsSunk()`. Валидация расстановки делегируется `ShipPlacementValidator`.
- `CellState` – EMPTY, SHIP, HIT, MISS.
- `ShotResult` – MISS, HIT, SUNK, WIN.
- `Move` – номер хода, игрок, координата, результат.

#### Игровая сессия
- `Game` – идентификатор, два игрока (`player1`, `player2`), два поля, текущий ход, победитель, статус (`SETUP_P1`, `SETUP_P2`, `IN_PROGRESS`, `FINISHED`), лог всех ходов, изменения Эло.
- `GameSession` – основной интерфейс: старт игры, расстановка кораблей (возвращает `ValidationResult`), ход, получение состояния.
- `GameSessionImpl` – реализация; использует `ShipPlacementValidator`, `TurnValidator`, `EloRatingService`, `GameRepository` и опциональную стратегию бота `MoveStrategy`.

#### Валидаторы
- `ShipPlacementValidator` – проверяет корректность расстановки кораблей на доске, возвращает `ValidationResult`.
- `ValidationResult` – результат валидации: флаг `isValid` и список ошибок `errors`.
- `TurnValidator` – проверяет, может ли игрок сделать выстрел в текущий момент (очерёдность, повтор координаты).

#### Бот
- `MoveStrategy` – интерфейс стратегии бота: `nextMove(board, history): Coordinate`.
- `RandomBot` – случайный выстрел по ещё не атакованным клеткам.
- `SmartBot` – при попадании переходит в режим «добивания»: обстреливает соседние клетки. Если незаконченных попаданий нет — делегирует ход `RandomBot` (композиция, не наследование).

#### Хранение (in-memory на старте)
- `GameRepository` – сохранение и получение объектов `Game`.
- `PlayerRepository` – сохранение и поиск игроков по `id` и имени.

#### Статистика
- `StatisticsService` – собирает статистику по игроку, используя `GameRepository` и `PlayerRepository`.
- `PlayerStats` – количество игр, побед, винрейт, текущий Эло.

## Структура проекта
    ```
    Kotlin_homework
    ├── .github/
    │   └── workflows/
    │       └── ci.yml                       - Конфигурация CI
    ├── gradle/
    │   └── wrapper/
    │       ├── gradle-wrapper.jar           - Исполняемый файл Gradle Wrapper
    │       └── gradle-wrapper.properties    - Версия и параметры Gradle Wrapper
    ├── src/
    │   └── main/
    │       ├── kotlin/
    │       │   └── Main.kt                  - Основной исходный код
    │       └── test/
    │           └── MainTest.kt              - Тесты
    ├── .gitignore                           - Файлы, игнорируемые Git
    ├── LICENSE                              - Лицензия проекта
    ├── README.md                            - Этот файл
    ├── build.gradle.kts                     - Конфигурация сборки
    ├── settings.gradle.kts                  - Название проекта и настройки Gradle
    ├── gradle.properties                    - Глобальные свойства Gradle
    └── gradlew                              - Запуск Gradle Wrapper на Linux/macOS

    ```