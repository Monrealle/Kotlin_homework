package battleship.presentation.console

import battleship.domain.model.Coordinate
import battleship.domain.model.Ship
import battleship.domain.model.ShipType

/**
 * =============================================================================================
 * Парсер ручной расстановки кораблей.
 *
 * Разбирает строковое представление, которое вводит администратор,
 * и преобразует его в список объектов [Ship].
 *
 * Поддерживаемый формат:
 * `A1-A4; C1-C3; F1-F3; A6-A7; ...; J2`.
 * =============================================================================================
 */
object PlacementParser {

    /**
     * ---------------------------------------------------------------------------------------------
     * Разбирает всю расстановку и возвращает результат с кораблями либо ошибкой.
     * ---------------------------------------------------------------------------------------------
     */
    fun parse(input: String): Result<List<Ship>> = runCatching {
        val entries = input.split(';').map(String::trim).filter(String::isNotEmpty)
        require(entries.size == 10) {
            "Нужно указать ровно 10 кораблей, разделённых точкой с запятой (получено ${entries.size})"
        }

        entries.mapIndexed { index, entry -> parseShip(entry, index + 1) }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Разбирает описание одного корабля.
     *
     * Возможны форматы `A1` для катера и `A1-A4` для кораблей большей длины.
     * ---------------------------------------------------------------------------------------------
     */
    private fun parseShip(entry: String, number: Int): Ship {
        val parts = entry.split('-').map(String::trim)
        require(parts.size in 1..2) {
            "Корабль №$number: используйте формат A1 или A1-A4"
        }

        val start = Coordinate.fromString(parts[0])
            ?: error("Корабль №$number: неверная координата '${parts[0]}'")

        val segments = if (parts.size == 1) {
            listOf(start)
        } else {
            val end = Coordinate.fromString(parts[1])
                ?: error("Корабль №$number: неверная координата '${parts[1]}'")
            buildSegments(start, end, number)
        }

        val type = ShipType.entries.firstOrNull { it.size == segments.size }
            ?: error("Корабль №$number: длина ${segments.size} не поддерживается")
        return Ship(type, segments)
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Строит непрерывный список сегментов между двумя координатами.
     *
     * Разрешены только горизонтальные и вертикальные корабли.
     * ---------------------------------------------------------------------------------------------
     */
    private fun buildSegments(start: Coordinate, end: Coordinate, number: Int): List<Coordinate> {
        require(start.row == end.row || start.col == end.col) {
            "Корабль №$number: корабль должен быть горизонтальным или вертикальным"
        }

        return if (start.row == end.row) {
            val from = minOf(start.col, end.col)
            val to = maxOf(start.col, end.col)
            (from..to).map { Coordinate(start.row, it) }
        } else {
            val from = minOf(start.row, end.row)
            val to = maxOf(start.row, end.row)
            (from..to).map { Coordinate(it, start.col) }
        }
    }
}
