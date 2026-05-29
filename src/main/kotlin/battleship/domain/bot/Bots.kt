package battleship.domain.bot

import battleship.domain.model.*
import kotlin.random.Random

/** Стратегия выбора хода для бота. */
interface MoveStrategy {
    /**
     * Следующий ход бота.
     * @param board поле противника (бот видит только HIT/MISS клетки)
     * @param history история всех ходов текущей партии
     */
    fun nextMove(board: Board, history: List<Move>): Coordinate
}

// ─────────────────────────────────────────────────────────────────────────────

/** Стрелял случайно по ещё не атакованным клеткам. */
class RandomBot(
    private val random: Random = Random.Default
) : MoveStrategy {

    override fun nextMove(board: Board, history: List<Move>): Coordinate {
        val available = board.unattackedCells()
        require(available.isNotEmpty()) { "Нет доступных клеток для хода" }
        return available.random(random)
    }
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * Умный бот: при наличии не затопленных попаданий («добивающий» режим)
 * атакует соседние клетки, следуя линии попаданий.
 * При отсутствии незакрытых попаданий делегирует RandomBot (композиция).
 */
class SmartBot(
    private val randomBot: RandomBot = RandomBot(),
    private val random: Random = Random.Default
) : MoveStrategy {

    override fun nextMove(board: Board, history: List<Move>): Coordinate {
        val attacked = board.attackedCells()
        val hitCoords = board.grid.entries
            .filter { it.value == CellState.HIT }
            .map { it.key }

        if (hitCoords.isNotEmpty()) {
            // Попытаться продолжить линию из 2+ попаданий
            val lineCandidate = findLineContinuation(hitCoords, attacked)
            if (lineCandidate != null) return lineCandidate

            // Иначе атаковать любого из соседей любого попадания
            val neighbors = hitCoords.flatMap { coord ->
                listOfNotNull(
                    Coordinate.ofOrNull(coord.row - 1, coord.col),
                    Coordinate.ofOrNull(coord.row + 1, coord.col),
                    Coordinate.ofOrNull(coord.row, coord.col - 1),
                    Coordinate.ofOrNull(coord.row, coord.col + 1)
                ).filter { it !in attacked }
            }.distinct()

            if (neighbors.isNotEmpty()) return neighbors.random(random)
        }

        return randomBot.nextMove(board, history)
    }

    /**
     * Если есть 2+ последовательных попадания по одной линии — продолжить её.
     */
    private fun findLineContinuation(
        hitCoords: List<Coordinate>,
        attacked: Set<Coordinate>
    ): Coordinate? {
        if (hitCoords.size < 2) return null

        // Горизонтальные линии
        hitCoords.groupBy { it.row }.forEach { (row, coords) ->
            if (coords.size >= 2) {
                val sorted = coords.sortedBy { it.col }
                val isContiguous = (1 until sorted.size)
                    .all { sorted[it].col - sorted[it - 1].col == 1 }
                if (isContiguous) {
                    val candidates = listOfNotNull(
                        Coordinate.ofOrNull(row, sorted.first().col - 1),
                        Coordinate.ofOrNull(row, sorted.last().col + 1)
                    ).filter { it !in attacked }
                    if (candidates.isNotEmpty()) return candidates.random(random)
                }
            }
        }

        // Вертикальные линии
        hitCoords.groupBy { it.col }.forEach { (col, coords) ->
            if (coords.size >= 2) {
                val sorted = coords.sortedBy { it.row }
                val isContiguous = (1 until sorted.size)
                    .all { sorted[it].row - sorted[it - 1].row == 1 }
                if (isContiguous) {
                    val candidates = listOfNotNull(
                        Coordinate.ofOrNull(sorted.first().row - 1, col),
                        Coordinate.ofOrNull(sorted.last().row + 1, col)
                    ).filter { it !in attacked }
                    if (candidates.isNotEmpty()) return candidates.random(random)
                }
            }
        }

        return null
    }
}
