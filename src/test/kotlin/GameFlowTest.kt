import battleship.application.GameSessionImpl
import battleship.application.PlacementParser
import battleship.application.RandomShipPlacer
import battleship.domain.model.*
import battleship.domain.service.*
import battleship.infrastructure.*
import java.nio.file.Files
import battleship.presentation.gui.GuiController
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * =============================================================================================
 * Тесты игрового процесса: очерёдность ходов в сессии и жизненный цикл партии в GUI-контроллере.
 * Используются in-memory репозитории, поэтому тесты не зависят от базы данных.
 * =============================================================================================
 */
class GameFlowTest {

    private val alice = Player("1", "Alice")
    private val bob = Player("2", "Bob")

    private val gameRepository = InMemoryGameRepository()
    private val eloRepository = InMemoryEloRatingRepository()

    private fun newSession(): GameSessionImpl =
        GameSessionImpl(
            placementValidator = ShipPlacementValidatorImpl(),
            turnValidator = TurnValidatorImpl(),
            eloService = EloRatingServiceImpl(Random(42)),
            gameRepository = gameRepository,
            eloRatingRepository = eloRepository
        )

    private fun startedSession(): GameSessionImpl {
        val session = newSession()
        session.startGame(alice, bob)
        assertTrue(session.placeShips(alice, RandomShipPlacer.generate()).isValid)
        assertTrue(session.placeShips(bob, RandomShipPlacer.generate()).isValid)
        return session
    }

    private fun newController(): GuiController =
        GuiController(
            playerRepo = InMemoryPlayerRepository(),
            gameRepo = gameRepository,
            eloRepo = eloRepository,
            placementValidator = ShipPlacementValidatorImpl(),
            turnValidator = TurnValidatorImpl(),
            eloService = EloRatingServiceImpl(Random(42)),
            statisticsService = StatisticsServiceImpl(gameRepository, eloRepository)
        )

    private fun cellsOf(board: Board, state: CellState): List<Coordinate> =
        board.grid.filterValues { it == state }.keys.sortedWith(compareBy({ it.row }, { it.col }))

    /* Промах передаёт ход сопернику, попадание оставляет ход у стрелявшего. */
    @Test
    fun `miss passes the turn and hit keeps it`() {
        val session = startedSession()
        val game = session.getGame()
        assertEquals(alice, game.currentTurn)

        val miss = session.makeMove(alice, cellsOf(game.board2, CellState.EMPTY).first())
        assertEquals(ShotResult.MISS, miss.result)
        assertEquals(bob, game.currentTurn)

        val hit = session.makeMove(bob, cellsOf(game.board1, CellState.SHIP).first())
        assertNotEquals(ShotResult.MISS, hit.result)
        assertEquals(bob, game.currentTurn)
    }

    /* Нельзя стрелять вне очереди и повторно по одной и той же клетке. */
    @Test
    fun `session should reject out of turn and repeated shots`() {
        val session = startedSession()
        val game = session.getGame()
        val target = cellsOf(game.board2, CellState.SHIP).first()

        assertThrows(IllegalArgumentException::class.java) {
            session.makeMove(bob, cellsOf(game.board1, CellState.EMPTY).first())
        }

        session.makeMove(alice, target)

        assertThrows(IllegalArgumentException::class.java) {
            session.makeMove(alice, target)
        }
        assertEquals(1, game.moves.size, "отклонённые ходы в историю не попадают")
    }

    /* Новая партия прерывает предыдущую незаконченную. */
    @Test
    fun `starting a new game should abandon the unfinished one`() {
        val controller = newController()

        assertNull(controller.startGame(alice, bob))
        val first = controller.game!!
        assertTrue(controller.placeShips(alice, RandomShipPlacer.generate()).isValid)
        assertTrue(controller.placeShips(bob, RandomShipPlacer.generate()).isValid)

        assertNull(controller.startGame(alice, bob))

        assertEquals(GameStatus.ABANDONED, first.status)
        assertEquals(GameStatus.SETUP_P1, controller.game!!.status)
        assertEquals(2, controller.getGameHistory().size)
    }

    /* Завершение сессии прерывает идущую партию, но не меняет законченную. */
    @Test
    fun `finish should abandon running game but keep finished game`() {
        val controller = newController()
        controller.startGame(alice, bob)
        val running = controller.game!!
        controller.finish()

        assertEquals(GameStatus.ABANDONED, running.status)
        assertNull(controller.game)

        controller.startGame(alice, bob)
        val done = controller.game!!
        done.status = GameStatus.FINISHED
        done.winner = alice
        controller.finish()

        assertEquals(GameStatus.FINISHED, done.status)
        assertEquals(alice, done.winner)
    }

    /* Партии, оставшиеся незавершёнными от прошлого запуска, помечаются прерванными. */
    @Test
    fun `new controller should abandon leftover games`() {
        val leftover = Game("old-game", alice, bob)
        leftover.status = GameStatus.IN_PROGRESS
        gameRepository.save(leftover)

        val finished = Game("finished-game", alice, bob)
        finished.status = GameStatus.FINISHED
        finished.winner = bob
        gameRepository.save(finished)

        newController()

        assertEquals(GameStatus.ABANDONED, leftover.status)
        assertEquals(GameStatus.FINISHED, finished.status)
    }

    /* Прерванные партии не попадают в статистику игрока. */
    @Test
    fun `abandoned games should not count in statistics`() {
        val won = Game("won", alice, bob)
        won.status = GameStatus.FINISHED
        won.winner = alice
        val dropped = Game("dropped", alice, bob)
        dropped.status = GameStatus.ABANDONED
        gameRepository.save(won)
        gameRepository.save(dropped)

        val stats = StatisticsServiceImpl(gameRepository, eloRepository).getStats(alice)

        assertEquals(1, stats.gamesPlayed)
        assertEquals(1, stats.wins)
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет сохранение и загрузку игрока и рейтинга через JSON-репозитории.
     *
     * Для теста используется временная директория, поэтому реальные файлы проекта
     * не затрагиваются.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `json repositories should persist players and ratings`() {
        val directory = Files.createTempDirectory("battleship-json-")
        val playersFile = directory.resolve("players.json")
        val ratingsFile = directory.resolve("ratings.json")

        val alice = Player("1", "Alice")

        JsonPlayerRepository(playersFile).save(alice)
        JsonEloRatingRepository(ratingsFile).save(
            EloRating(alice, 1042)
        )

        val loadedPlayer = JsonPlayerRepository(playersFile).findById("1")
        val loadedRating = JsonEloRatingRepository(ratingsFile)
            .findByPlayer(alice)

        assertEquals(alice, loadedPlayer)
        assertEquals(1042, loadedRating.rating)
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет сохранение истории партии вместе с расстановками и ходами.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `json game repository should persist placement and moves`() {
        val directory = Files.createTempDirectory("battleship-game-json-")
        val gamesFile = directory.resolve("games.json")

        val game = Game("json-game", alice, bob)
        game.board1.placeShips(RandomShipPlacer.generate())
        game.board2.placeShips(RandomShipPlacer.generate())
        game.status = GameStatus.IN_PROGRESS
        game.moves += Move(
            turnNumber = 1,
            player = alice,
            coordinate = Coordinate(0, 0),
            result = ShotResult.MISS
        )
        game.currentTurn = bob

        JsonGameRepository(gamesFile).save(game)

        val loaded = JsonGameRepository(gamesFile).findById("json-game")
            ?: error("Партия не загрузилась из JSON")

        assertEquals(GameStatus.IN_PROGRESS, loaded.status)
        assertEquals(10, loaded.board1.ships.size)
        assertEquals(10, loaded.board2.ships.size)
        assertEquals(1, loaded.moves.size)
        assertEquals("Alice", loaded.moves.first().player.name)
        assertEquals(Coordinate(0, 0), loaded.moves.first().coordinate)
    }


    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет общий парсер ручной расстановки, который используется и консолью, и GUI.
     * ---------------------------------------------------------------------------------------------
     */
    @Test
    fun `manual placement parser should return a valid fleet`() {
        val parsed = PlacementParser.parse(
            "A1-A4; C1-C3; E1-E3; G1-G2; I1-I2; G4-G5; A7; C7; E7; G7"
        )

        assertTrue(parsed.isSuccess)
        assertTrue(
            ShipPlacementValidatorImpl()
                .validate(parsed.getOrThrow())
                .isValid
        )
    }

}
