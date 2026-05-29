package battleship

import battleship.domain.service.*
import battleship.infrastructure.*

/**
 * =============================================================================================
 * Точка входа в приложение «Морской бой - Администратор».
 *
 * Назначение:
 * Выполняет роль композиционного корня (composition root): создаёт
 * все необходимые реализации репозиториев, сервисов и валидаторов.
 *
 * Порядок инициализации:
 * 1. In-memory репозитории - [InMemoryPlayerRepository], [InMemoryGameRepository],
 *    [InMemoryEloRatingRepository]. Хранят данные в памяти (без БД).
 * 2. Доменные сервисы - [ShipPlacementValidatorImpl], [TurnValidatorImpl],
 *    [EloRatingServiceImpl], [StatisticsServiceImpl]. Реализуют бизнес-логику.
 *
 * Запуск:
 * ./gradlew build
 * java -jar build/libs/battleship-assistant.jar
 * =============================================================================================
 */
fun main() {
    /* ----- Инфраструктура: in-memory хранилища ----- */
    val playerRepo = InMemoryPlayerRepository()
    val gameRepo = InMemoryGameRepository()
    val eloRepo = InMemoryEloRatingRepository()

    /* ----- Доменные сервисы ----- */
    val placementValidator = ShipPlacementValidatorImpl()
    val turnValidator = TurnValidatorImpl()
    val eloService = EloRatingServiceImpl()
    val statsService = StatisticsServiceImpl(gameRepo, playerRepo, eloRepo)

    println("Морской бой - Администратор готов к запуску.")
    /* Здесь будет создание и запуск графического интерфейса */
}
