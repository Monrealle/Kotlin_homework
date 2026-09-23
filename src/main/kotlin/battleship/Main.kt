package battleship

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import battleship.domain.service.*
import battleship.infrastructure.*
import battleship.infrastructure.database.Database
import battleship.presentation.gui.AdminApp
import battleship.presentation.gui.GuiController

/**
 * =============================================================================================
 * Точка входа приложения.
 *
 * Отвечает за сборку зависимостей приложения:
 *
 * 1. Инициализирует локальную SQLite-базу данных.
 * 2. Создаёт SQLite-репозитории.
 * 3. Создаёт Domain-сервисы.
 * 4. Создаёт GUI-контроллер.
 * 5. Запускает Compose Desktop приложение.
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
     * Все основные данные приложения теперь сохраняются
     * в локальной SQLite-базе данных.
     * ---------------------------------------------------------------------------------------------
     */
    val playerRepo = SqlitePlayerRepository()
    val gameRepo = SqliteGameRepository()
    val eloRepo = SqliteEloRatingRepository()

    /**
     * ---------------------------------------------------------------------------------------------
     * Domain services.
     * ---------------------------------------------------------------------------------------------
     */
    val placementValidator = ShipPlacementValidatorImpl()
    val turnValidator = TurnValidatorImpl()
    val eloService = EloRatingServiceImpl()
    val statisticsService = StatisticsServiceImpl(
            gameRepository = gameRepo,
            eloRatingRepository = eloRepo
            )

    /**
     * ---------------------------------------------------------------------------------------------
     * GUI controller.
     * ---------------------------------------------------------------------------------------------
     */
    val ctrl =
        GuiController(
            playerRepo = playerRepo,
            gameRepo = gameRepo,
            eloRepo = eloRepo,
            placementValidator = placementValidator,
            turnValidator = turnValidator,
            eloService = eloService,
            statisticsService = statisticsService
        )

    /**
     * ---------------------------------------------------------------------------------------------
     * Запуск Compose Desktop окна.
     * ---------------------------------------------------------------------------------------------
     */
    application {

        Window(
            onCloseRequest = ::exitApplication,
            title = "Администратор Морского боя",
            state = rememberWindowState(
                width = 800.dp,
                height = 600.dp
            )
        ) {
            AdminApp(ctrl)
        }
    }
}
