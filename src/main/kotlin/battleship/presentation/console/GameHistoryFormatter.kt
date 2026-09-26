package battleship.presentation.console

import battleship.domain.model.Board
import battleship.domain.model.CellState
import battleship.domain.model.Game
import battleship.domain.model.Ship
import battleship.domain.model.ShipType

/**
 * =============================================================================================
 * Форматировщик истории партий.
 *
 * Преобразует объект [Game] в подробный текстовый отчёт для администратора.
 * Отчёт содержит игроков, статус, все ходы, обе расстановки кораблей
 * и изменение рейтингов после завершения партии.
 * =============================================================================================
 */
object GameHistoryFormatter {

    /**
     * ---------------------------------------------------------------------------------------------
     * Формирует полный отчёт по партии.
     * ---------------------------------------------------------------------------------------------
     */
    fun format(game: Game): String = buildString {
        appendLine("=== Партия #${game.id.take(8)} ===")
        appendLine("Игроки: ${game.player1.name} vs ${game.player2.name}")
        appendLine("Статус: ${game.status}")
        appendLine("Победитель: ${game.winner?.name ?: "не определён"}")
        appendLine()
        appendLine("Ходы:")
        if (game.moves.isEmpty()) {
            appendLine("  — нет ходов")
        } else {
            game.moves.forEach { move ->
                appendLine(
                    "  ${move.turnNumber}. ${move.player.name}: " +
                        "${move.coordinate.toDisplayString()} -> ${move.result}"
                )
            }
        }

        appendLine()
        appendLine("Расстановка ${game.player1.name}:")
        appendBoard(game.board1, this)
        appendLine("Корабли: ${formatShips(game.board1.ships)}")

        appendLine()
        appendLine("Расстановка ${game.player2.name}:")
        appendBoard(game.board2, this)
        appendLine("Корабли: ${formatShips(game.board2.ships)}")

        appendLine()
        appendLine("Рейтинги:")
        game.eloChanges?.values
            ?.sortedBy { it.player.name.lowercase() }
            ?.forEach { change ->
                appendLine(
                    "  ${change.player.name}: ${change.oldRating} -> ${change.newRating} " +
                        "(${change.delta.signString()})"
                )
            }
            ?: appendLine("  — партия ещё не завершена")
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Формирует компактное описание кораблей и их координат.
     * ---------------------------------------------------------------------------------------------
     */
    private fun formatShips(ships: List<Ship>): String =
        if (ships.isEmpty()) {
            "—"
        } else {
            ships.groupBy { it.type }
                .toSortedMap(compareBy<ShipType> { it.size }.reversed())
                .flatMap { (type, sameType) ->
                    sameType.mapIndexed { index, ship ->
                        "${type.displayName} ${index + 1}: " +
                            ship.segments.joinToString(" ") { it.toDisplayString() }
                    }
                }
                .joinToString("; ")
        }

    /**
     * ---------------------------------------------------------------------------------------------
     * Добавляет в отчёт текстовое представление игрового поля.
     * ---------------------------------------------------------------------------------------------
     */
    private fun appendBoard(board: Board, out: StringBuilder) {
        out.appendLine("    1 2 3 4 5 6 7 8 9 10")
        for (row in 0..9) {
            val letter = ('A'.code + row).toChar()
            out.append("  $letter ")
            for (col in 0..9) {
                val state = board.grid.entries.first { it.key.row == row && it.key.col == col }.value
                val symbol = when (state) {
                    CellState.EMPTY -> '·'
                    CellState.SHIP -> '■'
                    CellState.HIT -> 'X'
                    CellState.MISS -> 'o'
                }
                out.append(symbol).append(' ')
            }
            out.appendLine()
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Добавляет знак перед положительным изменением рейтинга.
     * ---------------------------------------------------------------------------------------------
     */
    private fun Int.signString(): String = when {
        this > 0 -> "+$this"
        else -> this.toString()
    }
}
