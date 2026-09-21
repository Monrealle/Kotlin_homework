package battleship

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import battleship.domain.repository.*
import battleship.domain.service.*
import battleship.infrastructure.*
import battleship.presentation.gui.AdminApp
import battleship.presentation.gui.GuiController

/**
 * =============================================================================================
 * Точка входа приложения.
 *
 * Отвечает за сборку зависимостей приложения:
 *
 * 1. Создаёт репозитории.
 * 2. Создаёт Domain-сервисы.
 * 3. Создаёт GUI-контроллер.
 * 4. Запускает Compose Desktop приложение.
 * =============================================================================================
 */
fun main() {

    /* ─────────────────────────────────────────────────────────────────────────
       Infrastructure
       ──────────────────────────────────────────────────────────────────────── */

    /**
     * Репозиторий игроков с сохранением в JSON-файл.
     */
    val playerRepo = FilePlayerRepository()

    /**
     * Временный репозиторий партий в оперативной памяти.
     *
     * В дальнейшем может быть заменён на SQLite-реализацию
     * без изменения остальных слоёв.
     */
    val gameRepo = InMemoryGameRepository()

    /**
     * Репозиторий рейтингов Эло с сохранением в JSON-файл.
     */
    val eloRepo = FileEloRatingRepository()

    /* ─────────────────────────────────────────────────────────────────────────
       Domain services
       ──────────────────────────────────────────────────────────────────────── */

    val placementValidator =
        ShipPlacementValidatorImpl()

    val turnValidator =
        TurnValidatorImpl()

    val eloService =
        EloRatingServiceImpl()

    val statisticsService =
        StatisticsServiceImpl(
            gameRepository = gameRepo,
            eloRatingRepository = eloRepo
        )

    /* ─────────────────────────────────────────────────────────────────────────
       GUI controller
       ──────────────────────────────────────────────────────────────────────── */

    val ctrl = GuiController(
        playerRepo = playerRepo,
        gameRepo = gameRepo,
        eloRepo = eloRepo,
        placementValidator = placementValidator,
        turnValidator = turnValidator,
        eloService = eloService,
        statisticsService = statisticsService
    )

    /* ─────────────────────────────────────────────────────────────────────────
       Запуск Compose Desktop окна
       ───────────────────────────────────f───────────────────────────────────── */

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
