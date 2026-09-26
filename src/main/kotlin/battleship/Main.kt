package battleship

import battleship.domain.service.EloRatingServiceImpl
import battleship.domain.service.ShipPlacementValidatorImpl
import battleship.domain.service.StatisticsServiceImpl
import battleship.domain.service.TurnValidatorImpl
import battleship.infrastructure.SqliteEloRatingRepository
import battleship.infrastructure.SqliteGameRepository
import battleship.infrastructure.SqlitePlayerRepository
import battleship.infrastructure.database.Database
import battleship.presentation.console.ConsoleApplication
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * =============================================================================================
 * Точка входа консольной версии приложения «Морской бой».
 *
 * Отвечает за сборку зависимостей консольного приложения:
 *
 * 1. Инициализирует локальную SQLite-базу данных.
 * 2. Создаёт SQLite-репозитории.
 * 3. Создаёт Domain-сервисы.
 * 4. Передаёт зависимости в консольный интерфейс.
 * 5. Запускает главный цикл приложения.
 * =============================================================================================
 */
fun main() {

    /**
     * ---------------------------------------------------------------------------------------------
     * Инициализация локальной SQLite-базы данных.
     *
     * При запуске создаётся файл базы данных и необходимые таблицы,
     * если они ещё не существуют.
     * ---------------------------------------------------------------------------------------------
     */
    Database.init()

    /**
     * ---------------------------------------------------------------------------------------------
     * SQLite-репозитории.
     *
     * Все данные консольной версии сохраняются в локальной базе данных.
     * ---------------------------------------------------------------------------------------------
     */
    val playerRepository = SqlitePlayerRepository()
    val gameRepository = SqliteGameRepository()
    val eloRatingRepository = SqliteEloRatingRepository()

    /**
     * ---------------------------------------------------------------------------------------------
     * Domain services.
     * ---------------------------------------------------------------------------------------------
     */
    val placementValidator = ShipPlacementValidatorImpl()
    val turnValidator = TurnValidatorImpl()
    val eloService = EloRatingServiceImpl()
    val statisticsService = StatisticsServiceImpl(
        gameRepository = gameRepository,
        eloRatingRepository = eloRatingRepository
    )

    /**
     * ---------------------------------------------------------------------------------------------
     * Запуск консольного приложения администратора.
     * ---------------------------------------------------------------------------------------------
     */
    ConsoleApplication(
        input = BufferedReader(
            InputStreamReader(System.`in`, Charsets.UTF_8)
        ),
        output = OutputStreamWriter(
            System.out,
            Charsets.UTF_8
        ),
        playerRepository = playerRepository,
        gameRepository = gameRepository,
        eloRatingRepository = eloRatingRepository,
        placementValidator = placementValidator,
        turnValidator = turnValidator,
        eloService = eloService,
        statisticsService = statisticsService
    ).run()
}
