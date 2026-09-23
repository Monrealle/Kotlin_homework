package battleship.presentation.gui

import battleship.application.GameSession
import battleship.application.GameSessionImpl
import battleship.domain.model.*
import battleship.domain.repository.*
import battleship.domain.service.*
import java.util.UUID

/**
 * =============================================================================================
 * Контроллер GUI для управления игроками, игровыми сессиями и историей партий.
 *
 * Связывает графический интерфейс с Application-, Domain- и Infrastructure-слоями.
 *
 * Предоставляет операции:
 *
 * - создание игроков;
 * - запуск партии;
 * - расстановка кораблей;
 * - выполнение ходов;
 * - получение истории партий;
 * - получение статистики;
 * - завершение (или прерывание) текущей партии.
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

    var session: GameSession? = null
    var game: Game? = null

    init {
        /*
         * Корабли в базе не хранятся, поэтому партию, не дошедшую до конца
         * в прошлый запуск, продолжить нельзя. Помечаем такие партии как прерванные,
         * чтобы в истории они не выглядели «идущими».
         */
        gameRepo.findAll()
            .filter { !it.isOver() }
            .forEach { abandon(it) }
    }

    /**
     * =============================================================================================
     * Добавить игрока.
     *
     * @param name имя нового игрока
     * @return null при успешном добавлении или текст ошибки
     * =============================================================================================
     */
    fun addPlayer(name: String): String? {

        val trimmed = name.trim()

        if (trimmed.isBlank()) {
            return "Имя не может быть пустым"
        }

        if (playerRepo.findByName(trimmed) != null) {
            return "Игрок уже существует"
        }

        val player =
            Player(
                UUID.randomUUID().toString(),
                trimmed
            )

        playerRepo.save(player)
        eloRepo.save(EloRating(player))

        return null
    }

    /**
     * =============================================================================================
     * Начать новую партию между двумя людьми.
     *
     * Корабли расставляются последующим вызовом [placeShips].
     * Если предыдущая партия ещё не закончена, она помечается как прерванная.
     *
     * @param p1 первый игрок
     * @param p2 второй игрок
     * @return null при успешном создании партии или текст ошибки
     * =============================================================================================
     */
    fun startGame(
        p1: Player,
        p2: Player
    ): String? {

        if (p1 == p2) {
            return "Игроки должны быть разными"
        }

        finish()

        val s =
            GameSessionImpl(
                placementValidator,
                turnValidator,
                eloService,
                gameRepo,

          eloRepo
            )

        s.startGame(
            p1,
            p2
        )

        session = s
        game = s.getGame()

        return null
    }

    /**
     * =============================================================================================
     * Расставить корабли для игрока.
     *
     * @param player игрок, которому принадлежит расстановка
     * @param ships список кораблей
     * @return результат проверки расстановки
     * =============================================================================================
     */
    fun placeShips(
        player: Player,
        ships: List<Ship>
    ): ValidationResult {

        val s =
            session
                ?: return ValidationResult.failure(
                    "Нет активной сессии"
                )

        val result =
            s.placeShips(
                player,
                ships
            )

        if (result.isValid) {
            game = s.getGame()
        }

        return result
    }

    /**
     * =============================================================================================
     * Сделать ход от имени текущего игрока.
     *
     * @param coord координата выстрела
     * @return null при успешном выполнении или текст ошибки
     * =============================================================================================
     */
    fun makeMove(
        coord: Coordinate
    ): String? {

        val s =
            session
                ?: return "Нет активной игры"

        val g =
            game
                ?: return "Нет активной игры"

        return try {

            s.makeMove(
                g.currentTurn,
                coord
            )

            game = s.getGame()

            null

        } catch (e: IllegalArgumentException) {
            e.message

        } catch (e: IllegalStateException) {
            e.message
        }
    }

    /**
     * =============================================================================================
     * Получить историю всех партий.
     *
     * Репозиторий является единым источником данных для GUI,
     * поэтому метод работает одинаково с SQLite- и in-memory-реализацией.
     *
     * @return список всех сохранённых партий
     * =============================================================================================
     */
    fun getGameHistory(): List<Game> =
        gameRepo.findAll()

    /**
     * =============================================================================================
     * Завершить текущую GUI-сессию.
     *
     * Партия не удаляется из репозитория и остаётся доступной в истории.
     * Если она ещё не закончена, то помечается как прерванная ([GameStatus.ABANDONED]).
     * =============================================================================================
     */
    fun finish() {

        game?.let { abandon(it) }

        session = null
        game = null
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Помечает незаконченную партию как прерванную и сохраняет её.
     * Законченные партии не изменяются.
     * ---------------------------------------------------------------------------------------------
     */
    private fun abandon(game: Game) {

        if (game.isOver()) {
            return
        }

        game.status = GameStatus.ABANDONED
        gameRepo.save(game)
    }

    /**
     * =============================================================================================
     * Возвращает статистику указанного игрока.
     *
     * @param player игрок
     * @return агрегированная статистика игрока
     * =============================================================================================
     */
    fun getStats(
        player: Player
    ): PlayerStats =
        statisticsService.getStats(player)
}
