package battleship.domain.service

import battleship.domain.model.GameStatus
import battleship.domain.model.Player
import battleship.domain.model.PlayerStats
import battleship.domain.repository.EloRatingRepository
import battleship.domain.repository.GameRepository

/**
 * =============================================================================================
 * Сервис статистики игрока.
 *
 * Определяет контракт для сбора статистики по завершённым партиям
 * и получения текущего рейтинга игрока.
 *
 * @see StatisticsServiceImpl
 * =============================================================================================
 */
interface StatisticsService {

    /**
     * ---------------------------------------------------------------------------------------------
     * Собирает статистику указанного игрока.
     *
     * @param player игрок, для которого собирается статистика
     * @return [PlayerStats] с количеством партий, побед, винрейтом и рейтингом
     * ---------------------------------------------------------------------------------------------
     */
    fun getStats(player: Player): PlayerStats
}

/**
 * =============================================================================================
 * Реализация сервиса статистики.
 *
 * Учитываются только завершённые партии.
 * Винрейт вычисляется как доля побед от общего количества завершённых игр.
 * =============================================================================================
 */
class StatisticsServiceImpl(
    private val gameRepository: GameRepository,
    private val eloRatingRepository: EloRatingRepository
) : StatisticsService {

    /**
     * ---------------------------------------------------------------------------------------------
     * Собирает и возвращает статистику указанного игрока.
     * ---------------------------------------------------------------------------------------------
     */
    override fun getStats(player: Player): PlayerStats {
        /* Получаем завершённые партии указанного игрока. */
        val finishedGames = gameRepository.findByPlayer(player)
            .filter { it.status == GameStatus.FINISHED }

        /* Подсчитываем количество побед игрока. */
        val wins = finishedGames.count { it.winner == player }

        /* Для игрока без игр винрейт равен 0.0. */
        val winRate = if (finishedGames.isEmpty()) 0.0 else wins.toDouble() / finishedGames.size

        /* Получаем текущий рейтинг Эло. */
        val currentElo = eloRatingRepository.findByPlayer(player).rating

        return PlayerStats(
            gamesPlayed = finishedGames.size,
            wins = wins,
            winRate = winRate,
            currentElo = currentElo
        )
    }
}
