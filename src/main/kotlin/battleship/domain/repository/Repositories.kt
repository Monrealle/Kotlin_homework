package battleship.domain.repository

import battleship.domain.model.EloRating
import battleship.domain.model.Game
import battleship.domain.model.Player

/**
 * =============================================================================================
 * Контракты репозиториев Domain-слоя.
 *
 * Интерфейсы описывают операции с игроками, партиями и рейтингами,
 * но не зависят от конкретного способа хранения данных.
 *
 * Используются in-memory реализации.
 * =============================================================================================
 */

/**
 * ---------------------------------------------------------------------------------------------
 * Репозиторий игровых партий.
 * ---------------------------------------------------------------------------------------------
 */
interface GameRepository {
    fun save(game: Game)
    fun findAll(): List<Game>
    fun findById(id: String): Game?
    fun findByPlayer(player: Player): List<Game>
}

/**
 * ---------------------------------------------------------------------------------------------
 * Репозиторий игроков.
 * ---------------------------------------------------------------------------------------------
 */
interface PlayerRepository {
    fun save(player: Player)
    fun findAll(): List<Player>
    fun findById(id: String): Player?
    fun findByName(name: String): Player?
}

/**
 * ---------------------------------------------------------------------------------------------
 * Репозиторий рейтингов Эло.
 * ---------------------------------------------------------------------------------------------
 */
interface EloRatingRepository {
    fun save(rating: EloRating)
    fun findByPlayer(player: Player): EloRating
}
