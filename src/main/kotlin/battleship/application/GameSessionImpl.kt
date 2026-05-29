package battleship.application

import battleship.domain.bot.MoveStrategy
import battleship.domain.model.*
import battleship.domain.service.*
import battleship.infrastructure.*
import java.util.UUID

/**
 * =============================================================================================
 * Реализация игровой сессии - координатора одной партии «Морского боя».
 *
 * Жизненный цикл игры:
 * 1. [startGame] - создаётся объект [Game] со статусом `SETUP_P1`.
 * 2. [placeShips] для первого игрока → статус `SETUP_P2` (если оба человека)
 *    или сразу `IN_PROGRESS` (если второй игрок - бот, его корабли уже расставлены).
 * 3. [placeShips] для второго игрока → статус `IN_PROGRESS`.
 * 4. [makeMove] вызывается многократно. После промаха ход переходит к оппоненту.
 *    При попадании/потоплении ход остаётся у того же игрока.
 * 5. При уничтожении всех кораблей одного из игроков игра завершается (`FINISHED`),
 *    вычисляется рейтинг Эло и сохраняется в репозиториях.
 *
 * Игра с ботом:
 * Если в конструктор передан [botStrategy], то считается, что [player2] - бот.
 * Его корабли генерируются автоматически при старте (через [RandomShipPlacer]).
 * После хода человека бот отвечает серией выстрелов (пока не промахнётся или не победит).
 * Все ходы бота добавляются в общий лог [Game.moves].
 *
 * @param placementValidator валидатор расстановки кораблей
 * @param turnValidator валидатор очередности и допустимости выстрела
 * @param eloService сервис расчёта рейтинга Эло
 * @param gameRepository репозиторий для сохранения игр
 * @param eloRatingRepository репозиторий текущих рейтингов игроков
 * @param botStrategy стратегия бота (null, если играют два человека)
 * =============================================================================================
 */
class GameSessionImpl(
    private val placementValidator: ShipPlacementValidator,
    private val turnValidator: TurnValidator,
    private val eloService: EloRatingService,
    private val gameRepository: GameRepository,
    private val eloRatingRepository: EloRatingRepository,
    private val botStrategy: MoveStrategy? = null
) : GameSession {

    private lateinit var game: Game

    /**
     * ---------------------------------------------------------------------------------------------
     * Создаёт новую партию.
     *
     * Если игра против бота - сразу расставляет корабли бота и переводит игру
     * в фазу расстановки только для первого игрока.
     * ---------------------------------------------------------------------------------------------
     */
    override fun startGame(p1: Player, p2: Player) {
        game = Game(id = UUID.randomUUID().toString(), player1 = p1, player2 = p2)
        gameRepository.save(game)

        if (botStrategy != null) {
            /* Бот расставляет корабли заранее */
            val botShips = RandomShipPlacer.generate()
            game.board2.placeShips(botShips)
            /* Ждём расстановки только у p1 */
            game.status = GameStatus.SETUP_P1
        }
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Принимает список кораблей от игрока, проверяет через [placementValidator]
     * и, если валидация пройдена, размещает их на доске игрока.
     * Переводит игру в следующий статус (`SETUP_P2` или `IN_PROGRESS`).
     *
     * @return [ValidationResult.success] при успехе, иначе - результат с ошибками
     * ---------------------------------------------------------------------------------------------
     */
    override fun placeShips(player: Player, ships: List<Ship>): ValidationResult {
        val validation = placementValidator.validate(ships)
        if (!validation.isValid) return validation

        when (player) {
            game.player1 -> {
                if (game.status != GameStatus.SETUP_P1)
                    return ValidationResult.failure(
                        "Невозможно расставить корабли для ${player.name}: статус игры ${game.status}"
                    )
                game.board1.placeShips(ships)
                game.status = if (botStrategy != null) GameStatus.IN_PROGRESS else GameStatus.SETUP_P2
            }
            game.player2 -> {
                if (game.status != GameStatus.SETUP_P2)
                    return ValidationResult.failure(
                        "Невозможно расставить корабли для ${player.name}: статус игры ${game.status}"
                    )
                game.board2.placeShips(ships)
                game.status = GameStatus.IN_PROGRESS
            }
            else -> return ValidationResult.failure(
                "Невозможно расставить корабли для ${player.name}: статус игры ${game.status}"
            )
        }

        gameRepository.save(game)
        return ValidationResult.success()
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Выполняет ход игрока.
     *
     * Алгоритм:
     * - Проверяет право хода через [turnValidator].
     * - Выполняет выстрел (см. [executeShot]).
     * - Если после выстрела ход переходит к боту - запускает серию ответных ходов
     *   (см. [executeBotTurn]).
     *
     * @throws IllegalArgumentException если ход нелегален (очерёдность, повтор, статус)
     * @return объект [Move], описывающий ход, совершённый игроком [player]
     * ---------------------------------------------------------------------------------------------
     */
    override fun makeMove(player: Player, coord: Coordinate): Move {
        val validation = turnValidator.canFire(game, player, coord)
        require(validation.isValid) { validation.errors.joinToString("; ") }

        val playerMove = executeShot(player, coord)

        /* Если ход переключился к боту — отыгрываем всю серию бота */
        if (game.status == GameStatus.IN_PROGRESS
            && botStrategy != null
            && game.currentTurn == game.player2) {
            executeBotTurn()
        }

        return playerMove
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Возвращает текущий объект [Game].
     *
     * Это живая ссылка - все последующие изменения в сессии будут видны через неё.
     * ---------------------------------------------------------------------------------------------
     */
    override fun getGame(): Game = game

    /**
     * ---------------------------------------------------------------------------------------------
     * Выполняет одиночный выстрел от имени [player] по координате [coord].
     *
     * - Наносит удар по доске оппонента.
     * - Создаёт запись [Move] и добавляет её в лог игры.
     * - Если выстрел приводит к победе - вызывает [finishGame].
     * - При промахе передаёт ход оппоненту.
     * ---------------------------------------------------------------------------------------------
     */
    private fun executeShot(player: Player, coord: Coordinate): Move {
        val opponentBoard = game.opponentBoardOf(player)
        val result = opponentBoard.receiveShot(coord)

        val move = Move(
            turnNumber = game.moves.size + 1, /* Номер хода                              */
            player = player,                  /* Игрок, сделавший выстрел                */
            coordinate = coord,               /* Координата, куда был произведён выстрел */
            result = result                   /* Исход выстрела (MISS, HIT, SUNK, WIN)   */
        )
        game.moves.add(move)

        when (result) {
            ShotResult.WIN -> finishGame(winner = player)
            ShotResult.MISS -> game.currentTurn = opponent(player)
            else -> { /* HIT / SUNK — ход остаётся у того же игрока */ }
        }

        gameRepository.save(game)
        return move
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Запускает цикл ходов бота: бот стреляет до тех пор, пока не промахнётся
     * или пока не одержит победу.
     * ---------------------------------------------------------------------------------------------
     */
    private fun executeBotTurn() {
        while (game.status == GameStatus.IN_PROGRESS
            && game.currentTurn == game.player2
            && botStrategy != null) {
            val coord = botStrategy.nextMove(game.board1, game.moves)
            executeShot(game.player2, coord)
        }
    }
    /**
     * ---------------------------------------------------------------------------------------------
     * Завершает игру победой [winner].
     * Рассчитывает и сохраняет изменения рейтинга Эло для обоих игроков.
     * ---------------------------------------------------------------------------------------------
     */
    private fun finishGame(winner: Player) {
        game.winner = winner
        game.status = GameStatus.FINISHED

        val loser         = opponent(winner)
        val winnerRating  = eloRatingRepository.findByPlayer(winner).rating
        val loserRating   = eloRatingRepository.findByPlayer(loser).rating
        val changes       = eloService.calculateRatings(winner, winnerRating, loser, loserRating)

        game.eloChanges = changes
        changes.values.forEach { ch ->
            eloRatingRepository.save(EloRating(ch.player, ch.newRating))
        }
    }

    private fun opponent(player: Player): Player =
        if (player == game.player1) game.player2 else game.player1
}

/**
 * =============================================================================================
 * Утилита для генерации случайной расстановки кораблей.
 *
 * Использует метод случайного поиска с ограничением в 1000 попыток:
 * - для каждого типа корабля подбирается случайная позиция, не пересекающаяся
 *   и не соприкасающаяся с уже размещёнными (включая диагонали).
 * - если за 1000 попыток не удалось разместить все корабли - выбрасывается ошибка.
 *
 * Применяется для автоматической расстановки флота бота и для опции
 * «Авторасстановка» в консольном интерфейсе.
 * =============================================================================================
 */
internal object RandomShipPlacer {

    private val FLEET = listOf(
        ShipType.BATTLESHIP to 1, /* 1 линкор (по 4 клетки)   */
        ShipType.CRUISER to 2,    /* 2 крейсера (по 3 клетки) */
        ShipType.DESTROYER to 3,  /* 3 эсминца (по 2 клетки)  */
        ShipType.BOAT to 4        /* 4 катера (по 1 клетке)   */
    )

    /**
     * ---------------------------------------------------------------------------------------------
     * Генерирует полную расстановку флота.
     * @throws IllegalStateException если не удалось подобрать расстановку за 1000 попыток
     * ---------------------------------------------------------------------------------------------
     */
    fun generate(): List<Ship> {
        repeat(1000) {
            val result = tryPlace()
            if (result != null) return result
        }
        error("RandomShipPlacer: не удалось сгенерировать расстановку за 1000 попыток")
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Одна попытка разместить все корабли.
     * Возвращает список при успехе, иначе null.
     * ---------------------------------------------------------------------------------------------
     */
    private fun tryPlace(): List<Ship>? {
        val placed = mutableListOf<Ship>()
        /* Зона «буфера» вокруг уже размещённых кораблей (включая сами клетки) */
        val forbidden = mutableSetOf<Coordinate>()

        for ((type, count) in FLEET) {
            repeat(count) {
                val ship = randomShipFor(type, forbidden) ?: return null
                placed.add(ship)
                /* Запрещаем клетки корабля + все 8 соседей каждого сегмента */
                for (seg in ship.segments) {
                    for (dr in -1..1) for (dc in -1..1) {
                        Coordinate.ofOrNull(seg.row + dr, seg.col + dc)?.let { forbidden.add(it) }
                    }
                }
            }
        }
        return placed
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Пытается найти случайную позицию для корабля [type],
     * не попадающую в [forbidden]. Делает до 200 попыток.
     * ---------------------------------------------------------------------------------------------
     */
    private fun randomShipFor(type: ShipType, forbidden: Set<Coordinate>): Ship? {
        repeat(200) {
            val horizontal = (0..1).random() == 0
            val row = (0..9).random()
            val col = (0..9).random()
            val segments = if (horizontal) {
                (0 until type.size).mapNotNull { Coordinate.ofOrNull(row, col + it) }
            } else {
                (0 until type.size).mapNotNull { Coordinate.ofOrNull(row + it, col) }
            }
            if (segments.size == type.size && segments.none { it in forbidden }) {
                return Ship(type, segments)
            }
        }
        return null
    }
}
