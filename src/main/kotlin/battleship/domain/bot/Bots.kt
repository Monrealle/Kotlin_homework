package battleship.domain.bot

import battleship.domain.model.*
import java.util.ArrayDeque
import kotlin.random.Random

/**
 * =============================================================================================
 * Стратегия выбора следующего хода бота.
 *
 * Реализации данной стратегии получают доску противника
 * и историю уже совершённых ходов.
 * =============================================================================================
 */
interface MoveStrategy {

    /**
     * ---------------------------------------------------------------------------------------------
     * Выбирает следующую клетку для выстрела.
     *
     * @param board поле противника
     * @param history история ходов партии
     * @return координата следующего выстрела
     * ---------------------------------------------------------------------------------------------
     */
    fun nextMove(board: Board, history: List<Move>): Coordinate
}

/**
 * =============================================================================================
 * Случайный бот.
 *
 * Выбирает случайную клетку среди ещё не атакованных.
 * =============================================================================================
 */
class RandomBot(
    private val random: Random = Random.Default
) : MoveStrategy {

    override fun nextMove(
        board: Board,
        history: List<Move>
    ): Coordinate {

        val available = board.unattackedCells()

        require(available.isNotEmpty()) {
            "Нет доступных клеток для хода"
        }

        return available.random(random)
    }
}

/**
 * =============================================================================================
 * Умный бот.
 *
 * Поведение:
 *
 * 1. Если имеются активные попадания, пытается продолжить их.
 * 2. Если два и более попадания образуют непрерывную линию,
 *    пытается продолжить линию с одного из концов.
 * 3. Иначе стреляет по соседним клеткам активных попаданий.
 * 4. Если активных попаданий нет, делегирует выбор [RandomBot].
 *
 * Попадания по уже потопленным кораблям исключаются из активных.
 * =============================================================================================
 */
class SmartBot(
    private val randomBot: RandomBot = RandomBot(),
    private val random: Random = Random.Default
) : MoveStrategy {

    /**
     * ---------------------------------------------------------------------------------------------
     * Выбирает следующий ход.
     *
     * @param board поле противника
     * @param history история ходов
     * @return координата следующего выстрела
     * ---------------------------------------------------------------------------------------------
     */
    override fun nextMove(
        board: Board,
        history: List<Move>
    ): Coordinate {

        val attacked = board.attackedCells()

        /* Оставляем только попадания по ещё не потопленным кораблям. */
        val activeHits = getActiveHits(board, history)

        if (activeHits.isNotEmpty()) {

            /* Пытаемся продолжить уже найденную линию корабля. */
            val lineCandidate = findLineContinuation(
                activeHits.toList(),
                attacked
            )

            if (lineCandidate != null) {
                return lineCandidate
            }

            /* Иначе пробуем клетки, соседние с активными попаданиями. */
            val neighbors = activeHits
                .flatMap { coord ->
                    listOfNotNull(
                        Coordinate.ofOrNull(
                            coord.row - 1,
                            coord.col
                        ),
                        Coordinate.ofOrNull(
                            coord.row + 1,
                            coord.col
                        ),
                        Coordinate.ofOrNull(
                            coord.row,
                            coord.col - 1
                        ),
                        Coordinate.ofOrNull(
                            coord.row,
                            coord.col + 1
                        )
                    )
                }
                .filter { it !in attacked }
                .distinct()

            if (neighbors.isNotEmpty()) {
                return neighbors.random(random)
            }
        }

        /* При отсутствии активных попаданий используем случайную стратегию. */
        return randomBot.nextMove(board, history)
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает попадания только по ещё не потопленным кораблям.
     *
     * После результата `SUNK` соответствующая связная группа попаданий
     * удаляется из набора активных целей.
     *
     * @param board поле противника
     * @param history история ходов
     * @return множество активных попаданий
     * ---------------------------------------------------------------------------------------------
     */
    private fun getActiveHits(
        board: Board,
        history: List<Move>
    ): Set<Coordinate> {

        val activeHits =
            board.hitCells().toMutableSet()

        /* Обрабатываем все ходы, которыми были потоплены корабли. */
        val sunkMoves = history.filter {
            it.result == ShotResult.SUNK
        }

        for (move in sunkMoves) {

            val sunkShipCells =
                findHitComponent(
                    move.coordinate,
                    activeHits
                )

            activeHits.removeAll(sunkShipCells)
        }

        return activeHits
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Находит связную группу клеток-попаданий,
     * к которой относится указанная клетка.
     *
     * Используется для определения клеток уже потопленного корабля.
     *
     * @param start начальная клетка
     * @param hits множество всех попаданий
     * @return связная группа попаданий
     * ---------------------------------------------------------------------------------------------
     */
    private fun findHitComponent(
        start: Coordinate,
        hits: Set<Coordinate>
    ): Set<Coordinate> {

        if (start !in hits) {
            return emptySet()
        }

        val result = mutableSetOf<Coordinate>()
        val queue = ArrayDeque<Coordinate>()

        queue.add(start)
        result.add(start)

        while (queue.isNotEmpty()) {

            val current = queue.removeFirst()

            val neighbors = listOfNotNull(
                Coordinate.ofOrNull(
                    current.row - 1,
                    current.col
                ),
                Coordinate.ofOrNull(
                    current.row + 1,
                    current.col
                ),
                Coordinate.ofOrNull(
                    current.row,
                    current.col - 1
                ),
                Coordinate.ofOrNull(
                    current.row,
                    current.col + 1
                )
            )

            for (neighbor in neighbors) {

                if (neighbor in hits &&
                    result.add(neighbor)
                ) {
                    queue.add(neighbor)
                }
            }
        }

        return result
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Ищет продолжение линии из двух и более последовательных попаданий.
     *
     * @param hitCoords координаты активных попаданий
     * @param attacked уже атакованные клетки
     * @return подходящая клетка или `null`
     * ---------------------------------------------------------------------------------------------
     */
    private fun findLineContinuation(
        hitCoords: List<Coordinate>,
        attacked: Set<Coordinate>
    ): Coordinate? {

        if (hitCoords.size < 2) {
            return null
        }

        /* Горизонтальные линии. */
        hitCoords
            .groupBy { it.row }
            .forEach { (row, coords) ->

                if (coords.size >= 2) {

                    val sorted =
                        coords.sortedBy { it.col }

                    val isContiguous =
                        (1 until sorted.size).all {
                            sorted[it].col -
                                    sorted[it - 1].col == 1
                        }

                    if (isContiguous) {

                        val candidates = listOfNotNull(
                            Coordinate.ofOrNull(
                                row,
                                sorted.first().col - 1
                            ),
                            Coordinate.ofOrNull(
                                row,
                                sorted.last().col + 1
                            )
                        ).filter {
                            it !in attacked
                        }

                        if (candidates.isNotEmpty()) {
                            return candidates.random(random)
                        }
                    }
                }
            }

        /* Вертикальные линии. */
        hitCoords
            .groupBy { it.col }
            .forEach { (col, coords) ->

                if (coords.size >= 2) {

                    val sorted =
                        coords.sortedBy { it.row }

                    val isContiguous =
                        (1 until sorted.size).all {
                            sorted[it].row -
                                    sorted[it - 1].row == 1
                        }

                    if (isContiguous) {

                        val candidates = listOfNotNull(
                            Coordinate.ofOrNull(
                                sorted.first().row - 1,
                                col
                            ),
                            Coordinate.ofOrNull(
                                sorted.last().row + 1,
                                col
                            )
                        ).filter {
                            it !in attacked
                        }

                        if (candidates.isNotEmpty()) {
                            return candidates.random(random)
                        }
                    }
                }
            }

        return null
    }
}
