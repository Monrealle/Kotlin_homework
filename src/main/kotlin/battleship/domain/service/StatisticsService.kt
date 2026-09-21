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
 * Определяет контракт для сбора и агрегации статистики
 * по всем завершённым партиям указанного игрока.
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
     * @return [PlayerStats] с количеством игр, побед, винрейтом и текущим рейтингом
     * ---------------------------------------------------------------------------------------------
     */
    fun getStats(player: Player): PlayerStats
}

/**
 * =============================================================================================
 * Реализация сервиса статистики.
 *
 * Алгоритм:
 *
 * 1. Получает все партии указанного игрока.
 * 2. Оставляет только завершённые партии.
 * 3. Подсчитывает количество побед.
 * 4. Вычисляет винрейт.
 * 5. Получает текущий рейтинг Эло.
 *
 * Зависимости:
 *
 * - [GameRepository] - для получения партий игрока.
 * - [EloRatingRepository] - для получения актуального рейтинга.
 *
 * Репозитории используются через интерфейсы Domain-слоя,
 * поэтому сервис не зависит от конкретного способа хранения данных.
 * =============================================================================================
 */
class StatisticsServiceImpl(
    private val gameRepository: GameRepository,
    private val eloRatingRepository: EloRatingRepository
) : StatisticsService {

    /**
     * ---------------------------------------------------------------------------------------------
     * Собирает и возвращает статистику указанного игрока.
     *
     * @param player игрок
     * @return агрегированная статистика игрока
     * ---------------------------------------------------------------------------------------------
     */
    override fun getStats(player: Player): PlayerStats {

        /* Получаем все партии указанного игрока и оставляем только завершённые. */
        val finishedGames = gameRepository
            .findByPlayer(player)
            .filter { it.status == GameStatus.FINISHED }

        /* Подсчитываем количество побед игрока. */
        val wins = finishedGames.count {
            it.winner == player
        }

        /* Вычисляем долю побед.
         * Для игрока без завершённых партий винрейт равен 0.0,
         * чтобы избежать деления на ноль.
         */
        val winRate =
            if (finishedGames.isEmpty()) {
                0.0
            } else {
                wins.toDouble() / finishedGames.size
            }

        /* Получаем актуальный рейтинг Эло из репозитория. */
        val currentElo =
            eloRatingRepository.findByPlayer(player).rating

        return PlayerStats(
            gamesPlayed = finishedGames.size,
            wins = wins,
            winRate = winRate,
            currentElo = currentElo
        )
    }
}
