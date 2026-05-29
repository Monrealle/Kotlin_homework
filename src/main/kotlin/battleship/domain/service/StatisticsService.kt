package battleship.domain.service

import battleship.domain.model.*
import battleship.infrastructure.EloRatingRepository
import battleship.infrastructure.GameRepository
import battleship.infrastructure.PlayerRepository

/**
 * =============================================================================================
 * Сервис статистики игрока.
 *
 * Определяет контракт для сбора и агрегации статистики по всем завершённым партиям игрока.
 *
 * @see StatisticsServiceImpl
 * =============================================================================================
 */
interface StatisticsService {

    /**
     * ---------------------------------------------------------------------------------------------
     * Собрать статистику по указанному игроку.
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
 * 1. Находит все партии игрока через [GameRepository].
 * 2. Фильтрует только завершённые (`FINISHED`).
 * 3. Считает победы и винрейт.
 * 4. Запрашивает текущий рейтинг через [EloRatingRepository].
 *
 * Зависимости:
 * - [GameRepository] - для поиска партий.
 * - [PlayerRepository] - для проверок существования игрока.
 * - [EloRatingRepository] - для получения актуального рейтинга.
 *
 * @param gameRepository репозиторий игр
 * @param playerRepository репозиторий игроков (пока не используется, оставлен для расширения)
 * @param eloRatingRepository репозиторий рейтингов
 * =============================================================================================
 */
class StatisticsServiceImpl(
    private val gameRepository: GameRepository,
    @Suppress("unused") private val playerRepository: PlayerRepository,
    private val eloRatingRepository: EloRatingRepository
) : StatisticsService {

    /**
     * ---------------------------------------------------------------------------------------------
     * Собирает и возвращает статистику игрока.
     *
     * @param player игрок, для которого собирается статистика
     * @return [PlayerStats] с агрегированными показателями
     * ---------------------------------------------------------------------------------------------
     */
    override fun getStats(player: Player): PlayerStats {
        val allGames = gameRepository.findByPlayer(player)
        val finished = allGames.filter { it.status == GameStatus.FINISHED }
        val wins = finished.count { it.winner == player }
        val winRate = if (finished.isEmpty()) 0.0 else wins.toDouble() / finished.size
        val currentElo = eloRatingRepository.findByPlayer(player).rating

        return PlayerStats(
            gamesPlayed = finished.size, /* Сколько завершённых партий сыграно */
            wins = wins,                 /* Сколько из них выиграно            */
            winRate = winRate,           /* Доля побед (от 0.0 до 1.0)         */
            currentElo = currentElo      /* Текущий рейтинг Эло                */
        )
    }
}
