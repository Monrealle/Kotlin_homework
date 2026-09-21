package battleship.application

import battleship.domain.model.Coordinate
import battleship.domain.model.Ship
import battleship.domain.model.ShipType

/**
 * Утилита генерации случайной валидной расстановки кораблей.
 * Используется как ботом (GameSessionImpl), так и GUI (кнопка «Авторасстановка»).
 *
 * Алгоритм: случайное размещение с backtracking.
 * Запрещённая зона вокруг корабля гарантирует отсутствие смежных кораблей.
 */
object RandomShipPlacer {

    /** Порядок размещения: от большего к меньшему. */
    private val FLEET_ORDER = listOf(
        ShipType.BATTLESHIP to 1,
        ShipType.CRUISER     to 2,
        ShipType.DESTROYER   to 3,
        ShipType.BOAT        to 4
    )

    /**
     * Сгенерировать полный корректный набор кораблей.
     * @throws IllegalStateException если не удалось за 1000 попыток (не должно происходить).
     */
    fun generate(): List<Ship> {
        repeat(1_000) {
            val result = tryPlace()
            if (result != null) return result
        }
        error("RandomShipPlacer: не удалось сгенерировать расстановку за 1000 попыток")
    }

    private fun tryPlace(): List<Ship>? {
        val placed    = mutableListOf<Ship>()
        val forbidden = mutableSetOf<Coordinate>()   // клетки + буфер вокруг уже стоящих кораблей

        for ((type, count) in FLEET_ORDER) {
            repeat(count) {
                val ship = randomShipFor(type, forbidden) ?: return null
                placed.add(ship)
                for (seg in ship.segments) {
                    for (dr in -1..1) for (dc in -1..1) {
                        Coordinate.ofOrNull(seg.row + dr, seg.col + dc)?.let { forbidden.add(it) }
                    }
                }
            }
        }
        return placed
    }

    private fun randomShipFor(type: ShipType, forbidden: Set<Coordinate>): Ship? {
        repeat(200) {
            val horizontal = (0..1).random() == 0
            val row = (0..9).random()
            val col = (0..9).random()
            val segments = if (horizontal) {
                (0 until type.size).mapNotNull { Coordinate.ofOrNull(row, col + it) }
            } else {
                (0 until type.size).mapNotNull { Coordinate.ofOrNull(row + it, col) }
            }
            if (segments.size == type.size && segments.none { it in forbidden }) {
                return Ship(type, segments)
            }
        }
        return null
    }
}
