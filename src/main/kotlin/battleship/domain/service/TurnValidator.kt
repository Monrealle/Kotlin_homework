package battleship.domain.service

import battleship.domain.model.*

/**
 * =============================================================================================
 * Валидатор хода.
 *
 * Определяет контракт проверки: может ли указанный игрок
 * выстрелить по заданной клетке в текущий момент игры.
 *
 * @see TurnValidatorImpl
 * =============================================================================================
 */
interface TurnValidator {
    /**
     * ---------------------------------------------------------------------------------------------
     * Проверить право игрока на выстрел.
     *
     * @param game текущее состояние партии
     * @param player игрок, желающий сделать ход
     * @param coord координата выстрела
     * @return [ValidationResult] - успех или описание причины отказа
     * ---------------------------------------------------------------------------------------------
     */
    fun canFire(game: Game, player: Player, coord: Coordinate): ValidationResult
}

/**
 * =============================================================================================
 * Стандартная реализация валидатора хода.
 *
 * Правила проверки:
 * 1. Статус игры - игра должна быть в процессе (`IN_PROGRESS`).
 * 2. Очерёдность - текущий ход должен принадлежать игроку [player].
 * 3. Повторный выстрел - клетка не должна быть ранее атакована (HIT или MISS).
 *
 * Если все три условия выполнены, возвращается [ValidationResult.success].
 * Иначе - [ValidationResult.failure] с понятным сообщением.
 * =============================================================================================
 */
class TurnValidatorImpl : TurnValidator {

    /**
     * ---------------------------------------------------------------------------------------------
     * Выполняет три проверки: статус игры -> очерёдность -> повтор.
     *
     * @param game текущее состояние партии
     * @param player игрок, желающий сделать ход
     * @param coord координата выстрела
     * @return успех или ошибка с пояснением
     * ---------------------------------------------------------------------------------------------
     */
    override fun canFire(game: Game, player: Player, coord: Coordinate): ValidationResult {
        /* Проверка 1: статус игры */
        if (game.status != GameStatus.IN_PROGRESS) {
            return ValidationResult.failure("Игра не в процессе (статус: ${game.status})")
        }
        /* Проверка 2: очерёдность хода */
        if (game.currentTurn != player) {
            return ValidationResult.failure(
                "Сейчас ход ${game.currentTurn.name}, а не ${player.name}"
            )
        }

        /* Проверка 3: клетка не атакована ранее */
        val opponentBoard = game.opponentBoardOf(player)
        val cell = opponentBoard.grid[coord]
        if (cell == CellState.HIT || cell == CellState.MISS) {
            return ValidationResult.failure(
                "Клетка ${coord.toDisplayString()} уже была атакована"
            )
        }
        return ValidationResult.success()
    }
}
