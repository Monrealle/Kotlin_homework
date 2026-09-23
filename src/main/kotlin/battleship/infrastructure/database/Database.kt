package battleship.infrastructure.database

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * =============================================================================================
 * SQLite-база данных приложения администратора «Морской бой».
 *
 * База используется для долговременного хранения:
 *
 * - игроков;
 * - рейтингов Эло;
 * - истории партий;
 * - ходов партий.
 *
 * Файл базы данных хранится в ~/.battleship/battleship.db.
 *
 * Структура базы:
 *
 * players -> информация об игроках;
 * ratings -> текущие рейтинги Эло;
 * games -> история партий;
 * moves -> история ходов.
 * =============================================================================================
 */
object Database {

    /**
     * ---------------------------------------------------------------------------------------------
     * Файл локальной базы данных.
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
     * Таблицы в новом файле создаются при следующем вызове [init]
     * (репозитории вызывают его в своих конструкторах).
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
             * Индексы для ускорения поиска партий и ходов.
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
