import battleship.domain.model.*
import battleship.infrastructure.SqliteEloRatingRepository
import battleship.infrastructure.SqliteGameRepository
import battleship.infrastructure.SqlitePlayerRepository
import battleship.infrastructure.database.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

/**
 * =============================================================================================
 * Тесты SQLite-репозиториев.
 *
 * Каждый тест работает с отдельным временным файлом базы данных,
 * поэтому реальная база пользователя (~/.battleship/battleship.db) не затрагивается.
 * =============================================================================================
 */
class SqliteRepositoriesTest {

    private lateinit var tempDir: File

    private val alice = Player("p-alice", "Alice")
    private val bob = Player("p-bob", "Bob")

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("battleship-test").toFile()
        Database.useFile(File(tempDir, "test.db"))
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /*
     * Игроки сохраняются, находятся по id и имени (без учёта регистра)
     * и не пропадают при создании нового экземпляра репозитория.
     */
    @Test
    fun `player repository should persist players`() {
        val repo = SqlitePlayerRepository()
        repo.save(bob)
        repo.save(alice)

        assertEquals(listOf(alice, bob), repo.findAll(), "игроки сортируются по имени")
        assertEquals(alice, repo.findById("p-alice"))
        assertEquals(bob, repo.findByName("bOB"))
        assertNull(repo.findByName("Nobody"))

        val reopened = SqlitePlayerRepository()
        assertEquals(2, reopened.findAll().size)
    }

    /* Рейтинг по умолчанию равен начальному, сохранённый рейтинг обновляется. */
    @Test
    fun `rating repository should return default and update rating`() {
        SqlitePlayerRepository().save(alice)
        val repo = SqliteEloRatingRepository()

        assertEquals(EloRating.INITIAL_RATING, repo.findByPlayer(alice).rating)

        repo.save(EloRating(alice, 1033))
        repo.save(EloRating(alice, 1066))

        assertEquals(1066, SqliteEloRatingRepository().findByPlayer(alice).rating)
    }

    /*
     * Партия вместе с ходами восстанавливается из базы.
     * После промаха ход принадлежит сопернику, после попадания остаётся у того же игрока.
     */
    @Test
    fun `game repository should restore moves and current turn`() {
        savePlayers()
        val repo = SqliteGameRepository()

        val game = Game("game-1", alice, bob)
        game.status = GameStatus.IN_PROGRESS
        game.moves += Move(1, alice, Coordinate(0, 0), ShotResult.HIT)
        game.moves += Move(2, alice, Coordinate(0, 1), ShotResult.MISS)
        game.moves += Move(3, bob, Coordinate(5, 5), ShotResult.HIT)
        repo.save(game)

        val loaded = repo.findById("game-1")!!
        assertEquals(GameStatus.IN_PROGRESS, loaded.status)
        assertEquals(game.moves, loaded.moves)
        assertEquals(bob, loaded.currentTurn, "после попадания Боб ходит снова")

        game.moves += Move(4, bob, Coordinate(5, 6), ShotResult.MISS)
        repo.save(game)

        assertEquals(alice, repo.findById("game-1")!!.currentTurn, "после промаха ход у Алисы")
    }

    /*
     * Повторное сохранение партии обновляет её, а не дублирует ходы.
     */
    @Test
    fun `saving a game twice should not duplicate moves`() {
        savePlayers()
        val repo = SqliteGameRepository()

        val game = Game("game-1", alice, bob)
        game.moves += Move(1, alice, Coordinate(2, 2), ShotResult.MISS)
        repo.save(game)
        repo.save(game)

        game.status = GameStatus.FINISHED
        game.winner = alice
        repo.save(game)

        val loaded = repo.findAll()
        assertEquals(1, loaded.size)
        assertEquals(1, loaded[0].moves.size)
        assertEquals(GameStatus.FINISHED, loaded[0].status)
        assertEquals(alice, loaded[0].winner)
    }

    /*
     * Прерванная партия сохраняется со своим статусом и без победителя.
     */
    @Test
    fun `game repository should store abandoned status`() {
        savePlayers()
        val repo = SqliteGameRepository()

        val game = Game("game-1", alice, bob)
        game.status = GameStatus.ABANDONED
        repo.save(game)

        val loaded = repo.findById("game-1")!!
        assertEquals(GameStatus.ABANDONED, loaded.status)
        assertNull(loaded.winner)
    }

    /*
     * Партия сохраняет не только ходы, но и обе расстановки, состояния клеток
     * и изменения рейтинга после завершения.
     */
    @Test
    fun `game repository should restore boards and elo changes`() {
        savePlayers()
        val repo = SqliteGameRepository()

        val game = Game("game-full", alice, bob)
        val aliceShip = Ship(
            ShipType.DESTROYER,
            listOf(Coordinate(0, 0), Coordinate(0, 1))
        )
        val bobShip = Ship(
            ShipType.BOAT,
            listOf(Coordinate(5, 5))
        )

        game.board1.placeShips(listOf(aliceShip))
        game.board2.placeShips(listOf(bobShip))
        game.board2.receiveShot(Coordinate(0, 0))
        game.moves += Move(1, alice, Coordinate(0, 0), ShotResult.MISS)
        game.status = GameStatus.FINISHED
        game.winner = bob
        game.eloChanges = mapOf(
            alice to EloChange(alice, 1000, 975, -25),
            bob to EloChange(bob, 1000, 1025, 25)
        )

        repo.save(game)

        val loaded = SqliteGameRepository().findById("game-full")!!

        assertEquals(game.status, loaded.status)
        assertEquals(game.winner, loaded.winner)
        assertEquals(game.moves, loaded.moves)
        assertEquals(game.board1.ships, loaded.board1.ships)
        assertEquals(game.board2.ships, loaded.board2.ships)
        assertEquals(CellState.SHIP, loaded.board1.grid[Coordinate(0, 0)])
        assertEquals(CellState.MISS, loaded.board2.grid[Coordinate(0, 0)])
        assertEquals(game.eloChanges, loaded.eloChanges)
    }

    /* Партии игрока находятся по обоим местам за столом, порядок совпадает с порядком сохранения. */
    @Test
    fun `game repository should find games by player in creation order`() {
        val carol = Player("p-carol", "Carol")
        savePlayers()
        SqlitePlayerRepository().save(carol)
        val repo = SqliteGameRepository()

        repo.save(Game("g1", alice, bob))
        repo.save(Game("g2", bob, alice))
        repo.save(Game("g3", bob, carol))

        assertEquals(listOf("g1", "g2", "g3"), repo.findAll().map { it.id })
        assertEquals(listOf("g1", "g2"), repo.findByPlayer(alice).map { it.id })
        assertEquals(listOf("g3"), repo.findByPlayer(carol).map { it.id })
        assertNull(repo.findById("missing"))
    }

    /* База не принимает партию с несуществующим игроком (внешние ключи включены). */
    @Test
    fun `game repository should reject unknown players`() {
        SqlitePlayerRepository().save(alice)
        val repo = SqliteGameRepository()

        assertThrows(Exception::class.java) {
            repo.save(Game("game-1", alice, bob))
        }
    }

    private fun savePlayers() {
        val players = SqlitePlayerRepository()
        players.save(alice)
        players.save(bob)
    }
}
