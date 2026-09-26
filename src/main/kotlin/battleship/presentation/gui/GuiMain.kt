package battleship.presentation.gui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import battleship.domain.repository.EloRatingRepository
import battleship.domain.repository.GameRepository
import battleship.domain.repository.PlayerRepository
import battleship.domain.service.EloRatingServiceImpl
import battleship.domain.service.ShipPlacementValidatorImpl
import battleship.domain.service.StatisticsServiceImpl
import battleship.domain.service.TurnValidatorImpl
import battleship.infrastructure.SqliteEloRatingRepository
import battleship.infrastructure.SqliteGameRepository
import battleship.infrastructure.SqlitePlayerRepository
import battleship.infrastructure.database.Database
import kotlin.system.exitProcess

/**
 * =============================================================================================
 * Точка входа GUI-версии приложения «Морской бой».
 *
 * GUI использует SQLite-репозитории, поэтому игроки, рейтинги и история партий
 * сохраняются между запусками приложения.
 * =============================================================================================
 */
fun main() {

    /**
     * ---------------------------------------------------------------------------------------------
     * Инициализация локальной SQLite-базы данных.
     * ---------------------------------------------------------------------------------------------
     */
    Database.init()

    /**
     * ---------------------------------------------------------------------------------------------
     * SQLite-репозитории.
     * ---------------------------------------------------------------------------------------------
     */
    val playerRepository: PlayerRepository =
        SqlitePlayerRepository()

    val gameRepository: GameRepository =
        SqliteGameRepository()

    val eloRatingRepository: EloRatingRepository =
        SqliteEloRatingRepository()

    /**
     * ---------------------------------------------------------------------------------------------
     * Domain services.
     * ---------------------------------------------------------------------------------------------
     */
    val placementValidator =
        ShipPlacementValidatorImpl()

    val turnValidator =
        TurnValidatorImpl()

    val eloService =
        EloRatingServiceImpl()

    val statisticsService =
        StatisticsServiceImpl(
            gameRepository,
            eloRatingRepository
        )

    /**
     * ---------------------------------------------------------------------------------------------
     * GUI controller.
     * ---------------------------------------------------------------------------------------------
     */
    val controller =
        GuiController(
            playerRepo = playerRepository,
            gameRepo = gameRepository,
            eloRepo = eloRatingRepository,
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
            onCloseRequest = {
                exitProcess(0)
            },
            title = "Администратор Морского боя",
            state = rememberWindowState(
                width = 1200.dp,
                height = 800.dp
            )
        ) {
            AdminApp(controller)
        }
    }
}
