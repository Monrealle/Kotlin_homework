package battleship.infrastructure

import battleship.domain.model.EloRating
import battleship.domain.model.Player
import battleship.domain.repository.EloRatingRepository
import battleship.infrastructure.database.Database

/**
 * =============================================================================================
 * SQLite-репозиторий рейтингов Эло.
 *
 * Хранит текущий рейтинг каждого игрока в таблице [ratings].
 * =============================================================================================
 */
class SqliteEloRatingRepository : EloRatingRepository {

    init {
        /**
         * ---------------------------------------------------------------------------------------------
         * Гарантирует наличие базы данных и её таблиц перед первым обращением к репозиторию.
         * ---------------------------------------------------------------------------------------------
         */
        Database.init()
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Сохраняет текущий рейтинг игрока.
     *
     * @param rating рейтинг для сохранения
     * ---------------------------------------------------------------------------------------------
     */
    override fun save(rating: EloRating) {

        Database.connect().use { connection ->

            connection.prepareStatement(
                """
                INSERT INTO ratings (player_id, rating)
                VALUES (?, ?)
                ON CONFLICT(player_id) DO UPDATE SET rating = excluded.rating
                """.trimIndent()
            ).use { statement ->

                statement.setString(1, rating.player.id)
                statement.setInt(2, rating.rating)

                statement.executeUpdate()
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает текущий рейтинг игрока.
     *
     * Если рейтинг ещё не сохранён, используется начальное значение Elo.
     *
     * @param player игрок
     * @return текущий рейтинг игрока
     * ---------------------------------------------------------------------------------------------
     */
    override fun findByPlayer(player: Player): EloRating {

        Database.connect().use { connection ->

            connection.prepareStatement(
                "SELECT rating FROM ratings WHERE player_id = ?"
            ).use { statement ->

                statement.setString(1, player.id)

                statement.executeQuery().use { resultSet ->

                    return if (resultSet.next()) {
                        EloRating(
                            player = player,
                            rating = resultSet.getInt("rating")
                        )
                    } else {
                        EloRating(player)
                    }
                }
            }
        }
    }
}
