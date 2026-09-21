package battleship.domain.service

import battleship.domain.model.Coordinate
import battleship.domain.model.Ship
import battleship.domain.model.ShipType
import battleship.domain.model.ValidationResult

/**
 * =============================================================================================
 * Валидатор расстановки кораблей.
 *
 * Проверяет, соответствует ли переданный набор кораблей
 * правилам классического «Морского боя».
 *
 * Проверки:
 *
 * 1. Состав флота соответствует требуемому.
 * 2. Корабли расположены строго горизонтально или вертикально.
 * 3. Сегменты корабля идут последовательно без пропусков.
 * 4. Корабли не пересекаются.
 * 5. Корабли не соприкасаются друг с другом, включая диагонали.
 *
 * @see ShipPlacementValidatorImpl
 * =============================================================================================
 */
interface ShipPlacementValidator {

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет корректность расстановки кораблей.
     *
     * @param ships список кораблей
     * @return [ValidationResult] - результат проверки
     * ---------------------------------------------------------------------------------------------
     */
    fun validate(ships: List<Ship>): ValidationResult
}

/**
 * =============================================================================================
 * Стандартная реализация валидатора расстановки.
 * =============================================================================================
 */
class ShipPlacementValidatorImpl : ShipPlacementValidator {

    /**
     * Ожидаемый состав классического флота.
     */
    private val expectedFleet = mapOf(
        ShipType.BATTLESHIP to 1,
        ShipType.CRUISER to 2,
        ShipType.DESTROYER to 3,
        ShipType.BOAT to 4
    )

    /**
     * ---------------------------------------------------------------------------------------------
     * Выполняет полную проверку расстановки.
     *
     * @param ships список размещаемых кораблей
     * @return успешный результат или список ошибок
     * ---------------------------------------------------------------------------------------------
     */
    override fun validate(ships: List<Ship>): ValidationResult {

        val errors = mutableListOf<String>()

        /* Проверка 1: состав флота. */
        val actualFleet = ships
            .groupingBy { it.type }
            .eachCount()

        for ((type, expectedCount) in expectedFleet) {

            val actualCount = actualFleet[type] ?: 0

            if (actualCount != expectedCount) {
                errors +=
                    "${type.displayName}: должно быть $expectedCount, получено $actualCount"
            }
        }

        /* Проверка 2-3: форма и непрерывность каждого корабля. */
        for ((index, ship) in ships.withIndex()) {

            val shipName =
                "${ship.type.displayName} #${index + 1}"

            if (ship.segments.toSet().size != ship.segments.size) {
                errors +=
                    "$shipName содержит повторяющиеся клетки"
            }

            if (!isStraight(ship.segments)) {
                errors +=
                    "$shipName должен быть прямым горизонтальным или вертикальным кораблём"
            }
        }

        /* Проверка 4-5: пересечения и соприкосновения между кораблями. */
        for (i in ships.indices) {
            for (j in i + 1 until ships.size) {

                val first = ships[i]
                val second = ships[j]

                if (first.overlapsWith(second)) {

                    errors +=
                        "${first.type.displayName} #${i + 1} " +
                                "пересекается с ${second.type.displayName} #${j + 1}"

                } else if (first.isAdjacentTo(second)) {

                    errors +=
                        "${first.type.displayName} #${i + 1} " +
                                "соприкасается с ${second.type.displayName} #${j + 1}"
                }
            }
        }

        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет, что корабль расположен горизонтально или вертикально
     * и что его сегменты идут подряд без пропусков.
     *
     * Однопалубный корабль всегда считается корректным.
     *
     * @param segments координаты сегментов корабля
     * @return `true`, если корабль расположен корректно
     * ---------------------------------------------------------------------------------------------
     */
    private fun isStraight(
        segments: List<Coordinate>
    ): Boolean {

        if (segments.size <= 1) {
            return true
        }

        val sameRow =
            segments.all { it.row == segments.first().row }

        val sameCol =
            segments.all { it.col == segments.first().col }

        if (!sameRow && !sameCol) {
            return false
        }

        val sorted = if (sameRow) {
            segments.sortedBy { it.col }
        } else {
            segments.sortedBy { it.row }
        }

        return sorted.zipWithNext().all { (a, b) ->

            if (sameRow) {
                b.col - a.col == 1
            } else {
                b.row - a.row == 1
            }
        }
    }
}
