package battleship.domain.service

import battleship.domain.model.Ship
import battleship.domain.model.ShipType
import battleship.domain.model.ValidationResult

/**
 * =============================================================================================
 * Валидатор расстановки кораблей (Domain-слой).
 *
 * Определяет контракт проверки списка кораблей на соответствие
 * правилам «Морского боя».
 *
 * @see ShipPlacementValidatorImpl
 * =============================================================================================
 */
interface ShipPlacementValidator {

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверить корректность расстановки кораблей.
     *
     * @param ships список всех кораблей одного игрока
     * @return [ValidationResult] - успех или список ошибок
     * ---------------------------------------------------------------------------------------------
     */
    fun validate(ships: List<Ship>): ValidationResult
}

/**
 * =============================================================================================
 * Реализация валидатора по правилам классического «Морского боя».
 *
 * Правила:
 * - Состав флота: 1×Линкор(4), 2×Крейсер(3), 3×Эсминец(2), 4×Катер(1).
 * - Корабли должны быть прямыми (горизонтальными или вертикальными) и непрерывными.
 * - Корабли не перекрываются.
 * - Корабли не соприкасаются (включая диагонали).
 *
 * Алгоритм:
 * 1. Проверка состава флота - [validateFleetComposition].
 * 2. Проверка формы каждого корабля - [validateShipShape].
 * 3. Проверка на перекрытие - [validateNoOverlap].
 * 4. Проверка на смежность - [validateNoAdjacency].
 * =============================================================================================
 */
class ShipPlacementValidatorImpl : ShipPlacementValidator {

    companion object {
        /* Требуемый состав флота: тип → количество. */
        val REQUIRED_FLEET: Map<ShipType, Int> = mapOf(
            ShipType.BATTLESHIP to 1,                   /* 1 линкор (4 клетки)      */
            ShipType.CRUISER to 2,                      /* 2 крейсера (по 3 клетки) */
            ShipType.DESTROYER to 3,                    /* 3 эсминца (по 2 клетки)  */
            ShipType.BOAT to 4                          /* 4 катера (по 1 клетке)   */
        )
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет расстановку по всем правилам.
     *
     * @return [ValidationResult.success], если все проверки пройдены,
     *                                     иначе - результат со списком ошибок
     * ---------------------------------------------------------------------------------------------
     */
    override fun validate(ships: List<Ship>): ValidationResult {
        val errors = mutableListOf<String>()

        validateFleetComposition(ships, errors)
        ships.forEach { validateShipShape(it, errors) }
        validateNoOverlap(ships, errors)
        validateNoAdjacency(ships, errors)

        return if (errors.isEmpty()) ValidationResult.success()
        else ValidationResult.failure(errors)
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет, что состав флота соответствует [REQUIRED_FLEET].
     *
     * @param ships список кораблей для проверки
     * @param errors список для накопления ошибок
     * ---------------------------------------------------------------------------------------------
     */
    private fun validateFleetComposition(ships: List<Ship>, errors: MutableList<String>) {
        val counts = ships.groupingBy { it.type }.eachCount()
        for ((type, required) in REQUIRED_FLEET) {
            val actual = counts[type] ?: 0
            if (actual != required) {
                errors += "Требуется $required ${type.displayName}(а), расставлено: $actual"
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет, что корабль прямой и непрерывный.
     *
     * Одноклеточные корабли (BOAT) всегда корректны.
     *
     * @param ship корабль для проверки
     * @param errors список для накопления ошибок
     * ---------------------------------------------------------------------------------------------
     */
    private fun validateShipShape(ship: Ship, errors: MutableList<String>) {
        if (ship.segments.size == 1) return   /* BOAT всегда корректен */

        val rows = ship.segments.map { it.row }.distinct()
        val cols = ship.segments.map { it.col }.distinct()

        val isHorizontal = rows.size == 1
        val isVertical = cols.size == 1

        if (!isHorizontal && !isVertical) {
            errors += "${ship.type.displayName}: корабль должен быть прямым (горизонтально или вертикально)"
            return
        }

        if (isHorizontal) {
            val sorted = cols.sorted()
            for (i in 1 until sorted.size) {
                if (sorted[i] - sorted[i - 1] != 1) {
                    errors += "${ship.type.displayName}: сегменты должны быть непрерывными (горизонталь)"
                    break
                }
            }
        } else {
            val sorted = rows.sorted()
            for (i in 1 until sorted.size) {
                if (sorted[i] - sorted[i - 1] != 1) {
                    errors += "${ship.type.displayName}: сегменты должны быть непрерывными (вертикаль)"
                    break
                }
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет, что корабли не занимают одни и те же клетки.
     *
     * @param ships  список кораблей для проверки
     * @param errors список для накопления ошибок
     * ---------------------------------------------------------------------------------------------
     */
    private fun validateNoOverlap(ships: List<Ship>, errors: MutableList<String>) {
        for (i in ships.indices) {
            for (j in i + 1 until ships.size) {
                if (ships[i].overlapsWith(ships[j])) {
                    errors += "Корабли перекрываются: ${ships[i].type.displayName} и ${ships[j].type.displayName}"
                }
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Проверяет, что корабли не соприкасаются (включая диагонали).
     *
     * @param ships  список кораблей для проверки
     * @param errors список для накопления ошибок
     * ---------------------------------------------------------------------------------------------
     */
    private fun validateNoAdjacency(ships: List<Ship>, errors: MutableList<String>) {
        for (i in ships.indices) {
            for (j in i + 1 until ships.size) {
                if (!ships[i].overlapsWith(ships[j]) && ships[i].isAdjacentTo(ships[j])) {
                    errors += "Корабли стоят вплотную: ${ships[i].type.displayName} и ${ships[j].type.displayName}"
                }
            }
        }
    }
}
