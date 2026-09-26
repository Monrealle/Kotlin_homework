package battleship.application

import battleship.domain.model.Coordinate
import battleship.domain.model.EloRating
import battleship.domain.model.Game
import battleship.domain.model.GameStatus
import battleship.domain.model.Move
import battleship.domain.model.Player
import battleship.domain.model.Ship
import battleship.domain.model.ShotResult
import battleship.domain.model.ValidationResult
import battleship.domain.repository.EloRatingRepository
import battleship.domain.repository.GameRepository
import battleship.domain.service.EloRatingService
import battleship.domain.service.ShipPlacementValidator
import battleship.domain.service.TurnValidator
import java.util.UUID

/**
 * =============================================================================================
 * Реализация координатора игровой сессии (Application-слой).
 *
 * Отвечает за последовательность действий в одной партии:
 * создание партии -> расстановка кораблей -> выполнение ходов -> завершение.
 *
 * В hw3 администратор регистрирует расстановку обоих игроков
 * и вручную заносит ходы. Конкретный способ подготовки флота выбирается GUI.
 * =============================================================================================
 */
class GameSessionImpl(
    private val placementValidator: ShipPlacementValidator,
    private val turnValidator: TurnValidator,
    private val eloService: EloRatingService,
    private val gameRepository: GameRepository,
    private val eloRatingRepository: EloRatingRepository
) : GameSession {

    private var game: Game? = null

    /**
     * ---------------------------------------------------------------------------------------------
     * Создаёт и сохраняет новую партию.
     *
     * Игроки должны отличаться друг от друга.
     * ---------------------------------------------------------------------------------------------
     */
    override fun startGame(p1: Player, p2: Player) {
        require(p1 != p2) { "Нельзя создать партию игрока с самим собой" }
        val newGame = Game(UUID.randomUUID().toString(), p1, p2)
        game = newGame
        gameRepository.save(newGame)
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет и сохраняет расстановку кораблей одного из игроков.
     *
     * Порядок расстановки фиксирован: сначала первый игрок,
     * затем второй. После успешной второй расстановки партия начинается.
     * ---------------------------------------------------------------------------------------------
     */
    override fun placeShips(player: Player, ships: List<Ship>): ValidationResult {
        val currentGame = requireGame()

        val placementStatus = when (player) {
            currentGame.player1 -> GameStatus.SETUP_P1
            currentGame.player2 -> GameStatus.SETUP_P2
            else -> return ValidationResult.failure("Игрок не участвует в этой партии")
        }

        if (currentGame.status != placementStatus) {
            return ValidationResult.failure(
                "Сейчас нельзя расставить корабли: статус игры ${currentGame.status}"
            )
        }

        val validation = placementValidator.validate(ships)
        if (!validation.isValid) return validation

        currentGame.boardOf(player).placeShips(ships)
        currentGame.status = when (player) {
            currentGame.player1 -> GameStatus.SETUP_P2
            currentGame.player2 -> GameStatus.IN_PROGRESS
            else -> error("Недопустимый игрок")
        }
        gameRepository.save(currentGame)
        return ValidationResult.success()
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Выполняет один ход и сохраняет его в истории партии.
     *
     * Результат `MISS` передаёт ход сопернику.
     * После `HIT` и `SUNK` ход остаётся у текущего игрока.
     * При `WIN` партия завершается и пересчитывается рейтинг игроков.
     * ---------------------------------------------------------------------------------------------
     */
    override fun makeMove(player: Player, coord: Coordinate): Move {
        val currentGame = requireGame()
        val validation = turnValidator.canFire(currentGame, player, coord)
        require(validation.isValid) { validation.errors.joinToString("; ") }

        val result = currentGame.opponentBoardOf(player).receiveShot(coord)
        val move = Move(
            turnNumber = currentGame.moves.size + 1,
            player = player,
            coordinate = coord,
            result = result
        )
        currentGame.moves += move

        when (result) {
            ShotResult.WIN -> finishGame(currentGame, player)
            ShotResult.MISS -> currentGame.currentTurn = opponentOf(currentGame, player)
            ShotResult.HIT, ShotResult.SUNK -> Unit
        }

        gameRepository.save(currentGame)
        return move
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает текущую партию.
     * ---------------------------------------------------------------------------------------------
     */
    override fun getGame(): Game = requireGame()

    /**
     * ---------------------------------------------------------------------------------------------
     * Завершает партию и пересчитывает рейтинг игроков.
     *
     * Сохраняет победителя, статус `FINISHED`, изменения рейтингов
     * и новые значения рейтингов в репозитории.
     * ---------------------------------------------------------------------------------------------
     */
    private fun finishGame(game: Game, winner: Player) {
        game.winner = winner
        game.status = GameStatus.FINISHED

        val loser = opponentOf(game, winner)
        val winnerRating = eloRatingRepository.findByPlayer(winner).rating
        val loserRating = eloRatingRepository.findByPlayer(loser).rating
        val changes = eloService.calculateRatings(
            winner,
            winnerRating,
            loser,
            loserRating
        )

        game.eloChanges = changes
        changes.values.forEach { change ->
            eloRatingRepository.save(EloRating(change.player, change.newRating))
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Находит соперника указанного игрока в текущей партии.
     * ---------------------------------------------------------------------------------------------
     */
    private fun opponentOf(game: Game, player: Player): Player =
        when (player) {
            game.player1 -> game.player2
            game.player2 -> game.player1
            else -> error("Игрок не участвует в этой партии")
        }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает текущую партию или сообщает, что партия ещё не создана.
     * ---------------------------------------------------------------------------------------------
     */
    private fun requireGame(): Game =
        game ?: error("Партия ещё не создана")
}
