package battleship.infrastructure

import battleship.domain.model.Coordinate
import battleship.domain.model.Game
import battleship.domain.model.GameStatus
import battleship.domain.model.Move
import battleship.domain.model.Player
import battleship.domain.model.ShotResult
import battleship.domain.repository.GameRepository
import battleship.infrastructure.database.Database

/**
 * =============================================================================================
 * SQLite-репозиторий игровых партий.
 *
 * Хранит:
 *
 * - участников партии;
 * - статус партии;
 * - победителя;
 * - полную историю ходов.
 *
 * Игровые партии хранятся в таблице [games],
 * а отдельные ходы - в таблице [moves].
 *
 * Доски и расстановка кораблей в базу не сохраняются,
 * так как для истории партий и статистики достаточно
 * информации о самой партии и её ходах.
 * =============================================================================================
 */
class SqliteGameRepository : GameRepository {

    init {

        /* Гарантирует наличие базы данных и таблиц перед работой репозитория. */

        Database.init()
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Сохраняет партию и её историю ходов.
     *
     * При повторном сохранении существующей партии её состояние обновляется.
     * История ходов для этой партии пересоздаётся из текущего [Game.moves].
     *
     * @param game партия для сохранения
     * ---------------------------------------------------------------------------------------------
     */
    override fun save(game: Game) {

        Database.connect().use { connection ->

            /* Одна транзакция используется для сохранения партии и всех связанных с ней ходов. */
            connection.autoCommit = false

            try {

                connection.prepareStatement(
                    """
                    INSERT INTO games (
                        id,
                        player1_id,
                        player2_id,
                        winner_id,
                        status
                    )
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        player1_id = excluded.player1_id,
                        player2_id = excluded.player2_id,
                        winner_id = excluded.winner_id,
                        status = excluded.status
                    """.trimIndent()
                ).use { statement ->

                    statement.setString(1, game.id)
                    statement.setString(2, game.player1.id)
                    statement.setString(3, game.player2.id)

                    if (game.winner == null) {
                        statement.setNull(4, java.sql.Types.VARCHAR)
                    } else {
                        statement.setString(4, game.winner!!.id)
                    }

                    statement.setString(5, game.status.name)
                    statement.executeUpdate()
                }

                /* Перед повторной записью партии удаляем старую историю ходов. */
                connection.prepareStatement(
                    "DELETE FROM moves WHERE game_id = ?"
                ).use { statement ->

                    statement.setString(1, game.id)
                    statement.executeUpdate()
                }

                /* Сохраняем текущую историю ходов партии. */
                connection.prepareStatement(
                    """
                    INSERT INTO moves (
                        game_id,
                        turn_number,
                        player_id,
                        row,
                        col,
                        result
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { statement ->

                    game.moves.forEach { move ->

                        statement.setString(1, game.id)
                        statement.setInt(2, move.turnNumber)
                        statement.setString(3, move.player.id)
                        statement.setInt(4, move.coordinate.row)
                        statement.setInt(5, move.coordinate.col)
                        statement.setString(6, move.result.name)

                        statement.addBatch()
                    }

                    statement.executeBatch()
                }

                connection.commit()

            } catch (exception: Exception) {

                /* При ошибке откатываем изменения партии и её ходов. */
                connection.rollback()

                throw exception
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает все сохранённые партии.
     *
     * @return список партий
     * ---------------------------------------------------------------------------------------------
     */
    override fun findAll(): List<Game> {

        Database.connect().use { connection ->

            connection.prepareStatement(
                """
                SELECT
                    id,
                    player1_id,
                    player2_id,
                    winner_id,
                    status
                FROM games
                ORDER BY created_at, rowid
                """.trimIndent()
            ).use { statement ->

                statement.executeQuery().use { resultSet ->

                    return buildList {

                        while (resultSet.next()) {

                            add(
                                loadGame(
                                    connection,
                                    resultSet.getString("id"),
                                    resultSet.getString("player1_id"),
                                    resultSet.getString("player2_id"),
                                    resultSet.getString("winner_id"),
                                    resultSet.getString("status")
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
     * Ищет партию по идентификатору.
     *
     * @param id идентификатор партии
     * @return найденная партия или null
     * ---------------------------------------------------------------------------------------------
     */
    override fun findById(id: String): Game? {

        Database.connect().use { connection ->

            connection.prepareStatement(
                """
                SELECT
                    id,
                    player1_id,
                    player2_id,
                    winner_id,
                    status
                FROM games
                WHERE id = ?
                """.trimIndent()
            ).use { statement ->

                statement.setString(1, id)

                statement.executeQuery().use { resultSet ->

                    if (!resultSet.next()) {
                        return null
                    }

                    return loadGame(
                        connection,
                        resultSet.getString("id"),
                        resultSet.getString("player1_id"),
                        resultSet.getString("player2_id"),
                        resultSet.getString("winner_id"),
                        resultSet.getString("status")
                    )
                }
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает партии, в которых участвовал указанный игрок.
     *
     * @param player игрок
     * @return список его партий
     * ---------------------------------------------------------------------------------------------
     */
    override fun findByPlayer(player: Player): List<Game> {

        Database.connect().use { connection ->

            connection.prepareStatement(
                """
                SELECT
                    id,
                    player1_id,
                    player2_id,
                    winner_id,
                    status
                FROM games
                WHERE player1_id = ?
                   OR player2_id = ?
                ORDER BY created_at, rowid
                """.trimIndent()
            ).use { statement ->

                statement.setString(1, player.id)
                statement.setString(2, player.id)

                statement.executeQuery().use { resultSet ->

                    return buildList {

                        while (resultSet.next()) {

                            add(
                                loadGame(
                                    connection,
                                    resultSet.getString("id"),
                                    resultSet.getString("player1_id"),
                                    resultSet.getString("player2_id"),
                                    resultSet.getString("winner_id"),
                                    resultSet.getString("status")
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
     * Восстанавливает объект [Game] из строки таблицы games
     * и загружает связанные с ним ходы.
     *
     * @param connection соединение с базой данных
     * @param id идентификатор партии
     * @param player1Id идентификатор первого игрока
     * @param player2Id идентификатор второго игрока
     * @param winnerId идентификатор победителя
     * @param status статус партии
     * @return восстановленная партия
     * ---------------------------------------------------------------------------------------------
     */
    private fun loadGame(
        connection: java.sql.Connection,
        id: String,
        player1Id: String,
        player2Id: String,
        winnerId: String?,
        status: String
    ): Game {

        val player1 = findPlayer(connection, player1Id) ?: error("Игрок $player1Id не найден")
        val player2 = findPlayer(connection, player2Id) ?: error("Игрок $player2Id не найден")

        val game = Game(
                id = id,
                player1 = player1,
                player2 = player2
            )

        /* Восстанавливаем статус партии. */
        game.status =
            GameStatus.valueOf(status)

        /* Восстанавливаем победителя. */
        game.winner =
            winnerId?.let {
                findPlayer(connection, it)
            }

        /* Восстанавливаем историю ходов. */
        connection.prepareStatement(
            """
            SELECT
                turn_number,
                player_id,
                row,
                col,
                result
            FROM moves
            WHERE game_id = ?
            ORDER BY turn_number
            """.trimIndent()
        ).use { statement ->

            statement.setString(1, id)

            statement.executeQuery().use { resultSet ->

                while (resultSet.next()) {

                    val player =
                        findPlayer(
                            connection,
                            resultSet.getString("player_id")
                        )
                            ?: error(
                                "Игрок ${resultSet.getString("player_id")} не найден"
                            )

                    game.moves.add(
                        Move(
                            turnNumber = resultSet.getInt("turn_number"),
                            player = player,
                            coordinate = Coordinate(
                                row = resultSet.getInt("row"),
                                col = resultSet.getInt("col")
                            ),
                            result = ShotResult.valueOf(
                                resultSet.getString("result")
                            )
                        )
                    )
                }
            }
        }

        /**
         * ---------------------------------------------------------------------------------------------
         * Восстанавливаем игрока, которому принадлежит следующий ход.
         *
         * Правило совпадает с игровой логикой: после промаха ход переходит
         * к сопернику, после попадания / потопления / победы остаётся у того же игрока.
         * Для партии без ходов остаётся первый игрок.
         * ---------------------------------------------------------------------------------------------
         */
        if (game.moves.isNotEmpty()) {

            val lastMove =
                game.moves.last()

            game.currentTurn =
                when {
                    lastMove.result != ShotResult.MISS -> lastMove.player
                    lastMove.player == game.player1 -> game.player2
                    else -> game.player1
                }
        }

        return game
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Загружает игрока по идентификатору.
     *
     * @param connection соединение с базой данных
     * @param id идентификатор игрока
     * @return игрок или null
     * ---------------------------------------------------------------------------------------------
     */
    private fun findPlayer(
        connection: java.sql.Connection,
        id: String
    ): Player? {

        connection.prepareStatement(
            "SELECT id, name FROM players WHERE id = ?"
        ).use { statement ->

            statement.setString(1, id)

            statement.executeQuery().use { resultSet ->

                if (!resultSet.next()) {
                    return null
                }

                return Player(
                    id = resultSet.getString("id"),
                    name = resultSet.getString("name")
                )
            }
        }
    }
}
