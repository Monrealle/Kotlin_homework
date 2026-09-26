package battleship

import battleship.domain.repository.EloRatingRepository
import battleship.domain.repository.GameRepository
import battleship.domain.repository.PlayerRepository
import battleship.domain.service.EloRatingServiceImpl
import battleship.domain.service.ShipPlacementValidatorImpl
import battleship.domain.service.StatisticsServiceImpl
import battleship.domain.service.TurnValidatorImpl
import battleship.infrastructure.InMemoryEloRatingRepository
import battleship.infrastructure.InMemoryGameRepository
import battleship.infrastructure.InMemoryPlayerRepository
import battleship.presentation.console.ConsoleApplication
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * =============================================================================================
 * Точка входа приложения.
 *
 * Отвечает за сборку зависимостей консольного приложения:
 *
 * 1. Создаёт in-memory репозитории.
 * 2. Создаёт Domain-сервисы.
 * 3. Передаёт зависимости в консольное приложение.
 * 4. Запускает главный цикл консольного интерфейса.
 *
 * В hw2 данные хранятся только в памяти и пропадают после завершения программы.
 * База данных и GUI появятся на следующих этапах проекта.
 * =============================================================================================
 */
fun main() {

    /**
     * ---------------------------------------------------------------------------------------------
     * In-memory репозитории.
     *
     * Все данные игроков, партий и рейтингов хранятся
     * в оперативной памяти в течение одного запуска приложения.
     * ---------------------------------------------------------------------------------------------
     */
    val playerRepository: PlayerRepository = InMemoryPlayerRepository()
    val gameRepository: GameRepository = InMemoryGameRepository()
    val eloRatingRepository: EloRatingRepository = InMemoryEloRatingRepository()

    /**
     * ---------------------------------------------------------------------------------------------
     * Domain services.
     * ---------------------------------------------------------------------------------------------
     */
    val placementValidator = ShipPlacementValidatorImpl()
    val turnValidator = TurnValidatorImpl()
    val eloService = EloRatingServiceImpl()
    val statisticsService = StatisticsServiceImpl(gameRepository, eloRatingRepository)

    /**
     * ---------------------------------------------------------------------------------------------
     * Запуск консольного приложения администратора.
     * ---------------------------------------------------------------------------------------------
     */
    ConsoleApplication(
        input = BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8)),
        output = OutputStreamWriter(System.out, Charsets.UTF_8),
        playerRepository = playerRepository,
        gameRepository = gameRepository,
        eloRatingRepository = eloRatingRepository,
        placementValidator = placementValidator,
        turnValidator = turnValidator,
        eloService = eloService,
        statisticsService = statisticsService
    ).run()
}
