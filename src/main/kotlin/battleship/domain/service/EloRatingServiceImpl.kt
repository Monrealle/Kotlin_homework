package battleship.domain.service

import battleship.domain.model.EloChange
import battleship.domain.model.Player
import kotlin.random.Random

/**
 * =============================================================================================
 * Упрощённая реализация расчёта рейтинга Эло.
 *
 * Правила:
 * - Дельта рейтинга выбирается случайно в диапазоне [25..33].
 * - Победитель получает +delta, проигравший теряет -delta.
 * - Рейтинг не опускается ниже 0.
 *
 * Зависимости:
 * Не зависит от реального алгоритма Эло с ожидаемым счётом - используется
 * фиксированная дельта для простоты.
 *
 * @param random генератор случайных чисел (по умолчанию [Random.Default])
 * =============================================================================================
 */
class EloRatingServiceImpl(
    private val random: Random = Random.Default
) : EloRatingService {

    /**
     * ---------------------------------------------------------------------------------------------
     * Генерирует случайную дельту рейтинга в диапазоне [25..33].
     *
     * `nextInt(25, 34)` даёт значения от 25 до 33 включительно
     * (верхняя граница exclusive).
     * ---------------------------------------------------------------------------------------------
     */
    private fun randomDelta(): Int = random.nextInt(25, 34)

    /**
     * ---------------------------------------------------------------------------------------------
     * Рассчитывает изменения рейтинга для победителя и проигравшего.
     *
     * @param winner победитель партии
     * @param winnerCurrentRating текущий рейтинг победителя
     * @param loser проигравший
     * @param loserCurrentRating текущий рейтинг проигравшего
     * @return словарь [Player] -> [EloChange] с новыми рейтингами и дельтами
     * ---------------------------------------------------------------------------------------------
     */
    override fun calculateRatings(
        winner: Player, winnerCurrentRating: Int,
        loser: Player, loserCurrentRating: Int
    ): Map<Player, EloChange> {
        val delta = randomDelta()
        val newWinnerRating = winnerCurrentRating + delta
        val newLoserRating  = maxOf(0, loserCurrentRating - delta)

        return mapOf(
            winner to EloChange(
                player = winner,                 /* Победитель                            */
                oldRating = winnerCurrentRating, /* Его рейтинг до игры                   */
                newRating = newWinnerRating,     /* Его рейтинг после игры (+delta)       */
                delta = +delta                   /* На сколько увеличился (положительное) */
            ),
            loser to EloChange(
                player = loser,                 /* Проигравший                                   */
                oldRating = loserCurrentRating, /* Его рейтинг до игры                           */
                newRating = newLoserRating,     /* Его рейтинг после игры (-delta, но не ниже 0) */
                delta = -delta                  /* На сколько уменьшился (отрицательное)         */
            )
        )
    }
}
