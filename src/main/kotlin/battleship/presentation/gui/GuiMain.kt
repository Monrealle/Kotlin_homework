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
import battleship.infrastructure.JsonEloRatingRepository
import battleship.infrastructure.JsonGameRepository
import battleship.infrastructure.JsonPlayerRepository
import kotlin.system.exitProcess

/**
 * =============================================================================================
 * Точка входа GUI-версии приложения.
 *
 * GUI работает поверх JSON-репозиториев, поэтому данные игроков, партий и рейтингов
 * сохраняются между запусками приложения.
 * =============================================================================================
 */
fun main() {
    val playerRepository: PlayerRepository =
        JsonPlayerRepository()

    val gameRepository: GameRepository =
        JsonGameRepository()

    val eloRatingRepository: EloRatingRepository =
        JsonEloRatingRepository()

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

    application {
        Window(
            onCloseRequest = {
                /*
                 * Закрываем GUI-приложение.
                 *
                 * Используем стандартный механизм JVM,
                 * так как `exitApplication` в текущей конфигурации
                 * Compose недоступен.
                 */
                exitProcess(0)
            },
            title = "Battleship Assistant",
            state = rememberWindowState(
                width = 1200.dp,
                height = 800.dp
            )
        ) {
            AdminApp(controller)
        }
    }
}
