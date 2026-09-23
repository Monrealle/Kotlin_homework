package battleship.infrastructure

import battleship.domain.model.Player
import battleship.domain.repository.PlayerRepository
import battleship.infrastructure.database.Database

/**
 * =============================================================================================
 * SQLite-репозиторий игроков.
 *
 * Хранит игроков в таблице [players] локальной SQLite-базы данных.
 * =============================================================================================
 */
class SqlitePlayerRepository : PlayerRepository {

    init {
        /* Гарантирует наличие базы данных и её таблиц перед первым обращением к репозиторию. */
        Database.init()
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Сохраняет игрока в базу данных.
     *
     * При существующем идентификаторе данные игрока обновляются.
     *
     * @param player игрок для сохранения
     * ---------------------------------------------------------------------------------------------
     */
    override fun save(player: Player) {

        Database.connect().use { connection ->

            connection.prepareStatement(
                """
                INSERT INTO players (id, name)
                VALUES (?, ?)
                ON CONFLICT(id) DO UPDATE SET name = excluded.name
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, player.id)
                statement.setString(2, player.name)
                statement.executeUpdate()
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает всех игроков.
     *
     * @return список игроков
     * ---------------------------------------------------------------------------------------------
     */
    override fun findAll(): List<Player> {

        Database.connect().use { connection ->

            connection.prepareStatement(
                "SELECT id, name FROM players ORDER BY name"
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    return buildList {
                        while (resultSet.next()) {
                            add(
                                Player(
                                    id = resultSet.getString("id"),
                                    name = resultSet.getString("name")
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Ищет игрока по идентификатору.
     *
     * @param id идентификатор игрока
     * @return найденный игрок или null
     * ---------------------------------------------------------------------------------------------
     */
    override fun findById(id: String): Player? {

        Database.connect().use { connection ->
            connection.prepareStatement(
                "SELECT id, name FROM players WHERE id = ?"
            ).use { statement ->
                statement.setString(1, id)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        Player(
                            id = resultSet.getString("id"),
                            name = resultSet.getString("name")
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Ищет игрока по имени без учёта регистра.
     *
     * @param name имя игрока
     * @return найденный игрок или null
     * ---------------------------------------------------------------------------------------------
     */
    override fun findByName(name: String): Player? {
        Database.connect().use { connection ->
            connection.prepareStatement(
                "SELECT id, name FROM players WHERE lower(name) = lower(?)"
            ).use { statement ->
                statement.setString(1, name)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) {
                        Player(
                            id = resultSet.getString("id"),
                            name = resultSet.getString("name")
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }
}
