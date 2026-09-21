package battleship.domain.service

import battleship.domain.model.EloChange
import battleship.domain.model.Player
import kotlin.random.Random

/**
 * =============================================================================================
 * Упрощённая реализация расчёта рейтинга Эло.
 *
 * Правила:
 *
 * 1. Дельта рейтинга случайно выбирается в диапазоне [25..33].
 * 2. Победитель получает положительную дельту.
 * 3. Проигравший теряет такую же величину, но не более своего текущего рейтинга.
 * 4. Рейтинг игрока не может стать отрицательным.
 *
 * Зависимости:
 *
 * - [Random] используется для генерации дельты.
 *
 * Реализация намеренно упрощена и не использует
 * классическую формулу Эло с ожидаемой вероятностью победы.
 * =============================================================================================
 */
class EloRatingServiceImpl(
    private val random: Random = Random.Default
) : EloRatingService {

    /**
     * ---------------------------------------------------------------------------------------------
     * Генерирует случайную дельту рейтинга.
     *
     * `nextInt(25, 34)` возвращает значения от 25 до 33 включительно,
     * так как верхняя граница является исключающей.
     *
     * @return случайная дельта рейтинга
     * ---------------------------------------------------------------------------------------------
     */
    private fun randomDelta(): Int =
        random.nextInt(25, 34)

    /**
     * ---------------------------------------------------------------------------------------------
     * Рассчитывает изменения рейтинга двух игроков.
     *
     * @param winner победитель партии
     * @param winnerCurrentRating текущий рейтинг победителя
     * @param loser проигравший
     * @param loserCurrentRating текущий рейтинг проигравшего
     * @return отображение `Player -> EloChange`
     * ---------------------------------------------------------------------------------------------
     */
    override fun calculateRatings(
        winner: Player,
        winnerCurrentRating: Int,
        loser: Player,
        loserCurrentRating: Int
    ): Map<Player, EloChange> {

        val delta = randomDelta()

        /* Проигравший не может потерять больше рейтинга, чем у него есть. */
        val actualLoss = minOf(
            delta,
            loserCurrentRating
        )

        val newWinnerRating =
            winnerCurrentRating + delta

        val newLoserRating =
            loserCurrentRating - actualLoss

        return mapOf(

            winner to EloChange(
                player = winner,
                oldRating = winnerCurrentRating,
                newRating = newWinnerRating,
                delta = delta
            ),

            loser to EloChange(
                player = loser,
                oldRating = loserCurrentRating,
                newRating = newLoserRating,
                delta = -actualLoss
            )
        )
    }
}
