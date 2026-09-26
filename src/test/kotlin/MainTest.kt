import battleship.application.GameSessionImpl
import battleship.domain.model.*
import battleship.domain.service.*
import battleship.infrastructure.*
import battleship.presentation.console.ConsoleApplication
import battleship.presentation.console.PlacementParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.StringReader
import java.io.StringWriter

/**
 * =============================================================================================
 * Основные тесты приложения Battleship Assistant.
 *
 * В одном файле находятся:
 *
 * - unit-тесты отдельных компонентов;
 * - integration-тесты взаимодействия нескольких компонентов;
 * - system-тесты полного пользовательского сценария через ConsoleApplication.
 *
 * =============================================================================================
 */
class MainTest {

    /**
     * ---------------------------------------------------------------------------------------------
     * Unit tests.
     *
     * Проверяем отдельные доменные объекты и их инварианты.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `coordinate player and value objects should keep invariants`() {
        assertEquals(Coordinate(0, 0), Coordinate.fromString("A1"))
        assertEquals(Coordinate(9, 9), Coordinate.fromString("j10"))
        assertEquals("E5", Coordinate(4, 4).toDisplayString())

        assertNull(Coordinate.fromString("A01"))
        assertNull(Coordinate.fromString("K1"))
        assertNull(Coordinate.ofOrNull(-1, 0))

        val alice = Player("1", "Alice")

        assertEquals("Alice", alice.toString())
        assertThrows(IllegalArgumentException::class.java) {
            Player("2", " ")
        }

        assertEquals(1000, EloRating(alice).rating)
        assertEquals(16, EloChange(alice, 1000, 1016, 16).delta)
        assertEquals(
            "Игр: 5 | Побед: 3 | Винрейт: 60.0% | Рейтинг: 1016",
            PlayerStats(5, 3, 0.6, 1016).display()
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Unit tests.
     *
     * Проверяем расстановку кораблей, обработку ходов и расчёт рейтинга.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `board placement parser validator turn and elo should work`() {
        val alice = Player("1", "Alice")
        val bob = Player("2", "Bob")
        val board = Board(alice)
        val fleet = validFleet()

        assertTrue(
            PlacementParser.parse(validPlacementText()).isSuccess
        )

        assertTrue(
            ShipPlacementValidatorImpl().validate(fleet).isValid
        )

        assertFalse(
            ShipPlacementValidatorImpl().validate(fleet.dropLast(1)).isValid
        )

        val cruiser = ship(
            ShipType.CRUISER,
            listOf("A1", "A2", "A3")
        )

        val boat = ship(
            ShipType.BOAT,
            listOf("C3")
        )

        board.placeShips(listOf(cruiser, boat))

        assertEquals(
            ShotResult.MISS,
            board.receiveShot(c(9, 9))
        )

        assertEquals(
            ShotResult.HIT,
            board.receiveShot(c(0, 0))
        )

        assertEquals(
            ShotResult.HIT,
            board.receiveShot(c(0, 1))
        )

        assertEquals(
            ShotResult.SUNK,
            board.receiveShot(c(0, 2))
        )

        assertEquals(
            ShotResult.WIN,
            board.receiveShot(c(2, 2))
        )

        assertTrue(board.allShipsSunk())

        assertThrows(IllegalStateException::class.java) {
            board.receiveShot(c(2, 2))
        }

        val game = Game(
            "g1",
            alice,
            bob
        )

        game.board1.placeShips(fleet)
        game.board2.placeShips(fleet)
        game.status = GameStatus.IN_PROGRESS

        val turnValidator = TurnValidatorImpl()

        assertTrue(
            turnValidator
                .canFire(game, alice, c(9, 9))
                .isValid
        )

        game.board2.receiveShot(c(9, 9))

        assertFalse(
            turnValidator
                .canFire(game, alice, c(9, 9))
                .isValid
        )

        assertFalse(
            turnValidator
                .canFire(
                    game,
                    Player("3", "Eve"),
                    c(8, 8)
                )
                .isValid
        )

        /*
         * В текущей реализации Elo дельта случайная
         * и находится в диапазоне от 25 до 33.
         *
         * Поэтому тестируем не конкретное значение,
         * а гарантии контракта сервиса.
         */
        val changes = EloRatingServiceImpl()
            .calculateRatings(
                alice,
                1000,
                bob,
                1000
            )

        val winnerChange = changes.getValue(alice)
        val loserChange = changes.getValue(bob)

        assertTrue(winnerChange.delta in 25..33)
        assertEquals(-winnerChange.delta, loserChange.delta)

        assertEquals(
            winnerChange.oldRating + winnerChange.delta,
            winnerChange.newRating
        )

        assertEquals(
            loserChange.oldRating + loserChange.delta,
            loserChange.newRating
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Integration tests.
     *
     * Проверяем взаимодействие GameSession с репозиториями,
     * валидаторами и сервисом рейтинга.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `game session should coordinate placement turns repositories and rating`() {
        val alice = Player("1", "Alice")
        val bob = Player("2", "Bob")

        val games = InMemoryGameRepository()
        val ratings = InMemoryEloRatingRepository()

        val session = newSession(
            games,
            ratings
        )

        session.startGame(
            alice,
            bob
        )

        assertEquals(
            GameStatus.SETUP_P1,
            session.getGame().status
        )

        assertTrue(
            session
                .placeShips(alice, validFleet())
                .isValid
        )

        assertTrue(
            session
                .placeShips(bob, validFleet())
                .isValid
        )

        assertEquals(
            GameStatus.IN_PROGRESS,
            session.getGame().status
        )

        assertEquals(
            ShotResult.MISS,
            session
                .makeMove(alice, c(9, 9))
                .result
        )

        assertEquals(
            ShotResult.MISS,
            session
                .makeMove(bob, c(8, 8))
                .result
        )

        fleetCells().forEach { coordinate ->
            if (
                session.getGame().status ==
                GameStatus.IN_PROGRESS
            ) {
                session.makeMove(
                    alice,
                    coordinate
                )
            }
        }

        val game = session.getGame()

        assertEquals(
            GameStatus.FINISHED,
            game.status
        )

        assertEquals(
            alice,
            game.winner
        )

        assertEquals(
            listOf(game),
            games.findAll()
        )

        assertTrue(
            game.eloChanges!!
                .getValue(alice)
                .delta > 0
        )

        assertTrue(
            game.eloChanges!!
                .getValue(bob)
                .delta < 0
        )

        assertEquals(
            game.eloChanges!!
                .getValue(alice)
                .newRating,
            ratings.findByPlayer(alice).rating
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Integration tests.
     *
     * Проверяем совместную работу репозиториев и StatisticsService.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `repositories and statistics should expose saved game data`() {
        val alice = Player("1", "Alice")
        val bob = Player("2", "Bob")

        val games = InMemoryGameRepository()
        val ratings = InMemoryEloRatingRepository()
        val players = InMemoryPlayerRepository()

        val stats = StatisticsServiceImpl(
            games,
            ratings
        )

        val game = Game(
            "g1",
            alice,
            bob
        ).apply {
            status = GameStatus.FINISHED
            winner = alice
        }

        players.save(alice)
        players.save(bob)

        ratings.save(
            EloRating(
                alice,
                1016
            )
        )

        games.save(game)

        assertEquals(
            alice,
            players.findByName("aLiCe")
        )

        assertEquals(
            listOf(alice, bob),
            players.findAll()
        )

        assertEquals(
            game,
            games.findById("g1")
        )

        assertEquals(
            listOf(game),
            games.findByPlayer(bob)
        )

        assertEquals(
            1016,
            ratings.findByPlayer(alice).rating
        )

        assertEquals(
            1,
            stats.getStats(alice).gamesPlayed
        )

        assertEquals(
            1,
            stats.getStats(alice).wins
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * System tests.
     *
     * Пользователь проходит полный сценарий через настоящий ConsoleApplication.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `system scenario should create players run a full game and print history`() {
        val writer = StringWriter()

        val input = buildString {
            appendLine("1")
            appendLine("1")
            appendLine("Alice")

            appendLine("1")
            appendLine("Bob")
            appendLine("0")

            appendLine("2")
            appendLine("1")
            appendLine("2")

            appendLine(validPlacementText())
            appendLine(validPlacementText())

            fleetCells().forEach {
                appendLine(it.toDisplayString())
            }

            appendLine("0")
        }

        newApplication(
            BufferedReader(
                StringReader(input)
            ),
            writer
        ).run()

        val out = writer.toString()

        assertTrue(
            out.contains("Игрок 'Alice' добавлен")
        )

        assertTrue(
            out.contains("Партия создана")
        )

        assertTrue(
            out.contains("Победил Alice!")
        )

        assertTrue(
            out.contains("Расстановка Alice")
        )

        assertTrue(
            out.contains("Рейтинги:")
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * System tests.
     *
     * Проверяем, что приложение корректно обрабатывает ошибочную расстановку
     * и после этого завершает работу.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `system scenario should reject invalid placement and finish cleanly`() {
        val writer = StringWriter()

        val input = buildString {
            appendLine("1")
            appendLine("1")
            appendLine("Alice")

            appendLine("1")
            appendLine("Bob")
            appendLine("0")

            appendLine("2")
            appendLine("1")
            appendLine("2")

            repeat(5) {
                appendLine("A1-A4")
            }

            appendLine("0")
        }

        newApplication(
            BufferedReader(
                StringReader(input)
            ),
            writer
        ).run()

        val out = writer.toString()

        assertTrue(
            out.contains("Нужно указать ровно 10 кораблей")
        )

        assertTrue(
            out.contains("Не удалось принять расстановку")
        )

        assertTrue(
            out.contains("Работа завершена")
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Создаёт игровую сессию со всеми необходимыми зависимостями.
     * ---------------------------------------------------------------------------------------------
     */
    private fun newSession(
        games: InMemoryGameRepository,
        ratings: InMemoryEloRatingRepository
    ) = GameSessionImpl(
        ShipPlacementValidatorImpl(),
        TurnValidatorImpl(),
        EloRatingServiceImpl(),
        games,
        ratings
    )

    /**
     * ---------------------------------------------------------------------------------------------
     * Создаёт консольное приложение с in-memory репозиториями.
     * ---------------------------------------------------------------------------------------------
     */
    private fun newApplication(
        input: BufferedReader,
        output: StringWriter
    ): ConsoleApplication {
        val players = InMemoryPlayerRepository()
        val games = InMemoryGameRepository()
        val ratings = InMemoryEloRatingRepository()

        return ConsoleApplication(
            input = input,
            output = output,
            playerRepository = players,
            gameRepository = games,
            eloRatingRepository = ratings,
            placementValidator = ShipPlacementValidatorImpl(),
            turnValidator = TurnValidatorImpl(),
            eloService = EloRatingServiceImpl(),
            statisticsService = StatisticsServiceImpl(
                games,
                ratings
            )
        )
    }
}

/**
 * =============================================================================================
 * Валидный набор кораблей для тестов.
 * =============================================================================================
 */
private fun validFleet(): List<Ship> = listOf(
    ship(
        ShipType.BATTLESHIP,
        listOf("A1", "A2", "A3", "A4")
    ),

    ship(
        ShipType.CRUISER,
        listOf("C1", "C2", "C3")
    ),

    ship(
        ShipType.CRUISER,
        listOf("E1", "E2", "E3")
    ),

    ship(
        ShipType.DESTROYER,
        listOf("G1", "G2")
    ),

    ship(
        ShipType.DESTROYER,
        listOf("I1", "I2")
    ),

    ship(
        ShipType.DESTROYER,
        listOf("G4", "G5")
    ),

    ship(
        ShipType.BOAT,
        listOf("A7")
    ),

    ship(
        ShipType.BOAT,
        listOf("C7")
    ),

    ship(
        ShipType.BOAT,
        listOf("E7")
    ),

    ship(
        ShipType.BOAT,
        listOf("G7")
    )
)

/**
 * =============================================================================================
 * Текстовое представление валидной расстановки кораблей.
 * =============================================================================================
 */
private fun validPlacementText(): String =
    "A1-A4; C1-C3; E1-E3; G1-G2; I1-I2; G4-G5; A7; C7; E7; G7"

/**
 * =============================================================================================
 * Возвращает все клетки кораблей тестового флота.
 * =============================================================================================
 */
private fun fleetCells(
    fleet: List<Ship> = validFleet()
): List<Coordinate> =
    fleet.flatMap { it.segments }

/**
 * =============================================================================================
 * Создаёт корабль по списку строковых координат.
 * =============================================================================================
 */
private fun ship(
    type: ShipType,
    coordinates: List<String>
): Ship =
    Ship(
        type,
        coordinates.map {
            Coordinate.fromString(it)!!
        }
    )

/**
 * =============================================================================================
 * Удобный способ создания координаты в тестах.
 * =============================================================================================
 */
private fun c(
    row: Int,
    col: Int
): Coordinate =
    Coordinate(
        row,
        col
    )
