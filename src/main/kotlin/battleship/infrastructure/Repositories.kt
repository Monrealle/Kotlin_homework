package battleship.infrastructure

import battleship.domain.model.*

// ─── Интерфейсы ──────────────────────────────────────────────────────────────

interface GameRepository {
    fun save(game: Game)
    fun findAll(): List<Game>
    fun findById(id: String): Game?
    fun findByPlayer(player: Player): List<Game>
}

interface PlayerRepository {
    fun save(player: Player)
    fun findAll(): List<Player>
    fun findById(id: String): Player?
    fun findByName(name: String): Player?
}

/**
 * Репозиторий рейтингов Эло.
 * Не описан в оригинальной архитектуре явно, добавлен для хранения
 * актуальных рейтингов без пересчёта истории. (см. README — архитектурное решение)
 */
interface EloRatingRepository {
    fun save(rating: EloRating)
    fun findByPlayer(player: Player): EloRating
}

// ─── In-memory реализации ─────────────────────────────────────────────────────

class InMemoryGameRepository : GameRepository {
    private val store = mutableMapOf<String, Game>()

    override fun save(game: Game)          { store[game.id] = game }
    override fun findAll(): List<Game>     = store.values.toList()
    override fun findById(id: String): Game? = store[id]
    override fun findByPlayer(player: Player): List<Game> =
        store.values.filter { it.player1 == player || it.player2 == player }
}

class InMemoryPlayerRepository : PlayerRepository {
    private val store = mutableMapOf<String, Player>()

    override fun save(player: Player)          { store[player.id] = player }
    override fun findAll(): List<Player>       = store.values.toList()
    override fun findById(id: String): Player? = store[id]
    override fun findByName(name: String): Player? =
        store.values.find { it.name.equals(name, ignoreCase = true) }
}

class InMemoryEloRatingRepository : EloRatingRepository {
    private val store = mutableMapOf<String, EloRating>()

    override fun save(rating: EloRating)           { store[rating.player.id] = rating }
    override fun findByPlayer(player: Player): EloRating =
        store[player.id] ?: EloRating(player)  // По умолчанию — 1000
}
