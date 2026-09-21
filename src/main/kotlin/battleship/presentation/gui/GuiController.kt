package battleship.presentation.gui

import battleship.application.GameSessionImpl
import battleship.domain.model.*
import battleship.domain.repository.*
import battleship.domain.service.*
import java.util.UUID

/**
 * =============================================================================================
 * Контроллер GUI для управления игроками и текущей игровой сессией.
 *
 * Связывает графический интерфейс с Application-, Domain- и Infrastructure-слоями.
 *
 * Предоставляет операции:
 *
 * - создание игроков;
 * - запуск партии;
 * - расстановка кораблей;
 * - выполнение ходов;
 * - получение статистики;
 * - завершение текущей сессии.
 * =============================================================================================
 */
class GuiController(
    val playerRepo: PlayerRepository,
    val gameRepo: GameRepository,
    val eloRepo: EloRatingRepository,
    private val placementValidator: ShipPlacementValidator,
    private val turnValidator: TurnValidator,
    private val eloService: EloRatingService,
    private val statisticsService: StatisticsService
) {
    var session: GameSessionImpl? = null
    var game: Game? = null

    /** Добавить игрока, возвращает null при успехе или текст ошибки. */
    fun addPlayer(name: String): String? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return "Имя не может быть пустым"
        if (playerRepo.findByName(trimmed) != null) return "Игрок уже существует"
        val player = Player(UUID.randomUUID().toString(), trimmed)
        playerRepo.save(player)
        eloRepo.save(EloRating(player))
        return null
    }

    /**
     * Начать новую партию между двумя людьми.
     * Корабли расставляются вручную вызовом [placeShips].
     */
    fun startGame(p1: Player, p2: Player): String? {
        if (p1 == p2) return "Игроки должны быть разными"
        val s = GameSessionImpl(
            placementValidator, turnValidator, eloService,
            gameRepo, eloRepo, botStrategy = null
        )
        s.startGame(p1, p2)
        session = s
        game = s.getGame()
        return null
    }

    /** Расставить корабли для игрока (передать уже проверенный список). */
    fun placeShips(player: Player, ships: List<Ship>): ValidationResult {
        val s = session ?: return ValidationResult.failure("Нет активной сессии")
        val result = s.placeShips(player, ships)
        if (result.isValid) game = s.getGame()
        return result
    }

    /** Сделать ход от имени текущего игрока, возвращает null или ошибку. */
    fun makeMove(coord: Coordinate): String? {
        val s = session ?: return "Нет активной игры"
        val g = game ?: return "Нет активной игры"
        return try {
            s.makeMove(g.currentTurn, coord)
            game = s.getGame()
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }
    }

    /** Завершить сессию. */
    fun finish() {
        session = null
        game = null
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает статистику указанного игрока.
     *
     * @param player игрок
     * @return агрегированная статистика игрока
     * ---------------------------------------------------------------------------------------------
     */
    fun getStats(player: Player): PlayerStats =
        statisticsService.getStats(player)
}
