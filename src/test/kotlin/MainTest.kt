import battleship.application.GameSessionImpl
import battleship.application.RandomShipPlacer
import battleship.domain.model.*
import battleship.domain.service.*
import battleship.infrastructure.*
import battleship.presentation.gui.GuiController
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * =============================================================================================
 * Общие тесты проекта «Морской бой».
 *
 * Один тестовый класс используется для проверки основных компонентов приложения:
 *
 * 1. Value Objects и модели Domain-слоя.
 * 2. Координаты игрового поля.
 * 3. Корабли и игровая доска.
 * 4. Валидатор расстановки кораблей.
 * 5. Валидатор ходов.
 * 6. Расчёт рейтинга Эло.
 * 7. Генератор случайной расстановки.
 * 8. Полный жизненный цикл игровой сессии.
 * 9. Сервис статистики.
 * 10. GUI-контроллер.
 *
 * Тесты выполняются через JUnit 5 и запускаются Gradle-задачей `test`.
 * =============================================================================================
 */
class MainTest {

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет корректность разбора координат игрового поля.
     *
     * Поддерживаются:
     *
     * - A1;
     * - J10;
     * - координаты в нижнем регистре.
     *
     * Некорректные значения должны возвращать `null`.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `coordinate parsing should work correctly`() {

        assertEquals(
            Coordinate(0, 0),
            Coordinate.fromString("A1")
        )

        assertEquals(
            Coordinate(9, 9),
            Coordinate.fromString("J10")
        )

        assertEquals(
            Coordinate(4, 4),
            Coordinate.fromString("e5")
        )

        assertNull(
            Coordinate.fromString("K1")
        )

        assertNull(
            Coordinate.fromString("A11")
        )

        assertNull(
            Coordinate.fromString("ABC")
        )

        assertNull(
            Coordinate.fromString("")
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет человекочитаемое представление координаты.
     *
     * Например:
     *
     * row=0, col=0 -> A1
     * row=9, col=9 -> J10
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `coordinate display should work correctly`() {

        assertEquals(
            "A1",
            Coordinate(0, 0).toDisplayString()
        )

        assertEquals(
            "J10",
            Coordinate(9, 9).toDisplayString()
        )

        assertEquals(
            "E5",
            Coordinate(4, 4).toDisplayString()
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет базовую логику кораблей.
     *
     * Проверяется:
     *
     * - корректное определение потопления;
     * - обнаружение пересечения;
     * - обнаружение соприкосновения.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `ship logic should work correctly`() {

        val ship = Ship(
            type = ShipType.DESTROYER,
            segments = listOf(
                Coordinate(0, 0),
                Coordinate(0, 1)
            )
        )

        assertFalse(
            ship.isSunk(
                setOf(Coordinate(0, 0))
            )
        )

        assertTrue(
            ship.isSunk(
                setOf(
                    Coordinate(0, 0),
                    Coordinate(0, 1)
                )
            )
        )

        val overlappingShip = Ship(
            type = ShipType.BOAT,
            segments = listOf(
                Coordinate(0, 1)
            )
        )

        assertTrue(
            ship.overlapsWith(overlappingShip)
        )

        val adjacentShip = Ship(
            type = ShipType.BOAT,
            segments = listOf(
                Coordinate(1, 1)
            )
        )

        assertTrue(
            ship.isAdjacentTo(adjacentShip)
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет обработку выстрелов игровой доской.
     *
     * Проверяется:
     *
     * - HIT;
     * - SUNK;
     * - WIN;
     * - MISS.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `board should process shots correctly`() {

        val board = Board(
            owner = Player("1", "Alice")
        )

        val cruiser = Ship(
            type = ShipType.CRUISER,
            segments = listOf(
                Coordinate(0, 0),
                Coordinate(0, 1),
                Coordinate(0, 2)
            )
        )

        val boat = Ship(
            type = ShipType.BOAT,
            segments = listOf(
                Coordinate(2, 2)
            )
        )

        board.placeShips(
            listOf(
                cruiser,
                boat
            )
        )

        /* Первый выстрел по кораблю - обычное попадание. */
        assertEquals(
            ShotResult.HIT,
            board.receiveShot(Coordinate(0, 0))
        )

        /* Второе попадание ещё не уничтожает крейсер. */
        assertEquals(
            ShotResult.HIT,
            board.receiveShot(Coordinate(0, 1))
        )

        /* Последний сегмент крейсера уничтожает его. */
        assertEquals(
            ShotResult.SUNK,
            board.receiveShot(Coordinate(0, 2))
        )

        /* Выстрел по пустой клетке является промахом. */
        assertEquals(
            ShotResult.MISS,
            board.receiveShot(Coordinate(5, 5))
        )

        assertFalse(
            board.allShipsSunk()
        )

        /* Последний корабль приводит к победе. */
        assertEquals(
            ShotResult.WIN,
            board.receiveShot(Coordinate(2, 2))
        )

        assertTrue(
            board.allShipsSunk()
        )

        /* Проверяем, что повторный выстрел запрещён. */
        assertThrows(IllegalStateException::class.java) {
            board.receiveShot(Coordinate(2, 2))
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет корректную полную расстановку кораблей.
     *
     * Используется классический состав флота:
     *
     * - 1 линкор;
     * - 2 крейсера;
     * - 3 эсминца;
     * - 4 катера.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `valid ship placement should pass validation`() {

        val validator =
            ShipPlacementValidatorImpl()

        val result =
            validator.validate(validFleet())

        assertTrue(
            result.isValid,
            result.errors.joinToString("; ")
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет, что неполная расстановка кораблей отклоняется.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `invalid fleet composition should fail validation`() {

        val validator =
            ShipPlacementValidatorImpl()

        val ships = listOf(
            Ship(
                ShipType.BATTLESHIP,
                listOf(
                    Coordinate(0, 0),
                    Coordinate(0, 1),
                    Coordinate(0, 2),
                    Coordinate(0, 3)
                )
            )
        )

        val result =
            validator.validate(ships)

        assertFalse(
            result.isValid
        )

        assertTrue(
            result.errors.isNotEmpty()
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет, что пересекающиеся корабли не проходят валидацию.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `overlapping ships should fail validation`() {

        val validator =
            ShipPlacementValidatorImpl()

        val ships = listOf(
            Ship(
                ShipType.BATTLESHIP,
                listOf(
                    Coordinate(0, 0),
                    Coordinate(0, 1),
                    Coordinate(0, 2),
                    Coordinate(0, 3)
                )
            ),
            Ship(
                ShipType.CRUISER,
                listOf(
                    Coordinate(0, 3),
                    Coordinate(1, 3),
                    Coordinate(2, 3)
                )
            )
        )

        val result =
            validator.validate(ships)

        assertFalse(
            result.isValid
        )

        assertTrue(
            result.errors.any {
                it.contains("перекрываются")
            }
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет, что соприкасающиеся корабли не проходят валидацию.
     *
     * Соприкосновение учитывает, в том числе, диагональные клетки.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `adjacent ships should fail validation`() {

        val validator = ShipPlacementValidatorImpl()

        val ships = listOf(
            Ship(
                ShipType.BOAT,
                listOf(
                    Coordinate(0, 0)
                )
            ),
            Ship(
                ShipType.BOAT,
                listOf(
                    Coordinate(1, 1)
                )
            )
        )

        val result = validator.validate(ships)

        assertFalse(
            result.isValid
        )

        assertTrue(
            result.errors.any {
                it.contains("стоят вплотную")
            }
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет, что TurnValidator разрешает корректный ход.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `turn validator should allow valid move`() {

        val player1 = Player("1", "Alice")
        val player2 = Player("2", "Bob")
        val game = Game(
                id = "game",
                player1 = player1,
                player2 = player2
            )

        game.board1.placeShips(validFleet())
        game.board2.placeShips(validFleet())

        game.status = GameStatus.IN_PROGRESS

        val validator = TurnValidatorImpl()
        val result = validator.canFire(
                game = game,
                player = player1,
                coord = Coordinate(0, 0)
            )

        assertTrue(
            result.isValid,
            result.errors.joinToString("; ")
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет основные запреты TurnValidator:
     *
     * - нельзя стрелять не в свой ход;
     * - нельзя повторно атаковать клетку.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `turn validator should reject invalid moves`() {

        val player1 = Player("1", "Alice")
        val player2 = Player("2", "Bob")
        val game = Game(
                id = "game",
                player1 = player1,
                player2 = player2
            )

        game.board1.placeShips(validFleet())
        game.board2.placeShips(validFleet())

        game.status = GameStatus.IN_PROGRESS

        val validator = TurnValidatorImpl()

        /* Сейчас ход игрока 1, поэтому игрок 2 должен получить отказ. */
        val wrongPlayerResult =
            validator.canFire(
                game = game,
                player = player2,
                coord = Coordinate(0, 0)
            )

        assertFalse(
            wrongPlayerResult.isValid
        )

        /* Атакуем клетку один раз. */
        game.board2.receiveShot(
            Coordinate(0, 0)
        )

        /* Повторная атака этой клетки запрещена. */
        val repeatedShotResult =
            validator.canFire(
                game = game,
                player = player1,
                coord = Coordinate(0, 0)
            )

        assertFalse(
            repeatedShotResult.isValid
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет расчёт рейтинга Эло.
     *
     * Используется фиксированный генератор случайных чисел,
     * чтобы тест не зависел от конкретного случайного значения.
     *
     * Проверяется:
     *
     * - победитель увеличивает рейтинг;
     * - проигравший уменьшает рейтинг;
     * - рейтинг проигравшего не становится отрицательным.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `elo rating should be calculated correctly`() {

        val winner = Player("1", "Alice")
        val loser = Player("2", "Bob")
        val service = EloRatingServiceImpl(
                random = Random(42)
            )

        val changes =
            service.calculateRatings(
                winner = winner,
                winnerCurrentRating = 1000,
                loser = loser,
                loserCurrentRating = 1000
            )

        val winnerChange = changes[winner]!!
        val loserChange = changes[loser]!!

        assertTrue(
            winnerChange.newRating > winnerChange.oldRating
        )

        assertTrue(
            loserChange.newRating < loserChange.oldRating
        )

        assertEquals(
            winnerChange.delta,
            -loserChange.delta
        )

        assertTrue(
            loserChange.newRating >= 0
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет защиту от отрицательного рейтинга.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `elo rating should never become negative`() {

        val winner = Player("1", "Alice")
        val loser = Player("2", "Bob")
        val service = EloRatingServiceImpl(
                random = Random(42)
            )

        val changes = service.calculateRatings(
                winner = winner,
                winnerCurrentRating = 1000,
                loser = loser,
                loserCurrentRating = 1
            )

        val loserChange = changes[loser]!!

        assertEquals(
            0,
            loserChange.newRating
        )

        assertTrue(
            loserChange.delta <= 0
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет генератор случайной расстановки.
     *
     * Выполняется большое количество независимых генераций,
     * каждая из которых должна пройти полный валидатор.
     *
     * Это одновременно проверяет:
     *
     * - RandomShipPlacer;
     * - ShipPlacementValidator.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `random ship placer should always generate valid fleet`() {

        val validator = ShipPlacementValidatorImpl()

        repeat(100) {

            val ships = RandomShipPlacer.generate()
            val result = validator.validate(ships)

            assertTrue(
                result.isValid,
                result.errors.joinToString("; ")
            )
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет полный жизненный цикл игровой сессии.
     *
     * Сценарий:
     *
     * 1. Создаются два игрока.
     * 2. Создаётся игровая сессия.
     * 3. Запускается партия.
     * 4. Корабли обоих игроков размещаются.
     * 5. Первый игрок последовательно поражает все клетки флота второго игрока.
     * 6. Партия завершается победой.
     * 7. Рассчитываются изменения рейтинга.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `game session should finish game correctly`() {

        val player1 = Player("1", "Alice")
        val player2 = Player("2", "Bob")

        val gameRepository = InMemoryGameRepository()
        val eloRepository = InMemoryEloRatingRepository()

        eloRepository.save(
            EloRating(
                player1,
                1000
            )
        )

        eloRepository.save(
            EloRating(
                player2,
                1000
            )
        )

        val session =
            GameSessionImpl(
                placementValidator = ShipPlacementValidatorImpl(),
                turnValidator = TurnValidatorImpl(),
                eloService = EloRatingServiceImpl(
                    Random(42)
                ),
                gameRepository = gameRepository,
                eloRatingRepository = eloRepository
            )

        session.startGame(
            player1,
            player2
        )

        assertEquals(
            GameStatus.SETUP_P1,
            session.getGame().status
        )

        assertTrue(
            session.placeShips(
                player1,
                validFleet()
            ).isValid
        )

        assertEquals(
            GameStatus.SETUP_P2,
            session.getGame().status
        )

        assertTrue(
            session.placeShips(
                player2,
                validFleet()
            ).isValid
        )

        assertEquals(
            GameStatus.IN_PROGRESS,
            session.getGame().status
        )

        /*
         * Стреляем по всем клеткам кораблей второго игрока.
         * Все выстрелы являются попаданиями, поэтому ход остаётся у player1.
         */
        val targetCells = validFleet()
                .flatMap { it.segments }

        for (coord in targetCells) {
            if (session.getGame().status == GameStatus.FINISHED) {
                break
            }

            session.makeMove(
                player1,
                coord
            )
        }

        val game = session.getGame()

        assertEquals(
            GameStatus.FINISHED,
            game.status
        )

        assertEquals(
            player1,
            game.winner
        )

        assertNotNull(
            game.eloChanges
        )

        assertEquals(
            targetCells.size,
            game.moves.size
        )

        assertTrue(
            game.moves.all {
                it.player == player1
            }
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет сервис статистики.
     *
     * Создаётся несколько завершённых партий:
     *
     * - одна победа игрока;
     * - одна победа соперника.
     *
     * Ожидается:
     *
     * - 2 завершённые партии;
     * - 1 победа;
     * - винрейт 50%;
     * - корректный текущий рейтинг.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `statistics service should calculate player stats`() {

        val player1 = Player("1", "Alice")
        val player2 = Player("2", "Bob")

        val gameRepository = InMemoryGameRepository()
        val eloRepository = InMemoryEloRatingRepository()

        eloRepository.save(
            EloRating(
                player1,
                1050
            )
        )

        val game1 = Game(
                id = "game-1",
                player1 = player1,
                player2 = player2
            )

        game1.status = GameStatus.FINISHED
        game1.winner = player1

        val game2 = Game(
                id = "game-2",
                player1 = player1,
                player2 = player2
            )

        game2.status = GameStatus.FINISHED
        game2.winner = player2

        gameRepository.save(game1)
        gameRepository.save(game2)

        val service = StatisticsServiceImpl(
                gameRepository = gameRepository,
                eloRatingRepository = eloRepository
            )

        val stats = service.getStats(player1)

        assertEquals(
            2,
            stats.gamesPlayed
        )

        assertEquals(
            1,
            stats.wins
        )

        assertEquals(
            0.5,
            stats.winRate,
            0.0001
        )

        assertEquals(
            1050,
            stats.currentElo
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет базовую работу GUI-контроллера.
     *
     * Проверяется:
     *
     * - создание игрока;
     * - сохранение игрока в репозитории;
     * - создание начального рейтинга.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `gui controller should create player`() {

        val playerRepository = InMemoryPlayerRepository()
        val gameRepository = InMemoryGameRepository()
        val eloRepository = InMemoryEloRatingRepository()

        val statisticsService = StatisticsServiceImpl(
                gameRepository = gameRepository,
                eloRatingRepository = eloRepository
            )

        val controller = GuiController(
                playerRepo = playerRepository,
                gameRepo = gameRepository,
                eloRepo = eloRepository,
                placementValidator = ShipPlacementValidatorImpl(),
                turnValidator = TurnValidatorImpl(),
                eloService = EloRatingServiceImpl(
                    Random(42)
                ),
                statisticsService = statisticsService
            )

        val error = controller.addPlayer(
                "Alice"
            )

        assertNull(
            error
        )

        val player = playerRepository.findByName(
                "Alice"
            )

        assertNotNull(
            player
        )

        assertEquals(
            1000,
            eloRepository.findByPlayer(
                player!!
            ).rating
        )

        /* Попытка добавить игрока с тем же именем должна вернуть ошибку. */
        val duplicateError = controller.addPlayer(
                "alice")

        assertNotNull(
            duplicateError
        )
    }

    /**
     * =============================================================================================
     * Создаёт заранее корректную расстановку классического флота.
     *
     * Используется в нескольких тестах для исключения дублирования
     * большого количества координат.
     *
     * @return корректный набор из 10 кораблей
     * =============================================================================================
     */
    private fun validFleet(): List<Ship> {

        return listOf(
            /* 1 линкор (4 клетки). */
            Ship(
                ShipType.BATTLESHIP,
                listOf(
                    Coordinate(0, 0),
                    Coordinate(0, 1),
                    Coordinate(0, 2),
                    Coordinate(0, 3)
                )
            ),

            /* 2 крейсера (по 3 клетки). */
            Ship(
                ShipType.CRUISER,
                listOf(
                    Coordinate(2, 0),
                    Coordinate(2, 1),
                    Coordinate(2, 2)
                )
            ),

            Ship(
                ShipType.CRUISER,
                listOf(
                    Coordinate(4, 0),
                    Coordinate(4, 1),
                    Coordinate(4, 2)
                )
            ),

            /* 3 эсминца (по 2 клетки). */
            Ship(
                ShipType.DESTROYER,
                listOf(
                    Coordinate(6, 0),
                    Coordinate(6, 1)
                )
            ),

            Ship(
                ShipType.DESTROYER,
                listOf(
                    Coordinate(8, 0),
                    Coordinate(9, 0)
                )
            ),

            Ship(
                ShipType.DESTROYER,
                listOf(
                    Coordinate(6, 3),
                    Coordinate(7, 3)
                )
            ),

            /* 4 катера (по 1 клетке). */
            Ship(
                ShipType.BOAT,
                listOf(
                    Coordinate(9, 3)
                )
            ),

            Ship(
                ShipType.BOAT,
                listOf(
                    Coordinate(7, 5)
                )
            ),

            Ship(
                ShipType.BOAT,
                listOf(
                    Coordinate(5, 5)
                )
            ),

            Ship(
                ShipType.BOAT,
                listOf(
                    Coordinate(3, 5)
                )
            )
        )
    }
}
