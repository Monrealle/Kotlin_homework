package battleship.domain.service

import battleship.domain.model.EloChange
import battleship.domain.model.Player

/**
 * =============================================================================================
 * Сервис расчёта рейтинга Эло (Domain-слой).
 *
 * Определяет контракт для пересчёта рейтингов двух игроков
 * после завершения партии.
 *
 * @see EloRatingServiceImpl
 * =============================================================================================
 */
interface EloRatingService {

    /**
     * ---------------------------------------------------------------------------------------------
     * Рассчитать изменения рейтинга для победителя и проигравшего.
     *
     * @param winner победитель партии
     * @param winnerCurrentRating текущий рейтинг победителя
     * @param loser проигравший
     * @param loserCurrentRating текущий рейтинг проигравшего
     * @return словарь [Player] -> [EloChange]: новый рейтинг и дельта для каждого
     * ---------------------------------------------------------------------------------------------
     */
    fun calculateRatings(
        winner: Player, winnerCurrentRating: Int,
        loser: Player, loserCurrentRating: Int
    ): Map<Player, EloChange>
}
