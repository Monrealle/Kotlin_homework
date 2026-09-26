package battleship.infrastructure

import battleship.domain.model.Board
import battleship.domain.model.CellState
import battleship.domain.model.Coordinate
import battleship.domain.model.EloChange
import battleship.domain.model.Game
import battleship.domain.model.GameStatus
import battleship.domain.model.Move
import battleship.domain.model.Player
import battleship.domain.model.Ship
import battleship.domain.model.ShipType
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
 * - полную историю ходов;
 * - расстановки кораблей обеих сторон;
 * - состояние клеток обеих досок;
 * - изменения рейтинга Эло.
 *
 * Игровая информация хранится в связанных таблицах [games], [moves], [game_boards],
 * [ships], [ship_segments], [board_cells] и [elo_changes].
 * =============================================================================================
 */
class SqliteGameRepository : GameRepository {

    init {
        /* Гарантирует наличие базы данных и таблиц перед работой репозитория. */
        Database.init()
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Сохраняет партию и все связанные с ней данные.
     *
     * При повторном сохранении существующей партии её состояние обновляется,
     * а связанные ходы, доски и изменения рейтинга пересоздаются из текущего [Game].
     * Всё сохранение выполняется одной транзакцией.
     *
     * @param game партия для сохранения
     * ---------------------------------------------------------------------------------------------
     */
    override fun save(game: Game) {

        Database.connect().use { connection ->

            connection.autoCommit = false

            try {

                /**
                 * ---------------------------------------------------------------------------------------------
                 * Сохраняем основную информацию о партии.
                 * ---------------------------------------------------------------------------------------------
                 */
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

                /* Удаляем дочерние данные перед повторной записью текущего состояния. */
                connection.prepareStatement(
                    "DELETE FROM elo_changes WHERE game_id = ?"
                ).use { statement ->
                    statement.setString(1, game.id)
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    "DELETE FROM game_boards WHERE game_id = ?"
                ).use { statement ->
                    statement.setString(1, game.id)
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    "DELETE FROM moves WHERE game_id = ?"
                ).use { statement ->
                    statement.setString(1, game.id)
                    statement.executeUpdate()
                }

                /**
                 * ---------------------------------------------------------------------------------------------
                 * Сохраняем историю ходов партии.
                 * ---------------------------------------------------------------------------------------------
                 */
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

                /**
                 * ---------------------------------------------------------------------------------------------
                 * Сохраняем обе игровые доски.
                 * ---------------------------------------------------------------------------------------------
                 */
                saveBoard(
                    connection,
                    game.id,
                    1,
                    game.board1
                )

                saveBoard(
                    connection,
                    game.id,
                    2,
                    game.board2
                )

                /**
                 * ---------------------------------------------------------------------------------------------
                 * Сохраняем изменения рейтинга, если партия завершена.
                 * ---------------------------------------------------------------------------------------------
                 */
                game.eloChanges?.values?.forEach { change ->
                    connection.prepareStatement(
                        """
                        INSERT INTO elo_changes (
                            game_id,
                            player_id,
                            old_rating,
                            new_rating,
                            delta
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """.trimIndent()
                    ).use { statement ->
                        statement.setString(1, game.id)
                        statement.setString(2, change.player.id)
                        statement.setInt(3, change.oldRating)
                        statement.setInt(4, change.newRating)
                        statement.setInt(5, change.delta)
                        statement.executeUpdate()
                    }
                }

                connection.commit()

            } catch (exception: Exception) {

                /* При ошибке откатываем изменения партии и связанных данных. */
                connection.rollback()

                throw exception
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Сохраняет одну игровую доску и её состояние.
     * ---------------------------------------------------------------------------------------------
     */
    private fun saveBoard(
        connection: java.sql.Connection,
        gameId: String,
        boardNumber: Int,
        board: Board
    ) {

        connection.prepareStatement(
            """
            INSERT INTO game_boards (
                game_id,
                board_number,
                owner_id
            )
            VALUES (?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            statement.setString(1, gameId)
            statement.setInt(2, boardNumber)
            statement.setString(3, board.owner.id)
            statement.executeUpdate()
        }

        /**
         * ---------------------------------------------------------------------------------------------
         * Сохраняем корабли и их сегменты.
         * ---------------------------------------------------------------------------------------------
         */
        board.ships.forEachIndexed { index, ship ->

            val shipId = connection.prepareStatement(
                """
                INSERT INTO ships (
                    game_id,
                    board_number,
                    ship_number,
                    type
                )
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
                java.sql.Statement.RETURN_GENERATED_KEYS
            ).use { statement ->

                statement.setString(1, gameId)
                statement.setInt(2, boardNumber)
                statement.setInt(3, index + 1)
                statement.setString(4, ship.type.name)
                statement.executeUpdate()

                statement.generatedKeys.use { keys ->
                    if (!keys.next()) {
                        error("Не удалось получить идентификатор корабля")
                    }
                    keys.getLong(1)
                }
            }

            connection.prepareStatement(
                """
                INSERT INTO ship_segments (
                    ship_id,
                    row,
                    col
                )
                VALUES (?, ?, ?)
                """.trimIndent()
            ).use { statement ->
                ship.segments.forEach { coordinate ->
                    statement.setLong(1, shipId)
                    statement.setInt(2, coordinate.row)
                    statement.setInt(3, coordinate.col)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }

        /**
         * ---------------------------------------------------------------------------------------------
         * Сохраняем только занятые и атакованные клетки.
         * EMPTY-клетки восстанавливаются конструктором [Board].
         * ---------------------------------------------------------------------------------------------
         */
        connection.prepareStatement(
            """
            INSERT INTO board_cells (
                game_id,
                board_number,
                row,
                col,
                state
            )
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent()
        ).use { statement ->
            board.grid.forEach { (coordinate, state) ->
                if (state != CellState.EMPTY) {
                    statement.setString(1, gameId)
                    statement.setInt(2, boardNumber)
                    statement.setInt(3, coordinate.row)
                    statement.setInt(4, coordinate.col)
                    statement.setString(5, state.name)
                    statement.addBatch()
                }
            }
            statement.executeBatch()
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает все сохранённые партии.
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
                                    connection = connection,
                                    id = resultSet.getString("id"),
                                    player1Id = resultSet.getString("player1_id"),
                                    player2Id = resultSet.getString("player2_id"),
                                    winnerId = resultSet.getString("winner_id"),
                                    status = resultSet.getString("status")
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
                        connection = connection,
                        id = resultSet.getString("id"),
                        player1Id = resultSet.getString("player1_id"),
                        player2Id = resultSet.getString("player2_id"),
                        winnerId = resultSet.getString("winner_id"),
                        status = resultSet.getString("status")
                    )
                }
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает партии, в которых участвовал указанный игрок.
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
                                    connection = connection,
                                    id = resultSet.getString("id"),
                                    player1Id = resultSet.getString("player1_id"),
                                    player2Id = resultSet.getString("player2_id"),
                                    winnerId = resultSet.getString("winner_id"),
                                    status = resultSet.getString("status")
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
     * Восстанавливает объект [Game] из таблицы games и связанных таблиц.
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

        val player1 =
            findPlayer(connection, player1Id)
                ?: error("Игрок $player1Id не найден")

        val player2 =
            findPlayer(connection, player2Id)
                ?: error("Игрок $player2Id не найден")

        val game =
            Game(
                id = id,
                player1 = player1,
                player2 = player2
            )

        /* Восстанавливаем статус партии. */
        game.status = GameStatus.valueOf(status)

        /* Восстанавливаем победителя. */
        game.winner = winnerId?.let {
            findPlayer(connection, it)
        }

        /* Восстанавливаем обе доски вместе с кораблями и состоянием клеток. */
        loadBoard(
            connection,
            game,
            1,
            game.board1
        )

        loadBoard(
            connection,
            game,
            2,
            game.board2
        )

        /**
         * ---------------------------------------------------------------------------------------------
         * Восстанавливаем историю ходов.
         * ---------------------------------------------------------------------------------------------
         */
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
         * После промаха ход переходит к сопернику, после попадания / потопления
         * остаётся у того же игрока. Для партии без ходов остаётся первый игрок.
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

        /* Восстанавливаем изменения рейтинга по завершённой партии. */
        loadEloChanges(
            connection,
            game
        )

        return game
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Восстанавливает игровую доску вместе с кораблями и состояниями клеток.
     * ---------------------------------------------------------------------------------------------
     */
    private fun loadBoard(
        connection: java.sql.Connection,
        game: Game,
        boardNumber: Int,
        targetBoard: Board
    ) {

        val ships = mutableListOf<Ship>()

        connection.prepareStatement(
            """
            SELECT
                id,
                ship_number,
                type
            FROM ships
            WHERE game_id = ?
              AND board_number = ?
            ORDER BY ship_number
            """.trimIndent()
        ).use { statement ->

            statement.setString(1, game.id)
            statement.setInt(2, boardNumber)

            statement.executeQuery().use { resultSet ->

                while (resultSet.next()) {

                    val shipId = resultSet.getLong("id")
                    val type = ShipType.valueOf(
                        resultSet.getString("type")
                    )
                    val segments = mutableListOf<Coordinate>()

                    connection.prepareStatement(
                        """
                        SELECT row, col
                        FROM ship_segments
                        WHERE ship_id = ?
                        ORDER BY row, col
                        """.trimIndent()
                    ).use { segmentStatement ->

                        segmentStatement.setLong(1, shipId)

                        segmentStatement.executeQuery().use { segmentsResult ->
                            while (segmentsResult.next()) {
                                segments += Coordinate(
                                    row = segmentsResult.getInt("row"),
                                    col = segmentsResult.getInt("col")
                                )
                            }
                        }
                    }

                    ships += Ship(
                        type = type,
                        segments = segments
                    )
                }
            }
        }

        if (ships.isNotEmpty()) {
            targetBoard.placeShips(ships)
        }

        /**
         * ---------------------------------------------------------------------------------------------
         * Восстанавливаем состояния занятых и атакованных клеток.
         * ---------------------------------------------------------------------------------------------
         */
        connection.prepareStatement(
            """
            SELECT row, col, state
            FROM board_cells
            WHERE game_id = ?
              AND board_number = ?
            """.trimIndent()
        ).use { statement ->

            statement.setString(1, game.id)
            statement.setInt(2, boardNumber)

            statement.executeQuery().use { resultSet ->
                while (resultSet.next()) {
                    val coordinate = Coordinate(
                        row = resultSet.getInt("row"),
                        col = resultSet.getInt("col")
                    )

                    targetBoard.grid[coordinate] =
                        CellState.valueOf(
                            resultSet.getString("state")
                        )
                }
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Восстанавливает изменения рейтингов партии.
     * ---------------------------------------------------------------------------------------------
     */
    private fun loadEloChanges(
        connection: java.sql.Connection,
        game: Game
    ) {

        val changes = linkedMapOf<Player, EloChange>()

        connection.prepareStatement(
            """
            SELECT
                player_id,
                old_rating,
                new_rating,
                delta
            FROM elo_changes
            WHERE game_id = ?
            """.trimIndent()
        ).use { statement ->

            statement.setString(1, game.id)

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

                    changes[player] =
                        EloChange(
                            player = player,
                            oldRating = resultSet.getInt("old_rating"),
                            newRating = resultSet.getInt("new_rating"),
                            delta = resultSet.getInt("delta")
                        )
                }
            }
        }

        game.eloChanges =
            changes.takeIf { it.isNotEmpty() }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Загружает игрока по идентификатору.
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
