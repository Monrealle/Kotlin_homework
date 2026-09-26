package battleship.infrastructure

import battleship.domain.model.Board
import battleship.domain.model.CellState
import battleship.domain.model.Coordinate
import battleship.domain.model.EloChange
import battleship.domain.model.EloRating
import battleship.domain.model.Game
import battleship.domain.model.GameStatus
import battleship.domain.model.Move
import battleship.domain.model.Player
import battleship.domain.model.Ship
import battleship.domain.model.ShipType
import battleship.domain.model.ShotResult
import battleship.domain.repository.EloRatingRepository
import battleship.domain.repository.GameRepository
import battleship.domain.repository.PlayerRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * =============================================================================================
 * JSON-хранилища проекта.
 *
 * Используются только GUI-версией приложения.
 * Консольная версия продолжает работать с in-memory репозиториями.
 *
 * Состояние хранится в трёх JSON-файлах:
 *
 * - `players.json` - зарегистрированные игроки;
 * - `ratings.json` - текущие рейтинги;
 * - `games.json` - история партий, включая расстановки и ходы.
 *
 * Доменный слой ничего не знает о JSON и работает только с интерфейсами репозиториев.
 * =============================================================================================
 */
private object JsonStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun <T> readList(path: Path, decode: (String) -> T): T {
        return if (path.exists()) {
            decode(path.readText())
        } else {
            error("Не найден JSON-файл: $path")
        }
    }

    fun <T> write(path: Path, value: T, encode: (T) -> String) {
        path.parent?.createDirectories()
        path.writeText(encode(value))
    }

    fun encodePlayers(value: List<PlayerDto>): String = json.encodeToString(value)
    fun decodePlayers(value: String): List<PlayerDto> = json.decodeFromString(value)

    fun encodeRatings(value: List<RatingDto>): String = json.encodeToString(value)
    fun decodeRatings(value: String): List<RatingDto> = json.decodeFromString(value)

    fun encodeGames(value: List<GameDto>): String = json.encodeToString(value)
    fun decodeGames(value: String): List<GameDto> = json.decodeFromString(value)
}

@Serializable
private data class PlayerDto(
    val id: String,
    val name: String
)

@Serializable
private data class RatingDto(
    val player: PlayerDto,
    val rating: Int
)

@Serializable
private data class CoordinateDto(
    val row: Int,
    val col: Int
)

@Serializable
private data class ShipDto(
    val type: String,
    val segments: List<CoordinateDto>
)

@Serializable
private data class BoardDto(
    val owner: PlayerDto,
    val ships: List<ShipDto>,
    val hitCells: List<CoordinateDto>,
    val missCells: List<CoordinateDto>
)

@Serializable
private data class MoveDto(
    val turnNumber: Int,
    val player: PlayerDto,
    val coordinate: CoordinateDto,
    val result: String
)

@Serializable
private data class EloChangeDto(
    val player: PlayerDto,
    val oldRating: Int,
    val newRating: Int,
    val delta: Int
)

@Serializable
private data class GameDto(
    val id: String,
    val player1: PlayerDto,
    val player2: PlayerDto,
    val board1: BoardDto,
    val board2: BoardDto,
    val currentTurn: PlayerDto,
    val status: String,
    val moves: List<MoveDto>,
    val winner: PlayerDto?,
    val eloChanges: List<EloChangeDto>?
)

/**
 * =============================================================================================
 * JSON-репозиторий игроков.
 * =============================================================================================
 */
class JsonPlayerRepository(
    private val file: Path = Path.of("data", "players.json")
) : PlayerRepository {

    private val store = linkedMapOf<String, Player>()

    init {
        load()
    }

    /**
     * ---------------------------------------------------------------------------------------------
     * Сохраняет игрока и сразу записывает обновлённый список в JSON.
     * ---------------------------------------------------------------------------------------------
     */
    override fun save(player: Player) {
        store[player.id] = player
        persist()
    }

    override fun findAll(): List<Player> = store.values.toList()

    override fun findById(id: String): Player? = store[id]

    override fun findByName(name: String): Player? =
        store.values.find { it.name.equals(name.trim(), ignoreCase = true) }

    private fun load() {
        if (!file.exists()) {
            file.parent?.createDirectories()
            file.writeText("[]")
            return
        }

        JsonStorage.decodePlayers(file.readText()).forEach { dto ->
            store[dto.id] = dto.toDomain()
        }
    }

    private fun persist() {
        JsonStorage.write(
            file,
            store.values.map(Player::toDto),
            { value -> JsonStorage.encodePlayers(value) }
        )
    }
}

/**
 * =============================================================================================
 * JSON-репозиторий рейтингов Эло.
 * =============================================================================================
 */
class JsonEloRatingRepository(
    private val file: Path = Path.of("data", "ratings.json")
) : EloRatingRepository {

    private val store = mutableMapOf<String, EloRating>()

    init {
        load()
    }

    override fun save(rating: EloRating) {
        store[rating.player.id] = rating
        persist()
    }

    override fun findByPlayer(player: Player): EloRating =
        store[player.id] ?: EloRating(player)

    private fun load() {
        if (!file.exists()) {
            file.parent?.createDirectories()
            file.writeText("[]")
            return
        }

        JsonStorage.decodeRatings(file.readText()).forEach { dto ->
            val player = dto.player.toDomain()
            store[player.id] = EloRating(player, dto.rating)
        }
    }

    private fun persist() {
        JsonStorage.write(
            file,
            store.values.map { RatingDto(it.player.toDto(), it.rating) },
            { value -> JsonStorage.encodeRatings(value) }
        )
    }
}

/**
 * =============================================================================================
 * JSON-репозиторий игровых партий.
 *
 * В JSON сохраняется не только краткая информация о партии, но и полное состояние:
 * игроки, корабли обеих досок, попадания, промахи, все ходы, победитель и изменения Эло.
 * =============================================================================================
 */
class JsonGameRepository(
    private val file: Path = Path.of("data", "games.json")
) : GameRepository {

    private val store = linkedMapOf<String, Game>()

    init {
        load()
    }

    override fun save(game: Game) {
        store[game.id] = game
        persist()
    }

    override fun findAll(): List<Game> = store.values.toList()

    override fun findById(id: String): Game? = store[id]

    override fun findByPlayer(player: Player): List<Game> =
        store.values.filter { it.player1 == player || it.player2 == player }

    private fun load() {
        if (!file.exists()) {
            file.parent?.createDirectories()
            file.writeText("[]")
            return
        }

        JsonStorage.decodeGames(file.readText()).forEach { dto ->
            val game = dto.toDomain()
            store[game.id] = game
        }
    }

    private fun persist() {
        JsonStorage.write(
            file,
            store.values.map(Game::toDto),
            { value -> JsonStorage.encodeGames(value) }
        )
    }
}

private fun Player.toDto(): PlayerDto = PlayerDto(id, name)

private fun PlayerDto.toDomain(): Player = Player(id, name)

private fun Coordinate.toDto(): CoordinateDto = CoordinateDto(row, col)

private fun CoordinateDto.toDomain(): Coordinate = Coordinate(row, col)

private fun Ship.toDto(): ShipDto = ShipDto(
    type = type.name,
    segments = segments.map(Coordinate::toDto)
)

private fun ShipDto.toDomain(): Ship = Ship(
    type = ShipType.valueOf(type),
    segments = segments.map(CoordinateDto::toDomain)
)

private fun Board.toDto(): BoardDto = BoardDto(
    owner = owner.toDto(),
    ships = ships.map(Ship::toDto),
    hitCells = grid.filterValues { it == CellState.HIT }.keys.map(Coordinate::toDto),
    missCells = grid.filterValues { it == CellState.MISS }.keys.map(Coordinate::toDto)
)

private fun BoardDto.toDomain(): Board {
    val board = Board(owner.toDomain())
    val ships = ships.map(ShipDto::toDomain)
    if (ships.isNotEmpty()) {
        board.placeShips(ships)
    }

    hitCells.map(CoordinateDto::toDomain).forEach { board.grid[it] = CellState.HIT }
    missCells.map(CoordinateDto::toDomain).forEach { board.grid[it] = CellState.MISS }
    return board
}

private fun Move.toDto(): MoveDto = MoveDto(
    turnNumber = turnNumber,
    player = player.toDto(),
    coordinate = coordinate.toDto(),
    result = result.name
)

private fun MoveDto.toDomain(): Move = Move(
    turnNumber = turnNumber,
    player = player.toDomain(),
    coordinate = coordinate.toDomain(),
    result = ShotResult.valueOf(result)
)

private fun EloChange.toDto(): EloChangeDto = EloChangeDto(
    player = player.toDto(),
    oldRating = oldRating,
    newRating = newRating,
    delta = delta
)

private fun EloChangeDto.toDomain(): EloChange = EloChange(
    player = player.toDomain(),
    oldRating = oldRating,
    newRating = newRating,
    delta = delta
)

private fun Game.toDto(): GameDto = GameDto(
    id = id,
    player1 = player1.toDto(),
    player2 = player2.toDto(),
    board1 = board1.toDto(),
    board2 = board2.toDto(),
    currentTurn = currentTurn.toDto(),
    status = status.name,
    moves = moves.map(Move::toDto),
    winner = winner?.toDto(),
    eloChanges = eloChanges?.values?.map(EloChange::toDto)
)

private fun GameDto.toDomain(): Game {
    val player1 = player1.toDomain()
    val player2 = player2.toDomain()
    val game = Game(id, player1, player2)

    val board1Dto = board1
    val board2Dto = board2
    val restoredBoard1 = board1Dto.toDomain()
    val restoredBoard2 = board2Dto.toDomain()

    if (restoredBoard1.ships.isNotEmpty()) {
        game.board1.placeShips(restoredBoard1.ships)
        board1Dto.hitCells.map(CoordinateDto::toDomain).forEach { game.board1.grid[it] = CellState.HIT }
        board1Dto.missCells.map(CoordinateDto::toDomain).forEach { game.board1.grid[it] = CellState.MISS }
    }

    if (restoredBoard2.ships.isNotEmpty()) {
        game.board2.placeShips(restoredBoard2.ships)
        board2Dto.hitCells.map(CoordinateDto::toDomain).forEach { game.board2.grid[it] = CellState.HIT }
        board2Dto.missCells.map(CoordinateDto::toDomain).forEach { game.board2.grid[it] = CellState.MISS }
    }

    game.currentTurn = currentTurn.toDomain()
    game.status = GameStatus.valueOf(status)
    game.moves += moves.map(MoveDto::toDomain)
    game.winner = winner?.toDomain()
    game.eloChanges = eloChanges?.associateBy(
        keySelector = { it.player.toDomain() },
        valueTransform = EloChangeDto::toDomain
    )

    return game
}
