package battleship.application

import battleship.domain.model.*
import battleship.domain.repository.*
import battleship.domain.service.*
import java.util.UUID

/**
 * =============================================================================================
 * Реализация игровой сессии - координатора одной партии «Морского боя».
 *
 * Жизненный цикл игры:
 *
 * 1. [startGame] - создаётся объект [Game] со статусом `SETUP_P1`.
 * 2. [placeShips] для первого игрока → статус `SETUP_P2`.
 * 3. [placeShips] для второго игрока → статус `IN_PROGRESS`.
 * 4. [makeMove] выполняется многократно до завершения партии.
 * 5. При победе устанавливается `FINISHED`, определяется победитель
 *    и рассчитываются изменения рейтинга Эло.
 *
 * @param placementValidator валидатор расстановки кораблей
 * @param turnValidator валидатор очередности и допустимости выстрела
 * @param eloService сервис расчёта рейтинга Эло
 * @param gameRepository репозиторий игровых партий
 * @param eloRatingRepository репозиторий текущих рейтингов игроков
 * =============================================================================================
 */
class GameSessionImpl(
    private val placementValidator: ShipPlacementValidator,
    private val turnValidator: TurnValidator,
    private val eloService: EloRatingService,
    private val gameRepository: GameRepository,
    private val eloRatingRepository: EloRatingRepository
) : GameSession {

    private lateinit var game: Game

    /**
     * ---------------------------------------------------------------------------------------------
     * Создаёт новую партию со статусом `SETUP_P1` и сохраняет её в репозитории.
     * ---------------------------------------------------------------------------------------------
     */
    override fun startGame(p1: Player, p2: Player) {
        game = Game(id = UUID.randomUUID().toString(), player1 = p1, player2 = p2)
        gameRepository.save(game)
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Принимает список кораблей от игрока, проверяет через [placementValidator]
     * и, если валидация пройдена, размещает их на доске игрока.
     * Переводит игру в следующий статус (`SETUP_P2` или `IN_PROGRESS`).
     *
     * @return [ValidationResult.success] при успехе, иначе - результат с ошибками
     * ---------------------------------------------------------------------------------------------
     */
    override fun placeShips(player: Player, ships: List<Ship>): ValidationResult {
        val validation = placementValidator.validate(ships)
        if (!validation.isValid) return validation

        when (player) {
            game.player1 -> {
                if (game.status != GameStatus.SETUP_P1)
                    return ValidationResult.failure(
                        "Невозможно расставить корабли для ${player.name}: статус игры ${game.status}"
                    )
                game.board1.placeShips(ships)
                game.status = GameStatus.SETUP_P2
            }
            game.player2 -> {
                if (game.status != GameStatus.SETUP_P2)
                    return ValidationResult.failure(
                        "Невозможно расставить корабли для ${player.name}: статус игры ${game.status}"
                    )
                game.board2.placeShips(ships)
                game.status = GameStatus.IN_PROGRESS
            }
            else -> return ValidationResult.failure(
                "Невозможно расставить корабли для ${player.name}: статус игры ${game.status}"
            )
        }

        gameRepository.save(game)
        return ValidationResult.success()
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Выполняет ход игрока.
     *
     * Алгоритм:
     * - Проверяет право хода через [turnValidator].
     * - Выполняет выстрел (см. [executeShot]).
     *
     * @throws IllegalArgumentException если ход нелегален (очерёдность, повтор, статус)
     * @return объект [Move], описывающий ход, совершённый игроком [player]
     * ---------------------------------------------------------------------------------------------
     */
    override fun makeMove(player: Player, coord: Coordinate): Move {
        val validation = turnValidator.canFire(game, player, coord)
        require(validation.isValid) { validation.errors.joinToString("; ") }

        return executeShot(player, coord)
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает текущий объект [Game].
     *
     * Это живая ссылка - все последующие изменения в сессии будут видны через неё.
     * ---------------------------------------------------------------------------------------------
     */
    override fun getGame(): Game = game

    /**
     * ---------------------------------------------------------------------------------------------
     * Выполняет одиночный выстрел от имени [player] по координате [coord].
     *
     * - Наносит удар по доске оппонента.
     * - Создаёт запись [Move] и добавляет её в лог игры.
     * - Если выстрел приводит к победе - вызывает [finishGame].
     * - При промахе передаёт ход оппоненту.
     * ---------------------------------------------------------------------------------------------
     */
    private fun executeShot(player: Player, coord: Coordinate): Move {
        val opponentBoard = game.opponentBoardOf(player)
        val result = opponentBoard.receiveShot(coord)

        val move = Move(
            turnNumber = game.moves.size + 1, /* Номер хода                              */
            player = player,                  /* Игрок, сделавший выстрел                */
            coordinate = coord,               /* Координата, куда был произведён выстрел */
            result = result                   /* Исход выстрела (MISS, HIT, SUNK, WIN)   */
        )
        game.moves.add(move)

        when (result) {
            ShotResult.WIN -> finishGame(winner = player)
            ShotResult.MISS -> game.currentTurn = opponent(player)
            else -> { /* HIT / SUNK — ход остаётся у того же игрока */ }
        }

        gameRepository.save(game)
        return move
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Завершает игру победой [winner].
     * Рассчитывает и сохраняет изменения рейтинга Эло для обоих игроков.
     * ---------------------------------------------------------------------------------------------
     */
    private fun finishGame(winner: Player) {
        game.winner = winner
        game.status = GameStatus.FINISHED

        val loser = opponent(winner)
        val winnerRating = eloRatingRepository.findByPlayer(winner).rating
        val loserRating = eloRatingRepository.findByPlayer(loser).rating
        val changes = eloService.calculateRatings(winner, winnerRating, loser, loserRating)

        game.eloChanges = changes
        changes.values.forEach { ch -> eloRatingRepository.save(EloRating(ch.player, ch.newRating))
        }
    }

    private fun opponent(player: Player): Player =
        if (player == game.player1) game.player2 else game.player1
}
