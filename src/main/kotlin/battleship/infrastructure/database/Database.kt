package battleship.infrastructure.database

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * =============================================================================================
 * SQLite-база данных приложения «Морской бой».
 *
 * База используется для долговременного хранения:
 *
 * - игроков;
 * - рейтингов Эло;
 * - истории партий;
 * - ходов партий;
 * - расстановок кораблей;
 * - состояния клеток игровых досок;
 * - изменений рейтинга после завершения партии.
 *
 * Файл базы данных хранится в ~/.battleship/battleship.db.
 * =============================================================================================
 */
object Database {

    /**
     * ---------------------------------------------------------------------------------------------
     * Файл локальной базы данных.
     *
     * Значение можно временно заменить в тестах через [useFile],
     * чтобы не изменять реальную базу пользователя.
     * ---------------------------------------------------------------------------------------------
     */
    private var databaseFile =
        File(
            System.getProperty("user.home"),
            ".battleship/battleship.db"
        )

    /**
     * ---------------------------------------------------------------------------------------------
     * Переключает базу данных на другой файл.
     *
     * Используется в тестах, чтобы не трогать реальную базу пользователя.
     * Таблицы в новом файле создаются при следующем вызове [init].
     *
     * @param file файл базы данных
     * ---------------------------------------------------------------------------------------------
     */
    fun useFile(file: File) {
        databaseFile = file
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Инициализирует базу данных.
     *
     * Создаёт директорию и таблицы, если они ещё не существуют.
     * ---------------------------------------------------------------------------------------------
     */
    fun init() {

        databaseFile.parentFile.mkdirs()

        connect().use { connection ->

            /**
             * ---------------------------------------------------------------------------------------------
             * Включаем проверку внешних ключей SQLite.
             * ---------------------------------------------------------------------------------------------
             */
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
            }

            /**
             * ---------------------------------------------------------------------------------------------
             * Таблица игроков.
             * ---------------------------------------------------------------------------------------------
             */
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS players (
                        id   TEXT PRIMARY KEY,
                        name TEXT NOT NULL UNIQUE
                    )
                    """.trimIndent()
                )
            }

            /**
             * ---------------------------------------------------------------------------------------------
             * Таблица текущих рейтингов Эло.
             * ---------------------------------------------------------------------------------------------
             */
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS ratings (
                        player_id TEXT PRIMARY KEY,
                        rating    INTEGER NOT NULL DEFAULT 1000,

                        FOREIGN KEY (player_id)
                            REFERENCES players(id)
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }

            /**
             * ---------------------------------------------------------------------------------------------
             * Таблица истории игровых партий.
             * ---------------------------------------------------------------------------------------------
             */
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS games (
                        id         TEXT PRIMARY KEY,
                        player1_id TEXT NOT NULL,
                        player2_id TEXT NOT NULL,
                        winner_id  TEXT,
                        status     TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        FOREIGN KEY (player1_id)
                            REFERENCES players(id),

                        FOREIGN KEY (player2_id)
                            REFERENCES players(id),

                        FOREIGN KEY (winner_id)
                            REFERENCES players(id)
                    )
                    """.trimIndent()
                )
            }

            /**
             * ---------------------------------------------------------------------------------------------
             * Таблица истории ходов.
             * ---------------------------------------------------------------------------------------------
             */
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS moves (
                        id          INTEGER PRIMARY KEY AUTOINCREMENT,
                        game_id     TEXT NOT NULL,
                        turn_number INTEGER NOT NULL,
                        player_id   TEXT NOT NULL,
                        row         INTEGER NOT NULL,
                        col         INTEGER NOT NULL,
                        result      TEXT NOT NULL,

                        FOREIGN KEY (game_id)
                            REFERENCES games(id)
                            ON DELETE CASCADE,

                        FOREIGN KEY (player_id)
                            REFERENCES players(id)
                    )
                    """.trimIndent()
                )
            }

            /**
             * ---------------------------------------------------------------------------------------------
             * Таблица игровых досок.
             *
             * Для одной партии существует две записи: board_number = 1 и board_number = 2.
             * ---------------------------------------------------------------------------------------------
             */
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS game_boards (
                        game_id     TEXT NOT NULL,
                        board_number INTEGER NOT NULL,
                        owner_id    TEXT NOT NULL,

                        PRIMARY KEY (game_id, board_number),

                        FOREIGN KEY (game_id)
                            REFERENCES games(id)
                            ON DELETE CASCADE,

                        FOREIGN KEY (owner_id)
                            REFERENCES players(id)
                    )
                    """.trimIndent()
                )
            }

            /**
             * ---------------------------------------------------------------------------------------------
             * Таблица кораблей на игровых досках.
             * ---------------------------------------------------------------------------------------------
             */
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS ships (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        game_id      TEXT NOT NULL,
                        board_number INTEGER NOT NULL,
                        ship_number  INTEGER NOT NULL,
                        type         TEXT NOT NULL,

                        UNIQUE (game_id, board_number, ship_number),

                        FOREIGN KEY (game_id, board_number)
                            REFERENCES game_boards(game_id, board_number)
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }

            /**
             * ---------------------------------------------------------------------------------------------
             * Таблица сегментов кораблей.
             * ---------------------------------------------------------------------------------------------
             */
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS ship_segments (
                        ship_id INTEGER NOT NULL,
                        row     INTEGER NOT NULL,
                        col     INTEGER NOT NULL,

                        PRIMARY KEY (ship_id, row, col),

                        FOREIGN KEY (ship_id)
                            REFERENCES ships(id)
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }

            /**
             * ---------------------------------------------------------------------------------------------
             * Таблица состояния клеток досок.
             *
             * EMPTY-клетки в базу не записываются. При восстановлении они создаются
             * автоматически конструктором [battleship.domain.model.Board].
             * ---------------------------------------------------------------------------------------------
             */
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS board_cells (
                        game_id     TEXT NOT NULL,
                        board_number INTEGER NOT NULL,
                        row         INTEGER NOT NULL,
                        col         INTEGER NOT NULL,
                        state       TEXT NOT NULL,

                        PRIMARY KEY (game_id, board_number, row, col),

                        FOREIGN KEY (game_id, board_number)
                            REFERENCES game_boards(game_id, board_number)
                            ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }

            /**
             * ---------------------------------------------------------------------------------------------
             * Таблица изменений рейтингов по итогам партии.
             * ---------------------------------------------------------------------------------------------
             */
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS elo_changes (
                        game_id    TEXT NOT NULL,
                        player_id  TEXT NOT NULL,
                        old_rating INTEGER NOT NULL,
                        new_rating INTEGER NOT NULL,
                        delta      INTEGER NOT NULL,

                        PRIMARY KEY (game_id, player_id),

                        FOREIGN KEY (game_id)
                            REFERENCES games(id)
                            ON DELETE CASCADE,

                        FOREIGN KEY (player_id)
                            REFERENCES players(id)
                    )
                    """.trimIndent()
                )
            }

            /**
             * ---------------------------------------------------------------------------------------------
             * Индексы для ускорения поиска партий, ходов и данных досок.
             * ---------------------------------------------------------------------------------------------
             */
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE INDEX IF NOT EXISTS idx_games_player1
                    ON games(player1_id)
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE INDEX IF NOT EXISTS idx_games_player2
                    ON games(player2_id)
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE INDEX IF NOT EXISTS idx_moves_game
                    ON moves(game_id)
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE INDEX IF NOT EXISTS idx_ships_game_board
                    ON ships(game_id, board_number)
                    """.trimIndent()
                )

                statement.executeUpdate(
                    """
                    CREATE INDEX IF NOT EXISTS idx_board_cells_game_board
                    ON board_cells(game_id, board_number)
                    """.trimIndent()
                )
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Открывает соединение с локальной SQLite-базой данных.
     *
     * Внешние ключи дополнительно включаются для каждого нового соединения,
     * так как SQLite хранит эту настройку отдельно для каждого connection.
     *
     * @return открытое соединение с базой данных
     * ---------------------------------------------------------------------------------------------
     */
    fun connect(): Connection {

        return DriverManager.getConnection(
            "jdbc:sqlite:${databaseFile.absolutePath}"
        ).also { connection ->

            connection.createStatement().use { statement ->
                statement.execute("PRAGMA foreign_keys = ON")
            }
        }
    }
}
