package battleship.presentation.console

import battleship.application.PlacementParser
import battleship.application.GameSessionImpl
import battleship.domain.model.Coordinate
import battleship.domain.model.EloRating
import battleship.domain.model.GameStatus
import battleship.domain.model.Player
import battleship.domain.model.ShotResult
import battleship.domain.repository.EloRatingRepository
import battleship.domain.repository.GameRepository
import battleship.domain.repository.PlayerRepository
import battleship.domain.service.EloRatingService
import battleship.domain.service.ShipPlacementValidator
import battleship.domain.service.StatisticsService
import battleship.domain.service.TurnValidator
import java.io.BufferedReader
import java.io.PrintWriter
import java.io.Writer
import java.util.UUID

/**
 * =============================================================================================
 * Консольный интерфейс администратора.
 *
 * Отвечает за взаимодействие пользователя с приложением:
 * управление игроками, создание партий, ввод расстановок и ходов,
 * просмотр истории и статистики.
 *
 * Ввод и вывод передаются снаружи, поэтому пользовательские сценарии
 * можно воспроизводить в системных тестах через StringReader/StringWriter.
 * =============================================================================================
 */
class ConsoleApplication(
    private val input: BufferedReader,
    output: Writer,
    private val playerRepository: PlayerRepository,
    private val gameRepository: GameRepository,
    private val eloRatingRepository: EloRatingRepository,
    private val placementValidator: ShipPlacementValidator,
    private val turnValidator: TurnValidator,
    private val eloService: EloRatingService,
    private val statisticsService: StatisticsService
) {
    private val output = PrintWriter(output, true)

    /**
     * ---------------------------------------------------------------------------------------------
     * Запускает главный цикл консольного приложения.
     *
     * Через меню администратор может управлять игроками,
     * создавать партии, просматривать историю и статистику.
     * ---------------------------------------------------------------------------------------------
     */
    fun run() {
        output.println("=== Администратор Морского боя ===")
        output.println("Консольный администратор партий «Морской бой».")

        /* Незавершённые партии предыдущего запуска больше нельзя продолжить в этой сессии. */
        gameRepository.findAll()
            .filter { !it.isOver() }
            .forEach { game ->
                game.status = GameStatus.ABANDONED
                gameRepository.save(game)
            }

        while (true) {
            printMainMenu()
            when (readLine("Выберите пункт: ")?.trim()) {
                "1" -> managePlayers()
                "2" -> createGame()
                "3" -> showHistory()
                "4" -> showStatistics()
                "0", null -> {
                    output.println("Работа завершена.")
                    return
                }
                else -> output.println("Неизвестный пункт меню.")
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Выводит главное меню приложения.
     * ---------------------------------------------------------------------------------------------
     */
    private fun printMainMenu() {
        output.println()
        output.println("1. Игроки")
        output.println("2. Создать и провести партию")
        output.println("3. История партий")
        output.println("4. Статистика игроков")
        output.println("0. Выход")
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Открывает меню управления игроками.
     * ---------------------------------------------------------------------------------------------
     */
    private fun managePlayers() {
        while (true) {
            output.println()
            output.println("=== Игроки ===")
            output.println("1. Добавить игрока")
            output.println("2. Список игроков")
            output.println("0. Назад")
            when (readLine("Выберите пункт: ")?.trim()) {
                "1" -> addPlayer()
                "2" -> listPlayers()
                "0", null -> return
                else -> output.println("Неизвестный пункт меню.")
            }
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Добавляет нового игрока и создаёт для него начальный рейтинг.
     * ---------------------------------------------------------------------------------------------
     */
    private fun addPlayer() {
        val name = readLine("Имя игрока: ")?.trim().orEmpty()
        if (name.isBlank()) {
            output.println("Ошибка: имя не может быть пустым.")
            return
        }
        if (playerRepository.findByName(name) != null) {
            output.println("Ошибка: игрок с таким именем уже существует.")
            return
        }

        val player = Player(UUID.randomUUID().toString(), name)
        playerRepository.save(player)
        eloRatingRepository.save(EloRating(player))
        output.println("Игрок '$name' добавлен. Начальный рейтинг: ${EloRating.INITIAL_RATING}.")
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Выводит зарегистрированных игроков и их текущий рейтинг.
     * ---------------------------------------------------------------------------------------------
     */
    private fun listPlayers() {
        val players = playerRepository.findAll()
        if (players.isEmpty()) {
            output.println("Игроков пока нет.")
            return
        }

        players.forEachIndexed { index, player ->
            val rating = eloRatingRepository.findByPlayer(player).rating
            output.println("${index + 1}. ${player.name} — рейтинг $rating")
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Создаёт партию, принимает расстановки обоих игроков
     * и запускает цикл занесения ходов.
     * ---------------------------------------------------------------------------------------------
     */
    private fun createGame() {
        val players = playerRepository.findAll()
        if (players.size < 2) {
            output.println("Нужно добавить хотя бы двух игроков.")
            return
        }

        output.println("\n=== Создание партии ===")
        players.forEachIndexed { index, player ->
            output.println("${index + 1}. ${player.name} — рейтинг ${eloRatingRepository.findByPlayer(player).rating}")
        }

        val first = choosePlayer(players, "Номер первого игрока: ") ?: return
        val second = choosePlayer(players, "Номер второго игрока: ") ?: return
        if (first == second) {
            output.println("Нельзя выбрать одного и того же игрока дважды.")
            return
        }

        val session = GameSessionImpl(
            placementValidator,
            turnValidator,
            eloService,
            gameRepository,
            eloRatingRepository
        )
        session.startGame(first, second)
        output.println("Партия создана: #${session.getGame().id.take(8)}")

        if (!placeShips(session, first)) {
            abandonGame(session)
            return
        }

        if (!placeShips(session, second)) {
            abandonGame(session)
            return
        }

        playGame(session)
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Принимает и проверяет ручную расстановку кораблей игрока.
     *
     * На каждую расстановку отводится до пяти попыток.
     * ---------------------------------------------------------------------------------------------
     */
    private fun placeShips(session: GameSessionImpl, player: Player): Boolean {
        output.println()
        output.println("=== Расстановка для ${player.name} ===")
        output.println("Введите 10 кораблей через ';'. Формат: A1-A4 для корабля или J10 для катера.")
        output.println("Пример: A1-A4; C1-C3; F1-F3; A6-A7; C6-C7; E6-E7; A10; C10; E10; G10")

        repeat(5) { attempt ->
            val raw = readLine("Расстановка (${attempt + 1}/5): ") ?: return false
            val ships = PlacementParser.parse(raw).getOrElse { error ->
                output.println("Ошибка формата: ${error.message}")
                return@repeat
            }
            val validation = session.placeShips(player, ships)
            if (validation.isValid) {
                output.println("Расстановка принята.")
                return true
            }
            validation.errors.forEach { output.println("- $it") }
        }

        output.println("Не удалось принять расстановку. Партия отменена.")
        return false
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Помечает текущую партию как прерванную и сохраняет её в истории.
     * ---------------------------------------------------------------------------------------------
     */
    private fun abandonGame(session: GameSessionImpl) {
        val game = session.getGame()

        if (!game.isOver()) {
            game.status = GameStatus.ABANDONED
            gameRepository.save(game)
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Запускает цикл ввода ходов и завершает партию при победе.
     * ---------------------------------------------------------------------------------------------
     */
    private fun playGame(session: GameSessionImpl) {
        val game = session.getGame()
        output.println("\n=== Партия началась ===")
        output.println("Ход всегда остаётся у игрока после HIT/SUNK и переходит сопернику после MISS.")

        while (game.status == GameStatus.IN_PROGRESS) {
            val player = game.currentTurn
            output.println()
            output.println("Ход игрока: ${player.name}")
            game.moves.takeLast(5).forEach { move ->
                output.println("  ${move.turnNumber}. ${move.player.name}: ${move.coordinate.toDisplayString()} -> ${move.result}")
            }

            val input = readLine("Введите координату (A1-J10) или 0 для отмены: ")?.trim()
            if (input == "0" || input == null) {
                game.status = GameStatus.ABANDONED
                gameRepository.save(game)
                output.println("Партия помечена как прерванная и сохранена в истории.")
                return
            }

            val coord = Coordinate.fromString(input)
            if (coord == null) {
                output.println("Неверная координата. Допустимы A1-J10.")
                continue
            }

            try {
                val move = session.makeMove(player, coord)
                output.println("Результат: ${move.result}")
                if (move.result == ShotResult.WIN) {
                    output.println("Победил ${player.name}!")
                }
            } catch (error: IllegalArgumentException) {
                output.println("Ход отклонён: ${error.message}")
            }
        }

        output.println()
        output.print(GameHistoryFormatter.format(game))
        output.flush()
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Выводит сохранённую в базе данных историю партий.
     * ---------------------------------------------------------------------------------------------
     */
    private fun showHistory() {
        val games = gameRepository.findAll()
        if (games.isEmpty()) {
            output.println("История партий пуста.")
            return
        }
        games.forEach { game ->
            output.println()
            output.print(GameHistoryFormatter.format(game))
        }
        output.flush()
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Выводит локальную статистику зарегистрированных игроков.
     * ---------------------------------------------------------------------------------------------
     */
    private fun showStatistics() {
        val players = playerRepository.findAll()
        if (players.isEmpty()) {
            output.println("Игроков пока нет.")
            return
        }

        output.println("\n=== Статистика ===")
        players.forEach { player ->
            output.println("${player.name}: ${statisticsService.getStats(player).display()}")
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Читает номер игрока из списка и возвращает выбранного игрока.
     * ---------------------------------------------------------------------------------------------
     */
    private fun choosePlayer(players: List<Player>, prompt: String): Player? {
        val number = readLine(prompt)?.trim()?.toIntOrNull()
        if (number == null || number !in 1..players.size) {
            output.println("Неверный номер игрока.")
            return null
        }
        return players[number - 1]
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Выводит приглашение и читает одну строку из входного потока.
     * ---------------------------------------------------------------------------------------------
     */
    private fun readLine(prompt: String): String? {
        output.print(prompt)
        output.flush()
        return input.readLine()
    }


}
